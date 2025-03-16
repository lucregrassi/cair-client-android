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
import com.ricelab.cairclient.libraries.*
import kotlinx.coroutines.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aldebaran.qi.sdk.`object`.touch.Touch
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

private const val TAG = "MainActivity"
private const val SILENCE_THRESHOLD: Long = 300 // in seconds

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {

    private var qiContext: QiContext? = null
    private var coroutineJob: Job? = null

    // Microphone
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var userSpeechTextView: TextView
    private lateinit var robotSpeechTextView: TextView
    private lateinit var thresholdTextView: TextView
    private lateinit var recalibrateButton: Button

    // Network
    private var serverPort: Int = 12345 // Default value

    private var previousSentence: String = ""
    private var isAlive = true

    private val exitKeywords = listOf("esci dall'app")
    private val repeatKeywords = listOf("puoi ripetere", "non ho capito")
    private var language = "it-IT" // Default language is Italian

    var lastActiveSpeakerTime: Long = 0

    private lateinit var serverCommunicationManager: ServerCommunicationManager
    private lateinit var serverIp: String
    private lateinit var openAIApiKey: String
    private lateinit var personName: String
    private lateinit var personGender: String
    private lateinit var personAge: String
    private var useFillerSentence: Boolean = false
    private var autoDetectLanguage = false // default
    private var formalLanguage = false
    private var isTouchListenerAdded = false


    private lateinit var fileStorageManager: FileStorageManager
    private lateinit var conversationState: ConversationState
    private lateinit var personalizationServer: PersonalizationServer
    private lateinit var pepperInterface: PepperInterface
    private val scheduledInterventionsPort = 8000
    private var teleoperationManager: TeleoperationManager? = null
    private var sentenceGenerator: SentenceGenerator = SentenceGenerator()
    private var isListeningEnabled = true
    private var onGoingIntervention: DueIntervention? = null
    private var profileId: String = "00000000-0000-0000-0000-000000000000"

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.e(TAG, "Permission to record audio was denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        personalizationServer = PersonalizationServer(scheduledInterventionsPort)
        personalizationServer.startServer()

        pepperInterface = PepperInterface(null)

        setContentView(R.layout.activity_main)
        QiSDK.register(this, this)
        sentenceGenerator.loadFillerSentences(this)
        retrieveStoredValues() // autoDetectLanguage is now loaded here
        fileStorageManager = FileStorageManager(this, filesDir)

        userSpeechTextView = findViewById(R.id.userSpeechTextView)
        robotSpeechTextView = findViewById(R.id.robotSpeechTextView)
        thresholdTextView = findViewById(R.id.thresholdTextView)
        recalibrateButton = findViewById(R.id.recalibrateButton)

        // Pass autoDetectLanguage to AudioRecorder
        audioRecorder = AudioRecorder(this, autoDetectLanguage)
        // Assuming we modify AudioRecorder constructor to accept autoDetectLanguage boolean

        recalibrateButton.setOnClickListener {
            recalibrateThreshold()
        }
        checkAndRequestAudioPermission()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_setup -> {
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra("fromMenu", true)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun retrieveStoredValues() {
        val masterKeyAlias = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            this,
            "secure_prefs",
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        serverIp = sharedPreferences.getString("server_ip", null) ?: ""
        openAIApiKey = sharedPreferences.getString("openai_api_key", null) ?: ""
        serverPort = sharedPreferences.getInt("server_port", -1)
        personName = sharedPreferences.getString("person_name", "") ?: ""
        personGender = sharedPreferences.getString("person_gender", "") ?: ""
        personAge = sharedPreferences.getString("person_age", "") ?: ""
        useFillerSentence = sharedPreferences.getBoolean("use_filler_sentence", true)
        autoDetectLanguage = sharedPreferences.getBoolean("auto_detect_language", false)
        formalLanguage = sharedPreferences.getBoolean("use_formal_language", true)

        Log.i(TAG, "personName=$personName, personGender=$personGender, personAge=$personAge")
        Log.i(TAG, "useFillerSentence=$useFillerSentence")
        Log.i(TAG, "autoDetectLanguage=$autoDetectLanguage")
        Log.i(TAG, "formalLanguage=$formalLanguage")

        if (serverIp.isEmpty() || openAIApiKey.isEmpty() || serverPort == -1) {
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Permission granted
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun toggleListening() {
        isListeningEnabled = !isListeningEnabled

        if (!isListeningEnabled) {
            // Stop listening
            Log.i(TAG, "Stopping listening...")
            audioRecorder.stopRecording()
        } else {
            // Start listening
            Log.i(TAG, "Restarting listening...")
        }
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
        pepperInterface.setContext(this.qiContext)

        if (!isTouchListenerAdded) {
            // Retrieve the Touch service
            val touch: Touch = qiContext.touch

            Log.w(TAG, "ADDING TOUCH LISTENER!!!!!!!!")
            touch.getSensor("Head/Touch")?.addOnStateChangedListener { touchState ->
                if (touchState.touched) {
                    Log.i(TAG, "Head touched. Toggling listening...")
                    toggleListening()
                } else {
                    // Optional: do something if needed on release
                    Log.i(TAG, "Head touch released.")
                }
            }
            isTouchListenerAdded = true
        }

        teleoperationManager = TeleoperationManager(this, qiContext, pepperInterface)
        teleoperationManager?.startUdpListener()

        retrieveStoredValues()

        serverCommunicationManager =
            ServerCommunicationManager(this, serverIp, serverPort, openAIApiKey)

        coroutineJob = lifecycleScope.launch {
            startDialogue()
        }
    }

    override fun onRobotFocusLost() {
        teleoperationManager?.stopUdpListener()
        teleoperationManager = null
        this.qiContext = null
        pepperInterface.setContext(null)
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }

    private fun parseXmlForSentenceAndLanguage(xmlString: String): Pair<String, String> {
        //Log.w(TAG, "xmlString = $xmlString")
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val inputStream = xmlString.byteInputStream()
        val document = builder.parse(inputStream)
        document.documentElement.normalize()

        val profileIdElement = document.getElementsByTagName("profile_id").item(0) as Element
        val nodeList = profileIdElement.childNodes

        val sentenceBuilder = StringBuilder()
        var detectedLanguage = "it-IT"
        for (i in 0 until nodeList.length) {
            val currentNode = nodeList.item(i)
            if (currentNode.nodeType == Node.TEXT_NODE) {
                sentenceBuilder.append(currentNode.nodeValue.trim())
            }
            if (currentNode.nodeType == Node.ELEMENT_NODE && currentNode.nodeName == "language") {
                detectedLanguage = currentNode.textContent.trim()
            }
        }

        val sentence = sentenceBuilder.toString()
        return Pair(sentence, detectedLanguage)
    }

    private suspend fun startDialogue() {
        Log.i(TAG, "Starting dialogue")
        conversationState = ConversationState(fileStorageManager, previousSentence)

        initializeUserSession()

        withContext(Dispatchers.IO) {
          conversationState.loadFromFile()
        }
        // Update the value of formalLanguage in the DialogueState
        conversationState.dialogueState.formalLanguage = formalLanguage
        // Update the person gender and age but not the name as we want to keep it generic
        if (personGender.isNotEmpty()) {
            conversationState.speakersInfo.speakers[profileId]?.set("gender", personGender)
        }
        if (personAge.isNotEmpty()) {
            conversationState.speakersInfo.speakers[profileId]?.set("age", personAge)
        }

        lastActiveSpeakerTime = System.currentTimeMillis()

        while (isAlive) {
            // Check if listening is enabled
            if (!isListeningEnabled) {
                userSpeechTextView.text =
                    sentenceGenerator.getPredefinedSentence(language, "microphone")
                delay(500) // Sleep briefly and then keep checking
                continue
            }

            Log.i(TAG, "Time since last spoken words (ms) = ${System.currentTimeMillis() - lastActiveSpeakerTime}")
            conversationState.dialogueState.ongoingConversation =
                (System.currentTimeMillis() - lastActiveSpeakerTime) <= SILENCE_THRESHOLD * 1000
            Log.i(TAG, "OngoingConversation = ${conversationState.dialogueState.ongoingConversation}")

            if (onGoingIntervention == null) {
                onGoingIntervention = personalizationServer.getDueIntervention()
                if (onGoingIntervention != null) {
                    Log.d(TAG, "Entering handle for DueIntervention (onGoingIntervention = null)")
                    if (onGoingIntervention!!.type == "interaction_sequence") {
                        Log.d(TAG, "Resetting conversation history")
                        conversationState.dialogueState.resetConversation()
                    }
                    handle("")
                }
            } else if (onGoingIntervention!!.counter == 0) {
                Log.d(TAG, "Entering handle for DueIntervention (counter = 0)")
                if (onGoingIntervention!!.type == "interaction_sequence") {
                    Log.d(TAG, "Resetting conversation history")
                    conversationState.dialogueState.resetConversation()
                }
                handle("")
            }

            val xmlString = startListening()
            //handleUserInput(xmlString)
            Log.d(TAG, "Entering handle for xmlString = $xmlString")
            handle(xmlString)
            withContext(Dispatchers.IO) {
                conversationState.writeToFile()
            }
        }
    }

    private suspend fun initializeUserSession() {
        Log.i(TAG, "Initializing user session")
        val firstSentence: String
        if (!fileStorageManager.filesExist()) {
            val firstRequestResponse = try {
                withContext(Dispatchers.IO) {
                    serverCommunicationManager.firstServerRequest(language)
                }
            } catch (e: Exception) {
                pepperInterface.sayMessage(sentenceGenerator.getPredefinedSentence(language,"server_error"), language)
                return
            }

            conversationState.dialogueState.printDebug()
            firstSentence = firstRequestResponse.firstSentence

            val dialogueState = DialogueState(firstRequestResponse.dialogueState)
            // Update the value based on that taken from the settings activity so that it is
            // correctly saved in the file
            dialogueState.formalLanguage = formalLanguage

            val userName = if (language == "it-IT") "Utente" else "User"
            val userGender = personGender.ifEmpty { "nb" }
            val userAge = personAge.ifEmpty { "nd" }

            val speakerAttributes: MutableMap<String, Any?> = mutableMapOf(
                "name" to userName,
                "gender" to userGender,
                "age" to userAge
            )

            val speakersInfo = SpeakersInfo(
                speakers = mutableMapOf(profileId to speakerAttributes)
            )

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

            withContext(Dispatchers.IO) {
                fileStorageManager.writeToFile(dialogueState)
                fileStorageManager.writeToFile(speakersInfo)
                fileStorageManager.writeToFile(dialogueStatistics)
            }
        } else {
            Log.i(TAG, "generating predefined sentence")
            firstSentence = sentenceGenerator.getPredefinedSentence(language, "welcome_back")
        }
        if(firstSentence != "") {
            Log.i(TAG, "uttering first sentence")
            robotSpeechTextView.text = "Pepper: $firstSentence"
            pepperInterface.sayMessage(firstSentence, language)
            previousSentence = firstSentence
        } else {
            Log.e(TAG, "no first sentence!!!")
        }
    }

    private suspend fun startListening(): String {
        while (true) {
            userSpeechTextView.text = sentenceGenerator.getPredefinedSentence(language, "listening")

            val xmlResult = withContext(Dispatchers.IO) {
                audioRecorder.listenAndSplit()
            }

            val (userSentence, detectedLang) = parseXmlForSentenceAndLanguage(xmlResult)

            userSpeechTextView.text = "Utente: $userSentence"

            if (detectedLang.isNotEmpty() && detectedLang != "und") {
                language = detectedLang
            }

            if (xmlResult.isNotBlank()) {
                return xmlResult
            }
        }
    }

    override fun onPause() {
        super.onPause()
        coroutineJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        QiSDK.unregister(this, this)
        audioRecorder.stopRecording()
        teleoperationManager?.stopUdpListener()
        personalizationServer.stopServer()
    }


    // A helper function to persist the new formalLanguage to EncryptedSharedPreferences
    private fun updateFormalLanguageInPrefs(newValue: Boolean) {
        val masterKeyAlias = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            this,
            "secure_prefs",
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        with(sharedPreferences.edit()) {
            putBoolean("use_formal_language", newValue)
            apply()
        }

        Log.d(TAG, "Updated formal language in SharedPreferences -> $newValue")
    }

    private suspend fun handle(xmlStringInput: String) {
        val isIntervention = when {
            onGoingIntervention != null -> !onGoingIntervention!!.type.isNullOrEmpty()
            else -> false
        }
        Log.d(TAG, "Handle: isIntervention = $isIntervention")
        val xmlString = if (!xmlStringInput.isNullOrEmpty()) {
            xmlStringInput
        } else {
            if (isIntervention && onGoingIntervention!!.type != "interaction_sequence") {
                """
                <response>
                  <profile_id value="00000000-0000-0000-0000-000000000000">
                    ${onGoingIntervention!!.sentence}
                    <language>$language</language>
                    <speaking_time>0.0</speaking_time>
                  </profile_id>
                </response>
                """.trimIndent()
                    } else {
                """
                <response>
                  <profile_id value="00000000-0000-0000-0000-000000000000">*START*<language>$language</language>
                    <speaking_time>0.0</speaking_time>
                  </profile_id>
                </response>
                """.trimIndent()
            }
        }
        Log.d(TAG, "Handle: xmlString = $xmlString")

        val dueIntervention = if (!isIntervention) {
            DueIntervention(type = null, exclusive = false, sentence = "")
        } else {
            onGoingIntervention!!
        }

        var (sentence, detectedLang) = parseXmlForSentenceAndLanguage(xmlString)
        if (detectedLang.isNotEmpty() && detectedLang != "und") {
            language = detectedLang
        }
        Log.i(TAG, "*****$sentence*****")

        // Check for special keywords
        if (exitKeywords.any { sentence.contains(it, ignoreCase = true) }) {
            pepperInterface.sayMessage(sentenceGenerator.getPredefinedSentence(language, "goodbye"), language)
            isAlive = false
            return
        }

        if (repeatKeywords.any { sentence.contains(it, ignoreCase = true) }) {
            if (previousSentence.isNotEmpty()) {
                pepperInterface.sayMessage(sentenceGenerator.getPredefinedSentence(language, "repeat_previous") + " $previousSentence", language)
            } else {
                pepperInterface.sayMessage(sentenceGenerator.getPredefinedSentence(language, "nothing_to_repeat"), language)
            }
            return
        }

        // Proceed only if we have a meaningful user sentence
        if (sentence.isNotBlank() && (sentence != "*TIMEOUT*" || (isIntervention && onGoingIntervention!!.type == "interaction_sequence"))) {
            if (!xmlStringInput.isNullOrEmpty()) {
                lastActiveSpeakerTime = System.currentTimeMillis()
            }
            conversationState.dialogueState.updateConversation("user", sentence)

            // We'll store both jobs so we can await/join them
            val hubRequestDeferred: Deferred<ConversationState?>
            var fillerJob: Job? = null

            // Run them in parallel in a coroutineScope
            coroutineScope {
                val requestType = when (dueIntervention.type) {
                    "interaction_sequence" -> dueIntervention.type!!
                    else -> "reply"
                }
                Log.d(TAG, "handle: performing $requestType request")
                // 1) The Hub request, returning a Deferred
                hubRequestDeferred = async(Dispatchers.IO) {
                    serverCommunicationManager.hubRequest(
                        requestType,
                        xmlString,
                        language,
                        conversationState,
                        emptyList(),
                        dueIntervention
                    )
                }

                // 2) Say the filler sentence when it's not an intervention or when it's an
                // intervention of type interaction_sequence and the conversation is ongoing.
                // If the conversation is not ongoing, say it only if it's not the first sentence
                if ((!isIntervention ||
                    (dueIntervention.type == "interaction_sequence" && dueIntervention.counter != 0))
                     && useFillerSentence) {
                    fillerJob = launch(Dispatchers.IO) {
                        val randomFillerSentence = sentenceGenerator.getFillerSentence(language)

                        // Switch to Main thread for updating UI
                        withContext(Dispatchers.Main) {
                            robotSpeechTextView.text = "Pepper: $randomFillerSentence"
                        }
                        // Then speak the filler (in the Dispatchers.IO thread)
                        pepperInterface.sayMessage(randomFillerSentence, language)
                    }
                }
            }

            // At this point, the coroutineScope above is done,
            // so both the hubRequestDeferred and fillerJob have *started*,
            // but we haven't awaited them yet.

            // 3) Await the hub request result
            val updatedConversationState = hubRequestDeferred.await()
            // 4) Wait for the filler to finish speaking (if it was started)
            fillerJob?.join()

            // Now we can safely speak the reply *after* the filler is done
            if (updatedConversationState != null) {
                conversationState = updatedConversationState
                // Whenever the server sets the formal language during an interaction_sequence
                // (or at any other moment), store the new value in SharedPreferences if it changed.
                if (conversationState.dialogueState.formalLanguage != formalLanguage) {
                    formalLanguage = conversationState.dialogueState.formalLanguage
                    // Persist it to EncryptedSharedPreferences
                    updateFormalLanguageInPrefs(formalLanguage)
                }

                val replySentence = conversationState.getReplySentence(personName)
                if (!replySentence.isNullOrEmpty()) {
                    conversationState.dialogueState.updateConversation("assistant", replySentence)
                }

                coroutineScope {
                    Log.d("Debug", "before sayMessage $replySentence")

                    // Speak the reply sentence if present
                    var sayReplyJob: Job? = null
                    if (replySentence.isNotEmpty()) {
                        // Update UI on Main
                        withContext(Dispatchers.Main) {
                            robotSpeechTextView.text = ("Pepper: $replySentence")
                        }
                        // Actually speak it (on IO)
                        sayReplyJob = launch(Dispatchers.IO) {
                            pepperInterface.sayMessage(replySentence, language)
                        }
                    }

                    // Then perform the animation (this can also run in parallel or after speaking)
                    Log.d("Debug", "before performAnimationFromPlan")
                    performAnimationFromPlan(conversationState.plan)

                    // Decide if we do the continuation logic
                    if (!isIntervention || dueIntervention.type == "topic") {
                        val secondHubRequestJob = async(Dispatchers.IO) {
                            Log.d("Debug", "before hubRequest continuation")
                            serverCommunicationManager.hubRequest(
                                "continuation",
                                xmlString,
                                language,
                                conversationState,
                                listOf(),
                                DueIntervention(type = null, exclusive = false, sentence = "")
                            )
                        }
                        Log.d("Debug", "after performAnimationFromPlan")
                        val continuationConversationState = secondHubRequestJob.await()
                        Log.d("Debug", "after performAnimationFromPlan await")

                        if (continuationConversationState != null) {
                            conversationState = continuationConversationState
                            if (conversationState.dialogueState.dialogueSentence.size > 1 &&
                                conversationState.dialogueState.dialogueSentence[1].size > 1
                            ) {
                                var continuationSentence = conversationState.getLastContinuationSentence(personName)

                                if (continuationSentence.isNotEmpty()) {
                                    conversationState.dialogueState.updateConversation("assistant", continuationSentence)
                                    // Wait for the first reply to finish
                                    Log.d("Debug", "Waiting sayReplyJob")
                                    sayReplyJob?.join()
                                    // Update UI on Main
                                    withContext(Dispatchers.Main) {
                                        robotSpeechTextView.text = ("Pepper: $continuationSentence")
                                    }
                                    Log.d("Debug", "After sayReplyJob")

                                    pepperInterface.sayMessage(continuationSentence, language)
                                    previousSentence = continuationSentence
                                    conversationState.dialogueState.prevDialogueSentence =
                                        conversationState.dialogueState.dialogueSentence
                                } else {
                                    Log.i(TAG, "No continuation sentence")
                                }
                            } else {
                                Log.e(TAG, "No continuation sentence found")
                            }
                        } else {
                            Log.e(TAG, "Failed continuation hub request.")
                        }
                    } else {
                        // If we're in an intervention scenario
                        if (conversationState.dialogueState.ongoingConversation && dueIntervention.type == "action") {
                            Log.d(TAG, "Ongoing conversation = true && intervention == action")
                            coroutineScope {
                                val prefix = sentenceGenerator.getPredefinedSentence(language, "prefix_repeat")
                                val lastContinuationSentence = conversationState.getPreviousContinuationSentence(personName)

                                val repeatContinuation = "$prefix $lastContinuationSentence"
                                Log.i(TAG, "Repeat continuation: $repeatContinuation")

                                withContext(Dispatchers.Main) {
                                    robotSpeechTextView.text = ("Pepper: $repeatContinuation")
                                }
                                sayReplyJob?.join()
                                pepperInterface.sayMessage(repeatContinuation, language)
                            }
                        } else {
                            Log.d(TAG, "Ongoing conversation = false && intervention == action")
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to update conversation state.")
                // Handle the null case with a fallback reply sentence
                val fallbackSentence = when (language) {
                    "it-IT" -> "Scusa, non ho capito, puoi ripetere?"
                    "en-US" -> "Sorry, I didn't get it, can you repeat?"
                    else -> "Sorry, I didn't understand, could you repeat?"
                }

                coroutineScope {
                    // Update UI to display the fallback sentence
                    withContext(Dispatchers.Main) {
                        robotSpeechTextView.text = ("Pepper: $fallbackSentence")
                    }

                    // Speak the fallback sentence
                    launch(Dispatchers.IO) {
                        pepperInterface.sayMessage(fallbackSentence, language)
                    }
                }
            }
            audioRecorder.resetTimeout()
        }

        if (isIntervention) {
            if (onGoingIntervention!!.type == "topic" || onGoingIntervention!!.type == "action") {
                onGoingIntervention = null
            } else if (sentence.isNotBlank()) {
                Log.w(TAG, "Getting the next dueIntervention")
                onGoingIntervention = personalizationServer.getDueIntervention()
            }
        }
    }

    private suspend fun performAnimationFromPlan(plan: String?) {
        if (!plan.isNullOrEmpty()) {
            val planItems = plan.split("#").drop(1)
            val job = lifecycleScope.launch(Dispatchers.IO) {
                for (item in planItems) {
                    val actionMatch = "action=(\\w+)".toRegex().find(item)
                    val action = actionMatch?.groupValues?.get(1)
                    if (action != null) {
                        Log.d(TAG, "Launched the Animation job")
                        pepperInterface.performAnimation(action)
                    } else {
                        Log.e(TAG, "No action found in plan item: $item")
                    }
                }
            }
            job.join()
            Log.d(TAG, "Joined the Animation job")
        } else {
            Log.e(TAG, "Plan is null or empty")
        }
    }
}