package com.ricelab.cairclient

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.*

private const val TAG = "AudioRecorder"

class AudioRecorder(private val context: Context) {

    // Audio recording configuration parameters
    private val sampleRate = 16000
    private val audioSource = MediaRecorder.AudioSource.MIC
    private val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = 4 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val microsoftApiKey = BuildConfig.MICROSOFT_SPEECH_API_KEY
    private var speechDetectionThreshold = 2000 // Default threshold, adjusted after background noise measurement
    private val thresholdAdjustmentValue = 1500 // Value added to the background noise level to determine the threshold
    private val shortSilenceDurationMillis = 500L
    private val longSilenceDurationMillis = 2000L
    private val initialTimeoutMillis = 30000L

    @Volatile
    private var isRecording = false

    init {
        // Initial threshold calibration
        recalibrateThreshold()
    }

    /**
     * Measures the background noise level using the microphone and returns the maximum amplitude detected.
     * @return The measured maximum amplitude of background noise.
     */
    private fun measureBackgroundNoise(): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission to record audio was denied.")
            return -1 // Return an error code if permission is not granted
        }

        val audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
        val audioBuffer = ShortArray(bufferSize)
        var maxAmplitude = 0

        try {
            audioRecord.startRecording()
            audioRecord.read(audioBuffer, 0, bufferSize)
            maxAmplitude = audioBuffer.maxOrNull()?.toInt() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error measuring background noise: ${e.message}")
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        Log.i(TAG, "Measured background noise level: $maxAmplitude")
        return maxAmplitude
    }

    /**
     * Recalibrates the speech detection threshold based on the current background noise.
     * @return The new threshold value.
     */
    fun recalibrateThreshold(): Int {
        val backgroundNoiseLevel = measureBackgroundNoise()
        speechDetectionThreshold = backgroundNoiseLevel + thresholdAdjustmentValue

        // Update the threshold value in the UI if the context is an Activity
        (context as? Activity)?.runOnUiThread {
            val thresholdTextView: TextView? = context.findViewById(R.id.thresholdTextView)
            thresholdTextView?.text = "Threshold: $speechDetectionThreshold"
        }

        return speechDetectionThreshold
    }

    /**
     * Starts listening and splitting the audio input, processing the audio in 0.5-second intervals.
     * @return The final transcribed text after processing the audio input.
     */
    suspend fun listenAndSplit(): String {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Permission to record audio was denied.")
            return "Permission to record audio was denied."
        }

        return withContext(Dispatchers.IO) {
            val audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)

            // Enable NoiseSuppressor if available
            val noiseSuppressor: NoiseSuppressor? = if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioRecord.audioSessionId).apply {
                    Log.i(TAG, "NoiseSuppressor enabled")
                }
            } else {
                Log.w(TAG, "NoiseSuppressor not supported on this device")
                null
            }

            val audioBuffer = ShortArray(bufferSize)
            val finalResult = StringBuilder()
            val currentPartialResult = StringBuilder()
            val byteArrayStream = ByteArrayOutputStream()
            var lastSpeechTime: Long? = null
            val startTime = System.currentTimeMillis()

            try {
                audioRecord.startRecording()
                isRecording = true

                while (isRecording) {
                    val currentTime = System.currentTimeMillis()
                    val ret = audioRecord.read(audioBuffer, 0, audioBuffer.size)

                    if (ret < 0) {
                        Log.d(TAG, "audioRecord read error $ret")
                        return@withContext "Error reading audio"
                    }

                    val maxAmplitude = audioBuffer.maxOrNull()?.toInt() ?: 0
                    if (maxAmplitude > speechDetectionThreshold) {
                        lastSpeechTime = currentTime
                        byteArrayStream.write(shortsToBytes(audioBuffer))
                    }

                    // If no speech is detected within the initial timeout, stop recording
                    if (lastSpeechTime == null && currentTime - startTime > initialTimeoutMillis) {
                        Log.d(TAG, "Timeout due to no speech detected.")
                        isRecording = false
                        return@withContext "Timeout"
                    }

                    // Split the audio every 0.5 seconds and send it for processing
                    if (lastSpeechTime != null) {
                        if (currentTime - lastSpeechTime > shortSilenceDurationMillis && byteArrayStream.size() > 0) {
                            val audioBytes = byteArrayStream.toByteArray()
                            val partialResult = sendToMicrosoftSpeechRecognition(audioBytes)
                            if (partialResult.isNotEmpty()) {
                                currentPartialResult.append(partialResult).append(" ")
                                byteArrayStream.reset()
                            } else {
                                isRecording = false
                                return@withContext ""
                            }
                        }

                        // Finalize the result if there is a long silence
                        if (currentTime - lastSpeechTime > longSilenceDurationMillis) {
                            finalResult.append(currentPartialResult.toString().trim()).append(" ")
                            break
                        }
                    }
                }

                // Send any remaining audio for processing
                if (byteArrayStream.size() > 0) {
                    val audioBytes = byteArrayStream.toByteArray()
                    val partialResult = sendToMicrosoftSpeechRecognition(audioBytes)
                    finalResult.append(partialResult)
                }

                return@withContext finalResult.toString().trim()

            } catch (e: IOException) {
                Log.e(TAG, "Error recording audio: ${e.message}", e)
                return@withContext "Error recording audio: ${e.message}"
            } finally {
                isRecording = false
                audioRecord.stop()
                audioRecord.release()
                noiseSuppressor?.release() // Release NoiseSuppressor when done
            }
        }
    }

    /**
     * Sends the recorded audio bytes to Microsoft Speech Recognition for transcription.
     * @param audioBytes The audio data to be sent for transcription.
     * @return The transcribed text from Microsoft Speech Recognition.
     */
    private fun sendToMicrosoftSpeechRecognition(audioBytes: ByteArray): String {
        val client = OkHttpClient()
        val mediaType = "audio/wav".toMediaTypeOrNull()
        val tempFile = File.createTempFile("speech_chunk", ".wav", context.cacheDir)

        writeWavFile(audioBytes, tempFile)

        val requestBody = tempFile.asRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://westeurope.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=it-IT")
            .post(requestBody)
            .addHeader("Content-Type", "audio/wav")
            .addHeader("Ocp-Apim-Subscription-Key", microsoftApiKey)
            .build()

        Log.d(TAG, "Sending audio data to Microsoft Speech Recognition...")
        val response = client.newCall(request).execute()
        return if (response.isSuccessful) {
            val responseBody = response.body?.string()
            responseBody?.let { extractTextFromResponse(it) } ?: ""
        } else {
            Log.e(TAG, "Failed to get response from Microsoft Speech Recognition. Code: ${response.code}")
            ""
        }
    }

    /**
     * Writes the recorded audio data to a WAV file.
     * @param audioBytes The audio data to be written.
     * @param file The file where the audio data will be written.
     */
    private fun writeWavFile(audioBytes: ByteArray, file: File) {
        try {
            FileOutputStream(file).use { fos ->
                val totalAudioLen = audioBytes.size.toLong()
                val totalDataLen = totalAudioLen + 36
                val byteRate = 16 * sampleRate * 1 / 8

                fos.write("RIFF".toByteArray(Charsets.US_ASCII))
                fos.write(intToByteArray(totalDataLen.toInt()))
                fos.write("WAVE".toByteArray(Charsets.US_ASCII))
                fos.write("fmt ".toByteArray(Charsets.US_ASCII))
                fos.write(intToByteArray(16))  // Subchunk1Size
                fos.write(shortToByteArray(1)) // AudioFormat (PCM)
                fos.write(shortToByteArray(1)) // NumChannels
                fos.write(intToByteArray(sampleRate))
                fos.write(intToByteArray(byteRate))
                fos.write(shortToByteArray(2)) // BlockAlign
                fos.write(shortToByteArray(16)) // BitsPerSample
                fos.write("data".toByteArray(Charsets.US_ASCII))
                fos.write(intToByteArray(totalAudioLen.toInt()))
                fos.write(audioBytes)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing wav file: ${e.message}", e)
        }
    }

    /**
     * Extracts the transcribed text from the JSON response returned by Microsoft Speech Recognition.
     * @param response The JSON response string from the API.
     * @return The extracted transcribed text.
     */
    private fun extractTextFromResponse(response: String): String {
        return try {
            val jsonObject = JSONObject(response)
            jsonObject.optString("DisplayText", "")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response JSON: ${e.message}", e)
            ""
        }
    }

    /**
     * Converts an array of shorts to an array of bytes.
     * @param sData The short array to be converted.
     * @return The resulting byte array.
     */
    private fun shortsToBytes(sData: ShortArray): ByteArray {
        val bytes = ByteArray(sData.size * 2)
        for (i in sData.indices) {
            bytes[i * 2] = (sData[i].toInt() and 0x00FF).toByte()
            bytes[i * 2 + 1] = (sData[i].toInt() shr 8).toByte()
        }
        return bytes
    }

    /**
     * Converts an integer to a byte array.
     * @param value The integer to be converted.
     * @return The resulting byte array.
     */
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 0).toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte()
        )
    }

    /**
     * Converts a short value to a byte array.
     * @param value The short to be converted.
     * @return The resulting byte array.
     */
    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() shr 0).toByte(),
            (value.toInt() shr 8).toByte()
        )
    }

    /**
     * Stops the audio recording.
     */
    fun stopRecording() {
        Log.d(TAG, "Stop recording requested.")
        isRecording = false
    }
}