package com.ricelab.cairclient.libraries

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.StringReader
import java.io.StringWriter
import org.xmlpull.v1.XmlPullParserException

data class TurnPiece(
    val profileId: String,
    var sentence: String,
    var language: String,
    var speakingTime: Float
) {
    private val numberOfWords: Int = sentence.split(" ").size

    // Converts the object to a dictionary (map in Kotlin)
    fun toDict(): Map<String, Any?> {
        return mapOf(
            "profileId" to profileId,
            "sentence" to sentence,
            "language" to language,
            "speakingTime" to speakingTime,
            "numberOfWords" to numberOfWords
        )
    }
}

class DialogueTurn {
    var turnPieces: MutableList<TurnPiece> = mutableListOf()

    // Constructor to initialize with XML string
    constructor(xmlString: String?) {
        if (xmlString != null) {
            parseXml(xmlString)
        }
    }

    // Constructor to initialize from a dictionary (map in Kotlin)
    constructor(d: Map<String, Any>?) {
        d?.let {
            val pieces = it["turn_pieces"]
            if (pieces is List<*>) {  // Check if it is a list
                turnPieces =
                    pieces.filterIsInstance<Map<String, Any>>() // Filter and cast valid entries
                        .map { piece ->
                            TurnPiece(
                                profileId = piece["profileId"] as String,
                                sentence = piece["sentence"] as String,
                                language = piece["language"] as String,
                                speakingTime = (piece["speaking_time"] as? Double)?.toFloat()
                                    ?: 0.0f
                            )
                        }.toMutableList()
            }
        }
    }

    // Converts the object to XML string
    fun toXmlString(): String {
        val writer = StringWriter()
        val serializer: XmlSerializer = XmlPullParserFactory.newInstance().newSerializer()
        serializer.setOutput(writer)

        serializer.startDocument("UTF-8", true)
        serializer.startTag("", "response")

        for (turnPiece in turnPieces) {
            serializer.startTag("", "profile_id")
            serializer.attribute("", "value", turnPiece.profileId)
            serializer.text(turnPiece.sentence)
            serializer.startTag("", "language")
            serializer.text(turnPiece.language)
            serializer.endTag("", "language")
            serializer.startTag("", "speaking_time")
            serializer.text(turnPiece.speakingTime.toString())
            serializer.endTag("", "speaking_time")
            serializer.endTag("", "profile_id")
        }

        serializer.endTag("", "response")
        serializer.endDocument()

        return writer.toString()
    }

    // Converts the object to a dictionary (map in Kotlin)
    fun toDict(): Map<String, Any?> {
        return mapOf(
            "turn_pieces" to turnPieces.map { it.toDict() }
        )
    }

    // Returns the plain text of the turn
    fun getText(): String {
        return turnPieces.joinToString(" ") { it.sentence }
    }

    // Checks if the dialogue turn is empty
    fun isEmpty(): Boolean {
        return turnPieces.isEmpty()
    }

    // Adds a turn piece to the dialogue turn
    fun addTurnPiece(turnPiece: TurnPiece) {
        if (turnPieces.isNotEmpty()) {
            val lastPiece = turnPieces.last()
            if (lastPiece.profileId == turnPiece.profileId) {
                lastPiece.sentence += " ${turnPiece.sentence}"
                lastPiece.language = turnPieces.first().language // Keep the first language
                lastPiece.speakingTime += turnPiece.speakingTime
            } else {
                turnPieces.add(turnPiece)
            }
        } else {
            turnPieces.add(turnPiece)
        }
    }

    // Calculates the total speaking time of the dialogue turn
    fun getTurnSpeakingTime(): Double {
        return turnPieces.sumOf { it.speakingTime.toDouble() }
    }

    // Helper method to parse XML string into DialogueTurn
    private fun parseXml(xmlString: String) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var currentProfileId: String? = null
            var currentSentence: String? = null
            var currentLanguage: String? = null
            var currentSpeakingTime: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "profile_id") {
                            currentProfileId = parser.getAttributeValue(null, "value")
                        }
                    }
                    XmlPullParser.TEXT -> {
                        currentSentence = parser.text
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "profile_id" -> {
                                if (currentProfileId != null && currentSentence != null && currentLanguage != null && currentSpeakingTime != null) {
                                    val turnPiece = TurnPiece(
                                        profileId = currentProfileId,
                                        sentence = currentSentence,
                                        language = currentLanguage,
                                        speakingTime = currentSpeakingTime.toFloat()
                                    )
                                    addTurnPiece(turnPiece)
                                }
                                currentProfileId = null
                                currentSentence = null
                                currentLanguage = null
                                currentSpeakingTime = null
                            }
                            "language" -> currentLanguage = parser.text
                            "speaking_time" -> currentSpeakingTime = parser.text
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        }
    }
}