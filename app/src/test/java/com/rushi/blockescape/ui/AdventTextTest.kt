package com.rushi.blockescape.ui

import com.rushi.blockescape.season.CalendarDate
import com.rushi.blockescape.season.Season.DoorState
import com.rushi.blockescape.season.SeasonalLook
import com.rushi.blockescape.shareText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventTextTest {

    private fun d(m: Int, day: Int, y: Int = 2026) = CalendarDate(y, m, day)

    @Test
    fun `subtitle explains the daily door while it is still filling up`() {
        assertEquals("A new door opens each day  ·  3 of 24 solved", adventSubtitle(d(12, 5), solved = 3))
    }

    @Test
    fun `subtitle says every door is open from Christmas Day to 6 January`() {
        assertEquals("Every door is open until 6 January  ·  7 of 24 solved", adventSubtitle(d(12, 28), solved = 7))
        assertEquals("Every door is open until 6 January  ·  7 of 24 solved", adventSubtitle(d(1, 3, 2027), solved = 7))
    }

    @Test
    fun `subtitle celebrates a finished calendar on any day`() {
        assertEquals("All 24 doors solved. Happy holidays!", adventSubtitle(d(12, 9), solved = 24))
        assertEquals("All 24 doors solved. Happy holidays!", adventSubtitle(d(1, 2, 2027), solved = 24))
    }

    @Test
    fun `door descriptions say what a tap will do`() {
        assertTrue(doorDescription(9, DoorState.TODAY).contains("today"))
        assertTrue(doorDescription(2, DoorState.AVAILABLE).contains("tap to open"))
        assertTrue(doorDescription(1, DoorState.CLEARED).contains("play again"))
        assertTrue(doorDescription(24, DoorState.LOCKED).contains("locked"))
    }

    @Test
    fun `settings labels match the three options`() {
        assertEquals(listOf("Auto", "On", "Off"), SeasonalLook.entries.map { seasonalLookLabel(it) })
        assertTrue(SEASONAL_LOOK_HELP.contains("1 December") && SEASONAL_LOOK_HELP.contains("6 January"))
    }

    @Test
    fun `share text never reveals a board or a solution, only the day`() {
        val plain = shareText()
        assertFalse(plain.contains("Advent"))
        assertTrue(plain.contains("play.google.com"))
        val day9 = shareText(9)
        assertTrue(day9.contains("Advent calendar day 9"))
        assertTrue(day9.contains("play.google.com"))
        // An out-of-range day is ignored rather than printed.
        assertFalse(shareText(0).contains("Advent"))
        assertFalse(shareText(25).contains("Advent"))
    }
}
