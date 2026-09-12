package com.rushi.blockescape.progress

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers `nextHighestUnlockedIndex`, the pure unlock-progression core factored out of
 * ProgressStore specifically so it's testable with plain JUnit - ProgressStore itself
 * wraps Android SharedPreferences, which this project's test setup has no
 * Context/Robolectric/androidTest infrastructure for (see LevelPackTest/GameViewModelTest
 * for the same "keep business logic separate from Android I/O so it stays testable"
 * pattern elsewhere in this codebase).
 */
class ProgressStoreTest {

    @Test
    fun `clearing the first level unlocks the second`() {
        assertEquals(1, nextHighestUnlockedIndex(currentHighest = 0, clearedLevelIndex = 0, totalLevels = 10))
    }

    @Test
    fun `clearing a level already below current highest does not regress progress`() {
        // Player replays level 1 (index 0) after already having unlocked up through
        // index 3 - stored progress must not fall back down to 1.
        assertEquals(3, nextHighestUnlockedIndex(currentHighest = 3, clearedLevelIndex = 0, totalLevels = 10))
    }

    @Test
    fun `clearing the current highest-unlocked level advances progress by exactly one`() {
        assertEquals(4, nextHighestUnlockedIndex(currentHighest = 3, clearedLevelIndex = 3, totalLevels = 10))
    }

    @Test
    fun `clearing the last level never unlocks an index past the end of the pack`() {
        // totalLevels = 10 -> valid indices are 0..9. Clearing index 9 (the last level)
        // must not produce index 10.
        assertEquals(9, nextHighestUnlockedIndex(currentHighest = 8, clearedLevelIndex = 9, totalLevels = 10))
    }

    @Test
    fun `clearing the second-to-last level unlocks the last level exactly`() {
        assertEquals(9, nextHighestUnlockedIndex(currentHighest = 8, clearedLevelIndex = 8, totalLevels = 10))
    }

    @Test
    fun `a single-level pack never unlocks past index zero`() {
        assertEquals(0, nextHighestUnlockedIndex(currentHighest = 0, clearedLevelIndex = 0, totalLevels = 1))
    }

    @Test
    fun `clearing a level far ahead of current highest still caps at the last valid index`() {
        // Defensive case: shouldn't happen via normal play (levels unlock strictly in
        // order), but the cap must hold regardless of which index is reported cleared.
        assertEquals(9, nextHighestUnlockedIndex(currentHighest = 0, clearedLevelIndex = 999, totalLevels = 10))
    }
}
