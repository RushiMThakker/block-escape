package com.rushi.blockescape.level

import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.domain.Orientation
import com.rushi.blockescape.domain.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure [bestMoveCounts] function - the part of LevelBestMoves.kt that
 * doesn't need a Context/asset manager, so it's tested directly with plain JUnit rather
 * than through [LevelBestMoves.getOrCompute]. Same split as ProgressStoreTest.kt testing
 * nextClearedCount directly instead of only through ProgressStore.
 */
class LevelBestMovesTest {

    @Test
    fun `maps each board to the solver's real minMoves, in order`() {
        val alreadySolved = Board(
            vehicles = listOf(Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 4, isPrimary = true))
        )
        val twoMove = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
                Vehicle("blocker", Orientation.VERTICAL, length = 3, row = 1, col = 4)
            )
        )

        // Order matters: this must line up 1:1 with the input list, since callers zip it
        // against LevelPack.ORDERED_LEVEL_FILES by index.
        assertEquals(listOf(0, 2), bestMoveCounts(listOf(alreadySolved, twoMove)))
        assertEquals(listOf(2, 0), bestMoveCounts(listOf(twoMove, alreadySolved)))
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList<Int>(), bestMoveCounts(emptyList()))
    }
}
