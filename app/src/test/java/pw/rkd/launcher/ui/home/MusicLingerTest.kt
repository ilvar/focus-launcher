package pw.rkd.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** When the music section is on the home screen although nothing plays. Times are elapsedRealtime, ms. */
class MusicLingerTest {
    private val t = 1_000_000L

    @Test fun `while something plays nothing lingers`() {
        assertEquals(0L, lingerUntil(wasPlaying = false, playing = true, live = true, now = t, previous = 0L))
        assertEquals(0L, lingerUntil(wasPlaying = true, playing = true, live = false, now = t, previous = 0L))
    }

    @Test fun `a stop seen happening keeps the section for a minute`() {
        assertEquals(t + MUSIC_LINGER_MS, lingerUntil(wasPlaying = true, playing = false, live = true, now = t, previous = 0L))
    }

    @Test fun `coming home to music that already stopped shows nothing`() {
        // It played when the home screen was left; what is found on return is not a moment anybody saw.
        assertEquals(0L, lingerUntil(wasPlaying = true, playing = false, live = false, now = t, previous = 0L))
    }

    @Test fun `nothing played and nothing plays`() {
        assertEquals(0L, lingerUntil(wasPlaying = false, playing = false, live = true, now = t, previous = 0L))
        assertEquals(0L, lingerUntil(wasPlaying = false, playing = false, live = false, now = t, previous = 0L))
    }

    @Test fun `a minute that is running survives leaving the home screen and coming back`() {
        val until = t + MUSIC_LINGER_MS
        assertEquals(until, lingerUntil(wasPlaying = false, playing = false, live = false, now = t + 20_000, previous = until))
        // ... and other news from the player in between (a new title while paused) does not restart it.
        assertEquals(until, lingerUntil(wasPlaying = false, playing = false, live = true, now = t + 30_000, previous = until))
    }

    @Test fun `a minute that is over is over`() {
        val until = t + MUSIC_LINGER_MS
        assertEquals(0L, lingerUntil(wasPlaying = false, playing = false, live = false, now = until, previous = until))
        assertEquals(0L, lingerUntil(wasPlaying = false, playing = false, live = true, now = until + 5, previous = until))
    }

    @Test fun `pressing play again ends the minute and the next stop starts a new one`() {
        val first = lingerUntil(wasPlaying = true, playing = false, live = true, now = t, previous = 0L)
        assertEquals(0L, lingerUntil(wasPlaying = false, playing = true, live = true, now = t + 10_000, previous = first))
        assertEquals(t + 40_000 + MUSIC_LINGER_MS, lingerUntil(wasPlaying = true, playing = false, live = true, now = t + 40_000, previous = 0L))
    }
}
