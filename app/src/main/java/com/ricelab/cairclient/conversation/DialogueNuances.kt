package com.ricelab.cairclient.conversation

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.ricelab.cairclient.config.AppMode

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

    companion object {
        private const val PREFS_NAME = "secure_prefs"

        fun defaultFlags(): Map<String, List<Int>> = mapOf(
            "diversity" to listOf(0, 0, 0, 1),
            "time" to listOf(0, 0, 0, 1),
            "place" to listOf(0, 0, 0, 1),
            "tone" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 1),
            "positive_speech_act" to listOf(0, 0, 0, 1),
            "contextual_speech_act" to listOf(0, 0, 0, 0, 1)
        )

        fun defaultValues(): Map<String, List<String>> = mapOf(
            "diversity" to List(3) {"not relevant"},
            "time" to List(3) {"nothing specific"},
            "place" to List(3) {"nowhere specific"},
            "tone" to List(9) { "neutral" },
            "positive_speech_act" to listOf("assertive", "commissive", "expressive"),
            "contextual_speech_act" to listOf("assertive", "commissive", "expressive", "declarative")
        )

        private fun getPrefs(context: Context) =
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

        private fun prefsKeyForMode(appMode: AppMode): String {
            return "dialogue_nuances_${appMode.name.lowercase()}"
        }

        private fun assetPathForMode(appMode: AppMode): String {
            return when (appMode) {
                AppMode.DEFAULT -> "nuances/default_nuances.json"
                AppMode.DELIRIUM -> "nuances/delirium_nuances.json"
                AppMode.APATHY -> "nuances/apathy_nuances.json"
                AppMode.PARAPLEGIA -> "nuances/paraplegia_nuances.json"
                AppMode.MARITIME_STATION -> "nuances/maritime_nuances.json"
            }
        }

        private fun loadFromAsset(context: Context, assetPath: String): DialogueNuances {
            return try {
                val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                val nuances = Gson().fromJson(json, DialogueNuances::class.java)
                Log.i(TAG, "Loaded DialogueNuances from asset: $assetPath")
                nuances
            } catch (e: Exception) {
                Log.e(TAG, "Error loading DialogueNuances from asset $assetPath", e)
                DialogueNuances()
            }
        }

        fun loadDefaultForMode(context: Context, appMode: AppMode): DialogueNuances {
            return loadFromAsset(context, assetPathForMode(appMode))
        }

        fun loadForMode(context: Context, appMode: AppMode): DialogueNuances {
            return try {
                val prefs = getPrefs(context)
                val key = prefsKeyForMode(appMode)
                val json = prefs.getString(key, null)

                if (!json.isNullOrBlank()) {
                    Log.i(TAG, "Loaded DialogueNuances from prefs for mode $appMode")
                    Gson().fromJson(json, DialogueNuances::class.java)
                } else {
                    loadFromAsset(context, assetPathForMode(appMode))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading DialogueNuances for mode $appMode", e)
                loadFromAsset(context, assetPathForMode(appMode))
            }
        }

        fun saveForMode(context: Context, appMode: AppMode, nuances: DialogueNuances) {
            try {
                val prefs = getPrefs(context)
                val json = Gson().toJson(nuances)
                prefs.edit {
                    putString(prefsKeyForMode(appMode), json)
                }
                Log.i(TAG, "DialogueNuances saved for mode $appMode: $json")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving DialogueNuances for mode $appMode", e)
            }
        }

        fun resetForMode(context: Context, appMode: AppMode) {
            try {
                val prefs = getPrefs(context)
                prefs.edit {
                    remove(prefsKeyForMode(appMode))
                }
                Log.i(TAG, "DialogueNuances reset for mode $appMode")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting DialogueNuances for mode $appMode", e)
            }
        }
    }
}