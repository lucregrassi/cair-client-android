package com.ricelab.cairclient

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "InterventionManager"

/**
 * Manages scheduled interventions with persistent storage and due-time retrieval.
 */
class InterventionManager private constructor(context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("interventions", Context.MODE_PRIVATE)
    private val interventions = CopyOnWriteArrayList<ScheduledIntervention>()

    fun loadFromPrefs() {
        val json = prefs.getString("scheduled_interventions", null) ?: return
        val type = object : TypeToken<List<ScheduledIntervention>>() {}.type
        val now = System.currentTimeMillis() / 1000.0
        val loaded = gson.fromJson<List<ScheduledIntervention>>(json, type)
        val adjusted = loaded.mapNotNull { intervention ->
            when (intervention.typeEnum) {
                InterventionType.PERIODIC, InterventionType.FIXED -> {
                    val period = intervention.period
                    if (period > 0) {
                        val newTimestamp = if (intervention.timestamp <= now) {
                            Log.d(TAG, "adjusting intervention timestamp by  ${(((now - intervention.timestamp) / period).toInt() + 1)} periods")
                            intervention.timestamp + (((now - intervention.timestamp) / period).toInt() + 1) * period
                        } else {
                            intervention.timestamp
                        }
                        intervention.copy(timestamp = newTimestamp, counter = 0)
                    } else {
                        null // discard if period is missing or invalid
                    }
                }
                else -> if (intervention.timestamp > now) intervention else null
            }
        }
        setScheduledInterventions(adjusted)
    }

    fun saveToPrefs() {
        prefs.edit {
            putString("scheduled_interventions", gson.toJson(getAllScheduledInterventions()))
        }
    }

    fun clearAll() {
        Log.w(TAG, "Clearing ALL interventions")
        interventions.clear()
    }

    fun setScheduledInterventions(list: List<ScheduledIntervention>) {
        interventions.clear()
        interventions.addAll(list)
        Log.i(TAG, "Scheduled interventions: $interventions")
    }

    fun getAllScheduledInterventions(): List<ScheduledIntervention> = interventions.toList()

    fun getDueIntervention(): DueIntervention? {
        Log.w(TAG, "Object identity: ${Integer.toHexString(System.identityHashCode(this))}")
        Log.d(TAG, "ENTERING getDueIntervention interventions.size = ${interventions.size}")
        val currentTime = System.currentTimeMillis() / 1000.0
        val sorted = interventions.filter { it.timestamp <= currentTime }.sortedBy { it.timestamp }
        Log.w(TAG, "sorted size = ${sorted.size}")
        val next = sorted.firstOrNull() ?: return null

        val result = when {
            !next.topics.isNullOrEmpty() -> {
                val topic = next.topics!![next.counter % next.topics!!.size]
                Log.d(TAG, "Returning topic sentence = ${topic.sentence} counter = ${next.counter}")
                DueIntervention("topic", topic.exclusive, topic.sentence, next.timestamp, next.contextualData, next.counter)
            }
            !next.actions.isNullOrEmpty() -> {
                val action = next.actions!![next.counter % next.actions!!.size]
                Log.d(TAG, "Returning action action = $action counter = ${next.counter}")
                DueIntervention("action", false, action, next.timestamp, next.contextualData, next.counter)
            }
            !next.interactionSequence.isNullOrEmpty() -> {
                val sentence = next.interactionSequence!![next.counter % next.interactionSequence!!.size]
                Log.d(TAG, "Returning interaction_sequence sentence = $sentence counter = ${next.counter} size = ${next.interactionSequence!!.size}")
                DueIntervention("interaction_sequence", false, sentence, next.timestamp, next.contextualData, next.counter)
            }
            else -> {
                Log.w(TAG, "No valid interventions found")
                null
            }
        } ?: return null

        next.counter++
        Log.d(TAG, "dueIntervention counter = ${next.counter}")

        val isEndOfSequence = !next.interactionSequence.isNullOrEmpty() && next.counter == next.interactionSequence!!.size
        Log.d(TAG, "isEndOfSequence = $isEndOfSequence")

        if (next.topics.isNullOrEmpty() && next.actions.isNullOrEmpty()) {
            // interaction_sequence
            if (isEndOfSequence) {
                next.counter = 0
                if (next.typeEnum == InterventionType.PERIODIC || next.typeEnum == InterventionType.FIXED) {
                    Log.d(TAG, "incrementing sequence period: next.typeEnum = ${next.typeEnum} ")
                    next.timestamp += next.period
                } else {
                    Log.d(TAG, "removing sequence intervention (immediate)")
                    interventions.remove(next)
                }
            }
        } else if (next.typeEnum == InterventionType.PERIODIC || next.typeEnum == InterventionType.FIXED) {
            // action or topic
            Log.d(TAG, "incrementing action/topic period: next.typeEnum = ${next.typeEnum} ")
            next.timestamp += next.period
        } else {
            Log.d(TAG, "removing action/topic intervention (immediate)")
            interventions.remove(next)
        }

        return result
    }

    companion object {
        @Volatile
        private var INSTANCE: InterventionManager? = null

        fun getInstance(context: Context): InterventionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InterventionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

enum class InterventionType {
    IMMEDIATE, FIXED, PERIODIC;

    companion object {
        fun from(type: String): InterventionType? = when (type.lowercase()) {
            "immediate", "immediato" -> IMMEDIATE
            "fixed", "fisso" -> FIXED
            "periodic", "periodico" -> PERIODIC
            else -> null
        }
    }
}

// --- Data Classes ---
data class ScheduledIntervention(
    var type: String,
    var timestamp: Double,
    var period: Long,
    var offset: Long,
    var topics: List<Topic>? = null,
    var actions: List<String>? = null,
    var counter: Int = 0,
    @SerializedName("interaction_sequence")
    var interactionSequence: List<String>? = null,
    @SerializedName("contextual_data")
    var contextualData: Map<String, String>? = null
) {
    val typeEnum: InterventionType?
        get() = InterventionType.from(type)
}

data class Topic(
    var sentence: String,
    var exclusive: Boolean
)

data class DueIntervention(
    var type: String?,
    var exclusive: Boolean,
    var sentence: String,
    var timestamp: Double = 0.0,
    @SerializedName("contextual_data")
    var contextualData: Map<String, String>? = null,
    var counter: Int = 0
)