package com.rushi.blockescape.solver

import com.rushi.blockescape.domain.Board

object Solver {

    /** A single vehicle move: move [vehicleId] by [delta] cells along its own axis. */
    data class Move(val vehicleId: String, val delta: Int)

    /**
     * [nextMove] is the first move of a shortest solution from the board passed to
     * [solve] (null when the board is already solved, or unsolvable). Defaulted so
     * existing call sites that only read [solvable]/[minMoves] are unaffected.
     *
     * When multiple first moves tie for the same shortest-path length, which one ends up
     * here is an unspecified artifact of vehicle/delta iteration order, not a chosen
     * "best" move among ties — any of them is a legitimate optimal hint, but don't rely
     * on a specific tie always winning.
     */
    data class Result(val solvable: Boolean, val minMoves: Int, val nextMove: Move? = null)

    // A BFS frontier node: a reachable board state, tagged with the very first move that
    // was made (from the ORIGINAL `initial` board) to start the path leading here. This
    // tag is carried forward unchanged as the BFS expands outward — it never needs to be
    // recomputed — so by the time a solved state is found, the tag attached to it already
    // *is* the first move of that shortest solution. This avoids reconstructing a path by
    // walking parent pointers backward: the "parent pointer" that matters (the first
    // edge out of the root) is tracked directly, once, as each node is born.
    private data class Node(val board: Board, val firstMove: Move)

    fun solve(initial: Board): Result {
        if (initial.isSolved) return Result(true, 0, null)

        val visited = mutableSetOf(stateKey(initial))
        // Seed frontier: every board one move away from `initial`, each tagged with the
        // move that produced it. Mirrors the original algorithm's first loop iteration
        // exactly (same traversal order, same visited-set semantics, same immediate
        // return the instant a solved state is generated) so `solvable`/`minMoves` are
        // unchanged from the previous implementation.
        var frontier: List<Node> = buildList {
            for (vehicle in initial.vehicles) {
                val range = initial.legalMoves(vehicle.id)
                for (delta in range) {
                    if (delta == 0) continue
                    val moved = initial.move(vehicle.id, delta)
                    val move = Move(vehicle.id, delta)
                    if (moved.isSolved) return Result(true, 1, move)
                    if (visited.add(stateKey(moved))) add(Node(moved, move))
                }
            }
        }
        var depth = 1

        while (frontier.isNotEmpty()) {
            depth++
            val next = mutableListOf<Node>()
            for (node in frontier) {
                for (vehicle in node.board.vehicles) {
                    val range = node.board.legalMoves(vehicle.id)
                    for (delta in range) {
                        if (delta == 0) continue
                        val moved = node.board.move(vehicle.id, delta)
                        if (moved.isSolved) return Result(true, depth, node.firstMove)
                        val key = stateKey(moved)
                        if (visited.add(key)) next.add(Node(moved, node.firstMove))
                    }
                }
            }
            frontier = next
        }
        return Result(false, -1, null)
    }

    /**
     * Convenience wrapper for callers (e.g. the hint feature) that only care about the
     * next move to make, not the full [Result]. Delegates to [solve] rather than running
     * a second BFS, so there is exactly one traversal implementation to keep correct.
     */
    fun hintMove(board: Board): Move? = solve(board).nextMove

    private fun stateKey(board: Board): String =
        board.vehicles.sortedBy { it.id }.joinToString(";") { "${it.id}:${it.row},${it.col}" }
}
