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
        val valid = loaded.filter {
            it.typeEnum == InterventionType.PERIODIC || it.timestamp > now
        }
        setScheduledInterventions(valid)
    }

    fun saveToPrefs() {
        prefs.edit {
            putString("scheduled_interventions", gson.toJson(getAllScheduledInterventions()))
        }
    }

    fun clearAll() {
        interventions.clear()
    }

    fun setScheduledInterventions(list: List<ScheduledIntervention>) {
        interventions.clear()
        interventions.addAll(list)
        Log.i(TAG, "Scheduled interventions: $interventions")
    }

    fun getAllScheduledInterventions(): List<ScheduledIntervention> = interventions.toList()

    fun getDueIntervention(): DueIntervention? {
        val currentTime = System.currentTimeMillis() / 1000.0
        val sorted = interventions.filter { it.timestamp <= currentTime }.sortedBy { it.timestamp }
        val next = sorted.firstOrNull() ?: return null

        val result = when {
            !next.topics.isNullOrEmpty() -> {
                val topic = next.topics!![next.counter % next.topics!!.size]
                DueIntervention("topic", topic.exclusive, topic.sentence, next.timestamp, next.contextualData, next.counter)
            }
            !next.actions.isNullOrEmpty() -> {
                val action = next.actions!![next.counter % next.actions!!.size]
                DueIntervention("action", false, action, next.timestamp, next.contextualData, next.counter)
            }
            !next.interactionSequence.isNullOrEmpty() -> {
                val sentence = next.interactionSequence!![next.counter % next.interactionSequence!!.size]
                DueIntervention("interaction_sequence", false, sentence, next.timestamp, next.contextualData, next.counter)
            }
            else -> null
        } ?: return null

        next.counter++
        Log.d(TAG, "dueIntervention counter = ${next.counter}")

        val isEndOfSequence = !next.interactionSequence.isNullOrEmpty() && next.counter == next.interactionSequence!!.size

        if (isEndOfSequence || next.topics.isNullOrEmpty() && next.actions.isNullOrEmpty()) {
            next.counter = 0
            if (next.typeEnum == InterventionType.PERIODIC || next.typeEnum == InterventionType.FIXED) {
                next.timestamp += next.period
            } else {
                interventions.remove(next)
            }
        } else if (next.typeEnum == InterventionType.PERIODIC || next.typeEnum == InterventionType.FIXED) {
            next.timestamp += next.period
        } else {
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