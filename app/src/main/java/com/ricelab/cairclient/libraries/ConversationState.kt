package com.ricelab.cairclient.libraries

import android.util.Log

class ConversationState(
    private var fileStorageManager: FileStorageManager,
    private var previousSentence: String
) {
    var dialogueState: DialogueState = DialogueState()
    var speakersInfo = SpeakerInfo()
    var dialogueStatistics = DialogueStatistics()
    var dialogueNuances = DialogueNuances() // This should be the actual type

    // Function to load conversation state
    fun loadConversationState() {
        // Step 1: Retrieve the state of the conversation
        Log.i("loadConversationState", "Loading DialogueState")
        dialogueState = fileStorageManager.readFromFile(DialogueState::class.java)!!

        Log.i("loadConversationState", "dialogueState = $dialogueState")

        // Step 2: If it is the first time, fill the nuance vectors
        dialogueNuances = fileStorageManager.readFromFile(DialogueNuances::class.java)!!

        // This should be a dictionary
        // dialogueState.dialogueNuances =
        Log.i("loadConversationState", "dialogueNuances = $dialogueNuances")

        // Step 3: Store the welcome or welcome back message in the conversation history
        dialogueState.conversationHistory.add(mapOf("role" to "assistant", "content" to previousSentence))

        // Step 4: Retrieve user info and store it in a dictionary
        Log.i("loadConversationState", "Loading SpeakersInfo")
        speakersInfo = fileStorageManager.readFromFile(SpeakerInfo::class.java)!!

        Log.i("loadConversationState", "speakersInfo = $speakersInfo")

        Log.i("loadConversationState", "Loading DialogueStatistics")
        dialogueStatistics = fileStorageManager.readFromFile(DialogueStatistics::class.java) !!

        Log.i("loadConversationState", "dialogueStatistics = $dialogueStatistics")

        // Step 6: Set the previous dialogue sentence
        dialogueState.prevDialogueSentence = listOf(Pair("s", previousSentence))
    }

    // Custom copy method
    fun copy(
        fileStorageManager: FileStorageManager = this.fileStorageManager,
        previousSentence: String = this.previousSentence,
        dialogueState: DialogueState = this.dialogueState.copy(), // Assuming DialogueState has a copy method
        speakersInfo: SpeakerInfo = this.speakersInfo.copy(), // Assuming SpeakerInfo has a copy method
        dialogueStatistics: DialogueStatistics = this.dialogueStatistics.copy(), // Assuming DialogueStatistics has a copy method
        dialogueNuances: DialogueNuances = this.dialogueNuances.copy() // Assuming DialogueNuances has a copy method
    ): ConversationState {
        val newConversationState = ConversationState(fileStorageManager, previousSentence)
        newConversationState.dialogueState = dialogueState
        newConversationState.speakersInfo = speakersInfo
        newConversationState.dialogueStatistics = dialogueStatistics
        newConversationState.dialogueNuances = dialogueNuances
        return newConversationState
    }
}