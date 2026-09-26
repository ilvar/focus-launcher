package pw.rkd.launcher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import pw.rkd.launcher.Graph
import pw.rkd.launcher.data.AppEntry
import pw.rkd.launcher.data.Settings
import pw.rkd.launcher.data.TAP_ALARMS
import pw.rkd.launcher.data.TAP_BATTERY
import pw.rkd.launcher.data.TAP_CALENDAR
import pw.rkd.launcher.data.TAP_NOTHING
import pw.rkd.launcher.data.TAP_SCREEN_TIME
import pw.rkd.launcher.ui.components.AppPickerDialog
import pw.rkd.launcher.ui.components.FocusDialog
import pw.rkd.launcher.ui.components.MenuRow

/**
 * Chooses what a tap on the clock does. Reached by long-pressing the clock on the home screen
 * (the same gesture that customises the corner shortcuts) and from Settings → Home screen.
 */
@Composable
fun ClockTapDialog(settings: Settings, apps: List<AppEntry>, onDismiss: () -> Unit) {
    var pickingApp by remember { mutableStateOf(false) }
    val set: (String) -> Unit = { spec -> Graph.settings.update { it.copy(clockTap = spec) } }

    if (pickingApp) {
        AppPickerDialog(
            title = "Tap on the clock opens",
            apps = apps.filter { it.key !in settings.hidden },
            onDismiss = onDismiss,
            onPick = { set(it.key) },
        )
        return
    }

    val opensApp = !settings.clockTap.startsWith("tap:")
    FocusDialog(onDismiss, title = "Tap on the clock", subtitle = "Long-press the clock any time to change this.") {
        MenuRow("Open an app…", detail = if (opensApp) clockTapLabel(settings.clockTap, apps) else null, selected = opensApp) {
            pickingApp = true
        }
        for (spec in listOf(TAP_ALARMS, TAP_CALENDAR, TAP_SCREEN_TIME, TAP_BATTERY, TAP_NOTHING)) {
            MenuRow(clockTapLabel(spec, apps), selected = settings.clockTap == spec) {
                set(spec)
                onDismiss()
            }
        }
    }
}
