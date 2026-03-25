package com.ricelab.cairclient.maritime_experiment

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object MaritimeExperimentManager {

    private const val PREFS_NAME = "maritime_experiment_prefs"
    private const val KEY_LAST_DATE = "last_date"
    private const val KEY_TODAY_CONDITION_ID = "today_condition_id"
    private const val KEY_REMAINING_IDS_JSON = "remaining_ids_json"

    fun getTodayConfig(context: Context): MaritimeExperimentConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val savedDate = prefs.getString(KEY_LAST_DATE, null)
        val savedConditionId = prefs.getInt(KEY_TODAY_CONDITION_ID, -1)

        if (savedDate == today && savedConditionId != -1) {
            return MaritimeExperimentResolver.fromId(savedConditionId)
        }

        val remaining = loadRemainingIds(prefs).toMutableList()
        if (remaining.isEmpty()) {
            remaining.addAll(listOf(1, 2, 3, 4, 5, 6))
        }

        val pickedIndex = Random.nextInt(remaining.size)
        val pickedId = remaining.removeAt(pickedIndex)

        prefs.edit {
            putString(KEY_LAST_DATE, today)
            putInt(KEY_TODAY_CONDITION_ID, pickedId)
            putString(KEY_REMAINING_IDS_JSON, Gson().toJson(remaining))
        }

        return MaritimeExperimentResolver.fromId(pickedId)
    }

    private fun loadRemainingIds(prefs: SharedPreferences): List<Int> {
        val json = prefs.getString(KEY_REMAINING_IDS_JSON, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Int>>() {}.type
            Gson().fromJson<List<Int>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}