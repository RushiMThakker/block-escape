package com.rushi.unblock.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.rushi.unblock.domain.Board

class GameViewModel(private val initialBoard: Board) : ViewModel() {

    private val history = ArrayDeque<Board>()

    private val _board = mutableStateOf(initialBoard)
    val board: State<Board> = _board

    private val _moveCount = mutableStateOf(0)
    val moveCount: State<Int> = _moveCount

    private val _isWon = mutableStateOf(initialBoard.isSolved)
    val isWon: State<Boolean> = _isWon

    fun attemptMove(vehicleId: String, delta: Int) {
        if (delta == 0) return
        val range = _board.value.legalMoves(vehicleId)
        if (delta !in range) return

        history.addLast(_board.value)
        _board.value = _board.value.move(vehicleId, delta)
        _moveCount.value += 1
        _isWon.value = _board.value.isSolved
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        _board.value = previous
        _moveCount.value -= 1
        _isWon.value = _board.value.isSolved
    }

    fun restart() {
        history.clear()
        _board.value = initialBoard
        _moveCount.value = 0
        _isWon.value = initialBoard.isSolved
    }
}
