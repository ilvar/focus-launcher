package pw.rkd.launcher.ui.settings

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pw.rkd.launcher.BlockActivity
import pw.rkd.launcher.Graph
import pw.rkd.launcher.ReviewActivity
import pw.rkd.launcher.data.AppCategory
import pw.rkd.launcher.data.AppEntry
import pw.rkd.launcher.data.LimitSource
import pw.rkd.launcher.data.Settings
import pw.rkd.launcher.data.TimeFormat
import pw.rkd.launcher.data.WeekSummary
import pw.rkd.launcher.service.FocusAccessibilityService
import pw.rkd.launcher.service.WeeklyReview
import pw.rkd.launcher.ui.components.AppPickerDialog
import pw.rkd.launcher.ui.components.ChoiceDialog
import pw.rkd.launcher.ui.components.MultiChoiceDialog
import pw.rkd.launcher.ui.components.SettingRow
import pw.rkd.launcher.ui.components.ToggleRow
import pw.rkd.launcher.ui.drawer.TimerDialog
import pw.rkd.launcher.ui.home.currentLocale
import pw.rkd.launcher.util.formatHour
import pw.rkd.launcher.util.formatMinutes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** Timer settings change what the service should be doing right now, so poke it after each edit. */
private fun updateTimers(transform: (Settings) -> Settings) {
    Graph.settings.update(transform)
    FocusAccessibilityService.recheck()
}

private fun dailyLabel(minutes: Int) = if (minutes > 0) "${formatMinutes(minutes)} a day" else "Off"

// ---- App timers ------------------------------------------------------------------------------

private enum class TimerDialogKind { NONE, SOCIAL, GAMES, VIDEO, CONTINUE, PAUSE, WARN }

private val DEFAULT_CHOICES = listOf(0, 10, 15, 20, 30, 45, 60, 90, 120)

@Composable
internal fun TimersPage(settings: Settings, apps: List<AppEntry>, status: SetupStatus, onBack: () -> Unit, go: (String) -> Unit) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(TimerDialogKind.NONE) }
    val close = { dialog = TimerDialogKind.NONE }
    val on = settings.timersEnabled
    val limited = remember(apps, settings) { apps.map { it.packageName }.distinct().count { Graph.limits.limitFor(it, settings) != null } }

    Page("App timers", onBack) {
        ToggleRow(
            "App timers", on,
            subtitle = "Give distracting apps a daily allowance. When it is used up, Rkd Launcher locks the app for the rest of the day.",
        ) { v -> updateTimers { it.copy(timersEnabled = v) } }

        if (on && !(status.usageAccess && status.timerService)) {
            Note(
                when {
                    !status.usageAccess -> "Timers cannot count anything until usage access is allowed.  Open setup  →"
                    else -> "The Rkd Launcher timer service is off: apps are only locked when opened from Rkd Launcher, not while you are inside them.  Open setup  →"
                },
            ) { go(Routes.SETUP) }
        }

        Section("Automatic limits")
        SettingRow("Social media", subtitle = "Feeds, social networks and dating apps. Not mail, browsers or messengers.", value = dailyLabel(settings.socialDefaultMin), enabled = on, onClick = { dialog = TimerDialogKind.SOCIAL })
        SettingRow("Games", subtitle = "Every app the Play Store lists as a game.", value = dailyLabel(settings.gameDefaultMin), enabled = on, onClick = { dialog = TimerDialogKind.GAMES })
        SettingRow("Video & streaming", subtitle = "YouTube, Netflix and other video apps.", value = dailyLabel(settings.videoDefaultMin), enabled = on, onClick = { dialog = TimerDialogKind.VIDEO })
        SettingRow("Limited apps", subtitle = "See exactly which apps these cover, change one, or add your own.", value = limited.toString(), enabled = on, onClick = { go(Routes.TIMER_APPS) })
        Note("An automatic limit only applies to apps without a limit of their own. Browsers, mail, messengers and other tools are never limited unless you add a timer yourself.")

        Section("When time is up")
        ToggleRow("Allow “continue for a few minutes”", settings.allowContinue, enabled = on) { v -> updateTimers { it.copy(allowContinue = v) } }
        SettingRow(
            "Continue options",
            value = settings.continueOptions.sorted().joinToString(", ") + " min",
            enabled = on && settings.allowContinue,
            onClick = { dialog = TimerDialogKind.CONTINUE },
        )
        ToggleRow(
            "Allow “ignore the limit for today”", settings.allowBypass, enabled = on,
            subtitle = if (!settings.allowContinue && !settings.allowBypass) "Strict mode: with both off, a locked app stays locked until midnight." else null,
        ) { v -> updateTimers { it.copy(allowBypass = v) } }
        ToggleRow(
            "Ask before every open after ignoring", settings.askAfterBypass, enabled = on && settings.allowBypass,
            subtitle = "Ignoring a limit does not make the app free for the day: Rkd Launcher still asks “open anyway?” each time.",
        ) { v -> updateTimers { it.copy(askAfterBypass = v) } }
        SettingRow(
            "Pause before continuing",
            subtitle = "A short wait before the continue choices unlock. Time to change your mind.",
            value = if (settings.frictionSeconds > 0) "${settings.frictionSeconds} s" else "None",
            enabled = on, onClick = { dialog = TimerDialogKind.PAUSE },
        )
        SettingRow(
            "Heads-up before the limit",
            value = if (settings.warnMinutes > 0) formatMinutes(settings.warnMinutes) else "Off",
            enabled = on, onClick = { dialog = TimerDialogKind.WARN },
        )

        Section("")
        SettingRow("Preview the lock screen", onClick = {
            context.startActivity(
                BlockActivity.intent(context, context.packageName, usedMs = 31 * 60_000L, limitMinutes = 30, midSession = false, preview = true),
            )
        })
    }

    when (dialog) {
        TimerDialogKind.NONE -> Unit
        TimerDialogKind.SOCIAL -> ChoiceDialog(
            "Social media", DEFAULT_CHOICES.map { it to dailyLabel(it) }, settings.socialDefaultMin, close,
            subtitle = "Instagram, X, Reddit, Snapchat, LinkedIn, dating apps and the like.",
        ) { v -> updateTimers { it.copy(socialDefaultMin = v) } }
        TimerDialogKind.VIDEO -> ChoiceDialog(
            "Video & streaming", DEFAULT_CHOICES.map { it to dailyLabel(it) }, settings.videoDefaultMin, close,
            subtitle = "YouTube, Netflix, Prime Video and anything else Android lists as a video app.",
        ) { v -> updateTimers { it.copy(videoDefaultMin = v) } }
        TimerDialogKind.GAMES -> ChoiceDialog(
            "Games", DEFAULT_CHOICES.map { it to dailyLabel(it) }, settings.gameDefaultMin, close,
            subtitle = "Every app Android lists as a game.",
        ) { v -> updateTimers { it.copy(gameDefaultMin = v) } }
        TimerDialogKind.CONTINUE -> MultiChoiceDialog(
            "Continue options", listOf(1, 2, 5, 10, 15, 20, 30).map { it to "$it min" }, settings.continueOptions.toSet(), close,
            subtitle = "Up to four are shown on the lock screen.",
        ) { picked -> updateTimers { it.copy(continueOptions = picked.sorted().take(4).ifEmpty { listOf(5) }) } }
        TimerDialogKind.PAUSE -> ChoiceDialog(
            "Pause before continuing", listOf(0, 3, 5, 10, 20, 30, 60).map { it to if (it == 0) "None" else "$it seconds" },
            settings.frictionSeconds, close,
        ) { v -> updateTimers { it.copy(frictionSeconds = v) } }
        TimerDialogKind.WARN -> ChoiceDialog(
            "Heads-up before the limit", listOf(0, 1, 2, 5, 10).map { it to if (it == 0) "Off" else "${formatMinutes(it)} before" },
            settings.warnMinutes, close,
        ) { v -> updateTimers { it.copy(warnMinutes = v) } }
    }
}

@Composable
internal fun TimerAppsPage(settings: Settings, apps: List<AppEntry>, onBack: () -> Unit, go: (String) -> Unit) {
    val today by Graph.usage.today.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AppEntry?>(null) }
    var adding by remember { mutableStateOf(false) }

    // One row per package, even when an app has several launcher entries or a work-profile twin.
    val packages = remember(apps) { apps.distinctBy { it.packageName } }
    val limited = remember(packages, settings) {
        packages.mapNotNull { app -> Graph.limits.limitFor(app.packageName, settings)?.let { app to it } }
    }
    val exempt = remember(packages, settings) {
        packages.filter { settings.appLimits[it.packageName] == 0 && Graph.limits.categoryDefaultFor(it.packageName, settings) != null }
    }
    // Android calls these "social", but that label also covers tools. Focus will not guess.
    val unsure = remember(packages, settings) {
        packages.filter { it.category == AppCategory.UNSURE && it.packageName !in settings.appLimits && Graph.apps.canLimit(it.packageName) }
    }

    Page("Limited apps", onBack) {
        SettingRow("Add a timer", subtitle = "Any app can have one, not just social apps and games.", onClick = { adding = true })

        if (limited.isNotEmpty()) Section("With a daily limit")
        for ((app, limit) in limited) {
            SettingRow(
                app.label,
                subtitle = when (limit.source) {
                    LimitSource.APP -> "Own limit"
                    LimitSource.SOCIAL_DEFAULT -> "Social media, automatic"
                    LimitSource.GAME_DEFAULT -> "Game, automatic"
                    LimitSource.VIDEO_DEFAULT -> "Video, automatic"
                },
                value = formatMinutes(limit.minutes),
                onClick = { editing = app },
            )
        }
        if (limited.isEmpty()) {
            Note("No app is limited right now. Add a timer above, or set automatic limits for social apps and games on the previous page.")
        }

        if (exempt.isNotEmpty()) {
            Section("Excluded from the automatic limits")
            for (app in exempt) SettingRow(app.label, value = "No limit", onClick = { editing = app })
        }

        if (unsure.isNotEmpty()) {
            Section("Not sure about these")
            Note("Android files these under “social”, a label it also gives to mail, browsers and messengers. Rkd Launcher leaves them alone; tap one to give it a timer.")
            for (app in unsure) SettingRow(app.label, value = "Add", onClick = { editing = app })
        }
    }

    editing?.let { app ->
        TimerDialog(
            packageName = app.packageName,
            label = app.label,
            settings = settings,
            usedToday = today?.perApp?.get(app.packageName),
            onDismiss = { editing = null },
            onOpenSetup = { go(Routes.SETUP) },
        )
    }
    if (adding) {
        AppPickerDialog(
            title = "Add a timer",
            apps = packages.filter { Graph.apps.canLimit(it.packageName) },
            onDismiss = { adding = false },
            onPick = { editing = it },
        )
    }
}

// ---- Weekly review ---------------------------------------------------------------------------

private enum class WeeklyDialog { NONE, WEEK_START, HOUR }

@Composable
internal fun WeeklyPage(settings: Settings, status: SetupStatus, onBack: () -> Unit, go: (String) -> Unit) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(WeeklyDialog.NONE) }
    val close = { dialog = WeeklyDialog.NONE }
    val on = settings.weeklyEnabled
    val use24h = when (settings.timeFormat) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
    }
    val lastDay = (if (settings.weekStartsMonday) DayOfWeek.SUNDAY else DayOfWeek.SATURDAY)
        .getDisplayName(TextStyle.FULL, currentLocale())

    fun change(transform: (Settings) -> Settings) {
        Graph.settings.update(transform)
        WeeklyReview.schedule(context)
    }

    Page("Weekly review", onBack) {
        ToggleRow(
            "Weekly review", on,
            subtitle = "At the end of each week Rkd Launcher adds it all up: total time, which apps, which hours of the day, " +
                "how often you went past a limit. Then it asks what you want to change.",
        ) { v -> change { it.copy(weeklyEnabled = v) } }

        Section("Schedule")
        SettingRow("Week starts on", value = if (settings.weekStartsMonday) "Monday" else "Sunday", onClick = { dialog = WeeklyDialog.WEEK_START })
        SettingRow("Review is ready", subtitle = "A line on the home screen, plus a notification if allowed.", value = "$lastDay, ${formatHour(settings.reviewHour, use24h)}", enabled = on, onClick = { dialog = WeeklyDialog.HOUR })
        if (on && !status.notifications) {
            Note("Notifications are off, so the reminder will only show on the home screen.  Open setup  →") { go(Routes.SETUP) }
        }

        Section("")
        SettingRow("Open this week so far", onClick = {
            context.startActivity(ReviewActivity.intent(context, WeekSummary.weekStartOf(LocalDate.now(), settings.weekStartsMonday)))
        })
    }

    when (dialog) {
        WeeklyDialog.NONE -> Unit
        WeeklyDialog.WEEK_START -> ChoiceDialog("Week starts on", listOf(true to "Monday", false to "Sunday"), settings.weekStartsMonday, close) { v ->
            change { it.copy(weekStartsMonday = v) }
        }
        WeeklyDialog.HOUR -> ChoiceDialog(
            "Review is ready at", (6..23).map { it to formatHour(it, use24h) }, settings.reviewHour, close,
            subtitle = "On $lastDay, the last day of your week.",
        ) { v -> change { it.copy(reviewHour = v) } }
    }
}
