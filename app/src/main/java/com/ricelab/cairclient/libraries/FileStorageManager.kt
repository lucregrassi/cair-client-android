package com.ricelab.cairclient.libraries

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStream

class FileStorageManager(
    val filesDir: File,
    var dialogueStateFile: File? = null,
    val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
    var speakersInfoFile: File? = null,
    var dialogueStatisticsFile: File? = null,
    var mycontext: Context? = null
) {
    // Secondary constructor that takes Context and filesDir
    constructor(context: Context, filesDir: File) : this(
        filesDir = filesDir,
        dialogueStateFile = File(filesDir, "dialogue_state.json"),
        speakersInfoFile = File(filesDir, "speakers_info.json"),
        dialogueStatisticsFile = File(filesDir, "dialogue_statistics.json"),
        mycontext = context
    )

    // Write data to file
    inline fun <reified T> writeToFile(data: T) {
        val jsonData = gson.toJson(data)
        when {
            isDialogueState(data) -> dialogueStateFile?.writeText(jsonData)
            isSpeakersInfo(data) -> speakersInfoFile?.writeText(jsonData)
            isDialogueStatistics(data) -> dialogueStatisticsFile?.writeText(jsonData)
            isDialogueNuances(data) -> {
                // Nuances are stored in internal storage since assets are read-only
                val nuanceVectorsFile = File(filesDir, "nuances/nuance_vectors.json")
                nuanceVectorsFile.writeText(jsonData)
            }
        }
    }

    // Read data from file
    fun <T> readFromFile(classOfT: Class<T>): T? {
        Log.d("ConversationState", "inside readFromFile")
        return when {
            isDialogueState(classOfT) -> {
                dialogueStateFile?.let {
                    val jsonData = it.readText()
                    gson.fromJson(jsonData, classOfT)
                }
            }
            isSpeakersInfo(classOfT) -> {
                speakersInfoFile?.let {
                    val jsonData = it.readText()
                    gson.fromJson(jsonData, classOfT)
                }
            }
            isDialogueStatistics(classOfT) -> {
                dialogueStatisticsFile?.let {
                    val jsonData = it.readText()
                    gson.fromJson(jsonData, classOfT)
                }
            }
            isDialogueNuances(classOfT) -> {
                mycontext!!.assets.open("nuances/nuance_vectors.json").bufferedReader().use { reader ->
                    val jsonData = reader.readText()
                    gson.fromJson(jsonData, classOfT)
                }
            }
            else -> {
                Log.d("ConversationState", "class is not recognized")
                null
            }
        }
    }

    // Check if files exist
    fun filesExist(): Boolean {
        return dialogueStateFile?.exists() ?: false
    }
}

// Helper functions for type checking
inline fun <reified T> isDialogueStatistics(data: T): Boolean {
    return data is DialogueStatistics
}

inline fun <reified T> isSpeakersInfo(data: T): Boolean {
    return data is SpeakersInfo
}

inline fun <reified T> isDialogueState(data: T): Boolean {
    return data is DialogueState
}

inline fun <reified T> isDialogueNuances(data: T): Boolean {
    return data is DialogueNuances
}

// Type checking helper function for DialogueState
fun <T> isDialogueState(classOfT: Class<T>): Boolean {
    return classOfT == DialogueState::class.java
}

// Similar helper functions for other classes
fun <T> isSpeakersInfo(classOfT: Class<T>): Boolean {
    return classOfT == SpeakersInfo::class.java
}

fun <T> isDialogueStatistics(classOfT: Class<T>): Boolean {
    return classOfT == DialogueStatistics::class.java
}

fun <T> isDialogueNuances(classOfT: Class<T>): Boolean {
    return classOfT == DialogueNuances::class.java
}