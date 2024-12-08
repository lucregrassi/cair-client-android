package com.ricelab.cairclient

import android.content.Intent
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ricelab.cairclient.libraries.FileStorageManager
import java.io.IOException

private const val TAG = "SettingsActivity"

class SettingsActivity : AppCompatActivity() {

    private lateinit var serverIpSpinner: Spinner
    private lateinit var serverPortEditText: EditText
    private lateinit var openAIApiKeyEditText: EditText
    private lateinit var proceedButton: Button
    private lateinit var fillerSentenceSwitch: Switch
    private lateinit var autoDetectLanguageSwitch: Switch // Added line for auto language detection

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
        openAIApiKeyEditText = findViewById(R.id.openAIApiKeyEditText)
        proceedButton = findViewById(R.id.proceedButton)
        fillerSentenceSwitch = findViewById(R.id.fillerSentenceSwitch)
        autoDetectLanguageSwitch = findViewById(R.id.autoDetectLanguageSwitch) // Initialize the switch

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
            val openAIApiKey = openAIApiKeyEditText.text.toString().trim()
            val serverPortText = serverPortEditText.text.toString().trim()
            val useFillerSentence = fillerSentenceSwitch.isChecked
            val autoDetectLanguage = autoDetectLanguageSwitch.isChecked // Get the switch state

            Log.d(TAG, "Server IP: $serverIp")
            Log.d(TAG, "OpenAI API Key: $openAIApiKey")
            Log.d(TAG, "Server Port Text: $serverPortText")
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
                    saveValues(serverIp, openAIApiKey, serverPort, useFillerSentence, autoDetectLanguage)

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
        val savedOpenAIApiKey = sharedPreferences.getString("openai_api_key", null)
        val savedServerPort = sharedPreferences.getInt("server_port", -1)
        val useFillerSentence = sharedPreferences.getBoolean("use_filler_sentence", false)
        val autoDetectLanguage = sharedPreferences.getBoolean("auto_detect_language", true) // Default true if not set

        if (!fromMenu && !savedServerIp.isNullOrEmpty() && !savedOpenAIApiKey.isNullOrEmpty() && savedServerPort != -1) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            savedServerIp?.let { setSelectedServerIp(it) }
            savedOpenAIApiKey?.let { openAIApiKeyEditText.setText(it) }
            if (savedServerPort != -1) {
                serverPortEditText.setText(savedServerPort.toString())
            }
            fillerSentenceSwitch.isChecked = useFillerSentence
            autoDetectLanguageSwitch.isChecked = autoDetectLanguage
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

    private fun saveValues(serverIp: String, openAIApiKey: String, serverPort: Int, useFillerSentence: Boolean, autoDetectLanguage: Boolean) {
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
            putString("openai_api_key", openAIApiKey)
            putInt("server_port", serverPort)
            putBoolean("use_filler_sentence", useFillerSentence)
            putBoolean("auto_detect_language", autoDetectLanguage)
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

        Toast.makeText(this, "Tutti i dati sono stati cancellati.", Toast.LENGTH_LONG).show()
    }
}