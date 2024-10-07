// ServerCommunicationManager.kt
package com.ricelab.cairclient.libraries

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import java.security.KeyStore
import javax.net.ssl.*
import android.content.Context
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.InputStream
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection

// Define data classes to model the server's response
data class ServerResponse(
    val firstSentence: String,
    val dialogueState: Map<String, Any>
)

// Define the ServerCommunicationManager class
class ServerCommunicationManager(
    private val context: Context,
    private val serverIp: String,
    private val serverPort: Int,
    private val openAIApiKey: String
) {

    private val client: OkHttpClient
    private val gson = Gson()

    init {
        // Initialize the OkHttpClient with SSL configuration
        client = createSecureOkHttpClient()
    }

    suspend fun acquireInitialState(language: String): ServerResponse {
        val url = "https://$serverIp:$serverPort/CAIR_hub/start"

        // Prepare the request payload as a JSON string
        val jsonPayloadString = gson.toJson(
            mapOf(
                "language" to language,
                "openai_api_key" to openAIApiKey
            )
        )

        // Build the POST request with JSON payload
        val requestBody = jsonPayloadString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                // Read the response body as a string
                val responseBody = response.body?.string() ?: throw Exception("Empty response")
                // Parse the JSON response
                parseServerResponse(responseBody)
            } else {
                throw Exception("Server returned an error: ${response.code}")
            }
        }
    }

    private fun parseServerResponse(responseBody: String): ServerResponse {
        return try {
            // Parse the JSON response into a Map
            val responseData = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val firstSentence = responseData["first_sentence"] as? String
                ?: throw Exception("Invalid first_sentence format")
            val dialogueState = (responseData["dialogue_state"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                ?.mapValues { it.value ?: throw Exception("Null value found in dialogue_state") }
                ?: throw Exception("Invalid dialogue_state format")

            ServerResponse(firstSentence = firstSentence, dialogueState = dialogueState)
        } catch (e: Exception) {
            throw Exception("Error parsing JSON response: ${e.message}", e)
        }
    }

    // Create an OkHttpClient that trusts the server certificate
    private fun createSecureOkHttpClient(): OkHttpClient {
        return try {
            // Convert server IP to filename format (replace dots with underscores)
            val ipFormatted = serverIp.replace(".", "_")
            val certificateFileName = "certificates/server_$ipFormatted.crt"

            // Load the certificate from assets
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val caInput: InputStream = context.assets.open(certificateFileName)
            val ca = certificateFactory.generateCertificate(caInput)
            caInput.close()

            // Create a KeyStore containing our trusted CAs
            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType).apply {
                load(null, null)
                setCertificateEntry("ca", ca)
            }

            // Create a TrustManager that trusts the CAs in our KeyStore
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }

            val trustManagers = trustManagerFactory.trustManagers
            val x509TrustManager = trustManagers[0] as X509TrustManager

            // Create an SSLContext that uses our TrustManager
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(x509TrustManager), null)
            }

            // Create an SSLSocketFactory with our SSLContext
            val sslSocketFactory = sslContext.socketFactory

            // Build the OkHttpClient with the custom SSL settings
            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, x509TrustManager)
                .hostnameVerifier { hostname, session ->
                    // Implement hostname verification logic if necessary
                    HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
                }
                .build()
        } catch (e: Exception) {
            throw RuntimeException("Failed to create a secure OkHttpClient: ${e.message}", e)
        }
    }
}