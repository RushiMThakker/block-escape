package com.rushi.blockescape.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.solver.Solver

class GameViewModel(private val initialBoard: Board) : ViewModel() {

    private val history = ArrayDeque<Board>()

    private val _board = mutableStateOf(initialBoard)
    val board: State<Board> = _board

    private val _moveCount = mutableStateOf(0)
    val moveCount: State<Int> = _moveCount

    private val _isWon = mutableStateOf(initialBoard.isSolved)
    val isWon: State<Boolean> = _isWon

    // The currently displayed hint, if any. Computed on demand (requestHint) against the
    // CURRENT board - not initialBoard - since the player may have already made moves by
    // the time they ask for one. Cleared on any action that changes the board out from
    // under it (a successful move, undo, restart): a hint pointing at a board state the
    // player has since left is misleading rather than helpful, so it's better to make
    // them tap Hint again than to leave a stale/wrong highlight on screen.
    private val _hint = mutableStateOf<Solver.Move?>(null)
    val hint: State<Solver.Move?> = _hint

    fun attemptMove(vehicleId: String, delta: Int) {
        if (delta == 0) return
        val range = _board.value.legalMoves(vehicleId)
        if (delta !in range) return

        history.addLast(_board.value)
        _board.value = _board.value.move(vehicleId, delta)
        _moveCount.value += 1
        _isWon.value = _board.value.isSolved
        _hint.value = null
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        _board.value = previous
        _moveCount.value -= 1
        _isWon.value = _board.value.isSolved
        _hint.value = null
    }

    fun restart() {
        history.clear()
        _board.value = initialBoard
        _moveCount.value = 0
        _isWon.value = initialBoard.isSolved
        _hint.value = null
    }

    /**
     * Computes a hint (which vehicle to move next, and roughly which way) via the real
     * BFS solver run against the current board state, and stores it for display. Always
     * overwrites any previous hint with this fresh result - including null, for the edge
     * cases where the puzzle is already won or genuinely unsolvable from here and there is
     * nothing sensible to highlight - so a tap always reflects the solver's answer for the
     * board as it stands right now.
     */
    fun requestHint() {
        _hint.value = Solver.hintMove(_board.value)
    }
}
