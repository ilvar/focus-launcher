package com.focus.launcher.ui.review

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focus.launcher.Graph
import com.focus.launcher.data.DayPeriod
import com.focus.launcher.data.DayUsage
import com.focus.launcher.data.Settings
import com.focus.launcher.data.TimeFormat
import com.focus.launcher.data.WeekSummary
import com.focus.launcher.ui.components.FocusButton
import com.focus.launcher.ui.components.Hairline
import com.focus.launcher.ui.components.Label
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.TabChip
import com.focus.launcher.ui.components.TextInputDialog
import com.focus.launcher.ui.components.VSpace
import com.focus.launcher.ui.home.DayBar
import com.focus.launcher.ui.home.HourScale
import com.focus.launcher.ui.home.currentLocale
import com.focus.launcher.ui.theme.LocalFocusColors
import com.focus.launcher.util.Perms
import com.focus.launcher.util.formatChange
import com.focus.launcher.util.formatDuration
import com.focus.launcher.util.formatHour
import com.focus.launcher.util.formatMinutes
import com.focus.launcher.util.formatWeekRange
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(settings: Settings, initialWeek: LocalDate?, onBack: () -> Unit) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(if (initialWeek != null) 1 else 0) }
    var hasAccess by remember { mutableStateOf(Perms.hasUsageAccess()) }
    LifecycleResumeEffect(Unit) {
        hasAccess = Perms.hasUsageAccess()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("←", Modifier.clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 10.dp), size = 22.sp)
            T("Screen time", Modifier.weight(1f).padding(start = 4.dp), size = 22.sp, weight = FontWeight.Medium, maxLines = 1)
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("Today", tab == 0) { tab = 0 }
            TabChip("Week", tab == 1) { tab = 1 }
        }
        Hairline()

        if (!hasAccess) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp)) {
                T("Rkd Launcher needs usage access to show where your time goes.", size = 17.sp, lineHeight = 25.sp)
                VSpace(8.dp)
                T("The numbers are computed on this phone and never leave it.", size = 14.sp, color = c.dim, lineHeight = 20.sp)
                VSpace(22.dp)
                FocusButton("Allow usage access", primary = true) { Perms.openUsageAccess(context) }
            }
            return@Column
        }

        Crossfade(targetState = tab, animationSpec = tween(220), label = "review tab") { shown ->
            if (shown == 0) TodayTab(settings) else WeekTab(settings, initialWeek)
        }
    }
}

// ---- Today -----------------------------------------------------------------------------------

@Composable
private fun TodayTab(settings: Settings) {
    val c = LocalFocusColors.current
    val today by Graph.usage.today.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Graph.usage.refreshToday(maxAgeMs = 0) }

    val usage = today ?: DayUsage.empty(LocalDate.now())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        VSpace(26.dp)
        T(formatDuration(usage.total), size = 54.sp, weight = FontWeight.Light, maxLines = 1)
        val share = (usage.total * 100f / 86_400_000f)
        T(
            "of 24h  ·  ${if (share < 1f && usage.total > 0) "<1" else share.roundToInt().toString()}% of the day  ·  ${usage.unlocks} unlocks",
            size = 14.sp, color = c.dim,
        )
        VSpace(22.dp)
        DayBar(usage.perHour, height = 18.dp, currentHour = java.time.LocalTime.now().hour)
        VSpace(2.dp)
        HourScale()

        VSpace(34.dp)
        Label("Apps today")
        VSpace(8.dp)
        val top = usage.topApps(12)
        if (top.isEmpty()) {
            T("Nothing yet. Enjoy it.", Modifier.padding(vertical = 10.dp), size = 16.sp, color = c.dim)
        }
        val max = top.firstOrNull()?.second ?: 1L
        for ((pkg, ms) in top) {
            val limit = Graph.limits.limitFor(pkg, settings)
            UsageRow(
                label = Graph.apps.labelForPackage(pkg),
                value = formatDuration(ms) + (limit?.let { " / ${formatMinutes(it.minutes)}" } ?: ""),
                fraction = ms.toFloat() / max,
                note = null,
            )
        }
        VSpace(40.dp)
    }
}

// ---- Week ------------------------------------------------------------------------------------

@Composable
private fun WeekTab(settings: Settings, initialWeek: LocalDate?) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    val locale = currentLocale()
    val today = LocalDate.now()
    val currentWeek = WeekSummary.weekStartOf(today, settings.weekStartsMonday)
    var weekStart by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(initialWeek?.let { WeekSummary.weekStartOf(it, settings.weekStartsMonday) } ?: currentWeek)
    }
    var summary by remember { mutableStateOf<WeekSummary?>(null) }

    LaunchedEffect(weekStart) {
        summary = null
        val days = Graph.usage.loadDays(weekStart.minusDays(7), weekStart.plusDays(6))
        val stats = Graph.limits.stats(weekStart, weekStart.plusDays(6))
        summary = WeekSummary.build(weekStart, today, days, stats) { Graph.apps.labelForPackage(it) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        // Week switcher
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            T("‹", Modifier.clickable { weekStart = weekStart.minusWeeks(1) }.padding(horizontal = 14.dp, vertical = 6.dp), size = 26.sp, color = c.dim)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                T(formatWeekRange(weekStart, weekStart.plusDays(6)), size = 17.sp, weight = FontWeight.Medium, maxLines = 1)
                T(
                    when (weekStart) {
                        currentWeek -> "This week"
                        currentWeek.minusWeeks(1) -> "Last week"
                        else -> weekStart.year.toString()
                    },
                    size = 12.sp, color = c.dim, maxLines = 1,
                )
            }
            val canGoForward = weekStart.isBefore(currentWeek)
            T(
                "›",
                Modifier.clickable(enabled = canGoForward) { weekStart = weekStart.plusWeeks(1) }.padding(horizontal = 14.dp, vertical = 6.dp),
                size = 26.sp, color = if (canGoForward) c.dim else c.line,
            )
        }

        val s = summary
        if (s == null) {
            T("Adding up your week…", Modifier.padding(vertical = 40.dp), size = 16.sp, color = c.dim)
            return@Column
        }
        if (!s.hasData) {
            VSpace(36.dp)
            T("No screen time recorded for this week.", size = 17.sp)
            VSpace(8.dp)
            T(
                "Android only keeps detailed usage for about a week. Rkd Launcher saves each day from now on, so future weeks stay available.",
                size = 14.sp, color = c.dim, lineHeight = 20.sp,
            )
            return@Column
        }

        // Headline
        VSpace(22.dp)
        T(formatDuration(s.total), size = 54.sp, weight = FontWeight.Light, maxLines = 1)
        T(
            buildString {
                append("${formatDuration(s.dailyAverage)} a day")
                s.changeVsPrevious?.let { append("  ·  ${formatChange(it)} than last week") }
            }.replace("about the same than", "about the same as"),
            size = 14.sp, color = c.dim,
        )
        val waking = s.dailyAverage * 100f / (16 * 3_600_000f)
        if (waking >= 1f) {
            VSpace(4.dp)
            T("That is ${waking.roundToInt()}% of your waking hours.", size = 14.sp, color = c.dim)
        }

        // Day by day
        VSpace(30.dp)
        Label("Day by day")
        VSpace(12.dp)
        val dayMax = s.days.maxOf { it.total }.coerceAtLeast(1L)
        Bars(
            fractions = s.days.map { it.total.toFloat() / dayMax },
            height = 130.dp,
            topLabels = s.days.map { if (it.total > 0) shortHours(it.total) else "" },
            bottomLabels = s.days.map { it.date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale) },
            emphasized = s.days.map { it.date == s.busiestDay?.date },
            muted = s.days.map { it.date.isAfter(today) },
        )
        s.busiestDay?.let { busiest ->
            VSpace(10.dp)
            val name = busiest.date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
            val light = s.lightestDay?.takeIf { it.date != busiest.date }
            T(
                "Most on $name (${formatDuration(busiest.total)})" +
                    (light?.let { ", least on ${it.date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)} (${formatDuration(it.total)})." } ?: "."),
                size = 14.sp, color = c.dim, lineHeight = 20.sp,
            )
        }

        // Time of day
        VSpace(32.dp)
        Label("When you were on your phone")
        VSpace(12.dp)
        val hourMax = s.perHour.max().coerceAtLeast(1L)
        Bars(
            fractions = s.perHour.map { it.toFloat() / hourMax },
            height = 84.dp,
            gap = 2.dp,
            emphasized = s.perHour.indices.map { it == s.peakHour },
        )
        VSpace(2.dp)
        HourScale()
        VSpace(14.dp)
        val use24h = when (settings.timeFormat) {
            TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
            TimeFormat.H24 -> true
            TimeFormat.H12 -> false
        }
        for (period in DayPeriod.entries) {
            val ms = s.perPeriod[period] ?: 0L
            UsageRow(
                label = period.label,
                value = "${formatDuration(ms)}  ·  ${(ms * 100f / s.total).roundToInt()}%",
                fraction = ms.toFloat() / (s.perPeriod.values.max().coerceAtLeast(1L)),
                note = period.hours,
                strong = period == s.peakPeriod,
            )
        }
        s.peakHour?.let { hour ->
            VSpace(6.dp)
            T("Busiest hour: ${formatHour(hour, use24h)} – ${formatHour(hour + 1, use24h)}.", size = 14.sp, color = c.dim)
        }

        // Apps
        VSpace(32.dp)
        Label("Where it went")
        VSpace(8.dp)
        val appMax = s.topApps.firstOrNull()?.total ?: 1L
        for (app in s.topApps) {
            val notes = buildList {
                add("mostly ${app.peakPeriod.label.lowercase()}")
                add("${app.daysUsed} of ${s.daysElapsed} days")
                val past = app.limitStats.continued + app.limitStats.bypassed
                if (past > 0) add("past its limit $past×")
            }
            UsageRow(
                label = app.label,
                value = "${formatDuration(app.total)}  ·  ${(app.total * 100f / s.total).roundToInt()}%",
                fraction = app.total.toFloat() / appMax,
                note = notes.joinToString("  ·  "),
            )
        }

        // Limits and unlocks
        VSpace(32.dp)
        Label("Limits and pickups")
        VSpace(10.dp)
        val l = s.limitStats
        if (l.blocked + l.continued + l.bypassed == 0) {
            T("No app hit its daily limit this week.", size = 15.sp, lineHeight = 22.sp)
        } else {
            T(
                "Time ran out ${times(l.blocked)}. You carried on ${times(l.continued)}" +
                    (if (l.continuedMinutes > 0) " (+${formatMinutes(l.continuedMinutes)})" else "") +
                    " and ignored the limit for the day ${times(l.bypassed)}.",
                size = 15.sp, lineHeight = 22.sp,
            )
        }
        if (s.unlocks > 0) {
            VSpace(6.dp)
            T("You unlocked your phone ${s.unlocks} times, about ${(s.unlocks.toFloat() / s.daysElapsed).roundToInt()} a day.", size = 15.sp, color = c.dim, lineHeight = 22.sp)
        }

        // Reflection
        VSpace(32.dp)
        Hairline()
        VSpace(24.dp)
        Reflection(weekStart)
        VSpace(48.dp)
    }
}

/** A sentence to carry into next week, plus a reminder of what was written the week before. */
@Composable
private fun Reflection(weekStart: LocalDate) {
    val c = LocalFocusColors.current
    var intention by remember(weekStart) { mutableStateOf(Graph.state.intention(weekStart)) }
    val previous = remember(weekStart) { Graph.state.intention(weekStart.minusWeeks(1)) }
    var editing by remember { mutableStateOf(false) }

    Label("Reflect")
    VSpace(10.dp)
    T("Was this how you wanted to spend your week?", size = 20.sp, weight = FontWeight.Light, lineHeight = 28.sp)
    if (previous.isNotEmpty()) {
        VSpace(14.dp)
        T("The week before, you wrote:", size = 13.sp, color = c.dim)
        T("“$previous”", Modifier.padding(top = 2.dp), size = 16.sp, lineHeight = 23.sp)
    }
    VSpace(16.dp)
    if (intention.isEmpty()) {
        FocusButton("Write one thing to change next week") { editing = true }
    } else {
        T("Next week:", size = 13.sp, color = c.dim)
        T("“$intention”", Modifier.clickable { editing = true }.padding(top = 2.dp, bottom = 6.dp), size = 16.sp, lineHeight = 23.sp)
    }
    if (editing) {
        TextInputDialog(
            title = "One thing to change",
            subtitle = "Keep it small and concrete. You will see it again in next week's review.",
            initial = intention,
            placeholder = "No phone in bed",
            onDismiss = { editing = false },
        ) { text ->
            Graph.state.setIntention(weekStart, text)
            intention = text
        }
    }
}

// ---- pieces ----------------------------------------------------------------------------------

/** Name and value over a thin proportional bar. The list rows of both tabs. */
@Composable
private fun UsageRow(label: String, value: String, fraction: Float, note: String?, strong: Boolean = false) {
    val c = LocalFocusColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(label, Modifier.weight(1f), size = 17.sp, weight = if (strong) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
            T(value, Modifier.padding(start = 12.dp), size = 14.sp, color = c.dim, maxLines = 1)
        }
        if (note != null) T(note, Modifier.padding(top = 2.dp), size = 12.sp, color = c.faint, maxLines = 1)
        VSpace(7.dp)
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.line)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.004f, 1f)).fillMaxHeight().background(c.fg))
        }
    }
}

/** Vertical bar chart drawn with plain boxes, so the labels line up with the bars for free. */
@Composable
private fun Bars(
    fractions: List<Float>,
    height: Dp,
    gap: Dp = 8.dp,
    topLabels: List<String>? = null,
    bottomLabels: List<String>? = null,
    emphasized: List<Boolean>? = null,
    muted: List<Boolean>? = null,
) {
    val c = LocalFocusColors.current
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
        fractions.forEachIndexed { i, fraction ->
            val strong = emphasized?.get(i) == true
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (topLabels != null) {
                    T(topLabels[i], size = 10.sp, color = if (strong) c.fg else c.dim, maxLines = 1)
                    VSpace(4.dp)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val barColor: Color = if (strong || emphasized == null) c.fg else c.dim
                    if (fraction > 0f) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceIn(0.015f, 1f)).background(barColor))
                    } else {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    }
                }
                if (bottomLabels != null) {
                    VSpace(6.dp)
                    T(
                        bottomLabels[i], size = 11.sp,
                        color = if (muted?.get(i) == true) c.line else if (strong) c.fg else c.dim,
                        weight = if (strong) FontWeight.Medium else FontWeight.Normal, maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun shortHours(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "${minutes}m" else String.format(Locale.US, "%.1fh", minutes / 60f)
}

private fun times(n: Int): String = when (n) {
    0 -> "0 times"
    1 -> "once"
    2 -> "twice"
    else -> "$n times"
}

private val LocalDateSaver = androidx.compose.runtime.saveable.Saver<LocalDate, String>(
    save = { it.toString() },
    restore = { LocalDate.parse(it) },
)
