package com.ricelab.cairclient.libraries

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
    )

    fun toDict(): Map<String, Any?> {
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