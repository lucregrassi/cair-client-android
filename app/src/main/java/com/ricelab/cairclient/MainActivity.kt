package com.ricelab.cairclient

import AudioRecorder
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

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            Log.e(TAG, "Permission to record audio was denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_main)
        textView = findViewById(R.id.textView)
        audioRecorder = AudioRecorder(this)
        checkAndRequestAudioPermission()
    }

    private fun checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Permesso già concesso, attendi il focus del robot per iniziare ad ascoltare
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        Log.i(TAG, "Begin startListening.")
        lifecycleScope.launch {
            val result = audioRecorder.listenAndSplit()
            handleListeningResult(result)
        }
    }

    private fun handleListeningResult(result: String) {
        // Update the UI with the result
        textView.text = "Result: $result"

        startListening()
    }

    private fun saySomething(text: String) {
        qiContext?.let { context ->
            lifecycleScope.launch(Dispatchers.IO) {
                val say = com.aldebaran.qi.sdk.builder.SayBuilder.with(context)
                    .withText(text)
                    .build()
                say.run() // This will run on the IO dispatcher
                withContext(Dispatchers.Main) {
                    startListening() // Back to the Main thread to start listening again
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        QiSDK.unregister(this, this)
        audioRecorder.stopRecording()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
        startListening()
    }

    override fun onRobotFocusLost() {
        this.qiContext = null
        Log.i(TAG, "Robot focus lost, stopping or pausing operations if necessary.")
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }
}