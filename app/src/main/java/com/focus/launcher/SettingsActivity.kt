package com.focus.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focus.launcher.ui.settings.SettingsRoot
import com.focus.launcher.ui.theme.FocusTheme
import com.focus.launcher.ui.theme.applyFocusWindow

/**
 * All preferences. Reached by long-pressing the home screen, and listed in every app drawer as
 * "Focus Settings" so it can be found even when Focus is not the active launcher.
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startRoute = intent.getStringExtra(EXTRA_ROUTE)
        applyFocusWindow(Graph.settings.value.dark)
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle()
            LaunchedEffect(settings.dark) { applyFocusWindow(settings.dark) }
            FocusTheme(settings) {
                SettingsRoot(settings = settings, startRoute = startRoute, onExit = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_ROUTE = "route"

        fun intent(context: Context, route: String? = null): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_ROUTE, route)
    }
}
