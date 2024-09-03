package com.ricelab.cairclient

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MainActivity"

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    private var qiContext: QiContext? = null
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var textView: TextView

    // Register for the audio permission request result
    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening() // Start listening if permission is granted
        } else {
            Log.e(TAG, "Permission to record audio was denied.")
        }
    }

    /**
     * Called when the activity is first created. Initializes the UI and checks for audio recording permission.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)
        audioRecorder = AudioRecorder(this)

        checkAndRequestAudioPermission() // Check and request audio permission if needed
    }

    /**
     * Checks if the audio recording permission is granted. If not, it requests the permission.
     */
    private fun checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted; listening will start when robot focus is gained
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO) // Request permission if not granted
        }
    }

    /**
     * Starts listening for audio input using the AudioRecorder.
     */
    private fun startListening() {
        Log.i(TAG, "Begin startListening.")
        lifecycleScope.launch {
            val result = audioRecorder.listenAndSplit() // Start listening and get the result
            handleListeningResult(result)
        }
    }

    /**
     * Handles the result from the AudioRecorder, updating the UI and restarting listening.
     * @param result The transcription result from the audio recording.
     */
    private fun handleListeningResult(result: String) {
        textView.text = "Result: $result" // Update the UI with the result
        startListening() // Restart listening after handling the result
    }

    /**
     * Makes the robot say something and then restarts listening.
     * @param text The text that the robot will say.
     */
    private fun saySomething(text: String) {
        qiContext?.let { context ->
            lifecycleScope.launch(Dispatchers.IO) {
                val say = com.aldebaran.qi.sdk.builder.SayBuilder.with(context)
                    .withText(text)
                    .build()
                say.run() // Runs the speech on the IO dispatcher
                withContext(Dispatchers.Main) {
                    startListening() // Back to the Main thread to restart listening
                }
            }
        }
    }

    /**
     * Cleans up resources when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        QiSDK.unregister(this, this)
        audioRecorder.stopRecording() // Stop any ongoing recording
    }

    /**
     * Called when the robot gains focus, allowing the application to interact with the robot's context.
     * @param qiContext The QiContext provided by the SDK.
     */
    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
        startListening() // Start listening when the robot gains focus
    }

    /**
     * Called when the robot loses focus, typically when another application gains control of the robot.
     */
    override fun onRobotFocusLost() {
        this.qiContext = null
        Log.i(TAG, "Robot focus lost, stopping or pausing operations if necessary.")
    }

    /**
     * Called when the robot's focus is refused, usually due to a critical issue or configuration problem.
     * @param reason A string explaining why the focus was refused.
     */
    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }
}