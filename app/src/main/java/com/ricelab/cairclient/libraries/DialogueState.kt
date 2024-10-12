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
    var dialogueNuances: List<Any> = listOf(),
    var conversationHistory: MutableList<Map<String, String>> = mutableListOf(),
    var ongoingConversation: Boolean? = null
) {
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
            "dialogueNuances" to dialogueNuances,
            "conversationHistory" to conversationHistory,
            "ongoingConversation" to ongoingConversation
        )
    }
}