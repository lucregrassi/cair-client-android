package com.ricelab.cairclient.libraries

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import androidx.core.content.edit

private const val TAG = "DialogueNuances"

data class DialogueNuances(
    var flags: Map<String, List<Int>> = defaultFlags(),
    var values: Map<String, List<String>> = defaultValues()
) {
    override fun toString(): String {
        return """
            Nuances(
                flags=${flags.entries.joinToString { "${it.key}=${it.value}" }},
                values=${values.entries.joinToString { "${it.key}=${it.value}" }}
            )
        """.trimIndent()
    }

    fun saveToPrefs(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val json = Gson().toJson(this)
            prefs.edit { putString("dialogue_nuances", json) }

            Log.i(TAG, "DialogueNuances saved successfully: $json")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving DialogueNuances to prefs", e)
        }
    }

    companion object {
        fun defaultFlags(): Map<String, List<Int>> = mapOf(
            "diversity" to listOf(0, 0, 0, 1),
            "time" to listOf(0, 0, 0, 1),
            "place" to listOf(0, 0, 0, 1),
            "tone" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 1),
            "positive_speech_act" to listOf(0, 0, 0, 1),
            "contextual_speech_act" to listOf(0, 0, 0, 0, 1)
        )

        fun defaultValues(): Map<String, List<String>> = mapOf(
            "diversity" to listOf("not relevant", "good mental health", "no physical problems"),
            "time" to listOf("nothing specific", "nothing specific", "nothing specific"),
            "place" to listOf("nowhere specific", "nowhere specific", "nowhere specific"),
            "tone" to List(9) { "neutral" },
            "positive_speech_act" to listOf("assertive", "commissive", "expressive"),
            "contextual_speech_act" to listOf("assertive", "commissive", "expressive", "declarative")
        )

        fun loadFromPrefs(context: Context): DialogueNuances {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val prefs = EncryptedSharedPreferences.create(
                    context,
                    "secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                val json = prefs.getString("dialogue_nuances", null)
                if (json != null) {
                    Log.i(TAG, "Loaded DialogueNuances from prefs: $json")
                    return Gson().fromJson(json, DialogueNuances::class.java)
                }

                Log.w(TAG, "No DialogueNuances found in prefs, returning default.")
                return DialogueNuances()

            } catch (e: Exception) {
                Log.e(TAG, "Error loading DialogueNuances from prefs", e)
                return DialogueNuances() // fallback
            }
        }

        fun resetToDefaults(context: Context) {
            Log.i(TAG, "Resetting DialogueNuances to defaults.")
            DialogueNuances().saveToPrefs(context)
        }
    }
}