package com.ricelab.cairclient

import android.content.Intent
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
import androidx.activity.OnBackPressedCallback

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
    private lateinit var voiceSpeedSeekBar: SeekBar
    private lateinit var voiceSpeedLabel: TextView
    private lateinit var userFontSizeSeekBar: SeekBar
    private lateinit var userFontSizeLabel: TextView
    private lateinit var robotFontSizeSeekBar: SeekBar
    private lateinit var robotFontSizeLabel: TextView
    private lateinit var silenceDurationSeekBar: SeekBar
    private lateinit var silenceDurationLabel: TextView
    private lateinit var proceedButton: Button

    private val serverIpList = mutableListOf<String>()

    fun refreshRobotPasswordVisibility() {
        val show = useLedsSwitch.isChecked
        val visibility = if (show) View.VISIBLE else View.GONE
        robotPasswordEditText.visibility = visibility
        findViewById<TextView>(R.id.robotPasswordLabel).visibility = visibility
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
        robotPasswordEditText = findViewById(R.id.robotPasswordEditText)
        voiceSpeedSeekBar = findViewById(R.id.voiceSpeedSeekBar)
        voiceSpeedLabel = findViewById(R.id.voiceSpeedLabel)
        userFontSizeSeekBar = findViewById(R.id.userFontSizeSeekBar)
        userFontSizeLabel = findViewById(R.id.userFontSizeLabel)
        robotFontSizeSeekBar = findViewById(R.id.robotFontSizeSeekBar)
        robotFontSizeLabel = findViewById(R.id.robotFontSizeLabel)
        silenceDurationSeekBar = findViewById(R.id.silenceDurationSeekBar)
        silenceDurationLabel = findViewById(R.id.silenceDurationLabel)

        // Set saved value (default 100 if none)
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

        val savedVoiceSpeed = sharedPreferences.getInt("voice_speed", 100)
        voiceSpeedSeekBar.progress = savedVoiceSpeed
        voiceSpeedLabel.text = "Velocità voce: $savedVoiceSpeed%"

        voiceSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                voiceSpeedLabel.text = "Velocità voce: ${progress}%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val savedUserFontSize = sharedPreferences.getInt("user_font_size", 24)
        userFontSizeSeekBar.progress = savedUserFontSize
        userFontSizeLabel.text = "Dimensione testo utente: ${savedUserFontSize}sp"

        userFontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                userFontSizeLabel.text = "Dimensione testo utente: ${progress}sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val savedRobotFontSize = sharedPreferences.getInt("robot_font_size", 24)
        robotFontSizeSeekBar.progress = savedRobotFontSize
        robotFontSizeLabel.text = "Dimensione testo robot: ${savedRobotFontSize}sp"

        robotFontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                robotFontSizeLabel.text = "Dimensione testo robot: ${progress}sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val savedSilenceDuration = sharedPreferences.getInt("silence_duration", 2)
        silenceDurationSeekBar.progress = savedSilenceDuration
        silenceDurationLabel.text = "Durata silenzio (s): ${savedSilenceDuration}"

        silenceDurationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                silenceDurationLabel.text = "Durata silenzio (s): $progress"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        proceedButton = findViewById(R.id.proceedButton)

        val fromMenu = intent.getBooleanExtra("fromMenu", false)
        val deleteAllButton: Button = findViewById(R.id.deleteAllButton)
        deleteAllButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Load server IPs from certificates
        loadServerIpsFromCertificates()

        // Load saved values
        loadSavedValues(fromMenu)

        refreshRobotPasswordVisibility()

        useLedsSwitch.setOnCheckedChangeListener { _, isChecked ->
            refreshRobotPasswordVisibility()
            if (isChecked && robotPasswordEditText.text.toString().trim().isEmpty()) {
                // Keep the switch ON; just guide the user to enter the password
                Toast.makeText(this, "Per usare i LED inserisci la password del robot.", Toast.LENGTH_SHORT).show()
                robotPasswordEditText.requestFocus()
            }
        }

        proceedButton.setOnClickListener {
            val serverIp = serverIpSpinner.selectedItem as String
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

            Log.d(TAG, "Server IP: $serverIp")
            Log.d(TAG, "Server Port Text: $serverPortText")
            Log.d(TAG, "Experiment ID: $experimentId")
            Log.d(TAG, "Device ID: $deviceId")
            Log.d(TAG, "OpenAI API Key: $openAIApiKey")
            Log.d(TAG, "Azure Speech Key: $azureSpeechKey")
            Log.d(TAG, "Use Filler Sentence: $useFillerSentence")
            Log.d(TAG, "Auto Detect Language: $autoDetectLanguage")
            Log.d(TAG, "Use Formal Language: $useFormalLanguage")
            Log.d(TAG, "Voice Speed: ${voiceSpeedSeekBar.progress}")

            if (openAIApiKey.isEmpty() || serverPortText.isEmpty()) {
                Toast.makeText(this, "Please enter your OpenAI API Key and Server Port.", Toast.LENGTH_SHORT).show()
            } else {
                val serverPort = serverPortText.toIntOrNull()
                if (serverPort == null || serverPort <= 0 || serverPort > 65535) {
                    Toast.makeText(this, "Please enter a valid port number (1-65535).", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "Validated Server Port: $serverPort")
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
                        userFontSizeSeekBar.progress,
                        robotFontSizeSeekBar.progress,
                        silenceDurationSeekBar.progress
                        )

                    if (!fromMenu) {
                        Log.i(TAG, "FromMenu False, spawning new MainActivity")
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                    }
                    finish()
                }
            }
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
                )
                true
            }
            R.id.action_personalization -> {
                safelyNavigateTo(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_FRAGMENT, MainActivity.OPEN_PERSONALIZATION)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_LISTENING, true)  // <—
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                true
            }
            R.id.action_intervention -> {
                safelyNavigateTo(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_FRAGMENT, MainActivity.OPEN_INTERVENTION)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_LISTENING, true)  // <—
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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

    private fun loadSavedValues(fromMenu: Boolean) {
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

        if (!fromMenu && !savedServerIp.isNullOrEmpty() && !savedOpenAIApiKey.isNullOrEmpty() && savedServerPort != -1) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
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

            val savedVoiceSpeed = sharedPreferences.getInt("voice_speed", 100)
            voiceSpeedSeekBar.progress = savedVoiceSpeed
            voiceSpeedLabel.text = "Velocità voce: ${savedVoiceSpeed}%"

            val savedSilenceDuration = sharedPreferences.getInt("silence_duration", 2)
            silenceDurationSeekBar.progress = savedSilenceDuration
            silenceDurationLabel.text = "Durata silenzio (s): $savedSilenceDuration"
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
        userFontSize: Int,
        robotFontSize: Int,
        silenceDuration: Int
    ) {
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
            putInt("user_font_size", userFontSize)
            putInt("robot_font_size", robotFontSize)
            putInt("silence_duration", silenceDuration)
            apply()
        }
    }

    private fun deleteAllData() {
        Log.i(TAG, "deleteAllData() with filesDir=$filesDir")
        val fileStorageManager = FileStorageManager(null, filesDir)

        fileStorageManager.dialogueStateFile?.delete()
        fileStorageManager.speakersInfoFile?.delete()
        fileStorageManager.dialogueStatisticsFile?.delete()

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