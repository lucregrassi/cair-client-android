import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaPlayer
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

class AudioRecorder(private val context: Context, private val thresholdTextView: TextView) {

    private val sampleRate = 16000
    private val audioSource = MediaRecorder.AudioSource.MIC
    private val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = 4 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val silenceDurationMillis = 500L
    private val longSilenceDurationMillis = 2000L
    private val initialTimeoutMillis = 30000L

    private var threshold = 1500  // Default value, will be adjusted based on background noise

    @Volatile
    private var isRecording = false

    init {
        // Measure background noise and set threshold
        threshold = measureBackgroundNoise() + 500  // Set threshold slightly above background noise level
        Log.d(TAG, "Threshold set to $threshold based on background noise")

        // Update the UI with the calculated threshold
        thresholdTextView.post {
            thresholdTextView.text = "Threshold: $threshold"
        }
    }

    private fun measureBackgroundNoise(): Int {
        val audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
        val audioBuffer = ShortArray(bufferSize)
        var maxAmplitude = 0

        try {
            audioRecord.startRecording()

            // Attach NoiseSuppressor if available
            val noiseSuppressor: NoiseSuppressor? = if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioRecord.audioSessionId)
            } else {
                Log.w(TAG, "NoiseSuppressor not supported on this device")
                null
            }

            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 2000) {  // Measure for 2 seconds
                val ret = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                if (ret > 0) {
                    val amplitude = audioBuffer.maxOrNull() ?: 0
                    if (amplitude > maxAmplitude) {
                        maxAmplitude = amplitude
                    }
                }
            }

            audioRecord.stop()
            noiseSuppressor?.release()
        } finally {
            audioRecord.release()
        }

        Log.d(TAG, "Measured background noise max amplitude: $maxAmplitude")
        return maxAmplitude
    }

    suspend fun listenAndSplit(): String {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Permission to record audio was denied.")
            return "Permission to record audio was denied."
        }

        return withContext(Dispatchers.IO) {
            val audioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)

            // Attach NoiseSuppressor if available
            val noiseSuppressor: NoiseSuppressor? = if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioRecord.audioSessionId)
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

                    val maxAmplitude = audioBuffer.maxOrNull() ?: 0
                    if (maxAmplitude > threshold) {
                        lastSpeechTime = currentTime
                        byteArrayStream.write(shortsToBytes(audioBuffer))
                    }

                    if (lastSpeechTime == null && currentTime - startTime > initialTimeoutMillis) {
                        Log.d(TAG, "Timeout due to no speech detected.")
                        isRecording = false
                        return@withContext "Timeout"
                    }

                    if (lastSpeechTime != null) {
                        if (currentTime - lastSpeechTime > silenceDurationMillis && byteArrayStream.size() > 0) {
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

                        if (currentTime - lastSpeechTime > longSilenceDurationMillis) {
                            finalResult.append(currentPartialResult.toString().trim()).append(" ")
                            break
                        }
                    }
                }

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
                noiseSuppressor?.release()
            }
        }
    }

    private fun sendToMicrosoftSpeechRecognition(audioBytes: ByteArray): String {
        val client = OkHttpClient()
        val mediaType = "audio/wav".toMediaTypeOrNull()
        val tempFile = File.createTempFile("speech_chunk", ".wav", context.cacheDir)

        writeWavFile(audioBytes, tempFile)

        // Play the audio file to check its quality
        playAudio(tempFile)

        val requestBody = tempFile.asRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://westeurope.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=it-IT")
            .post(requestBody)
            .addHeader("Content-Type", "audio/wav")
            .addHeader("Ocp-Apim-Subscription-Key", "8f95505a0a7f49edaa75edcf6440dcbf")
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

    private fun playAudio(file: File) {
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Audio file does not exist or is empty: ${file.absolutePath}")
            return
        }

        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            Log.i(TAG, "Playing recorded audio...")

            mediaPlayer.setOnCompletionListener {
                it.release()
                Log.i(TAG, "Audio playback completed.")
            }

        } catch (e: IOException) {
            Log.e(TAG, "Could not play audio: ${e.message}", e)
            mediaPlayer.release()
        }
    }

    private fun extractTextFromResponse(response: String): String {
        return try {
            val jsonObject = JSONObject(response)
            jsonObject.optString("DisplayText", "")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response JSON: ${e.message}", e)
            ""
        }
    }

    private fun shortsToBytes(sData: ShortArray): ByteArray {
        val bytes = ByteArray(sData.size * 2)
        for (i in sData.indices) {
            bytes[i * 2] = (sData[i].toInt() and 0x00FF).toByte()
            bytes[i * 2 + 1] = (sData[i].toInt() shr 8).toByte()
        }
        return bytes
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

    fun stopRecording() {
        Log.d(TAG, "Stop recording requested.")
        isRecording = false
    }
}