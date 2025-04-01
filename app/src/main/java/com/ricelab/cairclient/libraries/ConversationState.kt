package com.ricelab.cairclient.libraries

import android.util.Log
import kotlin.random.Random

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

    // TODO: these are not reinitialized with a new hubRequest
    // check if they are not already inside dialogueState
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

    private fun replaceSpeakerTags(
        sentence: String,
        prevSpeakerName: String?,
        destSpeakerName: String?
    ): String {
        var result = sentence

        // Regex patterns to find placeholders in text
        val patternDesspk = "\\s*,?\\s*\\\$desspk\\s*,?\\s*".toRegex()
        val patternPrevspk = "\\s*,?\\s*\\\$prevspk\\s*,?\\s*".toRegex()

        // Randomly decide whether to replace with the actual name or an empty string
        val usePrevSpeakerName = prevSpeakerName != null && Random.nextInt(100) < 10
        val useDestSpeakerName = destSpeakerName != null && Random.nextInt(100) < 10

        // Replace the placeholders
        result = if (usePrevSpeakerName) {
            result.replace(patternPrevspk, " $prevSpeakerName ")
        } else {
            // If no name found, just remove the placeholder
            result.replace(patternPrevspk, " ")
        }
        result = if (useDestSpeakerName) {
            result.replace(patternDesspk, " $destSpeakerName ")
        } else {
            result.replace(patternDesspk, " ")

        }
        return result
    }

    fun getLastContinuationSentence(personName: String): String {
        var lastContinuationSentence = dialogueState.dialogueSentence.lastOrNull()?.getOrNull(1) ?: ""
        val destSpeakerName = if (personName.isEmpty() || personName == "Utente" || personName == "User") {
            null
        } else {
            personName
        }
        lastContinuationSentence = replaceSpeakerTags(lastContinuationSentence, null, destSpeakerName)
        return lastContinuationSentence
    }

    fun getPreviousContinuationSentence(personName: String): String {
        var lastContinuationSentence = dialogueState.prevDialogueSentence.lastOrNull()?.getOrNull(1) ?: ""
        val destSpeakerName = if (personName.isEmpty() || personName == "Utente" || personName == "User") {
            null
        } else {
            personName
        }
        lastContinuationSentence = replaceSpeakerTags(lastContinuationSentence,null, destSpeakerName)
        return lastContinuationSentence
    }

    fun getReplySentence(personName: String): String {
        // Determine the initial reply sentence
        val replySentence = if (!plan.isNullOrEmpty()) {
            planSentence.toString()
        } else {
            dialogueState.dialogueSentence[0][1]
        }

        Log.i(TAG, "Reply sentence: $replySentence")

        // Determine whether to use the person's name or fallback to null
        val prevSpeakerName = personName.ifEmpty {
            null
        }

        // Clean up and process the reply sentence
        return if (replySentence.isNotEmpty()) {
            // Replace speaker tags with the determined name or null
            replaceSpeakerTags(replySentence, prevSpeakerName, null)
        } else {
            ""
        }
    }
}