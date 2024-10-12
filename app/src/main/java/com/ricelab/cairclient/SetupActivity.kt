package com.ricelab.cairclient

import android.content.Intent
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

// Libraries for secure storage
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException

class SetupActivity : AppCompatActivity() {

    private lateinit var serverIpSpinner: Spinner
    private lateinit var serverPortEditText: EditText
    private lateinit var openAIApiKeyEditText: EditText
    private lateinit var proceedButton: Button

    private val serverIpList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the layout
        setContentView(R.layout.activity_setup)

        // Initialize UI elements
        serverIpSpinner = findViewById(R.id.serverIpSpinner)
        serverPortEditText = findViewById(R.id.serverPortEditText)
        openAIApiKeyEditText = findViewById(R.id.openAIApiKeyEditText)
        proceedButton = findViewById(R.id.proceedButton)

        // Load saved values if they exist
        loadSavedValues()

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
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    // Function to load saved values
    private fun loadSavedValues() {
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

        if (!savedServerIp.isNullOrEmpty() && !savedOpenAIApiKey.isNullOrEmpty() && savedServerPort != -1) {
            // Values exist, proceed to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            // Set the saved values in the input fields (optional)
            savedOpenAIApiKey?.let { openAIApiKeyEditText.setText(it) }
            if (savedServerPort != -1) {
                serverPortEditText.setText(savedServerPort.toString())
            }
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
                Log.e("SetupActivity", "No server certificates found in assets/certificates/")
            } else {
                // Populate the Spinner with the server IPs
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serverIpList)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                serverIpSpinner.adapter = adapter
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error loading server IPs: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("SetupActivity", "Error loading server IPs", e)
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
}