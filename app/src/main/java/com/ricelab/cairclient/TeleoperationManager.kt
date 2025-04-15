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
        // In handleCommand method
        when (command) {
            "MOVE_FORWARD" -> pepperInterface.moveRobot(10.0, 0.0, 0.0)
            "MOVE_BACKWARD" -> pepperInterface.moveRobot(-10.0, 0.0, 3.14)
            "ROTATE_LEFT" -> pepperInterface.moveRobot(0.0, 0.0, 1.57)
            "ROTATE_RIGHT" -> pepperInterface.moveRobot(0.0, 0.0, -1.57)
            "STOP" -> pepperInterface.stopRobot()
            "VOLUME_UP" -> changeVolume(AudioManager.ADJUST_RAISE)
            "VOLUME_DOWN" -> changeVolume(AudioManager.ADJUST_LOWER)
            "HUG" -> CoroutineScope(Dispatchers.Main).launch { pepperInterface.performAnimation("hug") }
            "GREET" -> CoroutineScope(Dispatchers.Main).launch { pepperInterface.performAnimation("hello") }
            "HANDSHAKE" -> CoroutineScope(Dispatchers.Main).launch {
                pepperInterface.performAnimation(
                    "handshake"
                )
            }
            else -> {
                // For sayMessage, since it's a suspend function
                CoroutineScope(Dispatchers.Main).launch { pepperInterface.sayMessage(command, "it-IT") }
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