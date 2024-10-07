package com.ricelab.cairclient.libraries

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class ConversationStateLoader(
    private val dialogueStateFilePath: File,
    private val speakersInfoFilePath: File,
    private val dialogueStatisticsFilePath: File,
    private var previousSentence: String
) {
    private var dialogueState: DialogueState = DialogueState()
    private var speakersInfo = SpeakerInfo()
    private var dialogueStatistics = DialogueStatistics()
    private var nuanceVectors: List<Any> = listOf() // This should be the actual type

    private val gson = Gson() // Initialize Gson for JSON parsing

    // Function to load conversation state
    fun loadConversationState() {
        // Step 1: Retrieve the state of the conversation
        if (dialogueStateFilePath.exists()) {
            val dialogueStateJson = dialogueStateFilePath.readText()
            dialogueState = gson.fromJson(dialogueStateJson, DialogueState::class.java)
        }

        // Step 2: If it is the first time, fill the nuance vectors
        if (nuanceVectors.isNotEmpty()) {
            dialogueState.dialogueNuances = nuanceVectors
        }

        // Step 3: Store the welcome or welcome back message in the conversation history
        dialogueState.conversationHistory.add(mapOf("role" to "assistant", "content" to previousSentence))

        // Step 4: Retrieve user info and store it in a dictionary
        if (speakersInfoFilePath.exists()) {
            val speakersInfoJson = speakersInfoFilePath.readText()
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            speakersInfo = gson.fromJson(speakersInfoJson, mapType)
        }

        // Step 5: Retrieve dialogue statistics
        if (dialogueStatisticsFilePath.exists()) {
            val dialogueStatisticsJson = dialogueStatisticsFilePath.readText()
            dialogueStatistics = gson.fromJson(dialogueStatisticsJson, DialogueStatistics::class.java)
        }

        // Step 6: Set the previous dialogue sentence
        dialogueState.prevDialogueSentence = listOf(Pair("s", previousSentence))
    }
}