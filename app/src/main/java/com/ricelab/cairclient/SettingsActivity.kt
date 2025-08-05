package com.ricelab.cairclient

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ricelab.cairclient.libraries.FileStorageManager
import android.view.View
import java.io.IOException
import android.view.Menu

private const val TAG = "SettingsActivity"

class SettingsActivity : AppCompatActivity() {

    private lateinit var serverIpSpinner: Spinner
    private lateinit var serverPortEditText: EditText
    private lateinit var experimentIdEditText: EditText
    private lateinit var deviceIdEditText: EditText
    private lateinit var personNameEditText: EditText
    private lateinit var personGenderSpinner: Spinner
    private lateinit var personAgeEditText: EditText
    private lateinit var openAIApiKeyEditText: EditText
    private lateinit var azureSpeechKeyEditText: EditText
    private lateinit var fillerSentenceSwitch: SwitchCompat
    private lateinit var autoDetectLanguageSwitch: SwitchCompat
    private lateinit var formalLanguageSwitch: SwitchCompat
    private lateinit var useLedsSwitch: SwitchCompat
    private lateinit var robotPasswordEditText: EditText
    private lateinit var robotPasswordGroup: View
    private lateinit var voiceSpeedSeekBar: SeekBar
    private lateinit var voiceSpeedLabel: TextView
    private lateinit var voicePitchSeekBar: SeekBar
    private lateinit var voicePitchLabel: TextView
    private lateinit var userFontSizeSeekBar: SeekBar
    private lateinit var userFontSizeLabel: TextView
    private lateinit var robotFontSizeSeekBar: SeekBar
    private lateinit var robotFontSizeLabel: TextView
    private lateinit var silenceDurationSeekBar: SeekBar
    private lateinit var silenceDurationLabel: TextView
    private lateinit var micAutoOffSwitch: SwitchCompat
    private lateinit var micAutoOffSeekBar: SeekBar
    private lateinit var micAutoOffLabel: TextView
    private lateinit var micAutoOffGroup: View
    private lateinit var proceedButton: Button

    private val serverIpList = mutableListOf<String>()

    // mostra/nascondi gruppo password robot
    private fun refreshRobotPasswordVisibility() {
        robotPasswordGroup.visibility = if (useLedsSwitch.isChecked) View.VISIBLE else View.GONE
    }

    // mostra/nascondi gruppo microfono
    private fun refreshMicAutoOffVisibility() {
        val on = micAutoOffSwitch.isChecked
        micAutoOffGroup.visibility = if (on) View.VISIBLE else View.GONE
        micAutoOffSeekBar.isEnabled = on
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the layout
        setContentView(R.layout.activity_settings)

        // Initialize UI elements
        serverIpSpinner = findViewById(R.id.serverIpSpinner)
        serverPortEditText = findViewById(R.id.serverPortEditText)
        experimentIdEditText = findViewById(R.id.experimentIdEditText)
        deviceIdEditText = findViewById(R.id.deviceIdEditText)
        personNameEditText = findViewById(R.id.personNameEditText)
        personGenderSpinner = findViewById(R.id.personGenderSpinner)
        val genderOptions = listOf("Femmina", "Maschio", "Non binario")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genderOptions)
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        personGenderSpinner.adapter = genderAdapter
        personAgeEditText = findViewById(R.id.personAgeEditText)
        openAIApiKeyEditText = findViewById(R.id.openAIApiKeyEditText)
        azureSpeechKeyEditText = findViewById(R.id.azureSpeechKeyEditText)
        fillerSentenceSwitch = findViewById(R.id.fillerSentenceSwitch)
        autoDetectLanguageSwitch = findViewById(R.id.autoDetectLanguageSwitch)
        formalLanguageSwitch = findViewById(R.id.formalLanguageSwitch)
        useLedsSwitch = findViewById(R.id.useLedsSwitch)
        robotPasswordGroup = findViewById(R.id.robotPasswordGroup)
        robotPasswordEditText = findViewById(R.id.robotPasswordEditText)
        voiceSpeedSeekBar = findViewById(R.id.voiceSpeedSeekBar)
        voiceSpeedLabel = findViewById(R.id.voiceSpeedLabel)
        voicePitchSeekBar = findViewById(R.id.voicePitchSeekBar)
        voicePitchLabel = findViewById(R.id.voicePitchLabel)
        userFontSizeSeekBar = findViewById(R.id.userFontSizeSeekBar)
        userFontSizeLabel = findViewById(R.id.userFontSizeLabel)
        robotFontSizeSeekBar = findViewById(R.id.robotFontSizeSeekBar)
        robotFontSizeLabel = findViewById(R.id.robotFontSizeLabel)
        silenceDurationSeekBar = findViewById(R.id.silenceDurationSeekBar)
        silenceDurationLabel = findViewById(R.id.silenceDurationLabel)
        micAutoOffGroup    = findViewById(R.id.micAutoOffGroup)
        micAutoOffSwitch = findViewById(R.id.micAutoOffSwitch)
        micAutoOffSeekBar = findViewById(R.id.micAutoOffSeekBar)
        micAutoOffLabel = findViewById(R.id.micAutoOffLabel)
        proceedButton = findViewById(R.id.proceedButton)

        voiceSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                voiceSpeedLabel.text = "Velocità voce: ${progress}%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        voicePitchSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                voicePitchLabel.text = "Tonalità voce: ${p.coerceAtLeast(50)}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        userFontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                userFontSizeLabel.text = "Dimensione testo utente: ${progress}sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        robotFontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                robotFontSizeLabel.text = "Dimensione testo robot: ${progress}sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        silenceDurationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                silenceDurationLabel.text = "Durata silenzio (s): $progress"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        micAutoOffSwitch.setOnCheckedChangeListener { _, _ -> refreshMicAutoOffVisibility() }
        micAutoOffSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                micAutoOffLabel.text = "Timer auto-spegnimento microfono (min): ${p.coerceAtLeast(1)}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val fromMenu = intent.getBooleanExtra("fromMenu", false)
        val deleteAllButton: Button = findViewById(R.id.deleteAllButton)
        deleteAllButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Load server IPs from certificates
        loadServerIpsFromCertificates()

        if (loadSavedValues(fromMenu)) return  // stop qui se hai navigato

        useLedsSwitch.setOnCheckedChangeListener { _, isChecked ->
            refreshRobotPasswordVisibility()
            if (isChecked && robotPasswordEditText.text.toString().trim().isEmpty()) {
                // Keep the switch ON; just guide the user to enter the password
                Toast.makeText(this, "Per usare i LED inserisci la password del robot.", Toast.LENGTH_SHORT).show()
                robotPasswordEditText.requestFocus()
            }
        }

        proceedButton.setOnClickListener {
            if (serverIpSpinner.adapter == null || serverIpSpinner.adapter.count == 0) {
                Toast.makeText(this, "Nessun server disponibile. Aggiungi certificati o riprova.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val serverIp = (serverIpSpinner.selectedItem as? String) ?: run {
                Toast.makeText(this, "Seleziona un server.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val serverPortText = serverPortEditText.text.toString().trim()
            val experimentId = experimentIdEditText.text.toString().trim()
            val deviceId = deviceIdEditText.text.toString().trim()
            val personName = personNameEditText.text.toString().trim()
            val selectedGenderLabel = personGenderSpinner.selectedItem.toString()
            val personGender = when (selectedGenderLabel) {
                "Femmina" -> "f"
                "Maschio" -> "m"
                "Non binario" -> "nb"
                else -> ""
            }
            val personAge = personAgeEditText.text.toString().trim()
            val openAIApiKey = openAIApiKeyEditText.text.toString().trim()
            val azureSpeechKey = azureSpeechKeyEditText.text.toString().trim()
            val useFillerSentence = fillerSentenceSwitch.isChecked
            val autoDetectLanguage = autoDetectLanguageSwitch.isChecked
            val useFormalLanguage = formalLanguageSwitch.isChecked
            val useLeds = useLedsSwitch.isChecked
            val robotPassword = robotPasswordEditText.text.toString().trim()
            val micAutoOffEnabled = micAutoOffSwitch.isChecked
            val micAutoOffMinutes = micAutoOffSeekBar.progress.coerceAtLeast(1)
            val voicePitch = voicePitchSeekBar.progress.coerceAtLeast(50)

            Log.d(TAG, "Server IP: $serverIp")
            Log.d(TAG, "Server Port Text: $serverPortText")
            Log.d(TAG, "Experiment ID: $experimentId")
            Log.d(TAG, "Device ID: $deviceId")
            Log.d(TAG, "Person Name: $personName")
            Log.d(TAG, "Person Gender: $personGender")
            Log.d(TAG, "Person Age: $personAge")
            Log.d(TAG, "Use Filler Sentence: $useFillerSentence")
            Log.d(TAG, "Auto Detect Language: $autoDetectLanguage")
            Log.d(TAG, "Use Formal Language: $useFormalLanguage")
            Log.d(TAG, "Voice Speed: ${voiceSpeedSeekBar.progress}")
            Log.d(TAG, "Voice Pitch: $voicePitch")
            Log.d(TAG, "User Font Size: ${userFontSizeSeekBar.progress}")
            Log.d(TAG, "Robot Font Size: ${robotFontSizeSeekBar.progress}")
            Log.d(TAG, "Silence Duration: ${silenceDurationSeekBar.progress}")
            Log.d(TAG, "Mic Auto Off Enabled: $micAutoOffEnabled")
            Log.d(TAG, "Mic Auto Off Minutes: $micAutoOffMinutes")
            Log.d(TAG, "Use Leds: $useLeds")

            if (openAIApiKey.isEmpty()) {
                Toast.makeText(this, "Per favore, inserisci la chiave OpenAI", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (serverPortText.isEmpty()) {
                Toast.makeText(this, "Per favore, inserisci la porta del server", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val serverPort = serverPortText.toIntOrNull()
            if (serverPort == null || serverPort <= 0 || serverPort > 65535) {
                Toast.makeText(this, "Per favore, inserisci una porta valida (1-65535).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ageInt = personAge.toIntOrNull()
            if (personAge.isNotEmpty() && (ageInt == null || ageInt <= 0)) {
                Toast.makeText(this, "Età non valida.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (useLeds && robotPassword.isEmpty()) {
                Toast.makeText(this, "Inserisci la password del robot per usare i LED.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Save the values securely including the autoDetectLanguage parameter
            saveValues(
                serverIp,
                serverPort,
                experimentId,
                deviceId,
                personName,
                personGender,
                personAge,
                openAIApiKey,
                azureSpeechKey,
                useFillerSentence,
                autoDetectLanguage,
                useFormalLanguage,
                useLeds,
                robotPassword,
                voiceSpeedSeekBar.progress,
                voicePitch,
                userFontSizeSeekBar.progress,
                robotFontSizeSeekBar.progress,
                silenceDurationSeekBar.progress,
                micAutoOffEnabled,
                micAutoOffMinutes
            )

            if (!fromMenu) {
                Log.i(TAG, "FromMenu False, spawning new MainActivity")
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
            finish()
        }
    }

    private fun showDeleteConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Conferma cancellazione")
        builder.setMessage("Sei sicuro di voler cancellare tutti i dati?")

        builder.setPositiveButton("Sì") { dialog, which ->
            deleteAllData()
        }

        builder.setNegativeButton("No") { dialog, which ->
            dialog.dismiss()
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_home -> {
                safelyNavigateTo(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_WELCOME, true)
                )
                true
            }
            R.id.action_personalization -> {
                safelyNavigateTo(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_FRAGMENT, MainActivity.OPEN_PERSONALIZATION)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_LISTENING, true)  // <—
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_WELCOME, true)
                )
                true
            }
            R.id.action_intervention -> {
                safelyNavigateTo(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_FRAGMENT, MainActivity.OPEN_INTERVENTION)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_LISTENING, true)  // <—
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_WELCOME, true)
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun safelyNavigateTo(intent: Intent) {
        // chiudi l’overflow menu/popup
        closeOptionsMenu()

        // posta la navigazione al prossimo frame per dare tempo al popup di chiudersi
        window?.decorView?.post {
            startActivity(intent)
            // volendo puoi NON chiamare finish() e usare solo CLEAR_TOP/SINGLE_TOP
            finish()
        }
    }

    private fun loadSavedValues(fromMenu: Boolean) : Boolean {
        val sharedPreferences = securePrefs()

        val savedServerIp = sharedPreferences.getString("server_ip", null)
        val savedServerPort = sharedPreferences.getInt("server_port", -1)
        val savedExperimentId = sharedPreferences.getString("experiment_id", "")
        val savedDeviceId = sharedPreferences.getString("device_id", "")
        val savedPersonName = sharedPreferences.getString("person_name", "")
        val savedPersonGender = sharedPreferences.getString("person_gender", "")
        val savedPersonAge = sharedPreferences.getString("person_age", "")
        val savedOpenAIApiKey = sharedPreferences.getString("openai_api_key", null)
        val savedAzureSpeechKey = sharedPreferences.getString("azure_speech_key", null)
        val useFillerSentence = sharedPreferences.getBoolean("use_filler_sentence", true)
        val autoDetectLanguage = sharedPreferences.getBoolean("auto_detect_language", false)
        val useFormalLanguage = sharedPreferences.getBoolean("use_formal_language", true)

        return if (!fromMenu && !savedServerIp.isNullOrEmpty() && !savedOpenAIApiKey.isNullOrEmpty() && savedServerPort != -1) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            true
        } else {
            savedServerIp?.let { setSelectedServerIp(it) }
            if (savedServerPort != -1) {
                serverPortEditText.setText(savedServerPort.toString())
            }
            savedExperimentId?.let { experimentIdEditText.setText(it) }
            savedDeviceId?.let { deviceIdEditText.setText(it) }
            savedPersonName?.let { personNameEditText.setText(it) }
            savedPersonGender?.let {
                val index = when (it) {
                    "f" -> 0
                    "m" -> 1
                    "nb" -> 2
                    else -> -1
                }
                if (index != -1) personGenderSpinner.setSelection(index)
            }
            savedPersonAge?.let { personAgeEditText.setText(it) }
            savedOpenAIApiKey?.let { openAIApiKeyEditText.setText(it) }
            savedAzureSpeechKey?.let { azureSpeechKeyEditText.setText(it) }
            fillerSentenceSwitch.isChecked = useFillerSentence
            autoDetectLanguageSwitch.isChecked = autoDetectLanguage
            formalLanguageSwitch.isChecked = useFormalLanguage
            val useLeds = sharedPreferences.getBoolean("use_leds", false)
            val savedRobotPassword = sharedPreferences.getString("robot_password", "") ?: ""
            useLedsSwitch.isChecked = useLeds
            robotPasswordEditText.setText(savedRobotPassword)
            refreshRobotPasswordVisibility()

            val savedVoiceSpeed = sharedPreferences.getInt("voice_speed", 100)
            voiceSpeedSeekBar.progress = savedVoiceSpeed
            voiceSpeedLabel.text = "Velocità voce: ${savedVoiceSpeed}%"

            val savedVoicePitch = sharedPreferences.getInt("voice_pitch", 100)
            voicePitchSeekBar.progress = savedVoicePitch
            voicePitchLabel.text = "Tonalità voce: ${savedVoicePitch}%"

            val savedSilenceDuration = sharedPreferences.getInt("silence_duration", 2)
            silenceDurationSeekBar.progress = savedSilenceDuration
            silenceDurationLabel.text = "Durata silenzio (s): $savedSilenceDuration"

            val savedUserFontSize = sharedPreferences.getInt("user_font_size", 24)
            userFontSizeSeekBar.progress = savedUserFontSize
            userFontSizeLabel.text = "Dimensione testo utente: ${savedUserFontSize}sp"

            val savedRobotFontSize = sharedPreferences.getInt("robot_font_size", 24)
            robotFontSizeSeekBar.progress = savedRobotFontSize
            robotFontSizeLabel.text = "Dimensione testo robot: ${savedRobotFontSize}sp"

            val savedMicAutoOffEnabled = sharedPreferences.getBoolean("mic_auto_off_enabled", true)
            val savedMicAutoOffMinutes = sharedPreferences.getInt("mic_auto_off_minutes", 1)
            micAutoOffSwitch.isChecked = savedMicAutoOffEnabled
            micAutoOffSeekBar.progress = savedMicAutoOffMinutes
            micAutoOffLabel.text = "Timer auto-spegnimento microfono (min): $savedMicAutoOffMinutes"
            refreshMicAutoOffVisibility()
            false
        }
    }

    private fun setSelectedServerIp(ip: String) {
        val index = serverIpList.indexOf(ip)
        if (index != -1) {
            serverIpSpinner.setSelection(index)
        }
    }

    private fun loadServerIpsFromCertificates() {
        try {
            val assetManager: AssetManager = assets
            val certificateFiles = assetManager.list("certificates") ?: arrayOf()
            for (filename in certificateFiles) {
                if (filename.startsWith("server_") && filename.endsWith(".crt")) {
                    val ip = filename.removePrefix("server_").removeSuffix(".crt").replace("_", ".")
                    serverIpList.add(ip)
                }
            }
            if (serverIpList.isEmpty()) {
                Toast.makeText(this, "No server certificates found.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "No server certificates found in assets/certificates/")
            } else {
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serverIpList)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                serverIpSpinner.adapter = adapter
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error loading server IPs: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error loading server IPs", e)
        }
    }

    private fun saveValues(
        serverIp: String,
        serverPort: Int,
        experimentId: String,
        deviceId: String,
        personName: String,
        personGender: String,
        personAge: String,
        openAIApiKey: String,
        azureSpeechKey: String,
        useFillerSentence: Boolean,
        autoDetectLanguage: Boolean,
        useFormalLanguage: Boolean,
        useLeds: Boolean,
        robotPassword: String,
        voiceSpeed: Int,
        voicePitch: Int,
        userFontSize: Int,
        robotFontSize: Int,
        silenceDuration: Int,
        micAutoOffEnabled: Boolean,
        micAutoOffMinutes: Int
    ) {
        val sharedPreferences = securePrefs()

        val pwdToStore = if (useLeds) robotPassword else ""

        with(sharedPreferences.edit()) {
            putString("server_ip", serverIp)
            putInt("server_port", serverPort)
            putString("experiment_id", experimentId)
            putString("device_id", deviceId)
            putString("person_name", personName)
            putString("person_gender", personGender)
            putString("person_age", personAge)
            putString("openai_api_key", openAIApiKey)
            putString("azure_speech_key", azureSpeechKey)
            putBoolean("use_filler_sentence", useFillerSentence)
            putBoolean("auto_detect_language", autoDetectLanguage)
            putBoolean("use_formal_language", useFormalLanguage)
            putBoolean("use_leds", useLeds)
            putString("robot_password", pwdToStore)
            putInt("voice_speed", voiceSpeed)
            putInt("voice_pitch", voicePitch)
            putInt("user_font_size", userFontSize)
            putInt("robot_font_size", robotFontSize)
            putInt("silence_duration", silenceDuration)
            putBoolean("mic_auto_off_enabled", micAutoOffEnabled)
            putInt("mic_auto_off_minutes", micAutoOffMinutes)
            apply()
        }
    }

    private fun securePrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            this, "secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deleteAllData() {
        Log.i(TAG, "deleteAllData() with filesDir=$filesDir")
        val fileStorageManager = FileStorageManager(filesDir)

        fileStorageManager.dialogueStateFile?.delete()
        fileStorageManager.speakersInfoFile?.delete()
        fileStorageManager.dialogueStatisticsFile?.delete()

        val sharedPreferences = securePrefs()

        with(sharedPreferences.edit()) {
            clear()
            apply()
        }

        // Delete all scheduled interventions
        // val interventionPrefs = getSharedPreferences("interventions", MODE_PRIVATE)
        // with(interventionPrefs.edit()) {
        //    clear()
        //    apply()
        //}

        // Also clear in-memory interventions
        //InterventionManager.getInstance(this).clearAll()

        Toast.makeText(this, "Tutti i dati sono stati cancellati.", Toast.LENGTH_LONG).show()
    }
}