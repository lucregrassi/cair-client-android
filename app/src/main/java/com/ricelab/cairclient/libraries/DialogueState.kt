package com.ricelab.cairclient.libraries

data class DialogueState(
    var dialogueSentence: String? = null,
    var prevDialogueSentence: List<Pair<String, String>> = listOf(),
    var addressedSpeaker: String? = null,
    var topic: String? = null,
    var prevTopic: String? = null,
    var sentenceType: String? = null,
    var pattern: String? = null,
    var bool: Boolean? = null,
    var familiarities: Any? = null,
    var flags: Any? = null,
    var addressedCommunity: String? = null,
    var dialogueNuances: Any? = null,
    var conversationHistory: MutableList<Map<String, String>> = mutableListOf(),
    var ongoingConversation: Any? = null
) {
    constructor(d: DialogueState?) : this() {
        if (d != null) {
            dialogueSentence = d.dialogueSentence
            prevDialogueSentence = d.prevDialogueSentence
            addressedSpeaker = d.addressedSpeaker
            topic = d.topic
            prevTopic = d.prevTopic
            sentenceType = d.sentenceType
            pattern = d.pattern
            bool = d.bool
            familiarities = d.familiarities
            flags = d.flags
            addressedCommunity = d.addressedCommunity
            dialogueNuances = d.dialogueNuances
            conversationHistory = d.conversationHistory
            ongoingConversation = d.ongoingConversation
        }
    }

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