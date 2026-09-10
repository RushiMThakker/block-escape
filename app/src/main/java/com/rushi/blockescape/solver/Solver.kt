package com.rushi.blockescape.solver

import com.rushi.blockescape.domain.Board

object Solver {

    data class Result(val solvable: Boolean, val minMoves: Int)

    fun solve(initial: Board): Result {
        if (initial.isSolved) return Result(true, 0)

        val visited = mutableSetOf(stateKey(initial))
        var frontier = listOf(initial)
        var depth = 0

        while (frontier.isNotEmpty()) {
            depth++
            val next = mutableListOf<Board>()
            for (board in frontier) {
                for (vehicle in board.vehicles) {
                    val range = board.legalMoves(vehicle.id)
                    for (delta in range) {
                        if (delta == 0) continue
                        val moved = board.move(vehicle.id, delta)
                        if (moved.isSolved) return Result(true, depth)
                        val key = stateKey(moved)
                        if (visited.add(key)) next.add(moved)
                    }
                }
            }
            frontier = next
        }
        return Result(false, -1)
    }

    private fun stateKey(board: Board): String =
        board.vehicles.sortedBy { it.id }.joinToString(";") { "${it.id}:${it.row},${it.col}" }
}
