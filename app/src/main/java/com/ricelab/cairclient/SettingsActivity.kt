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
import java.io.IOException

private const val TAG = "SettingsActivity"

class SettingsActivity : AppCompatActivity() {

    private lateinit var serverIpSpinner: Spinner
    private lateinit var serverPortEditText: EditText
    private lateinit var experimentIdEditText: EditText
    private lateinit var deviceIdEditText: EditText
    private lateinit var personNameEditText: EditText
    private lateinit var personGenderEditText: EditText
    private lateinit var personAgeEditText: EditText
    private lateinit var openAIApiKeyEditText: EditText
    private lateinit var azureSpeechKeyEditText: EditText
    private lateinit var fillerSentenceSwitch: SwitchCompat
    private lateinit var autoDetectLanguageSwitch: SwitchCompat
    private lateinit var formalLanguageSwitch: SwitchCompat
    private lateinit var proceedButton: Button

    private val serverIpList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the layout
        setContentView(R.layout.activity_settings)
        // Enable the "Up" button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize UI elements
        serverIpSpinner = findViewById(R.id.serverIpSpinner)
        serverPortEditText = findViewById(R.id.serverPortEditText)
        experimentIdEditText = findViewById(R.id.experimentIdEditText)
        deviceIdEditText = findViewById(R.id.deviceIdEditText)
        personNameEditText = findViewById(R.id.personNameEditText)
        personGenderEditText = findViewById(R.id.personGenderEditText)
        personAgeEditText = findViewById(R.id.personAgeEditText)
        openAIApiKeyEditText = findViewById(R.id.openAIApiKeyEditText)
        azureSpeechKeyEditText = findViewById(R.id.azureSpeechKeyEditText)
        fillerSentenceSwitch = findViewById(R.id.fillerSentenceSwitch)
        autoDetectLanguageSwitch = findViewById(R.id.autoDetectLanguageSwitch)
        formalLanguageSwitch = findViewById(R.id.formalLanguageSwitch)
        proceedButton = findViewById(R.id.proceedButton)

        val fromMenu = intent.getBooleanExtra("fromMenu", false)
        val deleteAllButton: Button = findViewById(R.id.deleteAllButton)
        deleteAllButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Load saved values
        loadSavedValues(fromMenu)

        // Load server IPs from certificates
        loadServerIpsFromCertificates()

        proceedButton.setOnClickListener {
            val serverIp = serverIpSpinner.selectedItem as String
            val serverPortText = serverPortEditText.text.toString().trim()
            val experimentId = experimentIdEditText.text.toString().trim()
            val deviceId = deviceIdEditText.text.toString().trim()
            val personName = personNameEditText.text.toString().trim()
            val personGender = personGenderEditText.text.toString().trim()
            val personAge = personAgeEditText.text.toString().trim()
            val openAIApiKey = openAIApiKeyEditText.text.toString().trim()
            val azureSpeechKey = azureSpeechKeyEditText.text.toString().trim()
            val useFillerSentence = fillerSentenceSwitch.isChecked
            val autoDetectLanguage = autoDetectLanguageSwitch.isChecked
            val useFormalLanguage = formalLanguageSwitch.isChecked

            Log.d(TAG, "Server IP: $serverIp")
            Log.d(TAG, "Server Port Text: $serverPortText")
            Log.d(TAG, "Experiment ID: $experimentId")
            Log.d(TAG, "Device ID: $deviceId")
            Log.d(TAG, "OpenAI API Key: $openAIApiKey")
            Log.d(TAG, "Azure Speech Key: $azureSpeechKey")
            Log.d(TAG, "Use Filler Sentence: $useFillerSentence")
            Log.d(TAG, "Auto Detect Language: $autoDetectLanguage")

            if (openAIApiKey.isEmpty() || serverPortText.isEmpty()) {
                Toast.makeText(this, "Please enter your OpenAI API Key and Server Port.", Toast.LENGTH_SHORT).show()
            } else {
                val serverPort = serverPortText.toIntOrNull()
                if (serverPort == null || serverPort <= 0 || serverPort > 65535) {
                    Toast.makeText(this, "Please enter a valid port number (1-65535).", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "Validated Server Port: $serverPort")

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
                        useFormalLanguage)

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Navigate back to MainActivity when the back button is pressed
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
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
            savedPersonGender?.let { personGenderEditText.setText(it) }
            savedPersonAge?.let { personAgeEditText.setText(it) }
            savedOpenAIApiKey?.let { openAIApiKeyEditText.setText(it) }
            savedAzureSpeechKey?.let { azureSpeechKeyEditText.setText(it) }
            fillerSentenceSwitch.isChecked = useFillerSentence
            autoDetectLanguageSwitch.isChecked = autoDetectLanguage
            formalLanguageSwitch.isChecked = useFormalLanguage
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
        useFormalLanguage: Boolean
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
        val interventionPrefs = getSharedPreferences("interventions", MODE_PRIVATE)
        with(interventionPrefs.edit()) {
            clear()
            apply()
        }

        Toast.makeText(this, "Tutti i dati sono stati cancellati.", Toast.LENGTH_LONG).show()
    }
}