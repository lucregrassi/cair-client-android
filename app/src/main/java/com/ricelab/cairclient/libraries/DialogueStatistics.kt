package com.ricelab.cairclient.libraries

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DialogueStatistics(
    var mappingIndexSpeaker: MutableList<String> = mutableListOf(),
    var sameTurn: MutableList<MutableList<Int>> = mutableListOf(mutableListOf(0)),
    var successiveTurn: MutableList<MutableList<Int>> = mutableListOf(mutableListOf(0)),
    var averageTopicDistance: MutableList<MutableList<Double>> = mutableListOf(mutableListOf(0.0)),
    var speakersTurns: MutableList<Int> = mutableListOf(0),
    var aPrioriProb: MutableList<Double> = mutableListOf(0.0),
    var movingWindow: MutableList<Any> = mutableListOf(),
    var latestTurns: MutableList<Any> = mutableListOf()
) {
    constructor(profileId: String) : this() {
        mappingIndexSpeaker = mutableListOf(profileId)
    }

    suspend fun saveDialogueData(fileStorageManager: FileStorageManager) {
        withContext(Dispatchers.IO) {
            fileStorageManager.writeToFile(this)
        }
    }

    fun toDict(): Map<String, Any?> {
        return mapOf(
            "mappingIndexSpeaker" to mappingIndexSpeaker,
            "sameTurn" to sameTurn,
            "successiveTurn" to successiveTurn,
            "averageTopicDistance" to averageTopicDistance,
            "speakersTurns" to speakersTurns,
            "aPrioriProb" to aPrioriProb,
            "movingWindow" to movingWindow,
            "latestTurns" to latestTurns
        )
    }
}