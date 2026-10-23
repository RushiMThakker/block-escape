package com.rushi.blockescape.season

import java.util.Calendar

/**
 * A plain calendar date in the device's LOCAL time zone. Deliberately not java.time.LocalDate:
 * the app supports API 24 and java.time needs API 26 (or core-library desugaring, which is not
 * worth a build-wide change for three integers).
 */
data class CalendarDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(month in 1..12) { "month $month" }
        require(day in 1..31) { "day $day" }
    }

    companion object {
        /** Parses "2026-12-05"; null for anything else (a bad debug extra must never crash the app). */
        fun parse(text: String?): CalendarDate? {
            val parts = text?.trim()?.split("-") ?: return null
            if (parts.size != 3) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            if (m !in 1..12 || d !in 1..31) return null
            return CalendarDate(y, m, d)
        }

        fun systemToday(): CalendarDate {
            val c = Calendar.getInstance() // default time zone = the device's local one
            return CalendarDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
    }
}

/** The "Seasonal look" setting. */
enum class SeasonalLook { AUTO, ON, OFF }

/**
 * Everything calendar-related about the festive features, as pure functions of a date so the
 * boundaries (1 December, 24 December, 6 January) can be tested without a clock.
 *
 * The season is 1 December to 6 January inclusive. Door N opens on 1 December + (N - 1), by local
 * date, and stays available afterwards (catch-up). From 25 December to 6 January all 24 doors are
 * open; before the season and after it the Advent calendar is hidden.
 */
object Season {
    const val DOORS = 24
    private const val LAST_JANUARY_DAY = 6

    fun isSeason(date: CalendarDate): Boolean =
        date.month == 12 || (date.month == 1 && date.day <= LAST_JANUARY_DAY)

    fun lookActive(look: SeasonalLook, date: CalendarDate): Boolean = when (look) {
        SeasonalLook.ON -> true
        SeasonalLook.OFF -> false
        SeasonalLook.AUTO -> isSeason(date)
    }

    fun adventVisible(date: CalendarDate): Boolean = isSeason(date)

    /** The year whose December this season belongs to (a January date belongs to the previous year's). */
    fun adventYear(date: CalendarDate): Int = if (date.month == 1) date.year - 1 else date.year

    /** How many doors can be opened on [date]: 0 outside the season, 1..24 through December, 24 in early January. */
    fun doorsOpen(date: CalendarDate): Int = when {
        date.month == 12 -> minOf(date.day, DOORS)
        date.month == 1 && date.day <= LAST_JANUARY_DAY -> DOORS
        else -> 0
    }

    enum class DoorState {
        /** Its day has not come yet. */
        LOCKED,

        /** The door for today's date, not yet solved. */
        TODAY,

        /** An earlier day not yet solved (catch-up). */
        AVAILABLE,

        /** Solved; stays open and can be replayed. */
        CLEARED
    }

    fun doorState(day: Int, date: CalendarDate, cleared: Boolean): DoorState {
        require(day in 1..DOORS) { "day $day" }
        if (day > doorsOpen(date)) return DoorState.LOCKED
        if (cleared) return DoorState.CLEARED
        val isToday = date.month == 12 && date.day == day
        return if (isToday) DoorState.TODAY else DoorState.AVAILABLE
    }

    /**
     * The date the app should treat as "today". The system date, unless a debug override was
     * supplied AND the app is a debuggable build: a release build can never be moved off the real
     * date by an intent extra, whatever it contains.
     */
    fun resolveToday(system: CalendarDate, overrideText: String?, isDebuggable: Boolean): CalendarDate {
        if (!isDebuggable) return system
        return CalendarDate.parse(overrideText) ?: system
    }
}
