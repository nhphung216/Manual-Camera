package com.ssolstice.camera.manual.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class UpdatePromptManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEY_LAST_SHOWN_DATE = "last_shown_date"
        private const val KEY_OPEN_COUNT = "open_count"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    fun shouldShowPrompt(minOpensPerDay: Int = 3): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(KEY_LAST_SHOWN_DATE, "") ?: ""
        val count = prefs.getInt(KEY_OPEN_COUNT, 0)

        return if (lastDate == today) {
            count >= minOpensPerDay
        } else {
            savePromptState(today, 1)
            false
        }
    }

    fun increaseOpenCount() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(KEY_LAST_SHOWN_DATE, "") ?: ""

        if (lastDate == today) {
            val currentCount = prefs.getInt(KEY_OPEN_COUNT, 0)
            savePromptState(today, currentCount + 1)
        } else {
            savePromptState(today, 1)
        }
    }

    private fun savePromptState(date: String, count: Int) {
        prefs.edit { putString(KEY_LAST_SHOWN_DATE, date).putInt(KEY_OPEN_COUNT, count) }
    }
}
