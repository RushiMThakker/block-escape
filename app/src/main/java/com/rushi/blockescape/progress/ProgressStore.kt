package com.rushi.blockescape.progress

import android.content.Context

/**
 * Pure unlock-progression logic, deliberately kept separate from the SharedPreferences
 * I/O in [ProgressStore] below so it can be unit-tested with plain JUnit - no
 * Context/Robolectric/androidTest infrastructure needed. Same separation-of-concerns
 * pattern this project already uses for GameViewModel (pure domain state) vs. the
 * UI/Activity layer (Android framework I/O).
 *
 * This project's level progression is strictly linear (no branching), so a single
 * "highest unlocked index" integer is enough to derive both lock state and
 * cleared/checkmark state for every level - no need for a full Set<Int>/Set<String> of
 * individually-tracked levels.
 *
 * Given the currently-stored highest-unlocked index and the index of a level that was
 * just cleared, returns the new highest-unlocked index: clearing level [clearedIndex]
 * unlocks [clearedIndex] + 1, capped so it never points past the last valid level index
 * ([totalLevels] - 1), and never regresses (replaying an already-cleared level, or
 * clearing a level below the current highest, must never lower stored progress).
 */
fun nextHighestUnlockedIndex(currentHighest: Int, clearedLevelIndex: Int, totalLevels: Int): Int {
    val lastValidIndex = (totalLevels - 1).coerceAtLeast(0)
    val unlockedByThisClear = (clearedLevelIndex + 1).coerceAtMost(lastValidIndex)
    return maxOf(currentHighest, unlockedByThisClear)
}

/**
 * Thin, Context-based wrapper around Android SharedPreferences storing a single "highest
 * unlocked level index" (0-based; 0 = only level 1 unlocked, the default for a fresh
 * install). SharedPreferences is the simplest fit for "one small integer" - no need for
 * DataStore/Room here, consistent with this project's general preference for the
 * simplest thing that works over speculative infrastructure (see LevelPack.kt).
 *
 * Local-only for now: progress lives on this device and does not sync anywhere. Cloud/
 * global sync is a known future want, explicitly out of scope for this pass.
 */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The highest unlocked level index (0-based). Defaults to 0 (only level 1 unlocked). */
    fun highestUnlockedIndex(): Int = prefs.getInt(KEY_HIGHEST_UNLOCKED_INDEX, 0)

    /**
     * Call when [levelIndex] is won. Unlocks the next level, capped at [totalLevels] - 1.
     * Only writes when the computed value is actually higher than what's currently
     * stored, so replaying an already-cleared level (or any level at or below the
     * current highest) never triggers a pointless write.
     */
    fun markLevelCleared(levelIndex: Int, totalLevels: Int) {
        val current = highestUnlockedIndex()
        val updated = nextHighestUnlockedIndex(current, levelIndex, totalLevels)
        if (updated > current) {
            prefs.edit().putInt(KEY_HIGHEST_UNLOCKED_INDEX, updated).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "block_escape_progress"
        private const val KEY_HIGHEST_UNLOCKED_INDEX = "highest_unlocked_index"
    }
}
