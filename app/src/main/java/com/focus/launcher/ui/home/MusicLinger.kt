package com.focus.launcher.ui.home

/**
 * How long the music section stays on the home screen after the music stopped: long enough to
 * press play again, and to bridge the silence between two songs.
 */
internal const val MUSIC_LINGER_MS = 60_000L

/**
 * Until when (elapsedRealtime, ms) the music section stays although nothing plays; 0 = it does not.
 *
 * The section shows while something plays. It lingers only after a stop that was *seen happening*
 * ([live]: a callback, with the home screen in sight). Coming back to the home screen and finding
 * the music already stopped is not such a moment: nobody knows how long ago it stopped, and a
 * section that turns up for a minute on every return is what hiding it is meant to avoid. A
 * minute that is already running survives such a return.
 */
internal fun lingerUntil(wasPlaying: Boolean, playing: Boolean, live: Boolean, now: Long, previous: Long): Long = when {
    playing -> 0L
    wasPlaying && live -> now + MUSIC_LINGER_MS
    previous > now -> previous
    else -> 0L
}
