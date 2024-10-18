package com.ricelab.cairclient.libraries

import android.util.Log
class ConversationState(
    private var fileStorageManager: FileStorageManager,
    private var previousSentence: String
) {
    var dialogueState: DialogueState = DialogueState()
    var speakersInfo = SpeakersInfo()
    var dialogueStatistics = DialogueStatistics()
    // Remove dialogueNuances variable

    // Function to load conversation state
    fun loadConversationState() {
        // Step 1: Retrieve the state of the conversation
        Log.i("ConversationState", "Loading conversation state elements")
        dialogueState = fileStorageManager.readFromFile(DialogueState::class.java)!!

        Log.i("ConversationState", "dialogueState = $dialogueState")

        // Step 2: If it's the first time, initialize dialogueNuances
        if (dialogueState.dialogueNuances.flags.isEmpty() && dialogueState.dialogueNuances.values.isEmpty()) {
            Log.i("ConversationState", "dialogueState.dialogueNuances is empty, loading from file")
            val dialogueNuances = fileStorageManager.readFromFile(DialogueNuances::class.java)!!
            dialogueState.dialogueNuances = dialogueNuances
            Log.i("ConversationState", "dialogueState.dialogueNuances initialized")
        }

        // Step 3: Store the welcome or welcome back message in the conversation history
        dialogueState.conversationHistory.add(mapOf("role" to "assistant", "content" to previousSentence))

        // Step 4: Retrieve user info and store it
        speakersInfo = fileStorageManager.readFromFile(SpeakersInfo::class.java)!!

        Log.i("ConversationState", "speakersInfo = $speakersInfo")

        // Step 5: Load dialogue statistics
        dialogueStatistics = fileStorageManager.readFromFile(DialogueStatistics::class.java)!!

        Log.i("ConversationState", "dialogueStatistics = $dialogueStatistics")

        // Step 6: Set the previous dialogue sentence
        dialogueState.prevDialogueSentence = listOf(listOf("s", previousSentence))
    }

    // Custom copy method
    fun copy(
        fileStorageManager: FileStorageManager = this.fileStorageManager,
        previousSentence: String = this.previousSentence,
        dialogueState: DialogueState = this.dialogueState.copy(),
        speakersInfo: SpeakersInfo = this.speakersInfo.copy(),
        dialogueStatistics: DialogueStatistics = this.dialogueStatistics.copy()
    ): ConversationState {
        val newConversationState = ConversationState(fileStorageManager, previousSentence)
        newConversationState.dialogueState = dialogueState
        newConversationState.speakersInfo = speakersInfo
        newConversationState.dialogueStatistics = dialogueStatistics
        return newConversationState
    }
}