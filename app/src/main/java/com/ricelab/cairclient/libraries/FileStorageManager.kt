// FileStorageManager.kt
package com.ricelab.cairclient.libraries

import com.google.gson.Gson
import java.io.File

class FileStorageManager(private val gson: Gson, private val filesDir: File) {

    fun <T> writeToFile(data: T, filename: String) {
        val file = File(filesDir, filename)
        val jsonData = gson.toJson(data)
        file.writeText(jsonData)
    }

    fun <T> readFromFile(filename: String, classOfT: Class<T>): T? {
        val file = File(filesDir, filename)
        return if (file.exists()) {
            val jsonData = file.readText()
            gson.fromJson(jsonData, classOfT)
        } else {
            null
        }
    }
}