package com.focus.launcher.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.focus.launcher.Graph
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.CalEvent
import com.focus.launcher.data.CalendarInfo
import com.focus.launcher.data.CalendarRepository
import com.focus.launcher.data.ClockStyle
import com.focus.launcher.data.DayUsage
import com.focus.launcher.data.SHORTCUT_CAMERA
import com.focus.launcher.data.SHORTCUT_PHONE
import com.focus.launcher.data.Settings
import com.focus.launcher.data.TAP_ALARMS
import com.focus.launcher.data.TAP_BATTERY
import com.focus.launcher.data.TAP_CALENDAR
import com.focus.launcher.data.TAP_NOTHING
import com.focus.launcher.data.TAP_SCREEN_TIME
import com.focus.launcher.data.TimeFormat
import com.focus.launcher.service.FocusAccessibilityService
import com.focus.launcher.ui.components.AppPickerDialog
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.VSpace
import com.focus.launcher.ui.components.WorkBadge
import com.focus.launcher.ui.components.hasColourGlyphs
import com.focus.launcher.ui.components.monochrome
import com.focus.launcher.ui.components.press
import com.focus.launcher.ui.launchOptions
import com.focus.launcher.ui.theme.LocalFocusColors
import com.focus.launcher.util.Perms
import java.time.LocalDate

/**
 * Page one of the launcher. Top to bottom: the clock, the optional screen-time and calendar
 * sections, up to five fast apps, and the two corner shortcuts. Text only.
 *
 * Gestures on empty space: long-press opens settings, swipe down pulls the notification shade,
 * swipe up jumps to search, double-tap locks the phone. Swiping left (handled by the pager that
 * hosts this page) opens the app drawer.
 */
@Composable
fun HomeScreen(
    settings: Settings,
    apps: List<AppEntry>,
    today: DayUsage?,
    usageAccess: Boolean,
    setupIncomplete: Boolean,
    pendingReview: LocalDate?,
    resumeCount: Int,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onOpenSettings: (route: String?) -> Unit,
    onOpenReview: (week: LocalDate?) -> Unit,
) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val now by rememberNow()
    // The gesture detectors below outlive recompositions, so they must call the latest callbacks.
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val openSettings by rememberUpdatedState(onOpenSettings)

    // Calendar section data
    var calendarAccess by remember { mutableStateOf(CalendarRepository.hasAccess(context)) }
    var events by remember { mutableStateOf(emptyList<CalEvent>()) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarAccess = it }
    var shownCalendar by remember { mutableStateOf<CalendarInfo?>(null) }
    LaunchedEffect(settings.showCalendar, settings.calendarKey, resumeCount, calendarAccess) {
        if (settings.showCalendar) {
            calendarAccess = CalendarRepository.hasAccess(context)
            // Exactly one calendar is shown: the chosen one, else the main one with events coming up.
            val agenda = CalendarRepository.agenda(context, settings.calendarKey)
            shownCalendar = agenda.calendar
            events = agenda.events
        }
    }
    // The agenda is cached; the calendar provider tells us when that cache is no longer true.
    // The observer does no work itself, so a sync in the background costs nothing here.
    DisposableEffect(settings.showCalendar, calendarAccess) {
        if (!settings.showCalendar || !calendarAccess) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = CalendarRepository.invalidate()
        }
        val registered = try {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            true
        } catch (_: Exception) {
            false
        }
        onDispose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }

    var editingShortcut by remember { mutableStateOf<Boolean?>(null) } // true = left, false = right
    var choosingClockTap by remember { mutableStateOf(false) }

    val favorites = remember(settings.favorites, apps) { settings.favorites.mapNotNull { key -> apps.firstOrNull { it.key == key } } }
    // Fast apps whose allowance for today is gone are shown dimmed.
    val spent = remember(favorites, today, settings.appLimits, settings.timersEnabled, settings.socialDefaultMin, settings.gameDefaultMin) {
        favorites.filter { app ->
            val limit = Graph.limits.limitFor(app.packageName, settings) ?: return@filter false
            (today?.perApp?.get(app.packageName) ?: 0L) >= limit.millis && !Graph.limits.hasFreePass(app.packageName)
        }.mapTo(HashSet()) { it.key }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(settings.swipeDownNotifications, settings.swipeUpSearch) {
                val threshold = 64.dp.toPx()
                var dragged = 0f
                var fired = false
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f; fired = false },
                    onVerticalDrag = { _, dy ->
                        dragged += dy
                        if (!fired && dragged > threshold && settings.swipeDownNotifications) {
                            fired = true
                            expandNotifications(context)
                        } else if (!fired && dragged < -threshold && settings.swipeUpSearch) {
                            fired = true
                            openDrawer(true)
                        }
                    },
                )
            }
            .pointerInput(settings.doubleTapLock) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        openSettings(null)
                    },
                    onDoubleTap = if (settings.doubleTapLock) {
                        {
                            if (!FocusAccessibilityService.lockScreen()) {
                                Toast.makeText(context, "Turn on the Focus timer service to lock with a double tap", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else null,
                )
            },
    ) {
        val sections = (if (settings.showScreenTime) 1 else 0) + (if (settings.showCalendar) 1 else 0)

        // A home screen must never scroll or push its corner shortcuts off the edge. Estimate the
        // height each arrangement needs (constants measured on a real screen) and take the
        // roomiest one that fits, whatever the phone, text size, fast apps and enabled sections.
        // Detail is given up before the clock is: first fewer events and no app names under the
        // bar, and only then a smaller ring.
        val bars = WindowInsets.systemBars.asPaddingValues()
        val available = (maxHeight - bars.calculateTopPadding() - bars.calculateBottomPadding()).value
        val textScale = LocalDensity.current.fontScale * settings.textScale
        val notices = (if (setupIncomplete) 1 else 0) + (if (pendingReview != null) 1 else 0)
        val rowCount = favorites.size.coerceAtLeast(2) // the empty-state hint is two lines tall
        val eventCount = events.size
        val preferredRing = when (sections) {
            2 -> maxHeight * 0.22f
            1 -> maxHeight * 0.25f
            else -> maxHeight * 0.29f
        }.coerceIn(140.dp, 236.dp)

        class Fit(val ring: Dp, val textSp: Float, val padDp: Float, val maxEvents: Int, val topApps: Boolean)

        fun heightOf(fit: Fit): Float {
            val clock = if (settings.clockStyle == ClockStyle.RING) fit.ring.value else 100f * textScale
            val noticeLines = if (notices > 0) 14f + notices * (19f * textScale + 12f) else 0f
            val screenTime = if (settings.showScreenTime) 45f + (if (fit.topApps) 50f else 25f) * textScale else 0f
            val shownEvents = fit.maxEvents.coerceAtMost(eventCount).coerceAtLeast(1)
            val strip = if (settings.showWeekStrip) 46f + 14f * textScale else 0f
            val calendar = if (settings.showCalendar) 22f + 14f * textScale + strip + shownEvents * (18f * textScale + 6f) else 0f
            val gap = if (sections == 2) 16f else 0f
            val shortcuts = if (settings.showShortcuts) 28f + 20f * textScale else 20f
            val rows = rowCount * (fit.textSp * 1.2f * textScale + fit.padDp * 2)
            return clock + noticeLines + screenTime + calendar + gap + rows + shortcuts + 6f + 36f // 36 = air
        }

        val options = listOf(
            Fit(preferredRing, 26f, 10f, maxEvents = 3, topApps = true),
            Fit(preferredRing, 24f, 8f, maxEvents = 3, topApps = true),
            Fit(preferredRing, 24f, 7f, maxEvents = 2, topApps = false),
            Fit(preferredRing * 0.88f, 22f, 6f, maxEvents = 2, topApps = false),
            Fit(132.dp, 22f, 4f, maxEvents = 1, topApps = false),
        )
        val fit = options.firstOrNull { heightOf(it) <= available } ?: options.last()
        val ring = fit.ring.coerceAtLeast(132.dp)
        val favoriteSize = fit.textSp.sp
        val favoritePadding = fit.padDp.dp

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp),
            horizontalAlignment = settings.homeAlign.horizontal(),
        ) {
            Spacer(Modifier.weight(if (sections == 2) 0.5f else 0.9f))

            HomeClock(
                settings = settings,
                now = now,
                ringSize = ring,
                onTap = { performClockTap(context, settings.clockTap, apps, onLaunch) { onOpenReview(null) } },
                onLongPress = { choosingClockTap = true },
                modifier = if (settings.clockStyle == ClockStyle.RING) Modifier.align(Alignment.CenterHorizontally) else Modifier,
            )

            // One-line notices. They disappear as soon as they have been dealt with.
            if (setupIncomplete || pendingReview != null) {
                VSpace(14.dp)
                if (pendingReview != null) {
                    Notice("Your weekly review is ready  →", strong = true) { onOpenReview(pendingReview) }
                }
                if (setupIncomplete) {
                    Notice("Finish setting up Focus  →", strong = false) { onOpenSettings("setup") }
                }
            }

            Spacer(Modifier.weight(0.8f))

            if (settings.showScreenTime) {
                ScreenTimeWidget(
                    today = today,
                    hasAccess = usageAccess,
                    currentHour = now.hour,
                    labelOf = { Graph.apps.labelForPackage(it) },
                    onClick = { if (usageAccess) onOpenReview(null) else Perms.openUsageAccess(context) },
                    showTopApps = fit.topApps,
                )
            }
            if (sections == 2) VSpace(16.dp)
            if (settings.showCalendar) {
                val use24h = when (settings.timeFormat) {
                    TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
                    TimeFormat.H24 -> true
                    TimeFormat.H12 -> false
                }
                CalendarWidget(
                    today = now.toLocalDate(),
                    mondayStart = settings.weekStartsMonday,
                    events = events,
                    hasAccess = calendarAccess,
                    use24h = use24h,
                    onClick = { openCalendarApp(context) },
                    onRequestAccess = { askCalendar.launch(Manifest.permission.READ_CALENDAR) },
                    maxEvents = fit.maxEvents,
                    calendarName = shownCalendar?.shortName,
                    showWeekStrip = settings.showWeekStrip,
                )
            }

            Spacer(Modifier.weight(if (sections == 0) 0.6f else 0.9f))

            // Fast apps
            if (favorites.isEmpty()) {
                T(
                    "Swipe left for your apps.\nLong-press one to pin it here.",
                    Modifier.fillMaxWidth().clickable { onOpenDrawer(false) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    size = 15.sp, color = c.dim, align = settings.homeAlign.text(), lineHeight = 23.sp,
                )
            } else {
                for (app in favorites) {
                    Row(
                        Modifier
                            .press(onLongClick = { onAppMenu(app) }) { onLaunch(app) }
                            .padding(vertical = favoritePadding, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(
                            app.label,
                            Modifier.weight(1f, fill = false).then(if (hasColourGlyphs(app.label)) Modifier.monochrome() else Modifier),
                            size = favoriteSize,
                            color = if (app.key in spent) c.faint else c.fg,
                            weight = FontWeight.Normal,
                            maxLines = 1,
                        )
                        // Same marker as in the drawer, sized with the name next to it.
                        if (app.isWorkProfile) WorkBadge(Modifier.padding(start = 10.dp), side = (favoriteSize.value * 0.62f).dp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Corner shortcuts
            if (settings.showShortcuts) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (left in listOf(true, false)) {
                        val spec = if (left) settings.leftShortcut else settings.rightShortcut
                        T(
                            shortcutLabel(spec, apps),
                            Modifier
                                .press(onLongClick = { editingShortcut = left }) { launchShortcut(context, spec, apps, onLaunch) }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            size = 15.sp, color = c.dim, maxLines = 1,
                        )
                    }
                }
            } else {
                VSpace(20.dp)
            }
            VSpace(6.dp)
        }
    }

    if (choosingClockTap) ClockTapDialog(settings, apps) { choosingClockTap = false }

    editingShortcut?.let { left ->
        val visible = remember(apps, settings.hidden) { apps.filter { it.key !in settings.hidden } }
        AppPickerDialog(
            title = if (left) "Left shortcut" else "Right shortcut",
            subtitle = "Opens from the bottom corner of the home screen.",
            apps = visible,
            onDismiss = { editingShortcut = null },
            leading = listOf(
                "Phone" to { setShortcut(left, SHORTCUT_PHONE) },
                "Camera" to { setShortcut(left, SHORTCUT_CAMERA) },
            ),
            onPick = { setShortcut(left, it.key) },
        )
    }
}

@Composable
private fun Notice(text: String, strong: Boolean, onClick: () -> Unit) {
    val c = LocalFocusColors.current
    T(
        text,
        Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        size = 14.sp,
        color = if (strong) c.fg else c.dim,
        weight = if (strong) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
    )
}

private fun setShortcut(left: Boolean, spec: String) {
    Graph.settings.update { if (left) it.copy(leftShortcut = spec) else it.copy(rightShortcut = spec) }
}

fun shortcutLabel(spec: String, apps: List<AppEntry>): String = when (spec) {
    SHORTCUT_PHONE -> "Phone"
    SHORTCUT_CAMERA -> "Camera"
    else -> apps.firstOrNull { it.key == spec }?.label ?: "Not set"
}

private fun launchShortcut(context: Context, spec: String, apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    when (spec) {
        SHORTCUT_PHONE -> Perms.start(context, Intent(Intent.ACTION_DIAL), options = launchOptions(context))
        SHORTCUT_CAMERA -> Perms.start(
            context,
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            options = launchOptions(context),
        )
        else -> apps.firstOrNull { it.key == spec }?.let(onLaunch)
    }
}

private fun openCalendarApp(context: Context) {
    Perms.start(
        context,
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
        // Calendars that do not register the category still open on "show me this moment".
        Intent(Intent.ACTION_VIEW, "content://com.android.calendar/time/${System.currentTimeMillis()}".toUri()),
        options = launchOptions(context),
    )
}

/** Name of a clock tap action, for settings. */
fun clockTapLabel(spec: String, apps: List<AppEntry>): String = when (spec) {
    TAP_ALARMS -> "Alarms"
    TAP_CALENDAR -> "Calendar"
    TAP_SCREEN_TIME -> "Screen time"
    TAP_BATTERY -> "Battery"
    TAP_NOTHING -> "Nothing"
    else -> apps.firstOrNull { it.key == spec }?.label ?: "Not set"
}

private fun performClockTap(
    context: Context,
    spec: String,
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onOpenScreenTime: () -> Unit,
) {
    when (spec) {
        TAP_ALARMS -> Perms.start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), options = launchOptions(context))
        TAP_CALENDAR -> openCalendarApp(context)
        TAP_SCREEN_TIME -> onOpenScreenTime()
        TAP_BATTERY -> Perms.start(
            context,
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS),
            options = launchOptions(context),
        )
        TAP_NOTHING -> Unit
        else -> apps.firstOrNull { it.key == spec }?.let(onLaunch)
    }
}

/** Pulls the notification shade down: via the timer service when it is on, else the status-bar service. */
@SuppressLint("WrongConstant", "PrivateApi")
private fun expandNotifications(context: Context) {
    if (FocusAccessibilityService.openNotifications()) return
    try {
        val statusBar = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(statusBar)
    } catch (_: Exception) {
    }
}
