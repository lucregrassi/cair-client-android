package com.ricelab.cairclient

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.*


@Serializable
data class ScheduledIntervention(
    var type: String,
    var timestamp: Double,
    var period: Long,
    var offset: Long,
    var topics: List<Topic>?,
    var actions: List<String>?,
    var counter: Int = 0
)

@Serializable
data class Topic(
    var name: String,
    var exclusive: Boolean
)

@Serializable
data class DueIntervention(
    var type: String?,
    var exclusive: Boolean,
    var sentence: String,
    var timestamp: Double = 0.0
)

@Serializable
data class ScheduledInterventionsRequest(
    val scheduled_interventions: List<ScheduledIntervention>?
)

class PersonalizationServer(private val port: Int = 8000) {

    private val scheduledInterventions = CopyOnWriteArrayList<ScheduledIntervention>()
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    fun startServer() {
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            serverSocket = ServerSocket(port)
            Log.i("PersonalizationServer", "Server started on port $port")

            while (isActive) {
                try {
                    val clientSocket = serverSocket?.accept()
                    clientSocket?.let { socket ->
                        handleClient(socket)
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e("PersonalizationServer", "Error accepting client: ${e.message}")
                    }
                }
            }
            Log.i("PersonalizationServer", "Server stopped")
        }
    }

    private fun handleClient(socket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            socket.use { client ->
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)
                val data = reader.readLine()
                if (data != null) {
                    try {
                        val request = kotlinx.serialization.json.Json.decodeFromString(
                            ScheduledInterventionsRequest.serializer(), data
                        )
                        if (request.scheduled_interventions != null) {
                            scheduledInterventions.clear()
                            scheduledInterventions.addAll(request.scheduled_interventions)
                            writer.println("{\"message\":\"Data received successfully\"}")
                            Log.i("PersonalizationServer", "Scheduled interventions updated")
                        } else {
                            writer.println("{\"error\":\"Invalid data format\"}")
                        }
                    } catch (e: Exception) {
                        writer.println("{\"error\":\"Invalid JSON\"}")
                        Log.e("PersonalizationServer", "Error parsing JSON: ${e.message}")
                    }
                }
            }
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        serverSocket?.close()
        Log.i("PersonalizationServer", "Server stopped")
    }

    fun getDueIntervention(): DueIntervention? {
        val currentTime = System.currentTimeMillis() / 1000 // Current time in seconds
        Log.i("PersonalizationServer", "Current time: $currentTime")

        val fixedInterventions = scheduledInterventions.filter {
            it.type == "fixed" && it.timestamp <= currentTime
        }.sortedBy { it.timestamp }

        val periodicInterventions = scheduledInterventions.filter {
            it.type == "periodic" && it.timestamp <= currentTime
        }.sortedBy { it.timestamp }

        val dueIntervention = when {
            fixedInterventions.isNotEmpty() -> fixedInterventions.first()
            periodicInterventions.isNotEmpty() -> periodicInterventions.first()
            else -> null
        } ?: return null

        val counter = dueIntervention.counter
        val result = when {
            !dueIntervention.topics.isNullOrEmpty() -> {
                val topic = dueIntervention.topics!![counter % dueIntervention.topics!!.size]
                DueIntervention(
                    type = "topic",
                    sentence = "Parliamo di ${topic.name}",
                    exclusive = topic.exclusive,
                    timestamp = dueIntervention.timestamp
                )
            }
            !dueIntervention.actions.isNullOrEmpty() -> {
                val action = dueIntervention.actions!![counter % dueIntervention.actions!!.size]
                DueIntervention(
                    type = "action",
                    sentence = action,
                    exclusive = false,
                    timestamp = dueIntervention.timestamp
                )
            }
            else -> null
        } ?: return null

        // Update the counter and timestamp for periodic interventions
        dueIntervention.counter = counter + 1
        if (dueIntervention.type == "periodic") {
            dueIntervention.timestamp += dueIntervention.period
        } else {
            // For fixed interventions, remove them after execution
            scheduledInterventions.remove(dueIntervention)
        }

        Log.d("PersonalizationServer", "Result = $result")

        return result
    }
}