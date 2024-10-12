// ServerCommunicationManager.kt
package com.ricelab.cairclient.libraries

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.*

data class FirstServerResponse(
    val firstSentence: String,
    val dialogueState: Map<String, Any>
)

class ServerCommunicationManager(
    private val context: Context,
    private val serverIp: String,
    private val serverPort: Int,
    private val openAIApiKey: String
) {

    private val client: OkHttpClient
    private val gson = Gson()

    init {
        client = createSecureOkHttpClient()
    }

    // Create an OkHttpClient that trusts the server certificate
    private fun createSecureOkHttpClient(): OkHttpClient {
        return try {
            // Convert server IP to filename format (replace dots with underscores)
            val ipFormatted = serverIp.replace(".", "_")
            val certificateFileName = "certificates/server_$ipFormatted.crt"

            Log.i("MainActivity", "Certificate name: $certificateFileName")
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

    suspend fun firstServerRequest(language: String): FirstServerResponse {
        val url = "https://$serverIp:$serverPort/CAIR_hub/start"

        Log.d("ServerCommunication","Url for server connect: $url")

        // Preparare il payload della richiesta come stringa JSON
        val jsonPayloadString = gson.toJson(
            mapOf(
                "language" to language,
                "openai_api_key" to openAIApiKey
            )
        )

        // Costruire la richiesta POST con payload JSON
        val requestBody = jsonPayloadString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    // Leggere il corpo della risposta come stringa
                    val responseBody = response.body?.string() ?: throw Exception("Empty response")
                    // Analizzare la risposta JSON
                    parseFirstServerResponse(responseBody)
                } else {
                    throw Exception("Server returned an error: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("ServerCommunication", "Failed to acquire initial state: ${e.message}")
                throw e // Rilanciare l'eccezione per gestirla altrove
            }
        }
    }

    private fun parseFirstServerResponse(responseBody: String): FirstServerResponse {
        return try {
            // Analizzare la risposta JSON in una mappa
            val responseData = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
            val firstSentence = responseData["first_sentence"] as? String
                ?: throw Exception("Invalid first_sentence format")
            val dialogueState = (responseData["dialogue_state"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                ?.mapValues { it.value ?: throw Exception("Null value found in dialogue_state") }
                ?: throw Exception("Invalid dialogue_state format")

            FirstServerResponse(firstSentence = firstSentence, dialogueState = dialogueState)
        } catch (e: Exception) {
            Log.e("ServerCommunication", "Error parsing JSON response: ${e.message}")
            throw Exception("Error parsing JSON response: ${e.message}", e)
        }
    }


}