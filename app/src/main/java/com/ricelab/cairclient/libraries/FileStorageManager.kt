package com.ricelab.cairclient.libraries

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.InputStream

class FileStorageManager(
    val filesDir: File,
    var dialogueStateFile: File? = null,
    val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
    var speakersInfoFile: File? = null,
    var dialogueStatisticsFile: File? = null,
    var nuanceVectorsInputStream: InputStream? = null // InputStream for assets (read-only)
) {
    // Secondary constructor that takes Context and filesDir
    constructor(context: Context, filesDir: File) : this(
        filesDir = filesDir,
        dialogueStateFile = File(filesDir, "dialogue_state.json"),
        speakersInfoFile = File(filesDir, "speakers_info.json"),
        dialogueStatisticsFile = File(filesDir, "dialogue_statistics.json"),
        nuanceVectorsInputStream = context.assets.open("nuances/nuance_vectors.json")
    )

    inline fun <reified T> writeToFile(data: T) {
        val jsonData = gson.toJson(data)
        when {
            isDialogueState(data) -> dialogueStateFile?.writeText(jsonData)
            isSpeakerInfo(data) -> speakersInfoFile?.writeText(jsonData)
            isDialogueStatistics(data) -> dialogueStatisticsFile?.writeText(jsonData)
            isDialogueNuances(data) -> {
                // Nuances are in assets, cannot write to InputStream directly
                // If needed, write to internal storage instead
                val nuanceVectorsFile = File(filesDir, "nuances/nuance_vectors.json")
                nuanceVectorsFile.writeText(jsonData)
            }
        }
    }

    fun <T> readFromFile(classOfT: Class<T>): T? {
        return when {
            isDialogueState(classOfT) -> {
                dialogueStateFile?.let {
                    val jsonData = it.readText()
                    gson.fromJson(jsonData, classOfT)
                }
            }
            isSpeakerInfo(classOfT) -> {
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
                nuanceVectorsInputStream?.bufferedReader()?.use { reader ->
                    val jsonData = reader.readText()
                    gson.fromJson(jsonData, classOfT)
                }
            }
            else -> null
        }
    }

    fun filesExist(): Boolean {
        return dialogueStateFile?.exists() ?: false
    }
}

// Helper functions for type checking
inline fun <reified T> isDialogueStatistics(data: T): Boolean {
    return data is DialogueStatistics
}

inline fun <reified T> isSpeakerInfo(data: T): Boolean {
    return data is SpeakerInfo
}

inline fun <reified T> isDialogueState(data: T): Boolean {
    return data is DialogueState
}

inline fun <reified T> isDialogueNuances(data: T): Boolean {
    return data is DialogueNuances
}