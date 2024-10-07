// FileStorageManager.kt
package com.ricelab.cairclient.libraries

import android.content.Context
import com.google.gson.Gson
import java.io.File

class FileStorageManager(private val gson: Gson, private val filesDir: File) {

    fun <T> loadData(fileName: String, type: Class<T>): T? {
        val file = File(filesDir, fileName)
        if (file.exists()) {
            val json = file.readText(Charsets.UTF_8)
            return gson.fromJson(json, type)
        }
        return null
    }

    fun saveData(fileName: String, data: Any) {
        val file = File(filesDir, fileName)
        val json = gson.toJson(data)
        file.writeText(json, Charsets.UTF_8)
    }
}