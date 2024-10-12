package com.ricelab.cairclient.libraries

import java.util.Random

data class DialogueNuances(
    val nuanceMatrices: Map<String, Array<DoubleArray>>,
    val nuanceVectors: Map<String, Map<String, DoubleArray>>
) {
    private val nuances = listOf("diversity", "time", "place", "tone", "positive_speech_act", "contextual_speech_act")

    // Diversity
    var diversityMatrix: Array<DoubleArray> = nuanceMatrices["diversity"]!!
    var diversityFlags: DoubleArray = nuanceVectors["flags"]?.get("diversity") ?: doubleArrayOf()
    var diversityValues: List<String> = nuanceVectors["values"]?.get("diversity")?.map { it.toString() } ?: listOf()
    val diversityFields = listOf("nationality", "mental condition", "physical condition")

    // Time
    var timeMatrix: Array<DoubleArray> = nuanceMatrices["time"]!!
    var timeFlags: DoubleArray = nuanceVectors["flags"]?.get("time") ?: doubleArrayOf()
    var timeValues: List<String> = nuanceVectors["values"]?.get("time")?.map { it.toString() } ?: listOf()
    val timeFields = listOf("timeofday", "season", "events")

    // Place
    var placeMatrix: Array<DoubleArray> = nuanceMatrices["place"]!!
    var placeFlags: DoubleArray = nuanceVectors["flags"]?.get("place") ?: doubleArrayOf()
    var placeValues: List<String> = nuanceVectors["values"]?.get("place")?.map { it.toString() } ?: listOf()
    val placeFields = listOf("environment", "city", "nation")

    // Tone
    var toneMatrix: Array<DoubleArray> = nuanceMatrices["tone"]!!
    var toneFlags: DoubleArray = nuanceVectors["flags"]?.get("tone") ?: doubleArrayOf()
    var toneValues: List<String> = nuanceVectors["values"]?.get("tone")?.map { it.toString() } ?: listOf()

    // Positive speech act
    var positiveSpeechActMatrix: Array<DoubleArray> = nuanceMatrices["positive_speech_act"]!!
    var positiveSpeechActFlags: DoubleArray = nuanceVectors["flags"]?.get("positive_speech_act") ?: doubleArrayOf()
    var positiveSpeechActValues: List<String> = nuanceVectors["values"]?.get("positive_speech_act")?.map { it.toString() } ?: listOf()

    // Contextual speech act
    var contextualSpeechActMatrix: Array<DoubleArray> = nuanceMatrices["contextual_speech_act"]!!
    var contextualSpeechActFlags: DoubleArray = nuanceVectors["flags"]?.get("contextual_speech_act") ?: doubleArrayOf()
    var contextualSpeechActValues: List<String> = nuanceVectors["values"]?.get("contextual_speech_act")?.map { it.toString() } ?: listOf()

    private val random = Random(System.currentTimeMillis())

    fun fromProbabilitiesToFlags(probabilities: DoubleArray): DoubleArray {
        val updatedFlags = DoubleArray(probabilities.size) { 0.0 }
        val randNum = random.nextDouble()
        var incrementalSum = 0.0
        var oneIndex = 0

        for (n in probabilities.indices) {
            incrementalSum += probabilities[n]
            if (randNum <= incrementalSum) {
                oneIndex = n
                break
            }
        }

        for (i in updatedFlags.indices) {
            updatedFlags[i] = if (i == oneIndex) 1.0 else 0.0
        }

        return updatedFlags
    }

    fun updateFlags() {
        // Diversity
        val diversityProbabilities = multiplyMatrixByVector(diversityMatrix, diversityFlags)
        diversityFlags = fromProbabilitiesToFlags(diversityProbabilities)

        // Time
        val timeProbabilities = multiplyMatrixByVector(timeMatrix, timeFlags)
        timeFlags = fromProbabilitiesToFlags(timeProbabilities)

        // Place
        val placeProbabilities = multiplyMatrixByVector(placeMatrix, placeFlags)
        placeFlags = fromProbabilitiesToFlags(placeProbabilities)

        // Tone
        val toneProbabilities = multiplyMatrixByVector(toneMatrix, toneFlags)
        toneFlags = fromProbabilitiesToFlags(toneProbabilities)

        // Positive speech act
        val positiveSpeechActProbabilities = multiplyMatrixByVector(positiveSpeechActMatrix, positiveSpeechActFlags)
        positiveSpeechActFlags = fromProbabilitiesToFlags(positiveSpeechActProbabilities)

        // Contextual speech act
        val contextualSpeechActProbabilities = multiplyMatrixByVector(contextualSpeechActMatrix, contextualSpeechActFlags)
        contextualSpeechActFlags = fromProbabilitiesToFlags(contextualSpeechActProbabilities)
    }

    // Helper function for matrix-vector multiplication
    private fun multiplyMatrixByVector(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        val result = DoubleArray(matrix.size) { 0.0 }
        for (i in matrix.indices) {
            result[i] = matrix[i].zip(vector) { m, v -> m * v }.sum()
        }
        return result
    }

    fun toDictionary(): Map<String, Any> {
        return mapOf(
            "flags" to mapOf(
                "diversity" to diversityFlags.map { it.toInt() },
                "time" to timeFlags.map { it.toInt() },
                "place" to placeFlags.map { it.toInt() },
                "tone" to toneFlags.map { it.toInt() },
                "positive_speech_act" to positiveSpeechActFlags.map { it.toInt() },
                "contextual_speech_act" to contextualSpeechActFlags.map { it.toInt() }
            ),
            "values" to mapOf(
                "diversity" to diversityValues,
                "time" to timeValues,
                "place" to placeValues,
                "tone" to toneValues,
                "positive_speech_act" to positiveSpeechActValues,
                "contextual_speech_act" to contextualSpeechActValues
            )
        )
    }

    fun nuanceSentences(): Map<String, String> {
        val nuanceSentencesDict = mutableMapOf<String, String>()
        for (elem in nuances) {
            when (elem) {
                "diversity" -> {
                    val i = diversityFlags.indexOfFirst { it == 1.0 }
                    nuanceSentencesDict["diversity"] = if (i == diversityFlags.size - 1) {
                        "Optional: information about the person you are interacting with: [${diversityValues.joinToString()}]."
                    } else {
                        "Optional: the person you are interacting with is ${diversityValues[i]}."
                    }
                }
                "time" -> {
                    val i = timeFlags.indexOfFirst { it == 1.0 }
                    nuanceSentencesDict["time"] = if (i == timeFlags.size - 1) {
                        "Optional: information about the moment in which the conversation is happening: [${timeValues.joinToString()}]."
                    } else {
                        "Optional: the conversation is taking place during the ${timeValues[i]}."
                    }
                }
                "place" -> {
                    val i = placeFlags.indexOfFirst { it == 1.0 }
                    nuanceSentencesDict["place"] = if (i == placeFlags.size - 1) {
                        "Optional: information about the place in which the conversation is happening: [${placeValues.joinToString()}]."
                    } else {
                        "Optional: the conversation is taking place in ${placeValues[i]}."
                    }
                }
                "tone" -> {
                    val i = toneFlags.indexOfFirst { it == 1.0 }
                    nuanceSentencesDict["tone"] = "Compulsory: you have to use a ${toneValues[i]} tone."
                }
                "positive_speech_act" -> {
                    val i = positiveSpeechActFlags.indexOfFirst { it == 1.0 }
                    nuanceSentencesDict["positive_speech_act"] = if (i == positiveSpeechActFlags.size - 1) {
                        "You can use one of the speech acts among the following: ${positiveSpeechActValues.joinToString()}."
                    } else {
                        "If you want, you can use the ${positiveSpeechActValues[i]} speech act."
                    }
                }
                "contextual_speech_act" -> {
                    val i = contextualSpeechActFlags.indexOfFirst { it == 1.0 }
                    nuanceSentencesDict["contextual_speech_act"] = if (i == contextualSpeechActFlags.size - 1) {
                        "You can use one of the speech acts among the following: ${contextualSpeechActValues.joinToString()}."
                    } else {
                        "If you want, you can use the ${contextualSpeechActValues[i]} speech act."
                    }
                }
            }
        }
        return nuanceSentencesDict
    }
}