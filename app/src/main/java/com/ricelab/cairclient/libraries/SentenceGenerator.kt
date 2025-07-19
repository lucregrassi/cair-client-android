package com.ricelab.cairclient.libraries

import android.util.Log
import android.os.Bundle
import java.io.IOException
import android.content.Context


private const val TAG = "SentenceGenerator"

class SentenceGenerator {
    // Instead of a single list for fillerSentences, we now have a map keyed by language
    private val fillerSentencesMap = mutableMapOf<String, List<String>>()

    private val mapPredefinedSentences= mapOf(
        "welcome_back" to mapOf(
            "it-IT" to "È bello rivederci! Di cosa potremmo parlare?",
            "en-US" to "Welcome back! I missed you. What would you like to talk about?"
        ),
        "server_error" to mapOf(
            "it-IT" to "Mi dispiace, non riesco a connettermi al server.",
            "en-US" to "I'm sorry, I can't connect to the server."
        ),
        "goodbye" to mapOf(
            "it-IT" to "È stato bello conversare insieme. A presto!",
            "en-US" to "It was nice talking to you. See you soon!"
        ),
        "nothing_to_repeat" to mapOf(
            "it-IT" to "Mi dispiace, non ho niente da ripetere!",
            "en-US" to "I'm sorry, I have nothing to repeat!"
        ),
        "repeat_previous" to mapOf(
            "it-IT" to "Ho detto:",
            "en-US" to "I said:"
        ),
        "listening_user" to mapOf(
            "it-IT" to "Sto ascoltando...",
            "en-US" to "I'm listening..."
        ),
        "listening_robot" to mapOf(
            "it-IT" to "Sì, ti ascolto...",
            "en-US" to "Yes, I'm listening to you..."
        ),
        "prefix_repeat" to mapOf(
            "it-IT" to "Dicevo...",
            "en-US" to "I was saying..."
        ),
        "microphone_user" to mapOf(
            "it-IT" to "Microfono disattivato.",
            "en-US" to "Microphone disabled."
        ),
        "microphone_robot" to mapOf(
            "it-IT" to "Va bene, smetto di ascoltare. Toccami 4 volte la testa o dimmi: \"Hey, Pèpper\", quando vuoi che mi rimetta in ascolto.",
            "en-US" to "Ok, I'll stop listening. Touch my head four times or say \"Hey, Pepper\" when you want me to start listening again."
        )
    )

    /**
     * Load filler sentences from two separate files:
     * - filler_sentences_it-IT.txt for Italian
     * - filler_sentences_en-US.txt for English
     * Store them in fillerSentencesMap with keys "it-IT" and "en-US".
     */
    fun loadFillerSentences(context: Context) {
        fun loadSentencesForLang(fileName: String): List<String> {
            return try {
                val inputStream = context.assets.open("dialogue_data/$fileName")
                inputStream.bufferedReader().useLines { lines ->
                    lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error loading filler sentences from $fileName: ${e.message}")
                listOf("...")
            }
        }

        val itSentences = loadSentencesForLang("filler_sentences_it-IT.txt")
        val enSentences = loadSentencesForLang("filler_sentences_en-US.txt")

        fillerSentencesMap["it-IT"] = itSentences
        fillerSentencesMap["en-US"] = enSentences

        Log.d(TAG, "Filler sentences loaded: it-IT=${itSentences.size}, en-US=${enSentences.size}")
    }

    fun getPredefinedSentence(language: String, key: String): String {
        val messageMap = mapPredefinedSentences[key]
        return messageMap?.get(language) ?: (messageMap?.get("it-IT") ?: "")
    }

    fun getFillerSentence(language: String): String {
        // Pick a random filler in IO context
        val fillerList = fillerSentencesMap[language] ?: fillerSentencesMap["it-IT"]!!
        return fillerList.random()
    }
}