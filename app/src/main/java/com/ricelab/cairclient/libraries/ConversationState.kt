package com.ricelab.cairclient.libraries

import android.util.Log

private const val TAG = "ConversationState"

class ConversationState(
    private var fileStorageManager: FileStorageManager,
    private var previousSentence: String
) {
    var dialogueState: DialogueState = DialogueState()
    var speakersInfo = SpeakersInfo()
    var dialogueStatistics = DialogueStatistics()
    var planSentence: String? = null
    var plan: String? = null
    var lastActiveSpeakerTime: Long = 0
    var prevTurnLastSpeaker: String = ""
    var prevSpeakerTopic: Int? = null

    // Function to load conversation state
    fun loadFromFile() {
        // Step 1: Retrieve the state of the conversation
        Log.i(TAG, "Loading conversation state elements")
        dialogueState = fileStorageManager.readFromFile(DialogueState::class.java)!!

        Log.i(TAG, "dialogueState = $dialogueState")

        // Step 2: If it's the first time, initialize dialogueNuances
        if (dialogueState.dialogueNuances.flags.isEmpty() && dialogueState.dialogueNuances.values.isEmpty()) {
            Log.i(TAG, "dialogueState.dialogueNuances is empty, loading from file")
            val dialogueNuances = fileStorageManager.readFromFile(DialogueNuances::class.java)!!
            dialogueState.dialogueNuances = dialogueNuances
            Log.i(TAG, "dialogueState.dialogueNuances initialized")
        }

        // Step 3: Store the welcome or welcome back message in the conversation history
        dialogueState.conversationHistory.add(mapOf("role" to "assistant", "content" to previousSentence))

        // Step 4: Retrieve user info and store it
        speakersInfo = fileStorageManager.readFromFile(SpeakersInfo::class.java)!!
        Log.i(TAG, "speakersInfo = $speakersInfo")

        // Step 5: Load dialogue statistics
        dialogueStatistics = fileStorageManager.readFromFile(DialogueStatistics::class.java)!!
        Log.i(TAG, "dialogueStatistics = $dialogueStatistics")

        // Step 6: Set the previous dialogue sentence
        dialogueState.prevDialogueSentence = listOf(listOf("s", previousSentence))
    }

    fun writeToFile() {
        Log.i(TAG, "Writing conversation state elements")
        fileStorageManager.writeToFile(dialogueState)
        Log.i(TAG, "dialogueState = $dialogueState")
        fileStorageManager.writeToFile(speakersInfo)
        Log.i(TAG, "speakersInfo = $speakersInfo")
        fileStorageManager.writeToFile(dialogueStatistics)
        Log.i(TAG, "dialogueStatistics = $dialogueStatistics")
    }

    // Custom copy method
    fun copy(
        fileStorageManager: FileStorageManager = this.fileStorageManager,
        previousSentence: String = this.previousSentence,
        dialogueState: DialogueState = this.dialogueState.copy(),
        speakersInfo: SpeakersInfo = this.speakersInfo.copy(),
        dialogueStatistics: DialogueStatistics = this.dialogueStatistics.copy(),
        plan: String? = this.plan,
        planSentence: String? = this.planSentence
    ): ConversationState {
        val newConversationState = ConversationState(fileStorageManager, previousSentence)
        newConversationState.dialogueState = dialogueState
        newConversationState.speakersInfo = speakersInfo
        newConversationState.dialogueStatistics = dialogueStatistics
        newConversationState.plan = plan
        newConversationState.planSentence = planSentence
        return newConversationState
    }

    fun getLastContinuationSentence(): String {
        var lastContinuationSentence = dialogueState.dialogueSentence.lastOrNull()?.getOrNull(1) ?: ""
        val patternDesspk = "\\s*,?\\s*\\\$desspk\\s*,?\\s*".toRegex()
        val patternPrevspk = "\\s*,?\\s*\\\$prevspk\\s*,?\\s*".toRegex()
        lastContinuationSentence = lastContinuationSentence.replace(patternDesspk, " ")
        lastContinuationSentence = lastContinuationSentence.replace(patternPrevspk, " ")
        return lastContinuationSentence
    }

    fun getPreviousContinuationSentence(): String {
        var lastContinuationSentence = dialogueState.prevDialogueSentence.lastOrNull()?.getOrNull(1) ?: ""
        val patternDesspk = "\\s*,?\\s*\\\$desspk\\s*,?\\s*".toRegex()
        val patternPrevspk = "\\s*,?\\s*\\\$prevspk\\s*,?\\s*".toRegex()
        lastContinuationSentence = lastContinuationSentence.replace(patternDesspk, " ")
        lastContinuationSentence = lastContinuationSentence.replace(patternPrevspk, " ")
        return lastContinuationSentence
    }

    fun getReplySentence(): String {
        var replySentence = if (plan?.isNotEmpty() == true) {
            planSentence.toString()
        } else {
            dialogueState.dialogueSentence[0][1]
        }

        Log.i(TAG, "Reply sentence: $replySentence")

        // Clean up the reply sentence if needed
        if (replySentence.isNotEmpty()) {
            val patternPrevspk = "\\s*,?\\s*\\\$prevspk\\s*,?\\s*".toRegex()
            replySentence = replySentence.replace(patternPrevspk, " ")
            return replySentence
        } else {
            return ""
        }
    }
}