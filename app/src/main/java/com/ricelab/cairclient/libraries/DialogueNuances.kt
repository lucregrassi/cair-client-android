package com.ricelab.cairclient.libraries

import org.json.JSONObject
import java.util.Random

data class DialogueNuances (
    var flags: Map<String, List<Int>> = emptyMap(),
    var values: Map<String, List<String>> = emptyMap()
) {

    fun updateFromJson(json: JSONObject): DialogueNuances {
        // Update flags (Map<String, List<Int>>)
        val flagsJson = json.optJSONObject("flags")
        if (flagsJson != null) {
            val updatedFlags = mutableMapOf<String, List<Int>>()
            flagsJson.keys().forEach { key ->
                val jsonArray = flagsJson.optJSONArray(key)
                if (jsonArray != null) {
                    updatedFlags[key] = (0 until jsonArray.length()).map { jsonArray.getInt(it) }
                }
            }
            this.flags = updatedFlags
        }

        // Update values (Map<String, List<String>>)
        val valuesJson = json.optJSONObject("values")
        if (valuesJson != null) {
            val updatedValues = mutableMapOf<String, List<String>>()
            valuesJson.keys().forEach { key ->
                val jsonArray = valuesJson.optJSONArray(key)
                if (jsonArray != null) {
                    updatedValues[key] = (0 until jsonArray.length()).map { jsonArray.getString(it) }
                }
            }
            this.values = updatedValues
        }

        return this
    }

    // Convert current nuances to a map (for sending back to the server)
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "flags" to flags,
            "values" to values
        )
    }

    override fun toString(): String {
        return """
            Nuances(
                flags=${flags.entries.joinToString { "${it.key}=${it.value}" }},
                values=${values.entries.joinToString { "${it.key}=${it.value}" }}
            )
        """.trimIndent()
    }
}
