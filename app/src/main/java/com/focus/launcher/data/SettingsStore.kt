package com.focus.launcher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Single source of truth for [Settings]. The launcher UI, the settings screens and the
 * accessibility service all live in one process, so an in-memory StateFlow backed by
 * SharedPreferences keeps every one of them in sync without any IPC.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("focus_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())

    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val value: Settings get() = _flow.value

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_flow.value)
        if (next == _flow.value) return
        _flow.value = next
        prefs.edit { putString(KEY, next.toJson().toString()) }
    }

    /** Export only launcher preferences. System permissions and usage history are not part of this file. */
    fun exportBackup(): String = value.toJson().toString(2)

    /** Reject unrelated or newer files before replacing the current preferences. */
    fun parseBackup(raw: String): Settings? = try {
        val json = JSONObject(raw)
        if (json.optInt("v", -1) !in 1..Settings.SCHEMA || !json.has("favorites") || !json.has("dark")) null
        else Settings.fromJson(json)
    } catch (_: Exception) {
        null
    }

    @Synchronized
    fun restoreBackup(settings: Settings) {
        prefs.edit { putString(KEY, settings.toJson().toString()) }
        _flow.value = settings
    }

    private fun load(): Settings {
        val raw = prefs.getString(KEY, null) ?: return Settings()
        return try {
            Settings.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            Settings()
        }
    }

    private companion object {
        const val KEY = "settings_json"
    }
}
