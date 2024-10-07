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
    companion object {
        const val MOVING_WINDOW_TIME = 5 * 60
        const val COMMUNITY_TURNS = 20
    }

    // Primary constructor for initializing from a profile ID
    constructor(profileId: String) : this() {
        mappingIndexSpeaker.add(profileId)
        sameTurn.add(mutableListOf(0))
        successiveTurn.add(mutableListOf(0))
        averageTopicDistance.add(mutableListOf(0.0))
        speakersTurns.add(0)
        aPrioriProb.add(0.0)
    }

    // Copy constructor for initializing from an existing DialogueStatistics
    constructor(d: DialogueStatistics) : this() {
        mappingIndexSpeaker = d.mappingIndexSpeaker.toMutableList()
        sameTurn = d.sameTurn.map { it.toMutableList() }.toMutableList()
        successiveTurn = d.successiveTurn.map { it.toMutableList() }.toMutableList()
        averageTopicDistance = d.averageTopicDistance.map { it.toMutableList() }.toMutableList()
        speakersTurns = d.speakersTurns.toMutableList()
        aPrioriProb = d.aPrioriProb.toMutableList()
        movingWindow = d.movingWindow.toMutableList()
        latestTurns = d.latestTurns.toMutableList()
    }

    // Converts object to a dictionary (Map in Kotlin)
    fun toDict(): Map<String, Any> {
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

    // Updates statistics based on the current turn
    fun updateStatistics(dialogueTurn: DialogueTurn, prevTurnLastSpeaker: String) {
        println("Updating dialogue statistics")
        dialogueTurn.turnPieces.forEachIndexed { index, turnPiece ->
            val profileId = turnPiece.profileId

            if (profileId != "00000000-0000-0000-0000-000000000000") {
                var time = getMovingWindowTotalTime()
                while (movingWindow.isNotEmpty() &&
                    time + turnPiece.speakingTime - (movingWindow[0]["speaking_time"] as Float) > MOVING_WINDOW_TIME) {
                    movingWindow.removeAt(0)
                    time = getMovingWindowTotalTime()
                }
                val turnPieceDict = turnPiece.toDict().toMutableMap().apply {
                    remove("sentence")
                }.filterValues { it != null }.mapValues { it.value!! }.toMutableMap()  // Filter out nulls and force non-null

                movingWindow.add(turnPieceDict)
            }

            val speakerIndex = mappingIndexSpeaker.indexOf(profileId)
            speakersTurns[speakerIndex]++

            while (latestTurns.size > COMMUNITY_TURNS) {
                latestTurns.removeAt(0)
            }
            latestTurns.add(profileId)

            if (index == 0 && prevTurnLastSpeaker.isNotEmpty()) {
                val row = mappingIndexSpeaker.indexOf(prevTurnLastSpeaker)
                val column = mappingIndexSpeaker.indexOf(profileId)
                successiveTurn[row][column]++
            } else if (index > 0) {
                val row = mappingIndexSpeaker.indexOf(dialogueTurn.turnPieces[index - 1].profileId)
                val column = mappingIndexSpeaker.indexOf(profileId)
                sameTurn[row][column]++
            }
        }

        val totTurns = speakersTurns.sum()
        for (i in speakersTurns.indices) {
            aPrioriProb[i] = speakersTurns[i].toDouble() / totTurns.toDouble()
        }
    }

    // Generic function to increase matrix size both for integers and double
    fun <T> increaseMatrixSize(matrix: MutableList<MutableList<T>>, defaultValue: T): MutableList<MutableList<T>> {
        val matrixSize = matrix.size

        // Add the default value to each row (increase the column count)
        matrix.forEach { it.add(defaultValue) }

        // Add a new row full of the default value
        matrix.add(MutableList(matrixSize + 1) { defaultValue })

        return matrix
    }

    // Add new speaker statistics
    fun addNewSpeakerStatistics(profileId: String) {
        sameTurn = increaseMatrixSize(sameTurn, 0)
        successiveTurn = increaseMatrixSize(successiveTurn, 0)
        averageTopicDistance = increaseMatrixSize(averageTopicDistance, 0.0).map { it.map { it.toDouble() }.toMutableList() }.toMutableList()
        mappingIndexSpeaker.add(profileId)
        speakersTurns.add(0)
        aPrioriProb.add(0.0)
    }

    fun getTotalTurns(): Int = speakersTurns.sum()

    fun getRegisteredSpeakersTurns(): Int {
        return if (speakersTurns.size > 1) speakersTurns.drop(1).sum() else 0
    }

    fun getMovingWindowSpeakerTurns(profileId: String): Int {
        return movingWindow.count { it["profile_id"] == profileId }
    }

    private fun getMovingWindowSpeakerWords(profileId: String): Int {
        return movingWindow.filter { it["profile_id"] == profileId }.sumOf { it["number_of_words"] as Int }
    }

    fun getMovingWindowSpeakerTime(profileId: String): Double {
        return movingWindow.filter { it["profile_id"] == profileId }
            .sumOf { (it["speaking_time"] as Float).toDouble() }
    }

    fun getMovingWindowTotalTime(): Double {
        return movingWindow.sumOf { it["speaking_time"] as Double }
    }

    fun getMovingWindowTotalWords(): Int {
        return movingWindow.sumOf { it["number_of_words"] as Int }
    }

    fun getSpeakingTimeRatio(): List<Double> {
        val totalTime = getMovingWindowTotalTime()
        return mappingIndexSpeaker.drop(1).map { profileId ->
            getMovingWindowSpeakerTime(profileId).toDouble() / totalTime
        }
    }

    fun getNumberOfWordsRatio(): List<Double> {
        val totalWords = getMovingWindowTotalWords()
        return mappingIndexSpeaker.drop(1).map { profileId ->
            getMovingWindowSpeakerWords(profileId).toDouble() / totalWords
        }
    }

    fun getLatestTurnsSuccessiveTurnMatrix(): Array<IntArray> {
        val matrixSize = mappingIndexSpeaker.size
        val successiveTurnMatrix = Array(matrixSize) { IntArray(matrixSize) }
        latestTurns.forEachIndexed { index, speakerId ->
            if (index > 0) {
                val prevIndex = mappingIndexSpeaker.indexOf(latestTurns[index - 1])
                val currentIndex = mappingIndexSpeaker.indexOf(speakerId)
                successiveTurnMatrix[prevIndex][currentIndex]++
            }
        }
        return successiveTurnMatrix
    }
}