package com.rushi.blockescape.tools

import com.rushi.blockescape.level.LevelDto
import com.rushi.blockescape.level.OrientationDto
import com.rushi.blockescape.level.VehicleDto
import kotlin.random.Random

/**
 * Offline level-design tooling (test sources only, never shipped in the APK).
 *
 * The app's [com.rushi.blockescape.solver.Solver] is a clear, allocation-heavy BFS - right
 * for one hint at a time, far too slow for evaluating tens of thousands of candidate boards.
 * This file holds a compact bit-packed equivalent used ONLY to search for candidates. Every
 * board that is actually kept is re-solved with the real Solver (see LevelToolsTest), so a
 * bug here can never ship a wrong minMoves.
 *
 * Move semantics match Board.legalMoves: sliding a vehicle any number of free cells along its
 * axis counts as ONE move.
 */
const val GRID = 6

/** Fixed part of a board: which vehicles exist and the lane each slides in. Index 0 is the primary. */
class Layout(
    val exitRow: Int,
    val horizontal: BooleanArray,
    val length: IntArray,
    val lane: IntArray
) {
    val size: Int get() = length.size
}

class StateSpace(val layout: Layout) {
    val n = layout.size

    /** masks[vehicle][position] = occupied cells (bit row*6+col) when the vehicle sits at that position. */
    val masks: Array<LongArray> = Array(n) { i ->
        LongArray(GRID - layout.length[i] + 1) { p ->
            var m = 0L
            for (k in 0 until layout.length[i]) {
                val r = if (layout.horizontal[i]) layout.lane[i] else p + k
                val c = if (layout.horizontal[i]) p + k else layout.lane[i]
                m = m or (1L shl (r * GRID + c))
            }
            m
        }
    }

    fun pos(key: Long, i: Int): Int = ((key shr (3 * i)) and 7L).toInt()
    fun withPos(key: Long, i: Int, p: Int): Long =
        (key and (7L shl (3 * i)).inv()) or (p.toLong() shl (3 * i))

    fun occupancy(key: Long): Long {
        var occ = 0L
        for (i in 0 until n) occ = occ or masks[i][pos(key, i)]
        return occ
    }

    fun isSolved(key: Long): Boolean = pos(key, 0) == GRID - layout.length[0]

    fun forEachNeighbor(key: Long, action: (neighbor: Long, vehicle: Int) -> Unit) {
        val occ = occupancy(key)
        for (i in 0 until n) {
            val p = pos(key, i)
            val base = occ and masks[i][p].inv()
            var q = p - 1
            while (q >= 0 && (masks[i][q] and base) == 0L) {
                action(withPos(key, i, q), i); q--
            }
            q = p + 1
            val max = GRID - layout.length[i]
            while (q <= max && (masks[i][q] and base) == 0L) {
                action(withPos(key, i, q), i); q++
            }
        }
    }

    /** Distance (in moves) from every state of the connected component to the nearest solved state. */
    class Analysis(val dist: HashMap<Long, Int>, val moved: BooleanArray)

    fun analyse(start: Long, maxStates: Int): Analysis? {
        val seen = HashSet<Long>()
        val moved = BooleanArray(n)
        val queue = ArrayDeque<Long>()
        seen.add(start); queue.add(start)
        val solved = ArrayList<Long>()
        while (queue.isNotEmpty()) {
            val s = queue.removeFirst()
            if (isSolved(s)) solved.add(s)
            forEachNeighbor(s) { nk, v ->
                moved[v] = true
                if (seen.add(nk)) queue.add(nk)
            }
            if (seen.size > maxStates) return null
        }
        val dist = HashMap<Long, Int>(seen.size * 2)
        for (s in solved) { dist[s] = 0; queue.add(s) }
        while (queue.isNotEmpty()) {
            val s = queue.removeFirst()
            val d = dist.getValue(s)
            forEachNeighbor(s) { nk, _ -> if (dist.putIfAbsent(nk, d + 1) == null) queue.add(nk) }
        }
        return Analysis(dist, moved)
    }

    /** Number of states a forward BFS visits before reaching a solved state (a proxy for how much there is to search). */
    fun searchSize(start: Long): Int {
        val seen = HashSet<Long>()
        var frontier = listOf(start)
        seen.add(start)
        while (frontier.isNotEmpty()) {
            val next = ArrayList<Long>()
            for (s in frontier) {
                if (isSolved(s)) return seen.size
                forEachNeighbor(s) { nk, _ -> if (seen.add(nk)) next.add(nk) }
            }
            frontier = next
        }
        return seen.size
    }

    fun firstMoveCount(key: Long): Int {
        var c = 0
        forEachNeighbor(key) { _, _ -> c++ }
        return c
    }

    /** Distinct vehicles standing between the primary and the exit. */
    fun blockers(key: Long): Int {
        val primaryFront = pos(key, 0) + layout.length[0] - 1
        val ids = HashSet<Int>()
        for (c in primaryFront + 1 until GRID) {
            val bit = 1L shl (layout.exitRow * GRID + c)
            for (i in 1 until n) if ((masks[i][pos(key, i)] and bit) != 0L) ids.add(i)
        }
        return ids.size
    }

    fun toLevel(key: Long, id: String, minMoves: Int? = null): LevelDto = LevelDto(
        id = id,
        gridSize = GRID,
        exitRow = layout.exitRow,
        vehicles = (0 until n).map { i ->
            val p = pos(key, i)
            val h = layout.horizontal[i]
            VehicleDto(
                id = if (i == 0) "primary" else "v$i",
                orientationDto = if (h) OrientationDto.horizontal else OrientationDto.vertical,
                length = layout.length[i],
                row = if (h) layout.lane[i] else p,
                col = if (h) p else layout.lane[i],
                isPrimary = i == 0
            )
        },
        minMoves = minMoves
    )
}

object LayoutFactory {
    /** A random layout whose solved state (primary at the exit) is a legal, non-overlapping board, plus that state. */
    fun random(rnd: Random, vehicleCount: Int): Pair<Layout, Long>? {
        val exitRow = rnd.nextInt(1, 5)
        val primaryLen = if (rnd.nextInt(100) < 70) 2 else 3
        val horizontal = ArrayList<Boolean>().apply { add(true) }
        val length = ArrayList<Int>().apply { add(primaryLen) }
        val lane = ArrayList<Int>().apply { add(exitRow) }
        val startPos = ArrayList<Int>().apply { add(GRID - primaryLen) }

        var occ = 0L
        fun cells(h: Boolean, len: Int, ln: Int, p: Int): Long {
            var m = 0L
            for (k in 0 until len) {
                val r = if (h) ln else p + k
                val c = if (h) p + k else ln
                m = m or (1L shl (r * GRID + c))
            }
            return m
        }
        occ = occ or cells(true, primaryLen, exitRow, GRID - primaryLen)

        var attempts = 0
        while (length.size < vehicleCount && attempts < 400) {
            attempts++
            val h = rnd.nextBoolean()
            val len = if (rnd.nextInt(100) < 70) 2 else 3
            val ln = rnd.nextInt(GRID)
            val p = rnd.nextInt(GRID - len + 1)
            val m = cells(h, len, ln, p)
            if ((m and occ) != 0L) continue
            occ = occ or m
            horizontal.add(h); length.add(len); lane.add(ln); startPos.add(p)
        }
        if (length.size < vehicleCount - 1) return null
        val layout = Layout(exitRow, horizontal.toBooleanArray(), length.toIntArray(), lane.toIntArray())
        var key = 0L
        for (i in 0 until layout.size) key = key or (startPos[i].toLong() shl (3 * i))
        return layout to key
    }
}

/** Re-rolls one vehicle of an existing layout, keeping the solved state legal. Null if nothing fits. */
fun LayoutFactory.mutate(layout: Layout, key: Long, rnd: Random): Pair<Layout, Long>? {
    val n = layout.size
    if (n < 3) return null
    val drop = rnd.nextInt(1, n)
    val horizontal = ArrayList<Boolean>(); val length = ArrayList<Int>()
    val lane = ArrayList<Int>(); val start = ArrayList<Int>()
    var occ = 0L
    fun cells(h: Boolean, len: Int, ln: Int, p: Int): Long {
        var m = 0L
        for (k in 0 until len) {
            val r = if (h) ln else p + k
            val c = if (h) p + k else ln
            m = m or (1L shl (r * GRID + c))
        }
        return m
    }
    for (i in 0 until n) {
        if (i == drop) continue
        val p = ((key shr (3 * i)) and 7L).toInt()
        horizontal.add(layout.horizontal[i]); length.add(layout.length[i])
        lane.add(layout.lane[i]); start.add(p)
        occ = occ or cells(layout.horizontal[i], layout.length[i], layout.lane[i], p)
    }
    var placed = false
    repeat(60) {
        if (placed) return@repeat
        val h = rnd.nextBoolean()
        val len = if (rnd.nextInt(100) < 70) 2 else 3
        val ln = rnd.nextInt(GRID)
        val p = rnd.nextInt(GRID - len + 1)
        val m = cells(h, len, ln, p)
        if ((m and occ) == 0L) {
            horizontal.add(h); length.add(len); lane.add(ln); start.add(p); placed = true
        }
    }
    if (!placed) return null
    val out = Layout(layout.exitRow, horizontal.toBooleanArray(), length.toIntArray(), lane.toIntArray())
    var k = 0L
    for (i in 0 until out.size) k = k or (start[i].toLong() shl (3 * i))
    return out to k
}
