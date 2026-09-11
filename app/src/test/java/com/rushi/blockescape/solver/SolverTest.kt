package com.rushi.blockescape.solver

import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.domain.Orientation
import com.rushi.blockescape.domain.Vehicle
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
        assertEquals(null, result.nextMove)
    }

    // -- Hint (Solver.Move / Result.nextMove) --------------------------------------------

    @Test
    fun `an already-solved board has no hint`() {
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 4, isPrimary = true)
            )
        )
        val result = Solver.solve(board)
        assertEquals(null, result.nextMove)
        assertEquals(null, Solver.hintMove(board))
    }

    @Test
    fun `a board one move from solved hints the primary's own winning move`() {
        // primary at row 2, occupying col3-4 (front=4); width=6, exitRow=2, so sliding it
        // right by 1 (front=5) wins outright - no blocker to clear first.
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 3, isPrimary = true)
            )
        )
        val result = Solver.solve(board)
        assertTrue(result.solvable)
        assertEquals(1, result.minMoves)
        assertEquals(Solver.Move("primary", 1), result.nextMove)
        assertEquals(Solver.Move("primary", 1), Solver.hintMove(board))
    }

    @Test
    fun `two-move puzzle hints moving the blocker out of the way first`() {
        // Same board as the "two-move puzzle" test above. Reasoned by hand: the blocker
        // (rows1-3, col4) only fully clears row 2 - where primary needs to travel - by
        // sliding down 2 (to rows3-5); sliding down 1 or up 1 still leaves it covering
        // row 2. Primary itself can shuffle right within its own row but can never reach
        // the exit without the blocker moving first, so the unique 2-move solution is
        // blocker+2 then primary+4, making "blocker, delta=+2" the correct hint.
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
                Vehicle("blocker", Orientation.VERTICAL, length = 3, row = 1, col = 4)
            )
        )
        val result = Solver.solve(board)
        assertTrue(result.solvable)
        assertEquals(2, result.minMoves)
        assertEquals(Solver.Move("blocker", 2), result.nextMove)
        assertEquals(Solver.Move("blocker", 2), Solver.hintMove(board))

        // And it's genuinely useful: taking exactly this move leaves a board solvable in
        // one fewer move than the original.
        val afterHint = board.move("blocker", 2)
        assertEquals(1, Solver.solve(afterHint).minMoves)
    }

    @Test
    fun `an unsolvable board has no hint`() {
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 2, isPrimary = true),
                Vehicle("left", Orientation.VERTICAL, length = 6, row = 0, col = 1),
                Vehicle("right", Orientation.VERTICAL, length = 6, row = 0, col = 4)
            )
        )
        assertEquals(null, Solver.hintMove(board))
    }

    // For puzzles too large to hand-trace a specific expected move, this asserts the
    // property that actually matters for a hint: the hinted vehicle exists, the delta is
    // legal and nonzero, and - most importantly - taking exactly that move leaves a board
    // that is still solvable in precisely one fewer move. That confirms the hint is truly
    // the first step of a SHORTEST solution, not merely "a legal move somewhere".
    private fun assertHintIsOptimalFirstMove(board: Board) {
        val result = Solver.solve(board)
        assertTrue("board should be solvable", result.solvable)
        val hint = result.nextMove
        assertTrue("hint should be present for an unsolved, solvable board", hint != null)
        hint!!

        assertTrue(
            "hinted vehicle '${hint.vehicleId}' must exist on the board",
            board.vehicles.any { it.id == hint.vehicleId }
        )
        assertTrue("hinted delta must be nonzero", hint.delta != 0)
        val legalRange = board.legalMoves(hint.vehicleId)
        assertTrue(
            "hinted delta ${hint.delta} must be within legal range $legalRange",
            hint.delta in legalRange
        )

        val afterHint = board.move(hint.vehicleId, hint.delta)
        val afterResult = Solver.solve(afterHint)
        assertTrue("board after the hinted move must still be solvable", afterResult.solvable)
        assertEquals(result.minMoves - 1, afterResult.minMoves)
    }

    @Test
    fun `hint is an optimal first move for a 3-move shipped level (level_002)`() {
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
                Vehicle("blockerA", Orientation.VERTICAL, length = 2, row = 2, col = 3),
                Vehicle("blockerC", Orientation.HORIZONTAL, length = 2, row = 4, col = 2),
                Vehicle("blockerD", Orientation.HORIZONTAL, length = 2, row = 0, col = 3)
            )
        )
        assertEquals(3, Solver.solve(board).minMoves) // sanity-check against LevelPackTest's known curve
        assertHintIsOptimalFirstMove(board)
    }

    @Test
    fun `hint is an optimal first move for a 5-move shipped level (level_003)`() {
        val board = Board(
            exitRow = 3,
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 3, row = 3, col = 0, isPrimary = true),
                Vehicle("blockerA", Orientation.VERTICAL, length = 2, row = 3, col = 3),
                Vehicle("blockerUp", Orientation.HORIZONTAL, length = 2, row = 1, col = 2),
                Vehicle("downBlockA", Orientation.HORIZONTAL, length = 2, row = 5, col = 3),
                Vehicle("blockerB", Orientation.VERTICAL, length = 2, row = 2, col = 5),
                Vehicle("helperB", Orientation.HORIZONTAL, length = 2, row = 1, col = 4),
                Vehicle("fixedDown", Orientation.HORIZONTAL, length = 2, row = 4, col = 4)
            )
        )
        assertEquals(5, Solver.solve(board).minMoves) // sanity-check against LevelPackTest's known curve
        assertHintIsOptimalFirstMove(board)
    }
}
