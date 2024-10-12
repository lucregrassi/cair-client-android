package com.ricelab.cairclient.libraries

data class DialogueStatistics(
    var mappingIndexSpeaker: MutableList<String> = mutableListOf(),
    var sameTurn: MutableList<MutableList<Int>> = mutableListOf(),
    var successiveTurn: MutableList<MutableList<Int>> = mutableListOf(),
    var averageTopicDistance: MutableList<MutableList<Double>> = mutableListOf(),
    var speakersTurns: MutableList<Int> = mutableListOf(),
    var aPrioriProb: MutableList<Double> = mutableListOf(),
    var movingWindow: MutableList<Map<String, Any>> = mutableListOf(),
    var latestTurns: MutableList<String> = mutableListOf()
) {
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

    // Example methods; implement according to your server's logic
    fun getSpeakingTimeRatio(): List<Double> {
        val totalTime = movingWindow.sumOf { it["speaking_time"] as Double }
        return mappingIndexSpeaker.drop(1).map { profileId ->
            val speakerTime = movingWindow.filter { it["profile_id"] == profileId }
                .sumOf { it["speaking_time"] as Double }
            if (totalTime > 0) speakerTime / totalTime else 0.0
        }
    }

    fun getNumberOfWordsRatio(): List<Double> {
        val totalWords = movingWindow.sumOf { it["number_of_words"] as Int }
        return mappingIndexSpeaker.drop(1).map { profileId ->
            val speakerWords = movingWindow.filter { it["profile_id"] == profileId }
                .sumOf { it["number_of_words"] as Int }
            if (totalWords > 0) speakerWords.toDouble() / totalWords else 0.0
        }
    }

    fun getMovingWindowSpeakerTurns(profileId: String): Int {
        return movingWindow.count { it["profile_id"] == profileId }
    }

    fun getMovingWindowSpeakerWords(profileId: String): Int {
        return movingWindow.filter { it["profile_id"] == profileId }
            .sumOf { it["number_of_words"] as Int }
    }

    fun getMovingWindowSpeakerTime(profileId: String): Double {
        return movingWindow.filter { it["profile_id"] == profileId }
            .sumOf { it["speaking_time"] as Double }
    }

    fun getRegisteredSpeakersTurns(): Int {
        return speakersTurns.sum()
    }

    fun getMovingWindowTotalTime(): Double {
        return movingWindow.sumOf { it["speaking_time"] as Double }
    }

    fun getMovingWindowTotalWords(): Int {
        return movingWindow.sumOf { it["number_of_words"] as Int }
    }
}