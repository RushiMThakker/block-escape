package com.rushi.unblock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {

    private fun twoCarBoard(): Board = Board(
        width = 6,
        height = 6,
        exitRow = 2,
        vehicles = listOf(
            Vehicle(id = "primary", orientation = Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
            Vehicle(id = "blocker", orientation = Orientation.VERTICAL, length = 3, row = 1, col = 5)
        )
    )

    @Test
    fun `horizontal vehicle can slide right to the edge when unblocked`() {
        val board = twoCarBoard()
        assertEquals(0..3, board.legalMoves("primary"))
    }

    @Test
    fun `vertical blocker can slide down and clear the exit row`() {
        val board = twoCarBoard()
        assertEquals(-1..2, board.legalMoves("blocker"))
    }

    @Test
    fun `vehicle at the left wall cannot move further left`() {
        val board = twoCarBoard()
        assertEquals(0, board.legalMoves("primary").first)
    }

    @Test
    fun `move returns a new board with the vehicle shifted`() {
        val board = twoCarBoard()
        val moved = board.move("primary", 2)
        assertEquals(2, moved.vehicle("primary").col)
        assertEquals(0, board.vehicle("primary").col)
    }

    @Test
    fun `move throws for an illegal delta`() {
        val board = twoCarBoard()
        try {
            board.move("primary", 5)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `board is not solved before the primary vehicle reaches the right edge`() {
        assertFalse(twoCarBoard().isSolved)
    }

    @Test
    fun `board is solved once the primary vehicle's front reaches the right edge`() {
        val solved = twoCarBoard()
            .move("blocker", 2)
            .move("primary", 4)
        assertTrue(solved.isSolved)
    }
}
