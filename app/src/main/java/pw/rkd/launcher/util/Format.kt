package pw.rkd.launcher.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** "2h 41m", "48m", "<1m", "0m". */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalMinutes = ms / 60_000
    if (totalMinutes == 0L) return "<1m"
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0L -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

/** Limits and presets: 30 -> "30m", 90 -> "1h 30m". */
fun formatMinutes(minutes: Int): String = formatDuration(minutes * 60_000L)

/** 14 -> "14:00" or "2 PM". */
fun formatHour(hour: Int, use24h: Boolean): String {
    val h = ((hour % 24) + 24) % 24
    if (use24h) return String.format(Locale.US, "%02d:00", h)
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 ${if (h < 12) "AM" else "PM"}"
}

/** -0.12 -> "12% less", 0.3 -> "30% more". */
fun formatChange(fraction: Float): String {
    val pct = (abs(fraction) * 100).roundToInt()
    return when {
        pct == 0 -> "about the same"
        fraction < 0 -> "$pct% less"
        else -> "$pct% more"
    }
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM")

/** "14 – 20 Sep" or "28 Sep – 4 Oct". */
fun formatWeekRange(start: LocalDate, end: LocalDate): String =
    if (start.month == end.month) "${start.dayOfMonth} – ${end.format(DAY_MONTH)}"
    else "${start.format(DAY_MONTH)} – ${end.format(DAY_MONTH)}"
