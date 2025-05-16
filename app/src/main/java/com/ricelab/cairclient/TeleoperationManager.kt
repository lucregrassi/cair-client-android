package com.ricelab.cairclient

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.aldebaran.qi.sdk.QiContext
import com.ricelab.cairclient.libraries.PepperInterface
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket

class TeleoperationManager(
    private val context: Context,
    private var qiContext: QiContext?,
    private val pepperInterface: PepperInterface
) {
    private val commandPort = 54321 // The port to listen for commands
    private var job: Job? = null // Job for the UDP listener coroutine
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var socket: DatagramSocket? = null

    private val TAG = "TeleoperationManager"

    fun setQiContext(qiContext: QiContext?) {
        this.qiContext = qiContext
    }

    fun startUdpListener() {
        // Cancel any existing job before starting a new one
        job?.cancel()
        socket = DatagramSocket(commandPort)
        job = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            try {
                while (isActive) { // Check if the coroutine is still active
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    val command = String(packet.data, 0, packet.length).trim()
                    handleCommand(command)
                }
            } catch (e: Exception) {
                if (e is java.net.SocketException && e.message == "Socket closed") {
                    Log.i(TAG, "UDP listener closed cleanly.")
                } else {
                    Log.e(TAG, "UDP listener error: ${e.message}", e)
                }
            } finally {
                socket?.close()
            }
        }
    }

    private fun handleCommand(command: String) {
        Log.d(TAG, "Received command: $command")

        when (command) {
            "MOVE_FORWARD" -> {
                Log.i(TAG, "Executing MOVE_FORWARD")
                pepperInterface.moveRobot(10.0, 0.0, 0.0)
            }
            "MOVE_BACKWARD" -> {
                Log.i(TAG, "Executing MOVE_BACKWARD")
                pepperInterface.moveRobot(-10.0, 0.0, 3.14)
            }
            "ROTATE_LEFT" -> {
                Log.i(TAG, "Executing ROTATE_LEFT")
                pepperInterface.moveRobot(0.0, 0.0, 1.57)
            }
            "ROTATE_RIGHT" -> {
                Log.i(TAG, "Executing ROTATE_RIGHT")
                pepperInterface.moveRobot(0.0, 0.0, -1.57)
            }
            "STOP" -> {
                Log.i(TAG, "Executing STOP")
                pepperInterface.stopRobot()
            }
            "VOLUME_UP" -> {
                Log.i(TAG, "Executing VOLUME_UP")
                changeVolume(AudioManager.ADJUST_RAISE)
            }
            "VOLUME_DOWN" -> {
                Log.i(TAG, "Executing VOLUME_DOWN")
                changeVolume(AudioManager.ADJUST_LOWER)
            }
            "HUG" -> {
                Log.i(TAG, "Executing HUG animation")
                CoroutineScope(Dispatchers.Main).launch { pepperInterface.performAnimation("hug") }
            }
            "GREET" -> {
                Log.i(TAG, "Executing GREET animation")
                CoroutineScope(Dispatchers.Main).launch { pepperInterface.performAnimation("hello") }
            }
            "HANDSHAKE" -> {
                Log.i(TAG, "Executing HANDSHAKE animation")
                CoroutineScope(Dispatchers.Main).launch {
                    pepperInterface.performAnimation("handshake")
                }
            }
            else -> {
                Log.i(TAG, "Received unknown command, treating as sentence: $command")
                CoroutineScope(Dispatchers.Main).launch {
                    pepperInterface.sayMessage(command, "it-IT")
                }
            }
        }
    }

    private fun changeVolume(direction: Int) {
        // Adjust the volume for the STREAM_MUSIC (used for media playback)
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
    }

    fun stopUdpListener() {
        job?.cancel()
        socket?.close()
        job = null
    }
}