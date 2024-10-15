package com.ricelab.cairclient.libraries

import android.util.Log
import org.json.JSONObject

data class DialogueState(
    var dialogueSentence: String? = null,
    var prevDialogueSentence: List<Pair<String, String>> = listOf(),
    var addressedSpeaker: String? = null,
    var topic: String? = null,
    var prevTopic: String? = null,
    var sentenceType: String? = null,
    var pattern: List<String>? = null,
    var bool: Boolean? = null,
    var familiarities: Map<String, Any>? = null,
    var flags: Map<String, Any>? = null,
    var addressedCommunity: String? = null,
    //var dialogueNuances: List<Any> = listOf(),
    var dialogueNuances: DialogueNuances = DialogueNuances(),
    var conversationHistory: MutableList<Map<String, String>> = mutableListOf(),
    var ongoingConversation: Boolean? = null
) {
    constructor(dialogueState: Map<String, Any?>) : this(
        dialogueSentence = dialogueState["dialogue_sentence"] as? String,
        prevDialogueSentence = (dialogueState["prev_dialogue_sentence"] as? List<*>)?.filterIsInstance<Pair<String, String>>() ?: listOf(),
        addressedSpeaker = dialogueState["addressed_speaker"] as? String,
        topic = dialogueState["topic"] as? String,
        prevTopic = dialogueState["prev_topic"] as? String,
        sentenceType = dialogueState["sentence_type"] as? String,
        pattern = (dialogueState["pattern"] as? List<*>)?.filterIsInstance<String>() ?: listOf(),
        bool = dialogueState["bool"] as? Boolean,
        familiarities = (dialogueState["familiarities"] as? Map<*, *>)?.filterKeys { it is String }?.filterValues { it is Any }?.mapKeys { it.key as String }?.mapValues { it.value as Any } ?: mapOf(),
        flags = (dialogueState["flags"] as? Map<*, *>)?.filterKeys { it is String }?.filterValues { it is Any }?.mapKeys { it.key as String }?.mapValues { it.value as Any } ?: mapOf(),
        addressedCommunity = dialogueState["addressed_community"] as? String,
        //dialogueNuances = (dialogueState["dialogue_nuances"] as? List<*>)?.filterIsInstance<Any>() ?: listOf(),
        dialogueNuances = dialogueState["dialogue_nuances"]?.let {
            val nuancesMap = it as? Map<String, Map<String, List<*>>>
            val flags = nuancesMap?.get("flags")?.mapValues { entry -> entry.value.filterIsInstance<Int>() } ?: emptyMap()
            val values = nuancesMap?.get("values")?.mapValues { entry -> entry.value.filterIsInstance<String>() } ?: emptyMap()
            DialogueNuances(flags, values)
        } ?: DialogueNuances(), // Default to empty Nuances if missing
        conversationHistory = (dialogueState["conversation_history"] as? List<*>)?.filterIsInstance<Map<String, String>>()?.toMutableList() ?: mutableListOf(),
        ongoingConversation = dialogueState["ongoing_conversation"] as? Boolean
    ) {
        topic = when (val jsonTopic = dialogueState["topic"]) {
            is String -> jsonTopic
            is Number -> jsonTopic.toString() // Convert number to string
            else -> null // Handle other cases or set to null if the type is unexpected
        }
        prevTopic = when (val jsonTopic = dialogueState["prev_topic"]) {
            is String -> jsonTopic
            is Number -> jsonTopic.toString() // Convert number to string
            else -> null // Handle other cases or set to null if the type is unexpected
        }
        addressedCommunity = when (val jsonTopic = dialogueState["addressed_community"]) {
            is String -> jsonTopic
            is Number -> jsonTopic.toString() // Convert number to string
            else -> null // Handle other cases or set to null if the type is unexpected
        }

        Log.i("initializeUserSession", "Constructor: topic = $topic, jsonTopic = ${dialogueState["topic"]}")
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "dialogueSentence" to dialogueSentence,
            "prevDialogueSentence" to prevDialogueSentence,
            "addressedSpeaker" to addressedSpeaker,
            "topic" to topic,
            "prevTopic" to prevTopic,
            "sentenceType" to sentenceType,
            "pattern" to pattern,
            "bool" to bool,
            "familiarities" to familiarities,
            "flags" to flags,
            "addressedCommunity" to addressedCommunity,
            //"dialogueNuances" to dialogueNuances,
            "dialogue_nuances" to mapOf(
                "flags" to dialogueNuances.flags,
                "values" to dialogueNuances.values
            ),
            "conversationHistory" to conversationHistory,
            "ongoingConversation" to ongoingConversation
        )
    }

    // Function to update the dialogue state from a JSON object
    fun updateFromJson(json: JSONObject): DialogueState {
        this.dialogueSentence = json.optString("dialogueSentence", this.dialogueSentence)
        this.addressedSpeaker = json.optString("addressedSpeaker", this.addressedSpeaker)
        this.topic = json.optString("topic", this.topic)
        this.prevTopic = json.optString("prevTopic", this.prevTopic)
        this.sentenceType = json.optString("sentenceType", this.sentenceType)

        this.pattern = json.optJSONArray("pattern")?.let { jsonArray ->
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        }

        this.bool = json.optBoolean("bool", this.bool ?: false)

        this.familiarities = json.optJSONObject("familiarities")?.let { familiaritiesJson ->
            familiaritiesJson.keys().asSequence().associateWith { familiaritiesJson[it] as Any }
        }

        this.flags = json.optJSONObject("flags")?.let { flagsJson ->
            flagsJson.keys().asSequence().associateWith { flagsJson[it] as Any }
        }

        this.addressedCommunity = json.optString("addressedCommunity", this.addressedCommunity)

        // Assuming DialogueNuances has an updateFromJson method
        this.dialogueNuances.updateFromJson(json.optJSONObject("dialogueNuances") ?: JSONObject())

        this.conversationHistory = json.optJSONArray("conversationHistory")?.let { historyJson ->
            (0 until historyJson.length()).map { idx ->
                val historyItem = historyJson.getJSONObject(idx)
                historyItem.keys().asSequence().associateWith { historyItem.getString(it) }.toMutableMap()
            }.toMutableList()
        } ?: this.conversationHistory

        this.ongoingConversation = json.optBoolean("ongoingConversation", this.ongoingConversation ?: false)

        return this
    }

    override fun toString(): String {
        return """
            DialogueState(
                dialogueSentence=$dialogueSentence,
                prevDialogueSentence=${prevDialogueSentence.joinToString { "(${it.first}, ${it.second})" }},
                addressedSpeaker=$addressedSpeaker,
                topic=$topic,
                prevTopic=$prevTopic,
                sentenceType=$sentenceType,
                pattern=${pattern?.joinToString()},
                bool=$bool,
                familiarities=${familiarities?.entries?.joinToString { "${it.key}=${it.value}" }},
                flags=${flags?.entries?.joinToString { "${it.key}=${it.value}" }},
                addressedCommunity=$addressedCommunity,
                dialogueNuances=Nuances(
                flags=${dialogueNuances.flags.entries.joinToString { "${it.key}=${it.value}" }},
                values=${dialogueNuances.values.entries.joinToString { "${it.key}=${it.value}" }}
                ),
                conversationHistory=${conversationHistory.joinToString { it.toString() }},
                ongoingConversation=$ongoingConversation
            )
        """.trimIndent()

        //dialogueNuances=${dialogueNuances.joinToString()},
    }
}