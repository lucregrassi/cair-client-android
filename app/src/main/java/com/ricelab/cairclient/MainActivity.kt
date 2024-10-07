package com.ricelab.cairclient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import java.io.File
import com.google.gson.Gson
import com.ricelab.cairclient.libraries.*
import kotlinx.coroutines.*

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val TAG = "MainActivity"

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    private lateinit var qiContext: QiContext

    // Needed for the microphone
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var textView: TextView
    private lateinit var thresholdTextView: TextView
    private lateinit var recalibrateButton: Button

    // Network-related variables
    private val serverPort = 12345

    private var previousSentence: String = ""
    private var isAlive = true

    private val exitKeywords = listOf("esci dall'app")
    private val repeatKeywords = listOf("puoi ripetere", "ripeti", "non ho capito")
    private var language = "it-IT"

    // Time settings
    private val SILENCE_THRESHOLD: Long = 300
    private var lastActiveSpeakerTime: Long = 0

    // Declare the variables without initializing them
    private lateinit var dialogueStateFile: File
    private lateinit var speakersInfoFile: File
    private lateinit var dialogueStatisticsFile: File
    private lateinit var nuanceVectorsFile: File

    private val gson = Gson() // Initialize Gson instance
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
        dialogueStateFile = File(filesDir, "dialogue_state.json")
        speakersInfoFile = File(filesDir, "speakers_info.json")
        dialogueStatisticsFile = File(filesDir, "dialogue_statistics.json")
        nuanceVectorsFile = File(filesDir, "nuance_vectors.json")

        fileStorageManager = FileStorageManager(gson, filesDir)

        textView = findViewById(R.id.textView)
        thresholdTextView = findViewById(R.id.thresholdTextView)
        recalibrateButton = findViewById(R.id.recalibrateButton)
        audioRecorder = AudioRecorder(this)

        recalibrateButton.setOnClickListener {
            recalibrateThreshold()
        }
        checkAndRequestAudioPermission()
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

        // Retrieve the stored server IP and OpenAI API key
        serverIp = sharedPreferences.getString("server_ip", null) ?: ""
        openAIApiKey = sharedPreferences.getString("openai_api_key", null) ?: ""

        if (serverIp.isEmpty() || openAIApiKey.isEmpty()) {
            // Redirect back to SetupActivity if values are missing
            val intent = Intent(this, SetupActivity::class.java)
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
            SayBuilder.with(qiContext)
                .withText(text)
                .build().run()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        QiSDK.unregister(this, this)
        audioRecorder.stopRecording() // Stop any ongoing recording
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext

        // Initialize ServerCommunicationManager now that qiContext is available
        serverCommunicationManager = ServerCommunicationManager(this, serverIp, serverPort, openAIApiKey)

        // Start dialogue with Pepper when QiContext is ready
        lifecycleScope.launch {
            startDialogue()
        }
    }

    override fun onRobotFocusLost() {
        Log.i(TAG, "Robot focus lost, stopping or pausing operations if necessary.")
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }

    private suspend fun startDialogue() {
        initializeUserSession()
        val conversationLoader = ConversationStateLoader(
            dialogueStateFile,
            speakersInfoFile,
            dialogueStatisticsFile,
            previousSentence
        )
        withContext(Dispatchers.IO) {
            conversationLoader.loadConversationState()
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
        val welcomeMessage: String

        if (!speakersInfoFile.exists()) {
            // First-time user
            Log.i(TAG, "First user!")

            // Acquire initial state using ServerCommunicationManager
            val firstRequestResponse = withContext(Dispatchers.IO) {
                serverCommunicationManager.acquireInitialState(language)
            }
            // Extract the welcome message and dialogue state from the server response
            welcomeMessage = firstRequestResponse.firstSentence
        } else {
            // Returning user
            Log.i(TAG, "Returning user")
            welcomeMessage = if (language == "it-IT") {
                "È bello rivedervi! Di cosa vorreste parlare?"
            } else {
                "Welcome back! I missed you. What would you like to talk about?"
            }
        }

        // Say the welcome message using Pepper's text-to-speech
        sayMessage(welcomeMessage)

        // Store the welcome message in previousSentence
        previousSentence = welcomeMessage
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