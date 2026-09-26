package com.rkd.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundTrackerTest {
    private val intervals = ArrayList<Triple<String, Long, Long>>()
    private val tracker = ForegroundTracker(graceMs = 3_000) { pkg, start, end -> intervals += Triple(pkg, start, end) }

    private fun total(pkg: String) = intervals.filter { it.first == pkg }.sumOf { it.third - it.second }

    @Test
    fun `a plain session counts from resume to pause`() {
        tracker.resumed("a", 1_000)
        tracker.paused("a", 61_000)
        tracker.finish(100_000)
        assertEquals(listOf(Triple("a", 1_000L, 61_000L)), intervals)
    }

    @Test
    fun `an app still in front counts up to the end of the stream`() {
        tracker.resumed("a", 1_000)
        tracker.finish(31_000)
        assertEquals(30_000, total("a"))
    }

    @Test
    fun `moving between screens of one app only loses the gap between them`() {
        // Screen 1 pauses, screen 2 resumes 40 ms later. (The trailing STOPPED of screen 1 is
        // never fed in: without an instance id it cannot be told apart from screen 2 when both
        // are the same activity class, which is what used to cut Instagram's time short.)
        tracker.resumed("insta", 0)
        tracker.paused("insta", 10_000)
        tracker.resumed("insta", 10_040)
        tracker.paused("insta", 50_000)
        tracker.finish(60_000)
        assertEquals(10_000 + 39_960, total("insta"))
    }

    @Test
    fun `a new screen that resumes before the old one pauses keeps the app open`() {
        tracker.resumed("a", 0)
        tracker.resumed("a", 5_000)   // second activity comes up first...
        tracker.paused("a", 5_020)    // ...then the first one reports its pause
        tracker.paused("a", 20_000)
        tracker.finish(30_000)
        assertEquals(listOf(Triple("a", 0L, 20_000L)), intervals)
    }

    @Test
    fun `switching apps ends the old app at its own pause`() {
        tracker.resumed("a", 0)
        tracker.resumed("b", 10_000)  // b is already up
        tracker.paused("a", 10_150)   // a's pause trails slightly, as Android reports it
        tracker.paused("b", 25_000)
        tracker.finish(30_000)
        assertEquals(10_150, total("a"))
        assertEquals(15_000, total("b"))
    }

    @Test
    fun `an app whose pause never arrives is closed when another app covered it`() {
        tracker.resumed("crashy", 0)
        tracker.resumed("home", 8_000)     // crashy died; no PAUSED will ever come
        tracker.tick(20_000)               // well past the grace period
        tracker.paused("home", 40_000)
        tracker.finish(50_000)
        assertEquals(8_000, total("crashy"))
        assertEquals(32_000, total("home"))
    }

    @Test
    fun `a covered app that is still open at the end is closed where it was covered`() {
        tracker.resumed("a", 0)
        tracker.resumed("b", 5_000)
        tracker.finish(6_000)              // inside the grace period, stream ends
        assertEquals(5_000, total("a"))
        assertEquals(1_000, total("b"))
    }

    @Test
    fun `coming back to the front clears the covered mark`() {
        tracker.resumed("a", 0)
        tracker.resumed("b", 5_000)        // covers a
        tracker.paused("b", 6_000)
        tracker.resumed("a", 6_100)        // a is in front again within the grace period
        tracker.tick(60_000)               // must not retroactively close a at 5_000
        tracker.paused("a", 61_000)        // pairs with the first resume
        tracker.paused("a", 62_000)        // pairs with the second
        tracker.finish(70_000)
        assertEquals(62_000, total("a"))
    }

    @Test
    fun `screen off ends everything and later stray pauses are ignored`() {
        tracker.resumed("a", 0)
        tracker.screenOff(30_000)
        tracker.paused("a", 30_200)
        tracker.finish(90_000)
        assertEquals(listOf(Triple("a", 0L, 30_000L)), intervals)
    }

    @Test
    fun `asking what is open does not end it`() {
        tracker.resumed("a", 0)
        assertEquals(listOf(Triple("a", 0L, 10_000L)), tracker.openIntervals(10_000))
        assertEquals(listOf(Triple("a", 0L, 25_000L)), tracker.openIntervals(25_000)) // asked again, still running
        tracker.paused("a", 40_000)
        assertEquals(emptyList<Triple<String, Long, Long>>(), tracker.openIntervals(50_000))
        assertEquals(listOf(Triple("a", 0L, 40_000L)), intervals)                        // counted once, in full
    }

    @Test
    fun `an open app that was covered is reported up to the moment it was covered`() {
        tracker.resumed("a", 0)
        tracker.resumed("home", 8_000)
        val open = tracker.openIntervals(9_000).associate { it.first to it.third }
        assertEquals(8_000L, open["a"])
        assertEquals(9_000L, open["home"])
    }

    @Test
    fun `feeding events in two batches gives the same result as one`() {
        // This is what the running daily total relies on: a tracker that lives across refreshes.
        tracker.resumed("a", 0)
        tracker.paused("a", 5_000)
        tracker.resumed("b", 5_100)
        val midway = total("a") + tracker.openIntervals(7_000).filter { it.first == "b" }.sumOf { it.third - it.second }
        assertEquals(5_000 + 1_900, midway)
        tracker.paused("b", 9_000)   // second batch
        tracker.finish(20_000)
        assertEquals(5_000, total("a"))
        assertEquals(3_900, total("b"))
    }

    @Test
    fun `a pause without a resume produces nothing`() {
        tracker.paused("a", 5_000)
        tracker.finish(10_000)
        assertEquals(emptyList<Triple<String, Long, Long>>(), intervals)
    }
}
