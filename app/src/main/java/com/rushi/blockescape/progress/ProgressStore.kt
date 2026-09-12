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
 * "count of levels cleared" integer is enough to derive both lock state and
 * cleared/checkmark state for every level - no need for a full Set<Int>/Set<String> of
 * individually-tracked levels.
 *
 * Deliberately a COUNT, not a "highest unlocked index": an earlier version of this
 * function returned an index capped at `totalLevels - 1`, which meant clearing the very
 * last level in the pack was indistinguishable from merely having unlocked it (both
 * produced the same capped index) - the last level could never show as "cleared" in the
 * level-select grid. A count has no such collision: clearing level i (0-based) always
 * means i+1 levels are now cleared, up to and including totalLevels itself once the
 * whole pack is beaten, with no cap needed since a count (unlike an index) is never used
 * to look up an array element.
 *
 * A level i (0-based) is unlocked iff `i <= clearedCount`, and counts as cleared iff
 * `i < clearedCount`.
 *
 * Given the currently-stored cleared count and the index of a level that was just
 * cleared, returns the new cleared count: clearing level [clearedLevelIndex] means at
 * least [clearedLevelIndex] + 1 levels are now cleared (capped at [totalLevels], the
 * count reached once every level has been beaten), and this never regresses (replaying
 * an already-cleared level, or clearing a level below the current count, must never
 * lower stored progress).
 */
fun nextClearedCount(currentClearedCount: Int, clearedLevelIndex: Int, totalLevels: Int): Int {
    val clearedByThis = (clearedLevelIndex + 1).coerceAtMost(totalLevels.coerceAtLeast(0))
    return maxOf(currentClearedCount, clearedByThis)
}

/**
 * Thin, Context-based wrapper around Android SharedPreferences storing a single "count of
 * levels cleared" (0 = nothing cleared yet, the default for a fresh install - only level 1
 * is unlocked in that state). SharedPreferences is the simplest fit for "one small
 * integer" - no need for DataStore/Room here, consistent with this project's general
 * preference for the simplest thing that works over speculative infrastructure (see
 * LevelPack.kt).
 *
 * Local-only for now: progress lives on this device and does not sync anywhere. Cloud/
 * global sync is a known future want, explicitly out of scope for this pass.
 */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** How many levels have been cleared (0-based count). Defaults to 0. */
    fun clearedCount(): Int = prefs.getInt(KEY_CLEARED_COUNT, 0)

    /** Level [levelIndex] (0-based) is unlocked/playable iff this returns true. */
    fun isUnlocked(levelIndex: Int): Boolean = levelIndex <= clearedCount()

    /** Level [levelIndex] (0-based) has been cleared at least once iff this returns true. */
    fun isCleared(levelIndex: Int): Boolean = levelIndex < clearedCount()

    /**
     * Call when [levelIndex] is won. Only writes when the computed count is actually
     * higher than what's currently stored, so replaying an already-cleared level (or any
     * level at or below the current count) never triggers a pointless write.
     */
    fun markLevelCleared(levelIndex: Int, totalLevels: Int) {
        val current = clearedCount()
        val updated = nextClearedCount(current, levelIndex, totalLevels)
        if (updated > current) {
            prefs.edit().putInt(KEY_CLEARED_COUNT, updated).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "block_escape_progress"
        private const val KEY_CLEARED_COUNT = "cleared_count"
    }
}
