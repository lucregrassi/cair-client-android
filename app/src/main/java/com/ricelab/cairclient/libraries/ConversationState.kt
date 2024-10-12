package com.ricelab.cairclient.libraries

class ConversationState(
    private var fileStorageManager: FileStorageManager,
    private var previousSentence: String
) {
    private var dialogueState: DialogueState = DialogueState()
    private var speakersInfo = SpeakerInfo()
    private var dialogueStatistics = DialogueStatistics()
    //private var dialogueNuances = DialogueNuances() // This should be the actual type

    // Function to load conversation state
    fun loadConversationState() {
        // Step 1: Retrieve the state of the conversation
        dialogueState = fileStorageManager.readFromFile(DialogueState::class.java)!!

        // Step 2: If it is the first time, fill the nuance vectors
        //dialogueNuances = fileStorageManager.readFromFile(DialogueNuances::class.java)!!

        // This should be a dictionary
        // dialogueState.dialogueNuances =

        // Step 3: Store the welcome or welcome back message in the conversation history
        dialogueState.conversationHistory.add(mapOf("role" to "assistant", "content" to previousSentence))

        // Step 4: Retrieve user info and store it in a dictionary

        speakersInfo = fileStorageManager.readFromFile(SpeakerInfo::class.java)!!


        dialogueStatistics = fileStorageManager.readFromFile(DialogueStatistics::class.java) !!

        // Step 6: Set the previous dialogue sentence
        dialogueState.prevDialogueSentence = listOf(Pair("s", previousSentence))
    }
}