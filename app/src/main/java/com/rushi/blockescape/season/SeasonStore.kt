package com.rushi.blockescape.season

import android.content.Context

/**
 * Pure bit operations behind the stored set of solved Advent days (day 1 = bit 0), kept apart from
 * SharedPreferences so they can be unit-tested with plain JUnit.
 */
object AdventBits {
    fun isCleared(bits: Int, day: Int): Boolean = (bits shr (day - 1)) and 1 == 1
    fun withCleared(bits: Int, day: Int): Int = bits or (1 shl (day - 1))
    fun count(bits: Int): Int = Integer.bitCount(bits and ((1 shl Season.DOORS) - 1))
}

/**
 * Seasonal settings and Advent progress. Stored in its own preferences file, separate from the main
 * level progress: solving Advent boards never unlocks or clears a main level, and vice versa.
 *
 * Advent progress belongs to one season (identified by its December's year). When a new season
 * starts, the previous one's solved doors are dropped so next December starts with a fresh calendar.
 */
class SeasonStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun seasonalLook(): SeasonalLook =
        SeasonalLook.entries.firstOrNull { it.name == prefs.getString(KEY_LOOK, null) } ?: SeasonalLook.AUTO

    fun setSeasonalLook(look: SeasonalLook) {
        prefs.edit().putString(KEY_LOOK, look.name).apply()
    }

    /** Solved Advent days for the season that [today] belongs to (0 if the stored data is from another season). */
    fun clearedBits(today: CalendarDate): Int =
        if (prefs.getInt(KEY_SEASON_YEAR, Int.MIN_VALUE) == Season.adventYear(today)) prefs.getInt(KEY_CLEARED, 0) else 0

    fun markCleared(day: Int, today: CalendarDate) {
        val year = Season.adventYear(today)
        val current = clearedBits(today)
        prefs.edit()
            .putInt(KEY_SEASON_YEAR, year)
            .putInt(KEY_CLEARED, AdventBits.withCleared(current, day))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "block_escape_season"
        private const val KEY_LOOK = "seasonal_look"
        private const val KEY_SEASON_YEAR = "advent_season_year"
        private const val KEY_CLEARED = "advent_cleared_bits"
    }
}
