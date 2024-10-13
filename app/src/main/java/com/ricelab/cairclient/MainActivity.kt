package com.ricelab.cairclient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.ricelab.cairclient.libraries.*
import kotlinx.coroutines.*

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val TAG = "MainActivity"
private const val SILENCE_THRESHOLD: Long = 300

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {

    private var qiContext: QiContext? = null
    private var coroutineJob: Job? = null

    // Needed for the microphone
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var textView: TextView
    private lateinit var thresholdTextView: TextView
    private lateinit var recalibrateButton: Button

    // Network-related variables
    private var serverPort: Int = 12345 // Default value

    private var previousSentence: String = ""
    private var isAlive = true

    private val exitKeywords = listOf("esci dall'app")
    private val repeatKeywords = listOf("puoi ripetere", "ripeti", "non ho capito")
    private var language = "it-IT"

    // Time settings
    private var lastActiveSpeakerTime: Long = 0

    private lateinit var dialogueState : DialogueState

    // Declare the variables without initializing them
    private lateinit var serverCommunicationManager: ServerCommunicationManager
    private lateinit var serverIp: String  // Declare serverIp as a class property
    private lateinit var openAIApiKey: String

    private lateinit var fileStorageManager: FileStorageManager

    // Register for the audio permission request result
    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.e(TAG, "Permission to record audio was denied.")
            // Consider showing a dialog or toast to inform the user
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        QiSDK.register(this, this)

        // Retrieve stored server IP and OpenAI API key
        retrieveStoredValues()

        // Initialize file paths and other components that don't need qiContext


        fileStorageManager = FileStorageManager(this, filesDir)

        textView = findViewById(R.id.textView)
        thresholdTextView = findViewById(R.id.thresholdTextView)
        recalibrateButton = findViewById(R.id.recalibrateButton)
        audioRecorder = AudioRecorder(this)

        recalibrateButton.setOnClickListener {
            recalibrateThreshold()
        }
        checkAndRequestAudioPermission()
    }

    // Inflate the menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // Handle menu item clicks
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_setup -> {
                // Launch SetupActivity when the "Setup" menu item is clicked
                val intent = Intent(this, ConnectionSettingsActivity::class.java)
                intent.putExtra("fromMenu", true)  // Important for not redirecting back to MainActivity
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun retrieveStoredValues() {
        // Initialize MasterKey for encryption
        val masterKeyAlias = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Initialize EncryptedSharedPreferences
        val sharedPreferences = EncryptedSharedPreferences.create(
            this,
            "secure_prefs",
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Retrieve the stored server IP, OpenAI API key, and server port
        serverIp = sharedPreferences.getString("server_ip", null) ?: ""
        openAIApiKey = sharedPreferences.getString("openai_api_key", null) ?: ""
        serverPort = sharedPreferences.getInt("server_port", -1)

        if (serverIp.isEmpty() || openAIApiKey.isEmpty() || serverPort == -1) {
            // Redirect back to SetupActivity if values are missing
            val intent = Intent(this, ConnectionSettingsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun recalibrateThreshold() {
        lifecycleScope.launch(Dispatchers.IO) {
            val newThreshold = audioRecorder.recalibrateThreshold()
            withContext(Dispatchers.Main) {
                thresholdTextView.text = "Threshold: $newThreshold"
            }
        }
    }

    private fun checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted; listening will start when robot focus is gained
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO) // Request permission if not granted
        }
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext

        Log.i("MainActivity", "Entering onRobotFocusGained and creating ServerCommunicationManager")

        // Initialize ServerCommunicationManager now that qiContext is available
        serverCommunicationManager = ServerCommunicationManager(this, serverIp, serverPort, openAIApiKey)

        // Start dialogue with Pepper when QiContext is ready
        coroutineJob = lifecycleScope.launch {
            startDialogue()
        }
    }

    private suspend fun startDialogue() {
        initializeUserSession()

        val conversationState = ConversationState(
            fileStorageManager,
            previousSentence
        )
        withContext(Dispatchers.IO) {
            conversationState.loadConversationState()
        }
        lastActiveSpeakerTime = System.currentTimeMillis()

        while (isAlive) {
            // Check if the conversation is ongoing
            if ((System.currentTimeMillis() - lastActiveSpeakerTime) > SILENCE_THRESHOLD * 1000) {
                log("Silence threshold exceeded - setting ongoing conversation to false.")
                sayMessage("It seems the conversation has ended due to silence.")
                break
            }

            // Listening to the user input
            val userInput = startListening()
            lastActiveSpeakerTime = System.currentTimeMillis()
            handleUserInput(userInput)
        }
    }

    private suspend fun initializeUserSession() {
        val firstSentence: String

        if (!fileStorageManager.filesExist()) {
            // First-time user
            Log.i(TAG, "First user!")

            // Acquire initial state using ServerCommunicationManager
            val firstRequestResponse = try {
                withContext(Dispatchers.IO) {
                    serverCommunicationManager.firstServerRequest(language)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire initial state", e)
                sayMessage("Mi dispiace, non riesco a connettermi al server.")
                return
            }

            // Extract the welcome message and dialogue state from the server response
            firstSentence = firstRequestResponse.firstSentence

            dialogueState = DialogueState(firstRequestResponse.dialogueState)

// Initialize variables
            val profileId = "00000000-0000-0000-0000-000000000000"

// Determine the user name based on the language
            val userName = if (language == "it-IT") "Utente" else "User"

// Create the SpeakerInfo object
            val speakerInfo = SpeakerInfo(
                profileId = profileId,
                name = userName,
                gender = "nb",  // 'nb' stands for non-binary, based on your original code
                age = "nd"      // 'nd' stands for 'not determined' or undefined
            )

// Initialize DialogueStatistics with the profileId
            val dialogueStatistics = DialogueStatistics(
                mappingIndexSpeaker = mutableListOf(profileId),
                sameTurn = mutableListOf(mutableListOf(0)),
                successiveTurn = mutableListOf(mutableListOf(0)),
                averageTopicDistance = mutableListOf(mutableListOf(0.0)),
                speakersTurns = mutableListOf(0),
                aPrioriProb = mutableListOf(0.0),
                movingWindow = mutableListOf(),
                latestTurns = mutableListOf()
            )

            // Save the received data to files
            withContext(Dispatchers.IO) {
                fileStorageManager.writeToFile(dialogueState)
                fileStorageManager.writeToFile(speakerInfo)
                fileStorageManager.writeToFile(dialogueStatistics)
            }
        } else {
            // Returning user
            Log.i(TAG, "Returning user")
            firstSentence = if (language == "it-IT") {
                "È bello rivedervi! Di cosa vorreste parlare?"
            } else {
                "Welcome back! I missed you. What would you like to talk about?"
            }
        }

        // Say the welcome message using Pepper's text-to-speech
        sayMessage(firstSentence)
        // Store the welcome message in previousSentence
        previousSentence = firstSentence
    }

    private suspend fun startListening(): String {
        Log.i(TAG, "Begin startListening.")
        val result = withContext(Dispatchers.IO) {
            audioRecorder.listenAndSplit() // Start listening and get the result
        }
        withContext(Dispatchers.Main) {
            textView.text = "Result: $result" // Update the UI with the result
        }
        return result.ifBlank {
            startListening() // Restart listening if there's no result to say
        }
    }

    private suspend fun sayMessage(text: String) {
        // Build the Say action off the main thread
        withContext(Dispatchers.IO) {
            if (qiContext != null) {
                try {
                    Log.i("MainActivity", "Try sayMessage")
                    val say = SayBuilder.with(qiContext)
                        .withText(text)
                        .build()
                    say.run()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Errore durante Say: ${e.message}")
                }
            } else {
                Log.e("MainActivity", "Il focus non è disponibile, Say non può essere eseguito.")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "OnPause, cancelling coroutine")
        // Cancel the coroutine when the activity is paused
        coroutineJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        QiSDK.unregister(this, this)
        audioRecorder.stopRecording() // Stop any ongoing recording
    }

    override fun onRobotFocusLost() {
        this.qiContext = null
        Log.i(TAG, "Robot focus lost, stopping or pausing operations if necessary.")
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }

    private suspend fun handleUserInput(input: String) {
        if (exitKeywords.any { input.contains(it, ignoreCase = true) }) {
            sayMessage("È stato bello parlare con te. A presto!")
            isAlive = false
            return
        }

        if (repeatKeywords.any { input.contains(it, ignoreCase = true) }) {
            if (previousSentence.isNotEmpty()) {
                sayMessage("Ho detto: $previousSentence")
            } else {
                sayMessage("Mi dispiace, non ho niente da ripetere!")
            }
            return
        }

        // Normal interaction
        sayMessage("Hai detto: $input")
        previousSentence = input

        // Simulate sending data to the server or processing the input
        println("SENDING $input to server")
    }

    private fun log(message: String) {
        Log.d("CAIRclientAPP", message)
    }
}