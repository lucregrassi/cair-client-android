package com.ricelab.cairclient.libraries

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.aldebaran.qi.sdk.QiContext
import kotlinx.coroutines.*
import java.net.*

class TeleoperationManager(
    private val context: Context,
    private var qiContext: QiContext?,
    private val pepperInterface: PepperInterface,
    private val port: Int = 54321
) {
    companion object { private const val TAG = "TeleoperationManager" }

    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var job: Job? = null

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun setQiContext(qiContext: QiContext?) {
        this.qiContext = qiContext
    }

    fun isRunning(): Boolean = (socket != null) && (job?.isActive == true)

    /**
     * Start the UDP listener if not already running.
     * Returns true if started, false if skipped/failure.
     */
    @Synchronized
    fun startUdpListener(scope: CoroutineScope = GlobalScope): Boolean {
        if (isRunning()) {
            Log.d(TAG, "UDP listener already running. Skip start.")
            return false
        }

        // Create and bind the socket (outside the coroutine) so bind errors are caught here
        val s = try {
            DatagramSocket(null).apply {
                // Best-effort: on some Androids reuseAddress may be ignored, but it doesn't hurt.
                try { reuseAddress = true } catch (_: Throwable) {}
                bind(InetSocketAddress(port))
            }
        } catch (e: BindException) {
            Log.e(TAG, "Port $port already in use. Not starting another listener.", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind UDP socket on port $port", e)
            return false
        }

        socket = s
        job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            try {
                Log.i(TAG, "UDP listener started on port $port")
                while (isActive && !s.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    s.receive(packet)
                    val command = String(packet.data, 0, packet.length).trim()
                    handleCommand(command)
                }
            } catch (e: SocketException) {
                // "Socket closed" is expected on stop
                if (!e.message.orEmpty().contains("closed", true)) {
                    Log.e(TAG, "UDP listener socket error", e)
                } else {
                    Log.i(TAG, "UDP listener closed cleanly.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP listener error", e)
            } finally {
                try { s.close() } catch (_: Exception) {}
                socket = null
                Log.i(TAG, "UDP listener stopped.")
            }
        }
        return true
    }

    @Synchronized
    fun stopUdpListener() {
        // Idempotent stop
        job?.cancel()
        job = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
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
            "VOLUME_UP" -> changeVolume(AudioManager.ADJUST_RAISE)
            "VOLUME_DOWN" -> changeVolume(AudioManager.ADJUST_LOWER)
            "HUG" -> CoroutineScope(Dispatchers.Main).launch { pepperInterface.performAnimation("hug") }
            "GREET" -> CoroutineScope(Dispatchers.Main).launch { pepperInterface.performAnimation("hello") }
            "HANDSHAKE" -> CoroutineScope(Dispatchers.Main).launch { pepperInterface.performAnimation("handshake") }
            else -> {
                Log.i(TAG, "Unknown command → say it: $command")
                CoroutineScope(Dispatchers.Main).launch {
                    pepperInterface.sayMessage(command, "it-IT")
                }
            }
        }
    }

    private fun changeVolume(direction: Int) {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
    }
}