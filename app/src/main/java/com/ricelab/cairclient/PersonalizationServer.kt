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
    var counter: Int = 0,
    var interaction_sequence: List<String>?,
    var contextual_data: Map<String, String>?) {

    fun logVariables() {
        Log.i("ScheduledIntervention", this.toString())
    }
}

@Serializable
data class Topic(
    var sentence: String,
    var exclusive: Boolean
)

@Serializable
data class DueIntervention(
    var type: String?,
    var exclusive: Boolean,
    var sentence: String,
    var timestamp: Double = 0.0,
    var contextual_data: Map<String, String>? = null,
    var counter: Int = 0
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

                            if (scheduledInterventions.isEmpty()) {
                                Log.i("PersonalizationServer", "No scheduled interventions available.")
                            } else {
                                scheduledInterventions.forEachIndexed { index, intervention ->
                                    Log.i("PersonalizationServer", "Intervention #$index:")
                                    intervention.logVariables()
                                }
                            }
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
        //Log.i("PersonalizationServer", "Current time: $currentTime")

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

        val result = when {
            !dueIntervention.topics.isNullOrEmpty() -> {
                val topic = dueIntervention.topics!![dueIntervention.counter % dueIntervention.topics!!.size]
                DueIntervention(
                    type = "topic",
                    sentence = topic.sentence,
                    exclusive = topic.exclusive,
                    timestamp = dueIntervention.timestamp,
                    counter = dueIntervention.counter
                )
            }
            !dueIntervention.actions.isNullOrEmpty() -> {
                val action = dueIntervention.actions!![dueIntervention.counter % dueIntervention.actions!!.size]
                DueIntervention(
                    type = "action",
                    sentence = action,
                    exclusive = false,
                    timestamp = dueIntervention.timestamp,
                    counter = dueIntervention.counter
                )
            }
            !dueIntervention.interaction_sequence.isNullOrEmpty() -> {
                val sentence = dueIntervention.interaction_sequence!![dueIntervention.counter % dueIntervention.interaction_sequence!!.size]
                DueIntervention(
                    type = "interaction_sequence",
                    sentence = sentence,
                    exclusive = false,
                    timestamp = dueIntervention.timestamp,
                    contextual_data = dueIntervention.contextual_data,
                    counter = dueIntervention.counter
                )
            }
            else -> null
        } ?: return null

        // Update the counter and timestamp for periodic interventions
        dueIntervention.counter = dueIntervention.counter + 1
        Log.d("PersonalizationServer", "dueIntervention counter = ${dueIntervention.counter}")
        if (!dueIntervention.interaction_sequence.isNullOrEmpty()) {
            if (dueIntervention.counter == dueIntervention.interaction_sequence!!.size) {
                // reset counter to start the sequence from the beginning the next time (if periodic)
                Log.d("PersonalizationServer", "resetting due intervention counter")
                dueIntervention.counter = 0
                if (dueIntervention.type == "periodic") {
                    Log.d("PersonalizationServer", "Updating the period")
                    dueIntervention.timestamp += dueIntervention.period
                } else {
                    Log.d("PersonalizationServer", "Removing the intervention")
                    // For fixed interventions, remove them after execution
                    scheduledInterventions.remove(dueIntervention)
                }
            }
        } else {
            if (dueIntervention.type == "periodic") {
                dueIntervention.timestamp += dueIntervention.period
            } else {
                // For fixed interventions, remove them after execution
                scheduledInterventions.remove(dueIntervention)
            }
        }

        Log.d("PersonalizationServer", "Result = $result")

        return result
    }
}