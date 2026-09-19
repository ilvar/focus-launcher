package com.focus.launcher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** Small bits of launcher state that are not preferences: review reminders and reflection notes. */
class AppState(context: Context) {
    private val prefs = context.getSharedPreferences("focus_state", Context.MODE_PRIVATE)

    private val _pendingReview = MutableStateFlow(readDate(KEY_PENDING))
    /** Start date of a week whose review is waiting to be opened; shown as a line on the home screen. */
    val pendingReview: StateFlow<LocalDate?> = _pendingReview

    /** Start date of the most recent week the user has already been told about. */
    var lastAnnouncedWeek: LocalDate?
        get() = readDate(KEY_ANNOUNCED)
        set(value) = prefs.edit { putString(KEY_ANNOUNCED, value?.toString()) }

    fun setPendingReview(weekStart: LocalDate?) {
        prefs.edit { putString(KEY_PENDING, weekStart?.toString()) }
        _pendingReview.value = weekStart
    }

    /** The one-line intention the user wrote at the end of a weekly review. */
    fun intention(weekStart: LocalDate): String = prefs.getString("intention_$weekStart", "") ?: ""

    fun setIntention(weekStart: LocalDate, text: String) {
        prefs.edit { putString("intention_$weekStart", text.trim()) }
    }

    private fun readDate(key: String): LocalDate? = try {
        prefs.getString(key, null)?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val KEY_PENDING = "pending_review_week"
        const val KEY_ANNOUNCED = "last_announced_week"
    }
}
