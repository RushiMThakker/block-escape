package com.rushi.blockescape.season

import com.rushi.blockescape.season.Season.DoorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonTest {

    private fun d(y: Int, m: Int, day: Int) = CalendarDate(y, m, day)

    // --- The four dates the brief names, plus the edges around them ---

    @Test
    fun `Dec 5 is in season with five doors open`() {
        val date = d(2026, 12, 5)
        assertTrue(Season.isSeason(date))
        assertTrue(Season.adventVisible(date))
        assertEquals(5, Season.doorsOpen(date))
        assertEquals(DoorState.TODAY, Season.doorState(5, date, cleared = false))
        assertEquals(DoorState.AVAILABLE, Season.doorState(2, date, cleared = false)) // catch-up
        assertEquals(DoorState.LOCKED, Season.doorState(6, date, cleared = false))
    }

    @Test
    fun `Dec 31 is in season with all 24 doors open and no door is today`() {
        val date = d(2026, 12, 31)
        assertTrue(Season.isSeason(date))
        assertEquals(24, Season.doorsOpen(date))
        for (day in 1..24) assertEquals(DoorState.AVAILABLE, Season.doorState(day, date, cleared = false))
    }

    @Test
    fun `Jan 3 is still in season, belongs to the previous December, all doors open`() {
        val date = d(2027, 1, 3)
        assertTrue(Season.isSeason(date))
        assertTrue(Season.adventVisible(date))
        assertEquals(2026, Season.adventYear(date))
        assertEquals(24, Season.doorsOpen(date))
    }

    @Test
    fun `Jan 7 is out of season, calendar hidden, nothing open`() {
        val date = d(2027, 1, 7)
        assertFalse(Season.isSeason(date))
        assertFalse(Season.adventVisible(date))
        assertEquals(0, Season.doorsOpen(date))
    }

    @Test
    fun `season boundaries are exact`() {
        assertFalse(Season.isSeason(d(2026, 11, 30)))
        assertEquals(0, Season.doorsOpen(d(2026, 11, 30)))
        assertTrue(Season.isSeason(d(2026, 12, 1)))
        assertEquals(1, Season.doorsOpen(d(2026, 12, 1)))
        assertEquals(24, Season.doorsOpen(d(2026, 12, 24)))
        assertEquals(24, Season.doorsOpen(d(2026, 12, 25))) // never more than 24
        assertTrue(Season.isSeason(d(2027, 1, 6)))
        assertFalse(Season.isSeason(d(2027, 1, 7)))
        assertFalse(Season.isSeason(d(2026, 6, 15)))
    }

    @Test
    fun `a door opens on its own date, not the day before`() {
        assertEquals(DoorState.LOCKED, Season.doorState(9, d(2026, 12, 8), cleared = false))
        assertEquals(DoorState.TODAY, Season.doorState(9, d(2026, 12, 9), cleared = false))
        assertEquals(DoorState.AVAILABLE, Season.doorState(9, d(2026, 12, 10), cleared = false))
    }

    @Test
    fun `a solved door stays cleared and can be replayed, a locked door cannot be solved early`() {
        assertEquals(DoorState.CLEARED, Season.doorState(1, d(2026, 12, 20), cleared = true))
        assertEquals(DoorState.CLEARED, Season.doorState(1, d(2027, 1, 2), cleared = true))
        assertEquals(DoorState.LOCKED, Season.doorState(24, d(2026, 12, 5), cleared = true))
    }

    @Test
    fun `outside the season every door is locked`() {
        for (day in 1..24) assertEquals(DoorState.LOCKED, Season.doorState(day, d(2026, 8, 1), cleared = false))
    }

    // --- Seasonal look: Auto / On / Off ---

    @Test
    fun `Auto follows the season, On and Off ignore the date`() {
        val dec = d(2026, 12, 5)
        val jul = d(2026, 7, 5)
        assertTrue(Season.lookActive(SeasonalLook.AUTO, dec))
        assertFalse(Season.lookActive(SeasonalLook.AUTO, jul))
        assertTrue(Season.lookActive(SeasonalLook.AUTO, d(2027, 1, 6)))
        assertFalse(Season.lookActive(SeasonalLook.AUTO, d(2027, 1, 7)))
        assertTrue(Season.lookActive(SeasonalLook.ON, jul))
        assertFalse(Season.lookActive(SeasonalLook.OFF, dec))
    }

    // --- The debug-only date override ---

    @Test
    fun `a debuggable build honours the date override`() {
        val system = d(2026, 10, 20)
        assertEquals(d(2026, 12, 5), Season.resolveToday(system, "2026-12-05", isDebuggable = true))
        assertEquals(d(2026, 12, 31), Season.resolveToday(system, "2026-12-31", isDebuggable = true))
        assertEquals(d(2027, 1, 3), Season.resolveToday(system, "2027-01-03", isDebuggable = true))
        assertEquals(d(2027, 1, 7), Season.resolveToday(system, "2027-01-07", isDebuggable = true))
    }

    @Test
    fun `a release build ignores the date override completely`() {
        val system = d(2026, 10, 20)
        for (text in listOf("2026-12-05", "2026-12-31", "2027-01-03", "2027-01-07", "garbage", "")) {
            assertEquals(system, Season.resolveToday(system, text, isDebuggable = false))
        }
    }

    @Test
    fun `a malformed override falls back to the system date instead of crashing`() {
        val system = d(2026, 10, 20)
        for (text in listOf(null, "", "tomorrow", "2026-13-01", "2026-12-32", "2026-12", "x-y-z", "2026-0-5")) {
            assertEquals(system, Season.resolveToday(system, text, isDebuggable = true))
        }
        assertNull(CalendarDate.parse("2026-02-"))
    }

    // --- Advent progress bits ---

    @Test
    fun `solved days are tracked per day and counted`() {
        var bits = 0
        assertEquals(0, AdventBits.count(bits))
        bits = AdventBits.withCleared(bits, 1)
        bits = AdventBits.withCleared(bits, 9)
        bits = AdventBits.withCleared(bits, 24)
        assertTrue(AdventBits.isCleared(bits, 1))
        assertTrue(AdventBits.isCleared(bits, 9))
        assertTrue(AdventBits.isCleared(bits, 24))
        assertFalse(AdventBits.isCleared(bits, 2))
        assertEquals(3, AdventBits.count(bits))
        // Marking twice changes nothing.
        assertEquals(bits, AdventBits.withCleared(bits, 9))
    }

    @Test
    fun `a new season starts a fresh calendar`() {
        // The store compares Season.adventYear: Dec 2026 and Jan 2027 are one season, Dec 2027 is the next.
        assertEquals(Season.adventYear(d(2026, 12, 25)), Season.adventYear(d(2027, 1, 5)))
        assertTrue(Season.adventYear(d(2027, 12, 1)) != Season.adventYear(d(2027, 1, 5)))
    }
}
