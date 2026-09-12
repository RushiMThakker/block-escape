package com.rushi.blockescape.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [winOverlayMovesText] - the pure formatting logic behind the win
 * overlay's move-count/comparison line, tested directly with plain JUnit rather than
 * through Compose.
 */
class GameScreenTextTest {

    @Test
    fun `omits the comparison when best-move count is unavailable`() {
        assertEquals("Solved in 5 moves!", winOverlayMovesText(moveCount = 5, bestMoves = null))
    }

    @Test
    fun `shows the comparison when the player beat par`() {
        assertEquals("Solved in 5 moves! (Best: 2)", winOverlayMovesText(moveCount = 5, bestMoves = 2))
    }

    @Test
    fun `shows the comparison when the player matched par exactly`() {
        assertEquals("Solved in 2 moves! (Best: 2)", winOverlayMovesText(moveCount = 2, bestMoves = 2))
    }
}
