package com.focus.launcher.service

import com.focus.launcher.data.AppLimit
import com.focus.launcher.data.LimitSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the timer watcher decides for the app in front. The service around it only carries it out. */
class TimerWatchTest {
    private val own = "com.focus.launcher"
    private val app = "com.example.feed"
    private val limit = AppLimit(30, LimitSource.SOCIAL_DEFAULT)
    private val min = 60_000L

    private fun decide(
        pkg: String = app, limit: AppLimit? = this.limit, bypassed: Boolean = false, needsConsent: Boolean = false,
        consented: String? = null, now: Long = 1_000_000L, passUntil: Long = 0L, used: Long = 0L,
    ) = TimerWatch.decide(pkg, own, limit, bypassed, needsConsent, consented, now, passUntil, used)

    @Test fun `an app without a limit is left alone`() = assertEquals(WatchAction.Idle, decide(limit = null, used = 99 * min))

    @Test fun `focus itself, the wall included, is never locked`() = assertEquals(WatchAction.Idle, decide(pkg = own, used = 99 * min))

    @Test fun `time left means a timer for exactly that long`() = assertEquals(WatchAction.LockIn(12 * min), decide(used = 18 * min))

    @Test fun `an allowance that is used up locks`() {
        assertEquals(WatchAction.Lock(30 * min), decide(used = 30 * min))
        assertEquals(WatchAction.Lock(31 * min), decide(used = 31 * min))
    }

    @Test fun `inside a continue window nothing happens until it closes`() =
        assertEquals(WatchAction.LookAgainAt(1_300_000L), decide(used = 45 * min, now = 1_000_000L, passUntil = 1_300_000L))

    @Test fun `a continue window that has closed locks again`() =
        assertEquals(WatchAction.Lock(45 * min), decide(used = 45 * min, now = 1_300_001L, passUntil = 1_300_000L))

    @Test fun `ignored for today still asks before every visit`() =
        assertEquals(WatchAction.AskConsent, decide(bypassed = true, needsConsent = true, used = 45 * min))

    @Test fun `a visit that was agreed to is not asked about again`() =
        assertEquals(WatchAction.Idle, decide(bypassed = true, needsConsent = true, consented = app, used = 45 * min))

    @Test fun `consent for another app does not count`() =
        assertEquals(WatchAction.AskConsent, decide(bypassed = true, needsConsent = true, consented = "com.example.other", used = 45 * min))

    @Test fun `ignored for today without the asking setting is simply open`() =
        assertEquals(WatchAction.Idle, decide(bypassed = true, needsConsent = false, used = 45 * min))
}
