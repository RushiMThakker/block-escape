package com.rushi.blockescape.tools

import com.rushi.blockescape.level.LevelDto
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One curated-quality candidate from the generator, with the numbers curation ranks on. */
@Serializable
data class PoolEntry(
    val minMoves: Int,
    val searchSize: Int,
    val blockers: Int,
    val firstMoves: Int,
    val vehicleCount: Int,
    val longVehicles: Int,
    val level: LevelDto
)

/** Board identity that ignores vehicle ids, vehicle order and the vertical mirror image. */
fun canonicalKey(level: LevelDto): String {
    fun form(flip: Boolean): String {
        val exit = if (flip) GRID - 1 - level.exitRow else level.exitRow
        val parts = level.vehicles.map { v ->
            val vertical = v.orientationDto.name == "vertical"
            val row = if (!flip) v.row else if (vertical) GRID - v.row - v.length else GRID - 1 - v.row
            "${if (v.isPrimary) "P" else "o"}${if (vertical) "v" else "h"}${v.length}@$row,${v.col}"
        }.sorted()
        return "x$exit|" + parts.joinToString(";")
    }
    val a = form(false)
    val b = form(true)
    return if (a < b) a else b
}

/**
 * Identity of the puzzle's vehicle set irrespective of where the vehicles currently sit: the
 * same blocks rearranged are the same puzzle to a player, so curation allows one per layout.
 */
fun layoutId(level: LevelDto): String {
    val parts = level.vehicles.map { v ->
        val vertical = v.orientationDto.name == "vertical"
        "${if (v.isPrimary) "P" else "o"}${if (vertical) "v${v.col}" else "h${v.row}"}x${v.length}"
    }.sorted()
    return "x${level.exitRow}|" + parts.joinToString(";")
}

object PoolGenerator {
    /**
     * Random layouts -> exhaustive state-space analysis -> sample boards at each distance.
     * Because every state's true distance to the exit is known, minMoves is exact by
     * construction (re-checked with the real Solver before anything ships).
     */
    fun generate(
        seconds: Int,
        seed: Long,
        minMovesFloor: Int,
        perBucket: Int,
        existing: Set<String>,
        log: (String) -> Unit = {}
    ): List<PoolEntry> {
        val deadline = System.currentTimeMillis() + seconds * 1000L
        val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply { addAll(existing) }
        val out = java.util.concurrent.ConcurrentLinkedQueue<PoolEntry>()
        val layouts = java.util.concurrent.atomic.AtomicInteger()
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 6)

        fun worker(workerSeed: Long) {
            val rnd = Random(workerSeed)
            var current: Triple<Layout, Long, Int>? = null
            while (System.currentTimeMillis() < deadline) {
                // Mostly hill-climb from the hardest layout found so far: hard boards are rare in
                // purely random layouts, but one changed vehicle often keeps them hard.
                val cur = current
                val made = if (cur == null || rnd.nextInt(8) == 0) LayoutFactory.random(rnd, rnd.nextInt(9, 14))
                else LayoutFactory.mutate(cur.first, cur.second, rnd)
                val (layout, solved) = made ?: continue
                val space = StateSpace(layout)
                val analysis = space.analyse(solved, maxStates = 300_000) ?: continue
                layouts.incrementAndGet()
                if (analysis.moved.count { it } < layout.size * 0.75) continue

                val byDist = HashMap<Int, ArrayList<Long>>()
                var maxDist = 0
                for ((key, d) in analysis.dist) {
                    if (space.pos(key, 0) > 1) continue
                    if (d > maxDist) maxDist = d
                    if (d < minMovesFloor) continue
                    byDist.getOrPut(d) { ArrayList() }.add(key)
                }
                if (cur == null || maxDist >= cur.third - 1) current = Triple(layout, solved, maxDist)

                for ((d, keys) in byDist) {
                    keys.shuffle(rnd)
                    var taken = 0
                    for (key in keys) {
                        if (taken >= perBucket) break
                        val blockers = space.blockers(key)
                        val first = space.firstMoveCount(key)
                        if (blockers < 2 || first < 4) continue
                        val level = space.toLevel(key, "candidate", d)
                        if (!seen.add(canonicalKey(level))) continue
                        out.add(
                            PoolEntry(
                                minMoves = d,
                                searchSize = space.searchSize(key),
                                blockers = blockers,
                                firstMoves = first,
                                vehicleCount = layout.size,
                                longVehicles = layout.length.count { it == 3 },
                                level = level
                            )
                        )
                        taken++
                    }
                }
            }
        }

        val pool = (0 until threads).map { t -> Thread { worker(seed * 1000 + t) }.also { it.start() } }
        pool.forEach { it.join() }
        log("threads=$threads layouts analysed=${layouts.get()} candidates=${out.size}")
        return out.toList()
    }
}

/** A run of consecutive levels whose target difficulty ramps linearly from [fromMoves] to [toMoves]. */
data class PackSpec(val name: String, val count: Int, val fromMoves: Int, val toMoves: Int, val startIndex: Int = 1)

object Curator {
    /**
     * Picks one board per slot. For each target move count it prefers boards that add variety
     * (different vehicle count, exit row, number of long vehicles) over the ones already chosen,
     * and then larger search spaces and more blockers - both of which feel harder at equal minMoves.
     */
    fun select(pool: List<PoolEntry>, spec: PackSpec, avoidLayouts: Set<String> = emptySet()): List<PoolEntry> {
        val remaining = pool.toMutableList()
        val chosen = ArrayList<PoolEntry>()
        for (i in 0 until spec.count) {
            val target = if (spec.count == 1) spec.fromMoves
            else Math.round(spec.fromMoves + (spec.toMoves - spec.fromMoves) * i.toDouble() / (spec.count - 1)).toInt()
            // Never reuse a vehicle layout: the same blocks rearranged read as the same puzzle.
            val fresh = remaining.filter { r -> layoutId(r.level) !in avoidLayouts && chosen.none { layoutId(it.level) == layoutId(r.level) } }
            val best = fresh
                .filter { it.minMoves == target }
                .maxByOrNull { score(it, chosen) }
                ?: fresh.minByOrNull { Math.abs(it.minMoves - target) }
                ?: error("pool exhausted for ${spec.name}")
            chosen.add(best)
            remaining.remove(best)
        }
        // A smooth ramp: order by minMoves, then by how much there is to search.
        return chosen.sortedWith(compareBy({ it.minMoves }, { it.searchSize }))
    }

    private fun score(c: PoolEntry, chosen: List<PoolEntry>): Double {
        var s = c.searchSize / 1000.0 + c.blockers
        if (chosen.none { it.vehicleCount == c.vehicleCount }) s += 3.0
        if (chosen.none { it.level.exitRow == c.level.exitRow }) s += 2.0
        if (chosen.none { it.longVehicles == c.longVehicles }) s += 2.0
        return s
    }
}
