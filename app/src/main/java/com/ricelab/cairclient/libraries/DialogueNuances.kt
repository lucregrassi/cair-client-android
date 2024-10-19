package com.ricelab.cairclient.libraries

import org.json.JSONObject

data class DialogueNuances (
    var flags: Map<String, List<Int>> = emptyMap(),
    var values: Map<String, List<String>> = emptyMap()
) {

    fun updateFromJson(json: JSONObject): DialogueNuances {
        // Update flags
        json.optJSONObject("flags")?.let { flagsJson ->
            val updatedFlags = mutableMapOf<String, List<Int>>()
            val keys = flagsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val jsonArray = flagsJson.optJSONArray(key)
                if (jsonArray != null) {
                    val list = mutableListOf<Int>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getInt(i))
                    }
                    updatedFlags[key] = list
                }
            }
            this.flags = updatedFlags
        }

        // Update values
        json.optJSONObject("values")?.let { valuesJson ->
            val updatedValues = mutableMapOf<String, List<String>>()
            val keys = valuesJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val jsonArray = valuesJson.optJSONArray(key)
                if (jsonArray != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getString(i))
                    }
                    updatedValues[key] = list
                }
            }
            this.values = updatedValues
        }
        return this
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
