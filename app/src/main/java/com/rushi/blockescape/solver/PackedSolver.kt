package com.rushi.blockescape.solver

import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.domain.Orientation

/**
 * Fast path for [Solver.solve]: the same breadth-first search, on a bit-packed state.
 *
 * Each vehicle's position along its sliding axis takes 3 bits of a Long (so up to 21 vehicles on a
 * grid up to 8x8), and occupied cells are a bitmask, so expanding a state is a handful of integer
 * operations instead of building Board/List/String objects. On the 33-move boards this is the
 * difference between a hint that lands instantly and one that freezes a mid-range phone.
 *
 * It deliberately mirrors the reference implementation's traversal order (vehicles in list order,
 * deltas ascending, early exit the instant a solved state is generated), so `minMoves` and the
 * first move reported in `nextMove` are identical to the reference - SolverTest and
 * PackedSolverTest check this. Returns null for boards outside its limits so [Solver] falls back
 * to the generic implementation.
 */
internal object PackedSolver {
    private const val EMPTY = -1L

    fun solve(board: Board): Solver.Result? {
        val vehicles = board.vehicles
        val n = vehicles.size
        if (n == 0 || n > 21 || board.width > 8 || board.height > 8) return null
        val primary = vehicles.indexOfFirst { it.isPrimary }
        if (primary < 0) return null

        val horizontal = BooleanArray(n) { vehicles[it].orientation == Orientation.HORIZONTAL }
        val length = IntArray(n) { vehicles[it].length }
        // The primary can only be solved by sliding along its (exit) row to the right edge.
        if (!horizontal[primary] || vehicles[primary].row != board.exitRow) return null
        for (i in 0 until n) if (length[i] < 1) return null

        val maxPos = IntArray(n) { (if (horizontal[it]) board.width else board.height) - length[it] }
        if (maxPos.any { it < 0 }) return null
        val masks = Array(n) { i ->
            val v = vehicles[i]
            val lane = if (horizontal[i]) v.row else v.col
            LongArray(maxPos[i] + 1) { p ->
                var m = 0L
                for (k in 0 until length[i]) {
                    val r = if (horizontal[i]) lane else p + k
                    val c = if (horizontal[i]) p + k else lane
                    m = m or (1L shl (r * 8 + c))
                }
                m
            }
        }
        var start = 0L
        for (i in 0 until n) {
            val p = if (horizontal[i]) vehicles[i].col else vehicles[i].row
            if (p < 0 || p > maxPos[i]) return null
            start = start or (p.toLong() shl (3 * i))
        }
        val goalPos = maxPos[primary]

        fun pos(key: Long, i: Int) = ((key shr (3 * i)) and 7L).toInt()
        fun occupancy(key: Long): Long {
            var occ = 0L
            for (i in 0 until n) occ = occ or masks[i][pos(key, i)]
            return occ
        }

        val visited = LongSet()
        visited.add(start)

        var frontier = LongArray(1024)
        var frontierMove = IntArray(1024)
        var frontierSize = 0
        var next = LongArray(1024)
        var nextMove = IntArray(1024)
        var nextSize: Int

        // Move code: vehicle index * 64 + (delta + 32).
        fun move(code: Int) = Solver.Move(vehicles[code / 64].id, (code % 64) - 32)

        // Seed: every board one move from the start, each tagged with the move that made it.
        val startOcc = occupancy(start)
        for (i in 0 until n) {
            val p = pos(start, i)
            val base = startOcc and masks[i][p].inv()
            var lo = p
            while (lo > 0 && (masks[i][lo - 1] and base) == 0L) lo--
            var hi = p
            while (hi < maxPos[i] && (masks[i][hi + 1] and base) == 0L) hi++
            for (q in lo..hi) {
                if (q == p) continue
                val code = i * 64 + (q - p) + 32
                if (i == primary && q == goalPos) return Solver.Result(true, 1, move(code))
                val moved = (start and (7L shl (3 * i)).inv()) or (q.toLong() shl (3 * i))
                if (visited.add(moved)) {
                    if (frontierSize == frontier.size) {
                        frontier = frontier.copyOf(frontierSize * 2)
                        frontierMove = frontierMove.copyOf(frontierSize * 2)
                    }
                    frontier[frontierSize] = moved
                    frontierMove[frontierSize] = code
                    frontierSize++
                }
            }
        }

        var depth = 1
        while (frontierSize > 0) {
            depth++
            nextSize = 0
            for (f in 0 until frontierSize) {
                val key = frontier[f]
                val tag = frontierMove[f]
                val occ = occupancy(key)
                for (i in 0 until n) {
                    val p = pos(key, i)
                    val base = occ and masks[i][p].inv()
                    var lo = p
                    while (lo > 0 && (masks[i][lo - 1] and base) == 0L) lo--
                    var hi = p
                    while (hi < maxPos[i] && (masks[i][hi + 1] and base) == 0L) hi++
                    for (q in lo..hi) {
                        if (q == p) continue
                        if (i == primary && q == goalPos) return Solver.Result(true, depth, move(tag))
                        val moved = (key and (7L shl (3 * i)).inv()) or (q.toLong() shl (3 * i))
                        if (visited.add(moved)) {
                            if (nextSize == next.size) {
                                next = next.copyOf(nextSize * 2)
                                nextMove = nextMove.copyOf(nextSize * 2)
                            }
                            next[nextSize] = moved
                            nextMove[nextSize] = tag
                            nextSize++
                        }
                    }
                }
            }
            val swapKeys = frontier; frontier = next; next = swapKeys
            val swapTags = frontierMove; frontierMove = nextMove; nextMove = swapTags
            frontierSize = nextSize
        }
        return Solver.Result(false, -1, null)
    }

    /** Minimal primitive open-addressing set; boxing millions of Longs is what made the reference slow. */
    private class LongSet {
        private var table = LongArray(1 shl 14) { EMPTY }
        private var size = 0

        fun add(key: Long): Boolean {
            if (size * 2 >= table.size) grow()
            val added = insert(table, key)
            if (added) size++
            return added
        }

        private fun insert(t: LongArray, key: Long): Boolean {
            val mask = t.size - 1
            var h = ((key * -7046029254386353131L) ushr 32).toInt() and mask
            while (true) {
                val cur = t[h]
                if (cur == EMPTY) {
                    t[h] = key
                    return true
                }
                if (cur == key) return false
                h = (h + 1) and mask
            }
        }

        private fun grow() {
            val old = table
            table = LongArray(old.size * 2) { EMPTY }
            for (k in old) if (k != EMPTY) insert(table, k)
        }
    }
}
