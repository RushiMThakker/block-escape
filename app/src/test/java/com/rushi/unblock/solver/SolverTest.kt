package com.rushi.unblock.solver

import com.rushi.unblock.domain.Board
import com.rushi.unblock.domain.Orientation
import com.rushi.unblock.domain.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverTest {

    @Test
    fun `already-solved board needs zero moves`() {
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 4, isPrimary = true)
            )
        )
        val result = Solver.solve(board)
        assertTrue(result.solvable)
        assertEquals(0, result.minMoves)
    }

    @Test
    fun `two-move puzzle is solved in two moves`() {
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
                Vehicle("blocker", Orientation.VERTICAL, length = 3, row = 1, col = 4)
            )
        )
        val result = Solver.solve(board)
        assertTrue(result.solvable)
        assertEquals(2, result.minMoves)
    }

    @Test
    fun `an impossible board is reported unsolvable`() {
        // Primary boxed in on both sides by vehicles that cannot move out of row 2.
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 2, isPrimary = true),
                Vehicle("left", Orientation.VERTICAL, length = 6, row = 0, col = 1),
                Vehicle("right", Orientation.VERTICAL, length = 6, row = 0, col = 4)
            )
        )
        val result = Solver.solve(board)
        assertTrue(!result.solvable)
        assertEquals(-1, result.minMoves)
    }
}
