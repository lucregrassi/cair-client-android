package com.ricelab.cairclient.libraries

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.*
import com.ricelab.cairclient.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AudioRecorder"
private const val DEFAULT_LANGUAGE = "it-IT"

data class AudioResult(
    val xmlResult: String,
    val log: JSONObject
)

class AudioRecorder(private val context: Context, private val autoDetectLanguage: Boolean) {

    // Audio recording configuration
    private val sampleRate = 16000
    private val audioSource = MediaRecorder.AudioSource.MIC
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = 2*AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Detection thresholds
    private var speechDetectionThreshold = 2000
    private val thresholdAdjustmentValue = 2000
    private val shortSilenceDurationMillis = 500L
    private val longSilenceDurationMillis = 2000L
    private val initialTimeoutMillis = 60_000L

    @Volatile
    private var isRecording = false

    private var lastStartTime: Long = 0

    private val subscriptionKey: String by lazy {
        val masterKeyAlias = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        sharedPreferences.getString("azure_speech_key", "") ?: ""
    }

    private val serviceRegion = "westeurope"

    // Store detected languages for all chunks
    private val detectedLanguages = mutableListOf<String>()

    init {
        Log.d(TAG, "AudioRecorder initialized. Starting initial threshold calibration.")
        recalibrateThreshold()
    }

    private fun measureBackgroundNoise(): Int {
        Log.d(TAG, "Measuring background noise level.")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission to record audio was denied.")
            return -1
        }

        val audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
        val audioBuffer = ShortArray(bufferSize)
        var maxAmplitude = 0
        try {
            audioRecord.startRecording()
            audioRecord.read(audioBuffer, 0, bufferSize)
            maxAmplitude = audioBuffer.maxOrNull()?.toInt() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error measuring background noise: ${e.message}", e)
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        Log.i(TAG, "Measured background noise level: $maxAmplitude")
        return maxAmplitude
    }

    fun recalibrateThreshold(): Int {
        val backgroundNoiseLevel = measureBackgroundNoise()
        speechDetectionThreshold = backgroundNoiseLevel + thresholdAdjustmentValue
        Log.d(TAG, "Recalibrated threshold: $speechDetectionThreshold (background noise: $backgroundNoiseLevel)")

        (context as? Activity)?.runOnUiThread {
            val thresholdTextView: TextView? = context.findViewById(R.id.thresholdTextView)
            thresholdTextView?.text = "Soglia del rumore: $speechDetectionThreshold"
        }

        return speechDetectionThreshold
    }

    fun resetTimeout() {
        lastStartTime = System.currentTimeMillis()
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
    }

    suspend fun listenAndSplit(): AudioResult {
        val audioLogMap = mutableMapOf<String, Any>()
        Log.d(TAG, "Starting listenAndSplit method.")

        detectedLanguages.clear()

        return withContext(Dispatchers.IO) {
            var finalAudioResult: AudioResult? = null

            while (finalAudioResult == null) {
                finalAudioResult = recordAndRecognizeOneAttempt(audioLogMap)
            }

            finalAudioResult
        }
    }

    private suspend fun recordAndRecognizeOneAttempt(audioLogMap: MutableMap<String, Any>): AudioResult? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission to record audio was denied.")
            audioLogMap["timestamp"] = getCurrentTimestamp()
            audioLogMap["error_message"] = "Permission denied"
            return AudioResult(generateXmlString("Permission denied", "und"), JSONObject(audioLogMap))
        }

        val audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
        val noiseSuppressor: NoiseSuppressor? = if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioRecord.audioSessionId)
        } else {
            null
        }

        val audioBuffer = ShortArray(bufferSize)
        val byteArrayStream = ByteArrayOutputStream()
        var lastSpeechTime: Long? = null
        var lastDetectionTime: Long? = null
        val startTime = System.currentTimeMillis()
        val jobs = mutableListOf<Job>()
        val results = ConcurrentHashMap<Int, String>()
        var chunkIndex = 0

        try {
            audioRecord.startRecording()
            isRecording = true

            val scope = CoroutineScope(Dispatchers.Default)
            while (isRecording) {
                val currentTime = System.currentTimeMillis()
                val ret = audioRecord.read(audioBuffer, 0, audioBuffer.size)

                if (ret < 0) {
                    Log.e(TAG, "audioRecord read error $ret")
                    audioLogMap["timestamp"] = getCurrentTimestamp()
                    audioLogMap["error_message"] = "Error reading audio"
                    return AudioResult(generateXmlString("Error reading audio", "und"), JSONObject(audioLogMap))
                }

                val maxAmplitude = audioBuffer.maxOrNull()?.toInt() ?: 0
                if (maxAmplitude > speechDetectionThreshold) {
                    lastDetectionTime = currentTime
                    byteArrayStream.write(shortsToBytes(audioBuffer))
                }

                if (lastSpeechTime == null && currentTime - startTime > initialTimeoutMillis) {
                    Log.d(TAG, "Timeout due to no speech detected.")
                    isRecording = false
                    audioLogMap["timestamp"] = getCurrentTimestamp()
                    audioLogMap["error_message"] = "TIMEOUT"
                    return AudioResult(generateXmlString("*TIMEOUT*", "und"), JSONObject(audioLogMap))
                }

                if (lastDetectionTime != null) {
                    if (currentTime - lastDetectionTime > shortSilenceDurationMillis && byteArrayStream.size() > 0) {
                        val audioBytes = byteArrayStream.toByteArray()
                        byteArrayStream.reset()
                        val index = chunkIndex
                        chunkIndex++

                        val job = scope.launch {
                            val timestamp = getCurrentTimestamp()
                            val chunkAudioDuration = audioBytes.size.toDouble() / (sampleRate * 2)
                            val recognitionStart = System.currentTimeMillis()
                            val (partialText, partialLang) = recognizeChunk(audioBytes)
                            val recognitionEnd = System.currentTimeMillis()
                            val sttTime = (recognitionEnd - recognitionStart) / 1000.0

                            if (partialText.isNotEmpty()) {
                                if (lastSpeechTime == null || recognitionStart > lastSpeechTime!!) {
                                    Log.d(TAG, "New speech detected, setting lastSpeechTime to $recognitionStart.")
                                    lastSpeechTime = recognitionEnd
                                }
                                results[index] = partialText
                                // Only log chunk info if something was recognized
                                audioLogMap["timestamp"] = timestamp
                                audioLogMap["chunk_${chunkIndex}_duration"] = chunkAudioDuration
                                audioLogMap["chunk_${chunkIndex}_speech_to_text_time"] = sttTime
                                Log.d(TAG, "Chunk $chunkIndex recognized, duration=$chunkAudioDuration, sttTime=$sttTime")
                            }
                        }
                        jobs.add(job)
                    }

                    if (lastSpeechTime != null && currentTime - lastSpeechTime > longSilenceDurationMillis) {
                        Log.d(TAG, "Long silence detected, breaking recording.")
                        break
                    }
                }
            }

            val beforeJoinTime = System.currentTimeMillis()
            jobs.forEach { it.join() }
            val afterJoinTime = System.currentTimeMillis()

            val finalText = results.toSortedMap().values.joinToString(" ").trim()

            if (finalText.isNotEmpty()) {
                val finalDelayTime = (afterJoinTime - beforeJoinTime) / 1000.0
                audioLogMap["final_delay_time"] = finalDelayTime
                Log.d(TAG, "Final delay time logged: $finalDelayTime")
            }

            if (finalText.isEmpty()) {
                Log.i(TAG, "Nothing recognized in this attempt. Retrying...")
                return null // <---- KEY CHANGE
            }

            val finalLanguage = determineMajorityLanguage()
            return AudioResult(generateXmlString(finalText, finalLanguage), JSONObject(audioLogMap))

        } catch (e: Exception) {
            Log.e(TAG, "Exception during recording: ${e.message}", e)
            audioLogMap["timestamp"] = getCurrentTimestamp()
            audioLogMap["error_message"] = "Error during recording"
            return AudioResult(generateXmlString("Error during recording", "und"), JSONObject(audioLogMap))
        } finally {
            isRecording = false
            audioRecord.stop()
            audioRecord.release()
            noiseSuppressor?.release()
        }
    }

    private fun recognizeChunk(audioBytes: ByteArray): Pair<String, String> {
        if (audioBytes.isEmpty()) return Pair("", "")

        val tempFile = File.createTempFile("speech_chunk", ".wav", context.cacheDir)
        writeWavFile(audioBytes, tempFile)

        val speechConfig = SpeechConfig.fromSubscription(subscriptionKey, serviceRegion)
        val audioConfig = AudioConfig.fromWavFileInput(tempFile.absolutePath)

        val recognizer: SpeechRecognizer
        var autoDetectSourceLanguageConfig: AutoDetectSourceLanguageConfig? = null

        if (autoDetectLanguage) {
            // Use auto language detection
            Log.i(TAG, "Using autodetection of language")
            autoDetectSourceLanguageConfig = AutoDetectSourceLanguageConfig.fromLanguages(listOf("en-US", "it-IT"))
            recognizer = SpeechRecognizer(speechConfig, autoDetectSourceLanguageConfig, audioConfig)
        } else {
            // No auto detection, use default language
            Log.i(TAG, "Not using autodetection of language")
            speechConfig.speechRecognitionLanguage = DEFAULT_LANGUAGE // e.g. "it-IT"
            recognizer = SpeechRecognizer(speechConfig, audioConfig)
        }

        var recognizedText = ""
        var detectedLang = "und"

        // We'll store the cleanup actions and run them after returning the result.
        val cleanupActions: () -> Unit = {
            recognizer.close()
            autoDetectSourceLanguageConfig?.close()
            speechConfig.close()
            audioConfig.close()
            if (tempFile.exists()) tempFile.delete()
        }

        try {
            val result = recognizer.recognizeOnceAsync().get()
            when (result.reason) {
                ResultReason.RecognizedSpeech -> {
                    recognizedText = result.text ?: ""
                    if (autoDetectLanguage) {
                        val autoLangResult = AutoDetectSourceLanguageResult.fromResult(result)
                        val lang = autoLangResult.language
                        if (!lang.isNullOrEmpty()) {
                            detectedLang = lang
                            detectedLanguages.add(lang) // Store for majority decision later
                        }
                        Log.d(TAG, "SDK recognized text: $recognizedText")
                        Log.d(TAG, "Detected language via SDK: $detectedLang")
                    } else {
                        // If not auto-detecting, we know the language is DEFAULT_LANGUAGE.
                        detectedLang = speechConfig.speechRecognitionLanguage
                    }
                }
                ResultReason.NoMatch -> {
                    Log.w(TAG, "No speech recognized.")
                }
                ResultReason.Canceled -> {
                    val cancellation = CancellationDetails.fromResult(result)
                    Log.e(TAG, "Recognition canceled: ${cancellation.reason}, ${cancellation.errorDetails}")
                }
                else -> {
                    Log.w(TAG, "Recognition finished with reason: ${result.reason}")
                }
            }
            result.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during speech recognition: ${e.message}", e)
        }

        // Return the result immediately to speed up robot's response
        val finalText = recognizedText
        val finalLang = detectedLang

        // Perform cleanup asynchronously after returning
        // You can run it in a coroutine if you have one, or just in a thread:
        Thread {
            cleanupActions()
        }.start()

        return Pair(finalText, finalLang)
    }

    private fun determineMajorityLanguage(): String {
        if (detectedLanguages.isEmpty()) return "und"
        val langCount = detectedLanguages.groupingBy { it }.eachCount()
        return langCount.maxByOrNull { it.value }?.key ?: "und"
    }

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

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 0).toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() shr 0).toByte(),
            (value.toInt() shr 8).toByte()
        )
    }

    private fun shortsToBytes(sData: ShortArray): ByteArray {
        val bytes = ByteArray(sData.size * 2)
        for (i in sData.indices) {
            bytes[i * 2] = (sData[i].toInt() and 0x00FF).toByte()
            bytes[i * 2 + 1] = (sData[i].toInt() shr 8).toByte()
        }
        return bytes
    }

    private fun generateXmlString(sentence: String, lang: String): String {
        Log.d(TAG, "Generating XML string for sentence: '$sentence' in language: '$lang'")
        return """
            <response><profile_id value="00000000-0000-0000-0000-000000000000">$sentence<language>$lang</language><speaking_time>0.0</speaking_time></profile_id></response>
        """.trimIndent()
    }

    fun stopRecording() {
        Log.d(TAG, "Stop recording requested.")
        isRecording = false
    }
}