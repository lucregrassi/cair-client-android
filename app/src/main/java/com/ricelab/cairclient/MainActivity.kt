package com.ricelab.cairclient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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
import com.aldebaran.qi.sdk.builder.ListenBuilder
import com.aldebaran.qi.sdk.builder.PhraseSetBuilder
import com.aldebaran.qi.sdk.`object`.conversation.ListenResult
import com.aldebaran.qi.sdk.`object`.touch.Touch
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import com.aldebaran.qi.*
import kotlin.text.get


private const val TAG = "MainActivity"
private const val SILENCE_THRESHOLD: Long = 300 // in seconds
private const val INTERVENTION_POLLING_DELAY_MIC_OFF = 2000L

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {

    companion object {
        const val EXTRA_OPEN_FRAGMENT = "open_fragment"
        const val OPEN_HOME = "home"
        const val OPEN_PERSONALIZATION = "personalization"
        const val OPEN_INTERVENTION = "intervention"

        const val EXTRA_SUPPRESS_LISTENING = "suppress_listening" // <—
    }

    private var suppressOnStart = false  // <—
    private var isFragmentActive = false

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
    private var logPort: Int = 12350 // Default value

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
    private var voiceSpeed = 100
    private var isTouchListenerAdded = false
    private var experimentId: String = ""
    private var deviceId: String = ""
    private var userFontSize: Int = 24
    private var robotFontSize: Int = 24

    private lateinit var fileStorageManager: FileStorageManager
    internal lateinit var conversationState: ConversationState
    private lateinit var personalizationManager: InterventionManager
    private lateinit var pepperInterface: PepperInterface
    private var teleoperationManager: TeleoperationManager? = null
    private var sentenceGenerator: SentenceGenerator = SentenceGenerator()
    private var isListeningEnabled = true
    private var onGoingIntervention: DueIntervention? = null
    private var profileId: String = "00000000-0000-0000-0000-000000000000"

    private var headTouchCount = 0
    private var lastHeadTouchTime: Long = 0
    private val headTouchInterval = 1000L // 1 second to complete 3 touches
    private var silenceDuration : Int = 2

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.e(TAG, "Permission to record audio was denied.")
        }
    }

    // To handle hotword detection for microphone
    private var hotwordFuture: com.aldebaran.qi.Future<ListenResult>? = null
    private val micOffKeywords = listOf("disattiva il microfono", "smetti di ascoltare", "non ho più voglia di parlare", "smetti di parlare")
    private val micOnKeyword = "Hey Pepper"
    private var lastFillerSpeakingTime: Double? = null
    var fillerJob: Job? = null

    private lateinit var session: Session
    private lateinit var leds: AnyObject
    private var ledRefreshJob: Job? = null
    private var useLeds: Boolean = false
    private lateinit var robotPassword: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        QiSDK.register(this, this)

        personalizationManager = InterventionManager.getInstance(this)

        sentenceGenerator.loadFillerSentences(this)
        // retrieve values stored in settings
        retrieveStoredValues()
        pepperInterface = PepperInterface(null, voiceSpeed)
        Log.i(TAG, "******autoDetectLanguage = $autoDetectLanguage")
        loadScheduledInterventions()
        fileStorageManager = FileStorageManager(this, filesDir)

        userSpeechTextView = findViewById(R.id.userSpeechTextView)
        userSpeechTextView.textSize = userFontSize.toFloat()
        robotSpeechTextView = findViewById(R.id.robotSpeechTextView)
        robotSpeechTextView.textSize = robotFontSize.toFloat()
        thresholdTextView = findViewById(R.id.thresholdTextView)
        recalibrateButton = findViewById(R.id.recalibrateButton)

        isListeningEnabled = true

        // Pass autoDetectLanguage to AudioRecorder
        audioRecorder = AudioRecorder(this, autoDetectLanguage, silenceDuration)

        audioRecorder.onSilenceDetected = {
            if (useFillerSentence && isListeningEnabled && !isFragmentActive) {
                val shouldSayFiller = onGoingIntervention == null ||
                        (onGoingIntervention?.type == "interaction_sequence" && onGoingIntervention?.counter != 0)

                Log.d(TAG, "[onSilenceDetected] Triggered. useFillerSentence=$useFillerSentence, isListeningEnabled=$isListeningEnabled, isFragmentActive=$isFragmentActive, shouldSayFiller=$shouldSayFiller")

                if (shouldSayFiller) {
                    val filler = sentenceGenerator.getFillerSentence(language)
                    Log.i(TAG, "[onSilenceDetected] Saying filler: \"$filler\"")

                    runOnUiThread {
                        robotSpeechTextView.text = "Pepper: $filler"
                    }
                    fillerJob = lifecycleScope.launch(Dispatchers.IO) {
                        val fillerStart = System.currentTimeMillis()
                        pepperInterface.sayMessage(filler, language)
                        val fillerEnd = System.currentTimeMillis()
                        lastFillerSpeakingTime = (fillerEnd - fillerStart) / 1000.0
                        Log.i(TAG, "[onSilenceDetected] Filler speaking time: $lastFillerSpeakingTime s")
                    }
                } else {
                    Log.d(TAG, "[onSilenceDetected] Filler skipped due to condition.")
                }
            } else {
                Log.d(TAG, "[onSilenceDetected] Filler skipped: one or more conditions not met.")
            }
        }

        recalibrateButton.setOnClickListener {
            lifecycleScope.launch {
                recalibrateThresholdBlocking()
            }
        }
        checkAndRequestAudioPermission()

        supportFragmentManager.addOnBackStackChangedListener {
            val mainUI = findViewById<View>(R.id.main_ui_container)
            val fragmentContainer = findViewById<View>(R.id.fragment_container)

            val inFragment = supportFragmentManager.backStackEntryCount > 0
            isFragmentActive = inFragment
            setLeds(!inFragment)

            if (supportFragmentManager.backStackEntryCount == 0) {
                mainUI.visibility = View.VISIBLE
                fragmentContainer.visibility = View.GONE

                // Re-enable teleoperation when back to MainActivity
                teleoperationManager?.startUdpListener()
                Log.d(TAG, "Teleoperation listener restarted after fragment closed")

                if (isListeningEnabled) {
                    // Start listening
                    Log.i(TAG, "Restarting listening after exiting fragment...")
                }

                isFragmentActive = false
            } else {
                mainUI.visibility = View.GONE
                fragmentContainer.visibility = View.VISIBLE

                // Stop teleoperation when entering fragment
                teleoperationManager?.stopUdpListener()
                Log.d(TAG, "Teleoperation listener stopped for fragment")

                // Stop listening
                Log.i(TAG, "Stopping listening to enter fragment...")
                audioRecorder.stopRecording()
                //isListeningEnabled = false
                isFragmentActive = true
            }
        }
        openFromIntent(intent)
    }

    private fun openFromIntent(intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_OPEN_FRAGMENT)
        suppressOnStart = intent?.getBooleanExtra(EXTRA_SUPPRESS_LISTENING, false) ?: false

        // Svuota sempre lo stack dei fragment
        supportFragmentManager.popBackStack(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        when (target) {
            OPEN_PERSONALIZATION -> {
                // Segna SUBITO che sei “in fragment”, disattiva tutto
                isFragmentActive = true
                if (suppressOnStart) {
                    isListeningEnabled = false
                    audioRecorder.stopRecording()
                    setLeds(false)
                }
                showPersonalizationFragment()
            }
            OPEN_INTERVENTION -> {
                isFragmentActive = true
                if (suppressOnStart) {
                    isListeningEnabled = false
                    audioRecorder.stopRecording()
                    setLeds(false)
                }
                showInterventionFragment()
            }
            else -> showHomeUi()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromIntent(intent)
    }

    private fun showHomeUi() {
        findViewById<View>(R.id.main_ui_container)?.visibility = View.VISIBLE
        findViewById<View>(R.id.fragment_container)?.visibility = View.GONE
        isFragmentActive = false
        if (!isFragmentActive) setLeds(true)
    }

    override fun onResume() {
        super.onResume()
        isListeningEnabled = true
        if (!isFragmentActive && !suppressOnStart) {
            setLeds(true)
        } else {
            setLeds(false)
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        setLeds(false)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_home -> {
                // Chiude tutti i fragment nello stack e mostra la main UI
                supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                findViewById<View>(R.id.main_ui_container)?.visibility = View.VISIBLE
                findViewById<View>(R.id.fragment_container)?.visibility = View.GONE
                true
            }
            R.id.action_intervention -> { showInterventionFragment(); true }
            R.id.action_personalization -> { showPersonalizationFragment(); true }
            R.id.action_setup -> {
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra("fromMenu", true)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showInterventionFragment() {
        val mainUI = findViewById<View>(R.id.main_ui_container)
        val fragmentContainer = findViewById<View>(R.id.fragment_container)

        mainUI.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, InterventionFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun showPersonalizationFragment() {
        val mainUI = findViewById<View>(R.id.main_ui_container)
        val fragmentContainer = findViewById<View>(R.id.fragment_container)

        mainUI.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, PersonalizationFragment())
            .addToBackStack(null)
            .commit()
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
        serverPort = sharedPreferences.getInt("server_port", -1)
        experimentId = sharedPreferences.getString("experiment_id", "test") ?: ""
        deviceId = sharedPreferences.getString("device_id", "pepper") ?: ""
        personName = sharedPreferences.getString("person_name", "") ?: ""
        personGender = sharedPreferences.getString("person_gender", "") ?: ""
        personAge = sharedPreferences.getString("person_age", "") ?: ""
        openAIApiKey = sharedPreferences.getString("openai_api_key", null) ?: ""
        useFillerSentence = sharedPreferences.getBoolean("use_filler_sentence", true)
        autoDetectLanguage = sharedPreferences.getBoolean("auto_detect_language", false)
        formalLanguage = sharedPreferences.getBoolean("use_formal_language", true)
        useLeds = sharedPreferences.getBoolean("use_leds", true)
        robotPassword = sharedPreferences.getString("robot_password", "") ?: ""
        voiceSpeed = sharedPreferences.getInt("voice_speed", 100)
        userFontSize = sharedPreferences.getInt("user_font_size", 24)
        robotFontSize = sharedPreferences.getInt("robot_font_size", 24)
        silenceDuration = sharedPreferences.getInt("silence_duration", 2)

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

    private fun loadScheduledInterventions() {
        val prefs = getSharedPreferences("interventions", MODE_PRIVATE)
        val json = prefs.getString("scheduled_interventions", null) ?: return

        val listType = object : TypeToken<List<ScheduledIntervention>>() {}.type
        val loadedInterventions: List<ScheduledIntervention> = Gson().fromJson(json, listType)

        personalizationManager.loadFromPrefs()
        Log.i(TAG, "Loaded $loadedInterventions")
    }

    private suspend fun recalibrateThresholdBlocking() {
        val newThreshold = withContext(Dispatchers.IO) {
            audioRecorder.recalibrateThreshold()
        }
        withContext(Dispatchers.Main) {
            thresholdTextView.text = "Soglia del rumore: $newThreshold"
            Toast.makeText(
                thresholdTextView.context,
                "Soglia ricalibrata: $newThreshold",
                Toast.LENGTH_SHORT
            ).show()
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

    private suspend fun startHotwordRecognition(qiContext: QiContext) {
        //Both build should be outside the Main thread
        val (phraseSet, listen) = withContext(Dispatchers.IO) {
            val builtPhraseSet = PhraseSetBuilder.with(qiContext)
                .withTexts(micOnKeyword)
                .build()

            val builtListen = ListenBuilder.with(qiContext)
                .withPhraseSet(builtPhraseSet)
                .build()

            Pair(builtPhraseSet, builtListen)
        }

        // Call async().run() on the Main thread
        hotwordFuture = listen.async().run()
        hotwordFuture?.andThenConsume { result ->
            val matchedPhrase = result.heardPhrase.text
            if (matchedPhrase.equals(micOnKeyword, ignoreCase = true)) {
                Log.i(TAG, "Hotword detected: $matchedPhrase")
                lifecycleScope.launch {
                    toggleListening()
                }
            }
        }
    }

    private suspend fun toggleListening(announce: Boolean = true) {
        isListeningEnabled = !isListeningEnabled

        if (!isListeningEnabled) {
            Log.i(TAG, "Stopping listening...")
            audioRecorder.stopRecording()

            runOnUiThread {
                Toast.makeText(this, "Microfono disattivato", Toast.LENGTH_SHORT).show()
                userSpeechTextView.text = sentenceGenerator.getPredefinedSentence(language, "microphone_user")
                robotSpeechTextView.text = sentenceGenerator.getPredefinedSentence(language, "microphone_robot")
            }
            fillerJob?.join()
            pepperInterface.sayMessage(
                sentenceGenerator.getPredefinedSentence(language, "microphone_robot"),
                language
            )

            qiContext?.let { startHotwordRecognition(it) }
        } else {
            Log.i(TAG, "Restarting listening...")

            // when mic on, stop hotword
            hotwordFuture?.cancel(true)
            hotwordFuture = null

            if (announce) {
                runOnUiThread {
                    Toast.makeText(this, "Microfono attivato", Toast.LENGTH_SHORT).show()
                    userSpeechTextView.text = sentenceGenerator.getPredefinedSentence(language, "listening_user")
                    robotSpeechTextView.text = sentenceGenerator.getPredefinedSentence(language, "listening_robot")
                }
                pepperInterface.sayMessage(
                    sentenceGenerator.getPredefinedSentence(language, "listening_robot"),
                    language
                )
            } else {
                // silent enable for interventions: no TTS / toast
                runOnUiThread {
                    Toast.makeText(this, "Microfono attivato", Toast.LENGTH_SHORT).show()
                    userSpeechTextView.text = sentenceGenerator.getPredefinedSentence(language, "listening_user")
                }
            }
        }
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
        pepperInterface.setContext(this.qiContext)
        pepperInterface.holdBaseMovement()

        if (!isTouchListenerAdded) {
            // Retrieve the Touch service
            val touch: Touch = qiContext.touch

            Log.w(TAG, "ADDING TOUCH LISTENER")
            touch.getSensor("Head/Touch")?.addOnStateChangedListener { touchState ->
                if (touchState.touched) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastHeadTouchTime <= headTouchInterval) {
                        headTouchCount++
                    } else {
                        headTouchCount = 1
                    }
                    lastHeadTouchTime = currentTime

                    if (headTouchCount >= 4) {
                        Log.i(TAG, "Quadruple head touch detected. Posting toggleListening to Main thread.")
                        headTouchCount = 0
                        runOnUiThread {
                            lifecycleScope.launch {
                                toggleListening()
                            }
                        }
                    }
                }
            }
            isTouchListenerAdded = true
        }

        teleoperationManager = TeleoperationManager(this, qiContext, pepperInterface)
        teleoperationManager?.startUdpListener()

        // retrieve values stored in shared preferences
        retrieveStoredValues()
        // initialize connection with head and leds inside dispatcher to avoid blocks
        lifecycleScope.launch(Dispatchers.IO) {
            if (!useLeds) return@launch
            try {
                session = Session().apply {
                    setClientAuthenticator(UserTokenAuthenticator("nao", robotPassword))
                    connect("tcps://198.18.0.1:9503").get()
                }
                leds = session.service("ALLeds").get()
                // Se vuoi accendere i LED appena pronti:
                withContext(Dispatchers.Main) { if (!isFragmentActive) setLeds(true) }

            } catch (e: Exception) {
                Log.e(TAG, "Errore nella connessione alla sessione", e)
            }
        }

        audioRecorder.longSilenceDurationMillis = silenceDuration * 1000L
        pepperInterface.setVoiceSpeed(voiceSpeed)
        runOnUiThread {
            userSpeechTextView.textSize = userFontSize.toFloat()
            robotSpeechTextView.textSize = robotFontSize.toFloat()
        }

        serverCommunicationManager =
            ServerCommunicationManager(this, serverIp, serverPort, logPort, openAIApiKey)

        coroutineJob = lifecycleScope.launch {
            startDialogue()
        }
    }

    override fun onRobotFocusLost() {
        teleoperationManager?.stopUdpListener()
        teleoperationManager = null
        this.qiContext = null
        pepperInterface.releaseBaseMovement()
        pepperInterface.setContext(null)
        try { session.close() } catch (_: Exception) {}
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
            if (isFragmentActive) {
                Log.w(TAG, "Fragment is active, skipping loop")
                delay(1000)
                continue
            }
            // Check if listening is enabled
            if (!isListeningEnabled) {
                // userSpeechTextView.text = sentenceGenerator.getPredefinedSentence(language, "microphone")

                // Check for due interventions even if mic is off
                if (onGoingIntervention == null) {
                    Log.d("InterventionManager", "Entering DueIntervention 1 (onGoingIntervention = null)")
                    onGoingIntervention = personalizationManager.getDueIntervention()
                    if (onGoingIntervention != null) {
                        Log.d(TAG, "New intervention arrived while microphone is disabled")
                        if (onGoingIntervention!!.type == "interaction_sequence") {
                            conversationState.dialogueState.resetConversation()
                        }
                        handle("")
                        continue
                    }
                }
                delay(INTERVENTION_POLLING_DELAY_MIC_OFF)
                continue
            }

            Log.i(TAG, "Time since last spoken words (ms) = ${System.currentTimeMillis() - lastActiveSpeakerTime}")
            conversationState.dialogueState.ongoingConversation =
                (System.currentTimeMillis() - lastActiveSpeakerTime) <= SILENCE_THRESHOLD * 1000
            Log.i(TAG, "OngoingConversation = ${conversationState.dialogueState.ongoingConversation}")

            if (onGoingIntervention == null) {
                Log.d("InterventionManager", "Entering DueIntervention 1 (onGoingIntervention = null)")
                onGoingIntervention = personalizationManager.getDueIntervention()
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

            setLeds(true)
            val audioResult = startListening()
            setLeds(false)
            if (isFragmentActive) {
                Log.w(TAG, "Fragment is active, skipping loop after startListening")
                continue
            }
            if (!isListeningEnabled) {
                Log.w(TAG, "Listening is disabled, skipping loop after startListening")
                continue
            }

            val xmlString = audioResult.xmlResult
            val audioLog = audioResult.log
            serverCommunicationManager.sendLogToServer(
                audioLog,
                "audio_recorder",
                experimentId,
                deviceId,
                serverPort
            )
            Log.d(TAG, "Entering handle for xmlString = $xmlString")
            handle(xmlString)
            withContext(Dispatchers.IO) {
                conversationState.writeToFile()
            }
        }
    }

    fun getCurrentTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
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
            Log.i(TAG, "Uttering first sentence")
            robotSpeechTextView.text = "Pepper: $firstSentence"
            pepperInterface.sayMessage(firstSentence, language)
            previousSentence = firstSentence
        } else {
            Log.e(TAG, "No first sentence!")
        }
    }

    private fun setLeds(on: Boolean) {
        if (!useLeds || !this::leds.isInitialized) return

        // delete already existing jobs, if present
        ledRefreshJob?.cancel()
        ledRefreshJob = null

        val duration = 5000L
        val refreshInterval = 300L

        ledRefreshJob = lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < duration) {
                try {
                    if (on) {
                        leds.call<Void>("fadeRGB", "ChestLeds", 0x000000FF, 0.0).get()
                        leds.call<Void>("setIntensity", "EarLeds", 1.0).get()
                    } else {
                        leds.call<Void>("fadeRGB", "ChestLeds", 0x00000000, 0.0).get()
                        leds.call<Void>("fadeRGB", "EarLeds", 0x00000000, 0.0).get()
                        leds.call<Void>("setIntensity", "EarLeds", 0.0).get()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "LED update failed", e)
                }

                delay(refreshInterval)
            }

            Log.d(TAG, "LEDs ${if (on) "on" else "off"} cycle complete")
            ledRefreshJob?.cancel()
            ledRefreshJob = null
        }
    }

    private suspend fun startListening(): AudioResult {
        while (true) {
            userSpeechTextView.text = sentenceGenerator.getPredefinedSentence(language, "listening_user")
            val audioResult = withContext(Dispatchers.IO) {
                audioRecorder.listenAndSplit()
            }

            val xmlResult = audioResult.xmlResult
            val (userSentence, detectedLang) = parseXmlForSentenceAndLanguage(xmlResult)

            // Update the user sentence TextView only if listening is enabled
            if(isListeningEnabled) {
                userSpeechTextView.text = "Utente: $userSentence"
            }

            if (detectedLang.isNotEmpty() && detectedLang != "und") {
                language = detectedLang
            }

            if (xmlResult.isNotBlank()) {
                Log.i(TAG, "xmlResult = $xmlResult")
                return audioResult
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
        // If we're about to process an intervention and the mic is OFF, turn it ON.
        if (onGoingIntervention != null && !onGoingIntervention!!.type.isNullOrEmpty() && !isListeningEnabled) {
            Log.d(TAG, "handle(): enabling listening for incoming intervention...")
            toggleListening(announce = false)
        }

        val logMap = mutableMapOf<String, Any>()
        logMap["timestamp"] = getCurrentTimestamp()
        lastFillerSpeakingTime?.let {
            logMap["ack_sentence_speaking_time"] = it
            Log.d(TAG, "[handle] Using logged filler speaking time: $it s")
            lastFillerSpeakingTime = null
        } ?: Log.d(TAG, "[handle] No filler speaking time logged (filler not said or still in progress)")

        var firstRequestTime = -1.0
        var replySpeakingTime = -1.0
        var secondRequestTime = -1.0
        var continuationSpeakingTime = -1.0

        val isIntervention = when {
            onGoingIntervention != null -> !onGoingIntervention!!.type.isNullOrEmpty()
            else -> false
        }
        Log.d(TAG, "Handle: isIntervention = $isIntervention")
        val xmlString = if (xmlStringInput.isNotEmpty()) {
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
            logMap["intervention_type"] = onGoingIntervention!!.type!!
            if (xmlStringInput.isEmpty() && onGoingIntervention!!.type == "interaction_sequence")
                logMap["intervention_sentence"] = "*START*"
            else
                logMap["intervention_sentence"] = onGoingIntervention!!.sentence
            Log.d(TAG, "logging intervention sentence ${logMap["intervention_sentence"]} and type ${logMap["intervention_type"]}")

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
        // Check for mic off keywords
        if (micOffKeywords.any { sentence.contains(it, ignoreCase = true) }) {
            toggleListening()
            return
        }
        // Check for repeat keywords
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
            if (xmlStringInput.isNotEmpty()) {
                lastActiveSpeakerTime = System.currentTimeMillis()
            }
            conversationState.dialogueState.updateConversation("user", sentence)

            // We'll store both jobs so we can await/join them
            val hubRequestDeferred: Deferred<ConversationState?>

            // Run them in parallel in a coroutineScope
            val requestStart = System.currentTimeMillis()
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
                        experimentId,
                        deviceId,
                        xmlString,
                        language,
                        conversationState,
                        "",
                        dueIntervention
                    )
                }
            }

            // 2) Await the hub request result
            val updatedConversationState = hubRequestDeferred.await()
            val requestEnd = System.currentTimeMillis()
            firstRequestTime = (requestEnd - requestStart) / 1000.0
            logMap["first_request_response_time"] = firstRequestTime

            // Wait for any ongoing filler to finish
            if (fillerJob != null) {
                Log.d(TAG, "Waiting for fillerJob to finish before speaking reply...")
                fillerJob?.join()
                Log.d(TAG, "FillerJob completed.")
                fillerJob = null
            }

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
                if (replySentence.isNotEmpty()) {
                    conversationState.dialogueState.updateConversation("assistant", replySentence)
                }

                coroutineScope {
                    Log.d(TAG, "before sayMessage $replySentence")

                    // Speak the reply sentence if present
                    var sayReplyJob: Job? = null
                    if (replySentence.isNotEmpty()) {
                        // Update UI on Main
                        withContext(Dispatchers.Main) {
                            robotSpeechTextView.text = ("Pepper: $replySentence")
                        }
                        // Actually speak it (on IO)
                        sayReplyJob = launch(Dispatchers.IO) {
                            val replyStart = System.currentTimeMillis()
                            pepperInterface.sayMessage(replySentence, language)
                            val replyEnd = System.currentTimeMillis()
                            replySpeakingTime = (replyEnd - replyStart) / 1000.0
                            logMap["first_response_speaking_time"] = replySpeakingTime
                        }
                    }

                    // Then perform the animation (this can also run in parallel or after speaking)
                    Log.d("Debug", "before performAnimationFromPlan")
                    performAnimationFromPlan(conversationState.plan)

                    // Decide if we do the continuation logic
                    if (!isIntervention || dueIntervention.type == "topic") {
                        val contRequestStart = System.currentTimeMillis()
                        val secondHubRequestJob = async(Dispatchers.IO) {
                            Log.d("Debug", "before hubRequest continuation")
                            serverCommunicationManager.hubRequest(
                                "continuation",
                                experimentId,
                                deviceId,
                                xmlString,
                                language,
                                conversationState,
                                "",
                                DueIntervention(type = null, exclusive = false, sentence = "")
                            )
                        }
                        Log.d("Debug", "after performAnimationFromPlan")
                        val continuationConversationState = secondHubRequestJob.await()
                        val contRequestEnd = System.currentTimeMillis()
                        secondRequestTime = (contRequestEnd - contRequestStart) / 1000.0
                        logMap["second_request_response_time"] = secondRequestTime

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
                                    val contSpeakStart = System.currentTimeMillis()
                                    pepperInterface.sayMessage(continuationSentence, language)
                                    val contSpeakEnd = System.currentTimeMillis()
                                    continuationSpeakingTime = (contSpeakEnd - contSpeakStart) / 1000.0
                                    logMap["second_sentence_speaking_time"] = continuationSpeakingTime
                                    val logJson = JSONObject(logMap)
                                    serverCommunicationManager.sendLogToServer(
                                        logJson,
                                        "client_dialogue",
                                        experimentId,
                                        deviceId,
                                        serverPort
                                    )
                                    logMap.clear()
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
                        val logJson = JSONObject(logMap)
                        serverCommunicationManager.sendLogToServer(
                            logJson,
                            "client_dialogue",
                            experimentId,
                            deviceId,
                            serverPort
                        )
                        logMap.clear()
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
                Log.w(TAG, "Resetting onGoingIntervention to null")
                onGoingIntervention = null
            } else if (sentence.isNotBlank() && sentence != "*START*") {
                Log.w(TAG, "Getting the next dueIntervention")
                Log.d("InterventionManager", "Entering DueIntervention 3 (sentenceNotBlank)")
                onGoingIntervention = personalizationManager.getDueIntervention()
                if (onGoingIntervention == null) {
                    Log.w(TAG, "No dueIntervention found")
                } else {
                    Log.w(TAG, "DueIntervention found: ${onGoingIntervention!!.type}")
                }
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