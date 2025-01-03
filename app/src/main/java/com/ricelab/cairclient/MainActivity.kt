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
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.IOException
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
    private val repeatKeywords = listOf("puoi ripetere", "ripeti", "non ho capito")
    private var language = "it-IT" // Default language is Italian


    //private var ongoingConversation: Boolean = true

    private lateinit var serverCommunicationManager: ServerCommunicationManager
    private lateinit var serverIp: String
    private lateinit var openAIApiKey: String

    private lateinit var fileStorageManager: FileStorageManager
    private lateinit var conversationState: ConversationState

    private lateinit var personalizationServer: PersonalizationServer
    private val scheduledInterventionsPort = 8000

    private lateinit var pepperInterface: PepperInterface

    private var useFillerSentence: Boolean = false

    // Instead of a single list for fillerSentences, we now have a map keyed by language
    private val fillerSentencesMap = mutableMapOf<String, List<String>>()
    private var autoDetectLanguage = false // default

    private var teleoperationManager: TeleoperationManager? = null

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
        loadFillerSentences()
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

    /**
     * Load filler sentences from two separate files:
     * - filler_sentences_it-IT.txt for Italian
     * - filler_sentences_en-US.txt for English
     * Store them in fillerSentencesMap with keys "it-IT" and "en-US".
     */
    private fun loadFillerSentences() {
        fun loadSentencesForLang(fileName: String): List<String> {
            return try {
                val inputStream = assets.open("dialogue_data/$fileName")
                inputStream.bufferedReader().useLines { lines ->
                    lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error loading filler sentences from $fileName: ${e.message}")
                listOf("...")
            }
        }

        val itSentences = loadSentencesForLang("filler_sentences_it-IT.txt")
        val enSentences = loadSentencesForLang("filler_sentences_en-US.txt")

        fillerSentencesMap["it-IT"] = itSentences
        fillerSentencesMap["en-US"] = enSentences

        Log.d(TAG, "Filler sentences loaded: it-IT=${itSentences.size}, en-US=${enSentences.size}")
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
        useFillerSentence = sharedPreferences.getBoolean("use_filler_sentence", false)
        autoDetectLanguage = sharedPreferences.getBoolean("auto_detect_language", false) // Load the setting
        Log.i(TAG, "autodetect=$autoDetectLanguage")

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

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
        pepperInterface.setContext(this.qiContext)

        teleoperationManager = TeleoperationManager(this, qiContext, pepperInterface)
        teleoperationManager?.startUdpListener()

        retrieveStoredValues()

        serverCommunicationManager = ServerCommunicationManager(this, serverIp, serverPort, openAIApiKey)

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
        conversationState = ConversationState(fileStorageManager, previousSentence)

        initializeUserSession()

        withContext(Dispatchers.IO) {
          conversationState.loadFromFile()
       }

        conversationState.lastActiveSpeakerTime = System.currentTimeMillis()

        while (isAlive) {
            conversationState.dialogueState.ongoingConversation =
                (System.currentTimeMillis() - conversationState.lastActiveSpeakerTime) <= SILENCE_THRESHOLD * 1000

            val dueIntervention = personalizationServer.getDueIntervention()
            if (dueIntervention != null) {
                //handleDueIntervention(dueIntervention)
                handle("", dueIntervention)
                continue
            }
            val xmlString = startListening()
            //handleUserInput(xmlString)
            handle(xmlString, DueIntervention(null, false, ""))
            withContext(Dispatchers.IO) {
                conversationState.writeToFile()
            }
        }
    }

    private suspend fun initializeUserSession() {
        val firstSentence: String
        if (!fileStorageManager.filesExist()) {
            val firstRequestResponse = try {
                withContext(Dispatchers.IO) {
                    serverCommunicationManager.firstServerRequest(language)
                }
            } catch (e: Exception) {
                pepperInterface.sayMessage(getFixedMessage("server_error"), language)
                return
            }

            conversationState.dialogueState.printDebug()
            firstSentence = firstRequestResponse.firstSentence

            val dialogueState = DialogueState(firstRequestResponse.dialogueState)

            val profileId = "00000000-0000-0000-0000-000000000000"
            val userName = if (language == "it-IT") "Utente" else "User"

            val speakerAttributes = mapOf(
                "name" to userName,
                "gender" to "nb",
                "age" to "nd"
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
            firstSentence = getFixedMessage("welcome_back")
        }
        if(firstSentence != "") {
            robotSpeechTextView.text = "Pepper: $firstSentence"
            pepperInterface.sayMessage(firstSentence, language)
            previousSentence = firstSentence
        }
    }

    private suspend fun startListening(): String {
        while (true) {
            userSpeechTextView.text = getFixedMessage("listening")

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

    private suspend fun handle(xmlStringInput: String, dueInterventionInput: DueIntervention) {
        val isIntervention = !dueInterventionInput.type.isNullOrEmpty()
        val xmlString = when(isIntervention) {
            false -> xmlStringInput
            true ->  "<response><profile_id value=\"00000000-0000-0000-0000-000000000000\">${dueInterventionInput.sentence}<language>$language</language><speaking_time>0.0</speaking_time></profile_id></response>\n".trimIndent()
        }
        val dueIntervention = when(isIntervention) {
            false -> DueIntervention(type = null, exclusive = false, sentence = "")
            true -> dueInterventionInput
        }

        val (sentence, detectedLang) = parseXmlForSentenceAndLanguage(xmlString)

        if (detectedLang.isNotEmpty() && detectedLang != "und") {
            language = detectedLang
        }

        if (exitKeywords.any { sentence.contains(it, ignoreCase = true) }) {
            pepperInterface.sayMessage(getFixedMessage("goodbye"), language)
            isAlive = false
            return
        }

        if (repeatKeywords.any { sentence.contains(it, ignoreCase = true) }) {
            if (previousSentence.isNotEmpty()) {
                pepperInterface.sayMessage(getFixedMessage("repeat_previous") + " $previousSentence", language)
            } else {
                pepperInterface.sayMessage(getFixedMessage("nothing_to_repeat"), language)
            }
            return
        }

        if (sentence.isNotBlank() && sentence != "Timeout") {
            if (!isIntervention)
                conversationState.lastActiveSpeakerTime = System.currentTimeMillis()

            conversationState.dialogueState.updateConversation("user", sentence)

            var updatedConversationState: ConversationState? = null
            // Launch the hub request asynchronously
            coroutineScope {
                async(Dispatchers.IO) {
                    updatedConversationState = serverCommunicationManager.hubRequest(
                        "reply",
                        xmlString,
                        language,
                        conversationState,
                        listOf(),
                        dueIntervention
                    )
                }

                // If using filler sentences, say one immediately in parallel with the hub request
                if (!isIntervention && useFillerSentence) {
                    launch(Dispatchers.Main) {
                        val fillerList = fillerSentencesMap[language] ?: fillerSentencesMap["it-IT"]!!
                        val randomFillerSentence = fillerList.random()
                        robotSpeechTextView.text = "Pepper: $randomFillerSentence"
                        // Speak the filler sentence now (blocking this coroutine only)
                        pepperInterface.sayMessage(randomFillerSentence, language)
                    }
                }
            }

            // Now await the hub request result; the filler sentence was spoken in parallel
            if (updatedConversationState != null) {
                conversationState = updatedConversationState!!
                var replySentence = if (conversationState.plan?.isNotEmpty() == true) {
                    conversationState.planSentence.toString()
                } else {
                    conversationState.dialogueState.dialogueSentence[0][1]
                }
                Log.i(TAG, "Reply sentence: $replySentence")

                if (replySentence != ""){
                    val patternPrevspk = "\\s*,?\\s*\\\$prevspk\\s*,?\\s*".toRegex()
                    replySentence = replySentence.replace(patternPrevspk, " ")

                    conversationState.dialogueState.updateConversation("assistant", replySentence)
                }


                coroutineScope {
                    if (replySentence != "") {
                        robotSpeechTextView.text = ("Pepper: $replySentence")
                        pepperInterface.sayMessage(replySentence, language)
                    }

                    performAnimationFromPlan(conversationState.plan)

                    if (!isIntervention || dueIntervention.type == "topic") {
                        val secondHubRequestJob = async(Dispatchers.IO) {
                            //conversationState.dialogueState.ongoingConversation = ongoingConversation
                            serverCommunicationManager.hubRequest(
                                "continuation",
                                xmlString,
                                language,
                                conversationState,
                                listOf(),
                                DueIntervention(type = null, exclusive = false, sentence = "")
                            )
                        }


                        val continuationConversationState = secondHubRequestJob.await()
                        if (continuationConversationState != null) {
                            conversationState = continuationConversationState
                            if (conversationState.dialogueState.dialogueSentence.size > 1 &&
                                conversationState.dialogueState.dialogueSentence[1].size > 1
                            ) {
                                var continuationSentence =
                                    conversationState.dialogueState.dialogueSentence[1][1]

                                val patternDesspk = "\\s*,?\\s*\\\$desspk\\s*,?\\s*".toRegex()
                                continuationSentence = continuationSentence.replace(patternDesspk, " ")
                                val patternPrevspk = "\\s*,?\\s*\\\$prevspk\\s*,?\\s*".toRegex()
                                continuationSentence =
                                    continuationSentence.replace(patternPrevspk, " ")

                                if (continuationSentence != "") {
                                    conversationState.dialogueState.updateConversation("assistant", continuationSentence)
                                    robotSpeechTextView.text = ("Pepper: $continuationSentence")
                                    pepperInterface.sayMessage(continuationSentence, language)
                                    previousSentence = continuationSentence
                                    conversationState.dialogueState.prevDialogueSentence =
                                        conversationState.dialogueState.dialogueSentence
                                }
                                else {
                                    Log.i(TAG, "No continuation sentence")
                                }
                            } else {
                                Log.e(TAG, "No continuation sentence found")
                            }
                        } else {
                            Log.e(TAG, "Failed continuation hub request.")
                        }
                    } else {
                        if (conversationState.dialogueState.ongoingConversation) {
                            Log.d(TAG, "Ongoing conversation = true && intervention == action")
                            coroutineScope {
                                val mapLanguageSentence = mapOf(
                                    "it-IT" to "Dicevo...",
                                    "en-US" to "I was saying..."
                                    // Add other languages if needed
                                )
                                val prefix = mapLanguageSentence[language] ?: "I was saying..."
                                var lastContinuationSentence =
                                    conversationState.dialogueState.prevDialogueSentence.lastOrNull()
                                        ?.getOrNull(1) ?: ""

                                // Replace any $desspk tags
                                val patternDesspk = "\\s*,?\\s*\\\$desspk\\s*,?\\s*".toRegex()
                                val patternPrevspk = "\\s*,?\\s*\\\$prevspk\\s*,?\\s*".toRegex()
                                lastContinuationSentence =
                                    lastContinuationSentence.replace(patternDesspk, " ")
                                lastContinuationSentence =
                                    lastContinuationSentence.replace(patternPrevspk, " ")

                                val repeatContinuation = "$prefix $lastContinuationSentence"

                                Log.i(TAG, "Repeat continuation: $repeatContinuation")
                                robotSpeechTextView.text = ("Pepper: $repeatContinuation")
                                pepperInterface.sayMessage(repeatContinuation, language)
                            }
                        } else {
                            Log.d(TAG, "Ongoing conversation = false && intervention == action")
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to update conversation state.")
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

    /**
     * Get a fixed message depending on the current language.
     */
    private fun getFixedMessage(key: String): String {
        val messages = mapOf(
            "welcome_back" to mapOf(
                "it-IT" to "È bello rivederti! Di cosa vorresti parlare?",
                "en-US" to "Welcome back! I missed you. What would you like to talk about?"
            ),
            "server_error" to mapOf(
                "it-IT" to "Mi dispiace, non riesco a connettermi al server.",
                "en-US" to "I'm sorry, I can't connect to the server."
            ),
            "goodbye" to mapOf(
                "it-IT" to "È stato bello parlare con te. A presto!",
                "en-US" to "It was nice talking to you. See you soon!"
            ),
            "nothing_to_repeat" to mapOf(
                "it-IT" to "Mi dispiace, non ho niente da ripetere!",
                "en-US" to "I'm sorry, I have nothing to repeat!"
            ),
            "repeat_previous" to mapOf(
                "it-IT" to "Ho detto:",
                "en-US" to "I said:"
            ),
            "listening" to mapOf(
                "it-IT" to "Sto ascoltando...",
                "en-US" to "I'm listening..."
            )
        )

        val messageMap = messages[key]
        return messageMap?.get(language) ?: (messageMap?.get("it-IT") ?: "")
    }
}