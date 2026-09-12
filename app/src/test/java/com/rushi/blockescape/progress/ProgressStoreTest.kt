package com.rushi.blockescape.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers `nextClearedCount`, the pure unlock-progression core factored out of
 * ProgressStore specifically so it's testable with plain JUnit - ProgressStore itself
 * wraps Android SharedPreferences, which this project's test setup has no
 * Context/Robolectric/androidTest infrastructure for (see LevelPackTest/GameViewModelTest
 * for the same "keep business logic separate from Android I/O so it stays testable"
 * pattern elsewhere in this codebase).
 *
 * An earlier version of this function returned a "highest unlocked index" capped at
 * `totalLevels - 1`, which made clearing the final level in the pack indistinguishable
 * from merely having unlocked it (both produced the same capped index) - so the last
 * level could never show as "cleared" in the level-select grid. Several tests below
 * specifically cover that this no longer happens with the count-based model.
 */
class ProgressStoreTest {

    @Test
    fun `clearing the first level unlocks the second`() {
        assertEquals(1, nextClearedCount(currentClearedCount = 0, clearedLevelIndex = 0, totalLevels = 10))
    }

    @Test
    fun `clearing a level already below current count does not regress progress`() {
        // Player replays level 1 (index 0) after already having cleared through index 2
        // (clearedCount = 3) - stored progress must not fall back down.
        assertEquals(3, nextClearedCount(currentClearedCount = 3, clearedLevelIndex = 0, totalLevels = 10))
    }

    @Test
    fun `clearing the current frontier level advances progress by exactly one`() {
        assertEquals(4, nextClearedCount(currentClearedCount = 3, clearedLevelIndex = 3, totalLevels = 10))
    }

    @Test
    fun `clearing the second-to-last level unlocks (but does not clear) the last level`() {
        // totalLevels = 10 -> valid indices are 0..9. Clearing index 8 should leave index
        // 9 unlocked (9 <= 9) but not yet cleared (9 is not < 9).
        val updated = nextClearedCount(currentClearedCount = 8, clearedLevelIndex = 8, totalLevels = 10)
        assertEquals(9, updated)
        assertTrue("index 9 should be unlocked", 9 <= updated)
        assertFalse("index 9 should not yet be cleared", 9 < updated)
    }

    @Test
    fun `clearing the actual last level marks it cleared, not just unlocked`() {
        // Regression test for the original bug: clearing index 9 (the last level, having
        // already cleared 0-8 so currentClearedCount = 9) must push the count to 10, so
        // that index 9 finally satisfies "index < clearedCount" and shows as cleared -
        // not stay stuck at 9 forever, which is what an index-capped model produced.
        val updated = nextClearedCount(currentClearedCount = 9, clearedLevelIndex = 9, totalLevels = 10)
        assertEquals(10, updated)
        assertTrue("the last level must now show as cleared", 9 < updated)
    }

    @Test
    fun `a single-level pack can still show its only level as cleared`() {
        // Same bug pattern as the last-level case above, at the smallest possible pack
        // size: clearing the one and only level must make it show as cleared, not get
        // stuck at "just unlocked" forever.
        val updated = nextClearedCount(currentClearedCount = 0, clearedLevelIndex = 0, totalLevels = 1)
        assertEquals(1, updated)
        assertTrue("the only level must show as cleared", 0 < updated)
    }

    @Test
    fun `clearing a level far ahead of current count still caps at totalLevels`() {
        // Defensive case: shouldn't happen via normal play (levels unlock strictly in
        // order), but the cap must hold regardless of which index is reported cleared.
        // Capped at totalLevels (not totalLevels - 1): unlike an index, a count is never
        // used to look up an array element, so it can safely reach the full pack size.
        assertEquals(10, nextClearedCount(currentClearedCount = 0, clearedLevelIndex = 999, totalLevels = 10))
    }
}
