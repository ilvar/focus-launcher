package com.focus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StripEmojiTest {
    private fun strip(s: String) = CalendarRepository.stripEmoji(s)

    @Test
    fun `plain titles are untouched`() {
        assertEquals("Office", strip("Office"))
        assertEquals("1:1 with Priya (weekly) - Q3 plan", strip("1:1 with Priya (weekly) - Q3 plan"))
    }

    @Test
    fun `a trailing emoji and its space disappear`() {
        assertEquals("Do my Duolingo", strip("Do my Duolingo 💚")) // green heart
    }

    @Test
    fun `emoji in the middle do not leave a double space`() {
        assertEquals("Gym then dinner", strip("Gym 🏋️ then 🍝 dinner"))
    }

    @Test
    fun `joined sequences, skin tones, flags and keycaps all go`() {
        assertEquals("Family call", strip("👨‍👩‍👧 Family call")) // family ZWJ sequence
        assertEquals("Wave", strip("Wave 👋🏽"))                                          // skin tone modifier
        assertEquals("Trip to", strip("Trip to 🇮🇳"))                                     // flag
        assertEquals("Step 1", strip("Step 1️⃣"))                                                    // keycap keeps its digit
        assertEquals("Deadline", strip("⏰ Deadline ⭐✨"))                                        // alarm clock, star, sparkles
    }

    @Test
    fun `other scripts and accents survive`() {
        assertEquals("Café mit Jürgen", strip("Café mit Jürgen"))
        assertEquals("मीटिंग 10 बजे", strip("मीटिंग 10 बजे"))
    }

    @Test
    fun `a title that was only emoji becomes empty`() {
        assertEquals("", strip("🎉🎉"))
    }
}
