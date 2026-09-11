package com.rushi.blockescape.ui

import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.domain.Orientation
import com.rushi.blockescape.domain.Vehicle
import com.rushi.blockescape.solver.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewModelTest {

    private fun startBoard(): Board = Board(
        vehicles = listOf(
            Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
            Vehicle("blocker", Orientation.VERTICAL, length = 3, row = 1, col = 4)
        )
    )

    @Test
    fun `starts with move count zero and not won`() {
        val vm = GameViewModel(startBoard())
        assertEquals(0, vm.moveCount.value)
        assertFalse(vm.isWon.value)
    }

    @Test
    fun `a legal move updates the board and increments the counter`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("blocker", 2)
        assertEquals(1, vm.moveCount.value)
        assertEquals(3, vm.board.value.vehicle("blocker").row)
    }

    @Test
    fun `an illegal move is a no-op`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("primary", 99)
        assertEquals(0, vm.moveCount.value)
        assertEquals(0, vm.board.value.vehicle("primary").col)
    }

    @Test
    fun `undo reverts the last move and decrements the counter`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("blocker", 2)
        vm.undo()
        assertEquals(0, vm.moveCount.value)
        assertEquals(1, vm.board.value.vehicle("blocker").row)
    }

    @Test
    fun `undo on an empty history is a no-op`() {
        val vm = GameViewModel(startBoard())
        vm.undo()
        assertEquals(0, vm.moveCount.value)
    }

    @Test
    fun `restart resets to the initial board and clears the move count`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("blocker", 2)
        vm.attemptMove("primary", 4)
        vm.restart()
        assertEquals(0, vm.moveCount.value)
        assertEquals(0, vm.board.value.vehicle("primary").col)
        assertFalse(vm.isWon.value)
    }

    @Test
    fun `winning move sets isWon`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("blocker", 2)
        vm.attemptMove("primary", 4)
        assertTrue(vm.isWon.value)
    }

    // -- Hint -------------------------------------------------------------------------

    @Test
    fun `starts with no hint`() {
        val vm = GameViewModel(startBoard())
        assertNull(vm.hint.value)
    }

    @Test
    fun `requestHint computes the real solver's first move for the current board`() {
        val vm = GameViewModel(startBoard())
        vm.requestHint()
        // startBoard() is exactly the Solver "two-move puzzle" board: the solver's
        // known-correct first move is sliding the blocker down 2 to clear row 2.
        assertEquals(Solver.Move("blocker", 2), vm.hint.value)
    }

    @Test
    fun `requestHint is computed against the CURRENT board, not the original one`() {
        val vm = GameViewModel(startBoard())
        // Take the solver's own first move manually, so the blocker is already out of
        // the way; from here the correct hint should be the primary's own winning move,
        // not a repeat of the now-stale original-board hint.
        vm.attemptMove("blocker", 2)
        vm.requestHint()
        assertEquals(Solver.Move("primary", 4), vm.hint.value)
    }

    @Test
    fun `a successful move clears the current hint`() {
        val vm = GameViewModel(startBoard())
        vm.requestHint()
        assertTrue(vm.hint.value != null)
        vm.attemptMove("blocker", 2)
        assertNull(vm.hint.value)
    }

    @Test
    fun `an illegal move attempt does not clear the current hint`() {
        val vm = GameViewModel(startBoard())
        vm.requestHint()
        val hintBefore = vm.hint.value
        vm.attemptMove("primary", 99) // out of range, no-op
        assertEquals(hintBefore, vm.hint.value)
    }

    @Test
    fun `undo clears the current hint`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("blocker", 2)
        vm.requestHint()
        assertTrue(vm.hint.value != null)
        vm.undo()
        assertNull(vm.hint.value)
    }

    @Test
    fun `restart clears the current hint`() {
        val vm = GameViewModel(startBoard())
        vm.requestHint()
        assertTrue(vm.hint.value != null)
        vm.restart()
        assertNull(vm.hint.value)
    }

    @Test
    fun `requesting a hint on an already-won board yields no hint`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("blocker", 2)
        vm.attemptMove("primary", 4)
        assertTrue(vm.isWon.value)
        vm.requestHint()
        assertNull(vm.hint.value)
    }
}
