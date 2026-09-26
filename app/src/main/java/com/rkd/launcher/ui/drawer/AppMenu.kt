package com.rkd.launcher.ui.drawer

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rkd.launcher.Graph
import com.rkd.launcher.data.AppEntry
import com.rkd.launcher.data.DayUsage
import com.rkd.launcher.data.LimitSource
import com.rkd.launcher.data.MAX_FAVORITES
import com.rkd.launcher.data.Settings
import com.rkd.launcher.service.FocusAccessibilityService
import com.rkd.launcher.ui.components.ChoiceDialog
import com.rkd.launcher.ui.components.FocusDialog
import com.rkd.launcher.ui.components.Hairline
import com.rkd.launcher.ui.components.MenuRow
import com.rkd.launcher.ui.components.T
import com.rkd.launcher.ui.components.TextInputDialog
import com.rkd.launcher.ui.theme.LocalFocusColors
import com.rkd.launcher.util.Perms
import com.rkd.launcher.util.formatDuration
import com.rkd.launcher.util.formatMinutes

private enum class Sub { NONE, TIMER, RENAME, REPLACE }

/** The long-press menu of an app, on the home screen and in the drawer alike. */
@Composable
fun AppMenu(
    app: AppEntry,
    settings: Settings,
    today: DayUsage?,
    onDismiss: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val context = LocalContext.current
    var sub by remember { mutableStateOf(Sub.NONE) }

    val installed by Graph.apps.apps.collectAsStateWithLifecycle()
    val favorites = settings.favorites.filter { key -> installed.any { it.key == key } }
    val isFavorite = app.key in favorites
    val isHidden = app.key in settings.hidden
    val isPinned = app.key in settings.pinned
    val limit = Graph.limits.limitFor(app.packageName, settings)
    val used = today?.perApp?.get(app.packageName)

    when (sub) {
        Sub.NONE -> {
            val status = buildList {
                if (app.isRenamed) add(app.systemLabel)
                if (used != null && used > 0) add("${formatDuration(used)} today")
                if (limit != null) add("limit ${formatMinutes(limit.minutes)}")
            }.joinToString("  ·  ").ifEmpty { null }

            FocusDialog(onDismiss, title = app.label, subtitle = status) {
                if (!app.isSystem) {
                    MenuRow("Uninstall") {
                        Graph.apps.uninstall(app)
                        onDismiss()
                    }
                }
                MenuRow("App info") {
                    Graph.apps.openAppInfo(app)
                    onDismiss()
                }
                MenuRow(
                    if (isFavorite) "Remove from fast apps" else "Move to fast apps",
                    detail = "${favorites.size} / $MAX_FAVORITES",
                ) {
                    when {
                        isFavorite -> {
                            Graph.settings.update { it.copy(favorites = favorites - app.key) }
                            onDismiss()
                        }
                        favorites.size >= MAX_FAVORITES -> sub = Sub.REPLACE
                        else -> {
                            Graph.settings.update { it.copy(favorites = favorites + app.key) }
                            Toast.makeText(context, "${app.label} is on your home screen", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                }
                MenuRow(if (isPinned) "Unpin from top" else "Pin to top") {
                    Graph.settings.update { s -> s.copy(pinned = if (isPinned) s.pinned - app.key else s.pinned + app.key) }
                    onDismiss()
                }
                if (Graph.apps.canLimit(app.packageName)) {
                    MenuRow("App timer", detail = limit?.let { formatMinutes(it.minutes) + " a day" } ?: "Off") { sub = Sub.TIMER }
                }
                MenuRow("Rename") { sub = Sub.RENAME }
                MenuRow(if (isHidden) "Unhide app" else "Hide app") {
                    Graph.settings.update { s -> s.copy(hidden = if (isHidden) s.hidden - app.key else s.hidden + app.key) }
                    if (!isHidden) {
                        Toast.makeText(context, "Hidden. Bring it back from Settings → App drawer", Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                }
            }
        }

        Sub.TIMER -> TimerDialog(app.packageName, app.label, settings, used, onDismiss, onOpenSetup)

        Sub.RENAME -> TextInputDialog(
            title = "Rename",
            subtitle = "Only changes the name inside Rkd Launcher. Leave empty to restore “${app.systemLabel}”.",
            initial = app.label,
            placeholder = app.systemLabel,
            onDismiss = onDismiss,
        ) { name ->
            Graph.settings.update { s ->
                s.copy(renames = if (name.isEmpty() || name == app.systemLabel) s.renames - app.key else s.renames + (app.key to name))
            }
        }

        Sub.REPLACE -> ChoiceDialog(
            title = "Fast apps are full",
            subtitle = "Choose the one ${app.label} should replace.",
            options = favorites.map { key -> key to (installed.firstOrNull { it.key == key }?.label ?: key) },
            selected = null,
            onDismiss = onDismiss,
        ) { replaced ->
            Graph.settings.update { it.copy(favorites = favorites.map { key -> if (key == replaced) app.key else key }) }
        }
    }
}

private val PRESETS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180)

/** Daily time limit for one app. Reused by the long-press menu and by Settings → App timers. */
@Composable
fun TimerDialog(
    packageName: String,
    label: String,
    settings: Settings,
    usedToday: Long?,
    onDismiss: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    var custom by remember { mutableStateOf(false) }

    val own = settings.appLimits[packageName]
    val fallback = Graph.limits.categoryDefaultFor(packageName, settings)
    val ready = Perms.hasUsageAccess() && Perms.isTimerServiceEnabled(context)

    fun apply(minutes: Int?) {
        Graph.settings.update { s ->
            s.copy(appLimits = if (minutes == null) s.appLimits - packageName else s.appLimits + (packageName to minutes))
        }
        // A new limit should bite immediately, not after an old "continue" window runs out.
        Graph.limits.clearPasses(packageName)
        FocusAccessibilityService.recheck()
        onDismiss()
    }

    if (custom) {
        TextInputDialog(
            title = "Custom limit",
            subtitle = "Minutes per day for $label.",
            initial = own?.takeIf { it > 0 }?.toString().orEmpty(),
            placeholder = "Minutes",
            numeric = true,
            confirmLabel = "Set",
            onDismiss = onDismiss,
        ) { text -> text.toIntOrNull()?.takeIf { it in 1..1440 }?.let { apply(it) } }
        return
    }

    val subtitle = buildString {
        if (usedToday != null) append("Used ${formatDuration(usedToday)} today. ")
        append(Graph.apps.describeCategory(packageName))
    }
    FocusDialog(onDismiss, title = "Timer · $label", subtitle = subtitle) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (fallback != null) {
                val kind = when (fallback.source) {
                    LimitSource.GAME_DEFAULT -> "games"
                    LimitSource.VIDEO_DEFAULT -> "video apps"
                    else -> "social media"
                }
                MenuRow("Default for $kind", detail = formatMinutes(fallback.minutes), selected = own == null) { apply(null) }
            }
            MenuRow("No limit", selected = own == 0 || (own == null && fallback == null)) {
                apply(if (fallback != null) 0 else null)
            }
            for (minutes in PRESETS) {
                MenuRow(formatMinutes(minutes), selected = own == minutes) { apply(minutes) }
            }
            val isCustom = own != null && own > 0 && own !in PRESETS
            MenuRow("Custom…", detail = if (isCustom) formatMinutes(own) else null, selected = isCustom) { custom = true }
        }
        if (!ready) {
            Hairline()
            T(
                "Timers only lock apps once usage access and the Rkd Launcher timer service are on.  Finish setup  →",
                Modifier.clickable {
                    onDismiss()
                    onOpenSetup()
                }.padding(horizontal = 24.dp, vertical = 14.dp),
                size = 13.sp, color = c.dim, lineHeight = 19.sp,
            )
        }
    }
}
