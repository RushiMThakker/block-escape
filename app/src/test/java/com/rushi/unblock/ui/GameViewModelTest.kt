package com.rushi.unblock.ui

import com.rushi.unblock.domain.Board
import com.rushi.unblock.domain.Orientation
import com.rushi.unblock.domain.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
