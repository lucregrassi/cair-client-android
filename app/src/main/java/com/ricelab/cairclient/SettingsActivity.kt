package com.ricelab.cairclient

import android.content.Intent
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

// Libraries for secure storage
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

        // Check if SetupActivity was opened from the menu or on first launch
        val fromMenu = intent.getBooleanExtra("fromMenu", false)
        val deleteAllButton: Button = findViewById(R.id.deleteAllButton)
        deleteAllButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Load saved values
        loadSavedValues(fromMenu)

        // Load server IPs from certificates
        loadServerIpsFromCertificates()

        // Set click listener for the proceed button
        proceedButton.setOnClickListener {
            val serverIp = serverIpSpinner.selectedItem as String
            val openAIApiKey = openAIApiKeyEditText.text.toString().trim()
            val serverPortText = serverPortEditText.text.toString().trim()

            if (openAIApiKey.isEmpty() || serverPortText.isEmpty()) {
                Toast.makeText(this, "Please enter your OpenAI API Key and Server Port.", Toast.LENGTH_SHORT).show()
            } else {
                val serverPort = serverPortText.toIntOrNull()
                if (serverPort == null || serverPort <= 0 || serverPort > 65535) {
                    Toast.makeText(this, "Please enter a valid port number (1-65535).", Toast.LENGTH_SHORT).show()
                } else {
                    // Save the values securely
                    saveValues(serverIp, openAIApiKey, serverPort)
                    // Proceed to MainActivity
                    //val intent = Intent(this, MainActivity::class.java)
                    //intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
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

    // Function to show a confirmation dialog before deleting all data
    private fun showDeleteConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Conferma cancellazione")
        builder.setMessage("Sei sicuro di voler cancellare tutti i dati?")

        builder.setPositiveButton("Sì") { dialog, which ->
            deleteAllData()
        }

        builder.setNegativeButton("No") { dialog, which ->
            dialog.dismiss() // Do nothing on "No"
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    // Handle the "Up" button press
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

    // Function to load saved values based on the 'fromMenu' flag
    private fun loadSavedValues(fromMenu: Boolean) {
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

        // Load saved server IP, OpenAI API key, and server port if they exist
        val savedServerIp = sharedPreferences.getString("server_ip", null)
        val savedOpenAIApiKey = sharedPreferences.getString("openai_api_key", null)
        val savedServerPort = sharedPreferences.getInt("server_port", -1)

        // If opened for the first time (not from the menu) and values exist, proceed to MainActivity
        if (!fromMenu && !savedServerIp.isNullOrEmpty() && !savedOpenAIApiKey.isNullOrEmpty() && savedServerPort != -1) {
            // Values exist, proceed to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            Log.i(TAG, "Spawing MainActivity ONLY THE FIRST TIME")
            startActivity(intent)
            finish()
        } else {
            // If the activity is opened from the menu, or no saved values exist,
            // populate the fields with saved values or leave them empty for input
            savedServerIp?.let { setSelectedServerIp(it) }
            savedOpenAIApiKey?.let { openAIApiKeyEditText.setText(it) }
            if (savedServerPort != -1) {
                serverPortEditText.setText(savedServerPort.toString())
            }
        }
    }

    // Set the selected server IP in the spinner
    private fun setSelectedServerIp(ip: String) {
        val index = serverIpList.indexOf(ip)
        if (index != -1) {
            serverIpSpinner.setSelection(index)
        }
    }

    // Function to load server IPs from certificate filenames
    private fun loadServerIpsFromCertificates() {
        try {
            val assetManager: AssetManager = assets
            // List the files in the 'certificates' directory
            val certificateFiles = assetManager.list("certificates") ?: arrayOf()
            for (filename in certificateFiles) {
                // Check if filename matches the pattern 'server_<ip>.crt'
                if (filename.startsWith("server_") && filename.endsWith(".crt")) {
                    // Extract the IP address
                    val ip = filename.removePrefix("server_").removeSuffix(".crt").replace("_", ".")
                    serverIpList.add(ip)
                }
            }
            if (serverIpList.isEmpty()) {
                // No certificates found
                Toast.makeText(this, "No server certificates found.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "No server certificates found in assets/certificates/")
            } else {
                // Populate the Spinner with the server IPs
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serverIpList)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                serverIpSpinner.adapter = adapter
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error loading server IPs: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error loading server IPs", e)
        }
    }

    // Function to save values securely
    private fun saveValues(serverIp: String, openAIApiKey: String, serverPort: Int) {
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

        // Save the server IP, OpenAI API key, and server port securely
        with(sharedPreferences.edit()) {
            putString("server_ip", serverIp)
            putString("openai_api_key", openAIApiKey)
            putInt("server_port", serverPort)
            apply()
        }
    }

    private fun deleteAllData() {
        // Initialize FileStorageManager
        Log.i(TAG, "deleteAllData() with filesDir=$filesDir")
        var fileStorageManager = FileStorageManager(null, filesDir)

        Log.i(TAG, "File exists? ${fileStorageManager.filesExist()}")
        // Delete all the files managed by FileStorageManager
        fileStorageManager.dialogueStateFile?.delete()
        fileStorageManager.speakersInfoFile?.delete()
        fileStorageManager.dialogueStatisticsFile?.delete()
        Log.i(TAG, "File exists? ${fileStorageManager.filesExist()}")

        // Clear EncryptedSharedPreferences
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

        // Show a confirmation toast or log
        Toast.makeText(this, "Tutti i dati sono stati cancellati.", Toast.LENGTH_LONG).show()
    }
}