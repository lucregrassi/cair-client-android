package com.ricelab.cairclient.libraries

import android.util.Log
import org.json.JSONObject

private const val TAG = "DialogueState"

data class DialogueState(
    var dialogueSentence: List<List<String>> = listOf(),
    var prevDialogueSentence: List<List<String>> = listOf(),
    var addressedSpeaker: String? = null,
    var topic: Int? = null,
    var prevTopic: Int? = null,
    var sentenceType: String? = null,
    var pattern: List<String>? = null,
    var bool: Boolean? = null,
    var familiarities: Map<String, Any>? = null,
    var flags: Map<String, Any>? = null,
    var addressedCommunity: String? = null,
    var dialogueNuances: DialogueNuances = DialogueNuances(),
    var conversationHistory: MutableList<Map<String, String>> = mutableListOf(),
    var ongoingConversation: Boolean? = null
) {
    // Constructor using snake_case from server-side
    constructor(dialogueState: Map<String, Any?>) : this(
        dialogueSentence = parseDialogueSentence(dialogueState["dialogue_sentence"]),
        prevDialogueSentence = parseDialogueSentence(dialogueState["prev_dialogue_sentence"]),
        addressedSpeaker = dialogueState["addressed_speaker"] as? String,
        topic = (dialogueState["topic"] as? Number)?.toInt(),  // Adjusted
        prevTopic = (dialogueState["prev_topic"] as? Number)?.toInt(),
        sentenceType = dialogueState["sentence_type"] as? String,
        pattern = (dialogueState["pattern"] as? List<*>)?.filterIsInstance<String>() ?: listOf(),
        bool = dialogueState["bool"] as? Boolean,
        familiarities = (dialogueState["familiarities"] as? Map<*, *>)?.filterKeys { it is String }?.filterValues { it is Any }?.mapKeys { it.key as String }?.mapValues { it.value as Any } ?: mapOf(),
        flags = (dialogueState["flags"] as? Map<*, *>)?.filterKeys { it is String }?.filterValues { it is Any }?.mapKeys { it.key as String }?.mapValues { it.value as Any } ?: mapOf(),
        addressedCommunity = dialogueState["addressed_community"] as? String,
        dialogueNuances = dialogueState["dialogue_nuances"]?.let { nuances ->
            val nuancesMap = nuances as? Map<String, Any?>
            val flags = (nuancesMap?.get("flags") as? Map<String, List<Int>>) ?: emptyMap()
            val values = (nuancesMap?.get("values") as? Map<String, List<String>>) ?: emptyMap()
            DialogueNuances(flags, values)
        } ?: DialogueNuances(),
        conversationHistory = (dialogueState["conversation_history"] as? List<*>)?.filterIsInstance<Map<String, String>>()?.toMutableList() ?: mutableListOf(),
        ongoingConversation = dialogueState["ongoing_conversation"] as? Boolean
    )
    companion object {
        fun parseDialogueSentence(data: Any?): List<List<String>> {
            return (data as? List<*>)?.mapNotNull {
                when (it) {
                    is List<*> -> {
                        it.filterIsInstance<String>()
                    }
                    is Map<*, *> -> {
                        val first = it["first"]?.toString()
                        val second = it["second"]?.toString()
                        if (first != null && second != null) listOf(first, second) else null
                    }
                    else -> null
                }
            } ?: listOf()
        }
    }

    // Serializing camelCase fields to snake_case for server-side
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "dialogue_sentence" to dialogueSentence,
            "prev_dialogue_sentence" to prevDialogueSentence,
            "addressed_speaker" to addressedSpeaker,
            "topic" to topic,
            "prev_topic" to prevTopic,
            "sentence_type" to sentenceType,
            "pattern" to pattern,
            "bool" to bool,
            "familiarities" to familiarities,
            "flags" to flags,
            "addressed_community" to addressedCommunity,
            "dialogue_nuances" to mapOf(
                "flags" to dialogueNuances.flags,
                "values" to dialogueNuances.values
            ),
            "conversation_history" to conversationHistory,
            "ongoing_conversation" to ongoingConversation
        )
    }

    // Update dialogue state from JSON (handles snake_case from the server)
    fun updateFromJson(json: JSONObject): DialogueState {
        Log.d(TAG, "Received JSON to update DialogueState: $json")

        // Copy current dialogueSentence into prevDialogueSentence before updating
        this.prevDialogueSentence = this.dialogueSentence

        // Update dialogueSentence
        this.dialogueSentence = json.optJSONArray("dialogue_sentence")?.let { jsonArray ->
            (0 until jsonArray.length()).mapNotNull { idx ->
                val innerArray = jsonArray.optJSONArray(idx)
                if (innerArray != null) {
                    (0 until innerArray.length()).map { innerArray.optString(it, "") }
                } else {
                    null
                }
            }
        } ?: this.dialogueSentence

        // Update addressedSpeaker
        this.addressedSpeaker = this.addressedSpeaker?.let {
            json.optString("addressed_speaker",
                it
            )
        }

        // Update topic
        this.topic = if (json.has("topic")) {
            json.optInt("topic", this.topic ?: 0)
        } else {
            this.topic
        }

        // Update prevTopic
        this.prevTopic = if (json.has("prev_topic")) {
            json.optInt("prev_topic", this.prevTopic ?: 0)
        } else {
            this.prevTopic
        }

        // Update sentenceType
        this.sentenceType = this.sentenceType?.let { json.optString("sentence_type", it) }

        // Update pattern
        this.pattern = json.optJSONArray("pattern")?.let { jsonArray ->
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } ?: this.pattern

        // Update bool
        this.bool = if (json.has("bool")) json.optBoolean("bool") else this.bool

        // Update familiarities
        this.familiarities = json.optJSONObject("familiarities")?.let { familiaritiesJson ->
            familiaritiesJson.keys().asSequence().associateWith { familiaritiesJson[it] as Any }
        } ?: this.familiarities

        // Update flags
        this.flags = json.optJSONObject("flags")?.let { flagsJson ->
            flagsJson.keys().asSequence().associateWith { flagsJson[it] as Any }
        } ?: this.flags

        // Update addressedCommunity
        this.addressedCommunity =
            this.addressedCommunity?.let { json.optString("addressed_community", it) }

        // Update dialogueNuances (assuming it has its own updateFromJson method)
        json.optJSONObject("dialogue_nuances")?.let {
            this.dialogueNuances.updateFromJson(it)
        }

        // Update ongoingConversation
        this.ongoingConversation = if (json.has("ongoing_conversation")) json.optBoolean("ongoing_conversation") else this.ongoingConversation

        return this
    }
}