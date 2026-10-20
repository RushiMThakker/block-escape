package com.rushi.blockescape.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.solver.Solver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GameViewModel(
    private val initialBoard: Board,
    // Where the hint search runs. Default keeps it off the main thread; tests pass an
    // immediate dispatcher so requestHint() completes synchronously.
    hintDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val hintScope = CoroutineScope(SupervisorJob() + hintDispatcher)
    private var hintJob: Job? = null

    // A search result may only be applied if nothing has invalidated its board since it started.
    // Every move/undo/restart bumps this generation under the same lock the result is applied
    // under, so a search that finishes just after the board changed can never write a stale hint.
    private val hintLock = Any()
    private var hintGeneration = 0

    // True while a hint search is running; the UI shows a short "thinking" state instead of freezing.
    private val _hintThinking = mutableStateOf(false)
    val hintThinking: State<Boolean> = _hintThinking


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

    // Increments on every requestHint() call, regardless of whether the resulting Move is
    // equal to the previous one. `hint` alone isn't enough for UI code that needs to react
    // to "a hint was just (re)requested" (e.g. playing a haptic tick) - Solver.Move is a
    // data class and Compose's default structural-equality state skips notifying observers
    // when a write doesn't actually change the value, so repeat-tapping Hint without moving
    // (same board -> same solver answer) would otherwise go unnoticed by anything keyed on
    // `hint` itself.
    private val _hintRequestCount = mutableStateOf(0)
    val hintRequestCount: State<Int> = _hintRequestCount

    /** Drops any running search and the shown hint: the board they were computed for is gone. */
    private fun clearHint() {
        synchronized(hintLock) {
            hintGeneration++
            hintJob?.cancel()
            hintJob = null
            _hintThinking.value = false
            _hint.value = null
        }
    }

    override fun onCleared() {
        hintScope.cancel()
    }

    fun attemptMove(vehicleId: String, delta: Int) {
        if (delta == 0) return
        val range = _board.value.legalMoves(vehicleId)
        if (delta !in range) return

        history.addLast(_board.value)
        _board.value = _board.value.move(vehicleId, delta)
        _moveCount.value += 1
        _isWon.value = _board.value.isSolved
        clearHint()
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        _board.value = previous
        _moveCount.value -= 1
        _isWon.value = _board.value.isSolved
        clearHint()
    }

    fun restart() {
        history.clear()
        _board.value = initialBoard
        _moveCount.value = 0
        _isWon.value = initialBoard.isSolved
        clearHint()
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
        val snapshot: Board
        val generation: Int
        synchronized(hintLock) {
            if (_hintThinking.value) return
            _hintThinking.value = true
            snapshot = _board.value
            generation = hintGeneration
        }
        hintJob = hintScope.launch {
            // A solver failure must not crash the game or leave the button stuck on "...": no hint.
            val move = try {
                Solver.hintMove(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            synchronized(hintLock) {
                // A move, undo or restart since the search began: its board is gone, drop the result.
                if (generation != hintGeneration) return@launch
                _hint.value = move
                _hintThinking.value = false
                _hintRequestCount.value += 1
            }
        }
    }
}
