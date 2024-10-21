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
import com.aldebaran.qi.sdk.`object`.actuation.Animate
import com.aldebaran.qi.sdk.`object`.actuation.Animation
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

private const val TAG = "MainActivity"
private const val SILENCE_THRESHOLD: Long = 300

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {

    private var qiContext: QiContext? = null
    private var coroutineJob: Job? = null

    // Needed for the microphone
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var userSpeechTextView: TextView
    private lateinit var robotSpeechTextView: TextView
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

    // Declare the variables without initializing them
    private lateinit var serverCommunicationManager: ServerCommunicationManager
    private lateinit var serverIp: String  // Declare serverIp as a class property
    private lateinit var openAIApiKey: String

    private lateinit var fileStorageManager: FileStorageManager
    private lateinit var conversationState: ConversationState

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

        userSpeechTextView = findViewById(R.id.userSpeechTextView)
        robotSpeechTextView = findViewById(R.id.robotSpeechTextView)
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
                val intent = Intent(this, SettingsActivity::class.java)
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
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun recalibrateThreshold() {
        lifecycleScope.launch(Dispatchers.IO) {
            val newThreshold = audioRecorder.recalibrateThreshold()
            withContext(Dispatchers.Main) {
                thresholdTextView.text = "Soglia del rumore: $newThreshold"
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

        Log.i(TAG, "Entering onRobotFocusGained and creating ServerCommunicationManager")

        // Initialize ServerCommunicationManager now that qiContext is available
        serverCommunicationManager = ServerCommunicationManager(this, serverIp, serverPort, openAIApiKey)

        // Start dialogue with Pepper when QiContext is ready
        coroutineJob = lifecycleScope.launch {
            Log.d(TAG, "Starting Dialogue in a Coroutine")
            startDialogue()
        }
    }

    // Function to parse the XML string and extract content
    private fun parseXmlString(xmlString: String): String {
        // Create a new Document from the XML string
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val inputStream = xmlString.byteInputStream()
        val document = builder.parse(inputStream)
        document.documentElement.normalize()

        // Extract the profile_id element
        val profileIdElement = document.getElementsByTagName("profile_id").item(0) as Element

        // Extract the text content before any child elements (e.g., before <language> and <speaking_time>)
        val sentenceBuilder = StringBuilder()
        val nodeList = profileIdElement.childNodes

        for (i in 0 until nodeList.length) {
            val currentNode = nodeList.item(i)
            if (currentNode.nodeType == Node.TEXT_NODE) {
                sentenceBuilder.append(currentNode.nodeValue.trim())
            }
        }

        val sentence = sentenceBuilder.toString()

        // Extract the language element
        val languageElement = profileIdElement.getElementsByTagName("language").item(0).textContent

        // Extract the speaking_time element
        val speakingTimeElement = profileIdElement.getElementsByTagName("speaking_time").item(0).textContent
        val speakingTime = speakingTimeElement.toDoubleOrNull() ?: 0.0

        // Return the parsed values as a triple
        return sentence
    }

    private suspend fun startDialogue() {
        initializeUserSession()

        conversationState = ConversationState(
            fileStorageManager,
            previousSentence
        )
        withContext(Dispatchers.IO) {
            conversationState.loadFromFile()
        }

        lastActiveSpeakerTime = System.currentTimeMillis()

        while (isAlive) {
            // Check if the conversation is ongoing
            if ((System.currentTimeMillis() - lastActiveSpeakerTime) > SILENCE_THRESHOLD * 1000) {
                Log.i(TAG, "Silence threshold exceeded - setting ongoing conversation to false.")
                sayMessage("It seems the conversation has ended due to silence.")
                break
            }

            // Listening to the user input
            val xmlString = startListening()
            lastActiveSpeakerTime = System.currentTimeMillis()
            handleUserInput(xmlString)
            withContext(Dispatchers.IO) {
                conversationState.writeToFile()
            }
        }
    }

    private suspend fun initializeUserSession() {
        val firstSentence: String

        Log.d(TAG, "Initializing user session")
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

            Log.i("initializeUserSession", "First request response: dialogue state")
            Log.i("initializeUserSession", firstRequestResponse.dialogueState.toString())

            val dialogueState = DialogueState(firstRequestResponse.dialogueState)

            // Initialize variables
            val profileId = "00000000-0000-0000-0000-000000000000"

            // Determine the user name based on the language
            val userName = if (language == "it-IT") "Utente" else "User"

            // Create the SpeakerInfo object
            // Create the map of speaker attributes
            val speakerAttributes = mapOf(
                "name" to userName,
                "gender" to "nb",  // 'nb' stands for non-binary
                "age" to "nd"      // 'nd' stands for 'not determined'
            )

            // Create the SpeakersInfo object and add the speaker
            val speakersInfo = SpeakersInfo(
                speakers = mutableMapOf(profileId to speakerAttributes)
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
                fileStorageManager.writeToFile(speakersInfo)
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
        robotSpeechTextView.text = "Pepper: $firstSentence"
        sayMessage(firstSentence)
        // Store the welcome message in previousSentence
        previousSentence = firstSentence
    }

    private fun generateSimpleXmlString(sentence: String): String {
        return """
            <response><profile_id value="00000000-0000-0000-0000-000000000000">$sentence<language>$language</language><speaking_time>0.0</speaking_time></profile_id></response>
    """.trimIndent()
    }

    private suspend fun startListening(): String {
        Log.i(TAG, "Begin startListening.")

        // Show the "Listening..." status in textView and clear the robot's speech TextView
        withContext(Dispatchers.Main) {
            //robotSpeechTextView.text = ""  // Clear the robot's speech TextView
            userSpeechTextView.text = "Sto ascoltando..."  // Show "Listening..." in the user input TextView
        }

        // Perform the listening operation in the background
        val result = withContext(Dispatchers.IO) {
            audioRecorder.listenAndSplit() // Start listening and get the result
        }

        // After listening is done, update the UI with the result
        withContext(Dispatchers.Main) {
            userSpeechTextView.text = "Utente: $result"  // Update the user input TextView with the recognized result
        }

        // Simulate XML result generation (or actual logic)
        val xmlString = generateSimpleXmlString(result)

        // Restart listening if the result is blank
        return xmlString.ifBlank {
            startListening()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "OnPause, cancelling coroutine")
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
                    Log.e(TAG, "Errore durante Say: ${e.message}")
                }
            } else {
                Log.e(TAG, "Il focus non è disponibile, Say non può essere eseguito.")
            }
        }
    }

    private suspend fun handleUserInput(xmlString: String) {
        val sentence = parseXmlString(xmlString)

        if (exitKeywords.any { sentence.contains(it, ignoreCase = true) }) {
            sayMessage("È stato bello parlare con te. A presto!")
            isAlive = false
            return
        }

        if (repeatKeywords.any { sentence.contains(it, ignoreCase = true) }) {
            if (previousSentence.isNotEmpty()) {
                sayMessage("Ho detto: $previousSentence")
            } else {
                sayMessage("Mi dispiace, non ho niente da ripetere!")
            }
            return
        }

        // Normal interaction
        if (sentence.isNotBlank() && sentence != "Timeout") {

            val profileId = "00000000-0000-0000-0000-000000000000"

            // Add the user's sentence to conversationHistory before the request
            conversationState.dialogueState.conversationHistory.add(
                mapOf("role" to "user", "content" to sentence)
            )

            // Ensure only the last 5 entries are kept
            if (conversationState.dialogueState.conversationHistory.size > 5) {
                conversationState.dialogueState.conversationHistory =
                    conversationState.dialogueState.conversationHistory.takeLast(5).toMutableList()
            }

            // Perform the first hubRequest and capture the updated conversationState
            val updatedConversationState = serverCommunicationManager.hubRequest("reply",
                xmlString, language, conversationState, profileId,
                conversationState.dialogueState.topic, listOf(),
                DueIntervention(type = null, exclusive = false, sentence = "")
            )

            if (updatedConversationState != null) {
                conversationState = updatedConversationState
                var replySentence = ""
                if (conversationState.plan != ""){
                    replySentence = conversationState.planSentence.toString()
                } else {
                    replySentence = conversationState.dialogueState.dialogueSentence[0][1]
                }
                Log.i(TAG, "Reply sentence: $replySentence")

                // Replace any $prevspk tags, preserving surrounding spaces
                val patternPrevspk = "\\s*,?\\s*\\\$prevspk\\s*,?\\s*".toRegex()
                replySentence = replySentence.replace(patternPrevspk, " ")

                // Add the assistant's sentence to conversationHistory after the response
                conversationState.dialogueState.conversationHistory.add(
                    mapOf("role" to "assistant", "content" to replySentence)
                )

                // Ensure only the last 5 entries are kept
                if (conversationState.dialogueState.conversationHistory.size > 5) {
                    conversationState.dialogueState.conversationHistory =
                        conversationState.dialogueState.conversationHistory.takeLast(5).toMutableList()
                }

                // Copy conversationState for the second hubRequest
                val conversationStateCopy = conversationState.copy()

                // Start sayMessage and second hubRequest concurrently
                coroutineScope {
                    // Launch the second hub request in the background
                    val secondHubRequestJob = async(Dispatchers.IO) {
                        serverCommunicationManager.hubRequest(
                            "continuation", // Pass "continuation" as the reqType
                            xmlString, language, conversationStateCopy, profileId,
                            conversationStateCopy.dialogueState.topic, listOf(),
                            DueIntervention(type = null, exclusive = false, sentence = "")
                        )
                    }

                    // Say the replySentence synchronously
                    robotSpeechTextView.text = ("Pepper: $replySentence")
                    sayMessage(replySentence)

                    val job = launch(Dispatchers.IO) {
                        Log.d(TAG, "Launched the Animation job")
                        when (conversationState.plan) {
                            "#action=hello" -> {
                                Log.d(TAG, "Executing action: hello")
                                // Perform the hello animation
                                performAnimation(R.raw.hello)
                            }
                            "#action=hug" -> {
                                Log.d(TAG, "Executing action: hug")
                                // Perform the hug animation (replace with actual hug animation resource)
                                performAnimation(R.raw.hug)
                            }
                            "#action=handshake" -> {
                                Log.d(TAG, "Executing action: handshake")
                                // Perform the hug animation (replace with actual hug animation resource)
                                performAnimation(R.raw.handshake)
                            }
                            else -> {
                                Log.e(TAG, "No plan to perform")
                            }
                        }
                    }
                    Log.d(TAG, "Joining on the Animation job")
                    job.join()
                    Log.d(TAG, "Joined the Animation job")

                    // Wait for the second hub request to complete
                    val continuationConversationState = secondHubRequestJob.await()

                    if (continuationConversationState != null) {
                        // Update conversationState with the result from the second hubRequest
                        conversationState = continuationConversationState

                        // Get the assistant's second sentence
                        if (conversationState.dialogueState.dialogueSentence.size > 1 &&
                            conversationState.dialogueState.dialogueSentence[1].size > 1) {

                            var continuationSentence = conversationState.dialogueState.dialogueSentence[1][1]

                            // Replace any $desspk tags
                            val patternDesspk = "\\s*,?\\s*\\\$desspk\\s*,?\\s*".toRegex()
                            continuationSentence = continuationSentence.replace(patternDesspk, " ")

                            // Add the assistant's second sentence to conversationHistory
                            conversationState.dialogueState.conversationHistory.add(
                                mapOf("role" to "assistant", "content" to continuationSentence)
                            )

                            // Ensure only the last 5 entries are kept
                            if (conversationState.dialogueState.conversationHistory.size > 5) {
                                conversationState.dialogueState.conversationHistory =
                                    conversationState.dialogueState.conversationHistory.takeLast(5).toMutableList()
                            }

                            Log.i(TAG, "Continuation sentence: $continuationSentence")

                            // Now say the second assistant sentence after the animation is completed
                            robotSpeechTextView.text = ("Pepper: $continuationSentence")
                            sayMessage(continuationSentence)
                            Log.d(TAG, "Setting previousSentence as ${continuationSentence}")
                            previousSentence = continuationSentence

                        } else {
                            Log.e(TAG, "Dialogue sentence structure is unexpected. Cannot find assistant's second sentence.")
                        }
                    } else {
                        Log.e(TAG, "Failed to perform continuation hub request.")
                    }
                }
            } else {
                // Handle the case where the updated conversation state is null
                Log.e(TAG, "Failed to update conversation state.")
            }
        }
    }

    // Mark the function as 'suspend' to allow coroutine-based waiting
    private suspend fun performAnimation(animationRes: Int) {
        if (qiContext == null) {
            Log.e("MainActivity", "QiContext is not initialized.")
            return
        }

        try {
            // Build the animation off the main thread
            val myAnimation: Animation = withContext(Dispatchers.IO) {
                AnimationBuilder.with(qiContext)
                    .withResources(animationRes)
                    .build()
            }

            // Build the Animate action off the main thread
            val animate: Animate = withContext(Dispatchers.IO) {
                AnimateBuilder.with(qiContext)
                    .withAnimation(myAnimation)
                    .build()
            }

            Log.i(TAG, "About to start Animation")
            // Run the animation synchronously (blocks until the animation is complete)
            animate.run()

            // Once the animation completes successfully, you can proceed
            Log.i(TAG, "Animation completed successfully.")
        } catch (e: Exception) {
            // Handle errors in the animation process
            Log.e(TAG, "Error during animation: ${e.message}", e)
        }
    }
}