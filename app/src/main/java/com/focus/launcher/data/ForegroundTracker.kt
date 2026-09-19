package com.focus.launcher.data

/**
 * Turns a stream of usage events into foreground intervals: a pure state machine with no Android
 * dependencies, so it can be unit tested.
 *
 * Android itself pairs RESUMED/PAUSED per activity *instance*, but the instance id is hidden from
 * apps, and apps such as Instagram stack several instances of one activity class. Tracking by
 * class name therefore loses time: the late STOPPED of an old instance gets mistaken for the newer
 * instance of the same class (on a real day this lost 24 of Instagram's 75 minutes). Instead:
 *
 *  - each package keeps a counter of resumed-but-not-yet-paused activities and is "open" while
 *    that counter is above zero. STOPPED events are simply never fed in.
 *  - as a safety net for a PAUSED that never comes (crash, split-screen), an open package is
 *    marked "covered" when another package resumes. If its own PAUSED does not follow within
 *    [graceMs], it is closed at the moment it was covered.
 *
 * Checked against the system's instance-aware totals for 40 apps over a real day, every app
 * landed within 10 seconds.
 *
 * Events must be fed in chronological order. [emit] receives (package, start, end) in epoch ms.
 */
class ForegroundTracker(
    private val graceMs: Long = 3_000L,
    private val emit: (pkg: String, start: Long, end: Long) -> Unit,
) {
    private val resumedCount = HashMap<String, Int>()
    private val startedAt = HashMap<String, Long>()
    private val coveredAt = HashMap<String, Long>()

    fun resumed(pkg: String, ts: Long) {
        expireCovered(ts)
        for (other in startedAt.keys) if (other != pkg) coveredAt.putIfAbsent(other, ts)
        if (pkg in startedAt) {
            resumedCount[pkg] = (resumedCount[pkg] ?: 0) + 1
            coveredAt.remove(pkg) // it is in front again
        } else {
            startedAt[pkg] = ts
            resumedCount[pkg] = 1
        }
    }

    fun paused(pkg: String, ts: Long) {
        expireCovered(ts)
        if (pkg !in startedAt) return
        val left = (resumedCount[pkg] ?: 1) - 1
        if (left <= 0) close(pkg, ts) else resumedCount[pkg] = left
    }

    /** Screen off or shutdown: nothing is in the foreground any more. */
    fun screenOff(ts: Long) {
        expireCovered(ts)
        closeAll(ts)
    }

    /** Any other event; only moves time forward so stale "covered" packages get closed. */
    fun tick(ts: Long) = expireCovered(ts)

    /** End of the stream: whatever is still open counts up to [ts]. */
    fun finish(ts: Long) = closeAll(ts)

    /**
     * What [finish] would emit at [now], without ending anything. This is what lets a long-lived
     * tracker be asked "how much so far?" again and again while only ever being fed new events.
     */
    fun openIntervals(now: Long): List<Triple<String, Long, Long>> =
        startedAt.mapNotNull { (pkg, start) ->
            val end = coveredAt[pkg] ?: now
            if (end > start) Triple(pkg, start, end) else null
        }

    val hasOpenIntervals: Boolean get() = startedAt.isNotEmpty()

    private fun expireCovered(now: Long) {
        if (coveredAt.isEmpty()) return
        for ((pkg, since) in coveredAt.entries.toList()) if (now - since > graceMs) close(pkg, since)
    }

    private fun closeAll(at: Long) {
        for (pkg in startedAt.keys.toList()) close(pkg, coveredAt[pkg] ?: at)
    }

    private fun close(pkg: String, at: Long) {
        startedAt.remove(pkg)?.let { start -> if (at > start) emit(pkg, start, at) }
        resumedCount.remove(pkg)
        coveredAt.remove(pkg)
    }
}
