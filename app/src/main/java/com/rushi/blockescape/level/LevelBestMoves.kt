package com.rushi.blockescape.level

import android.content.Context
import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.solver.Solver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-possible (minimum) move count for each of [boards], in order, via the real BFS
 * solver (Solver.kt) - this is deliberately never hardcoded anywhere in app code, so the
 * displayed numbers can never drift out of sync if a level file is edited later.
 * (LevelPackTest.kt separately locks in today's known values as a *test* fixture, which is
 * fine - production code always asks the solver fresh.)
 *
 * Pure function over already-loaded boards, kept separate from the Context/asset-loading
 * and caching/threading concerns in [LevelBestMoves] below - same pure-logic/Context-
 * wrapper split as ProgressStore.kt's nextClearedCount.
 */
fun bestMoveCounts(boards: List<Board>): List<Int> = boards.map { Solver.solve(it).minMoves }

/**
 * Process-lifetime cache + off-main-thread runner for [bestMoveCounts] over the shipped
 * level pack ([LevelPack.ORDERED_LEVEL_FILES]).
 *
 * Measured, not assumed: a JVM unit test timing this exact call (solving today's 10-level
 * pack end-to-end, up to 17 moves / 12 vehicles) took on the order of a few hundred
 * milliseconds total, with individual levels ranging from ~5ms to over 100ms depending on
 * JIT/interpreter warmup. On-device (emulator) it was slower still - tens of seconds on a
 * cold first run, almost certainly interpreter/GC overhead specific to that constrained
 * environment rather than the algorithm itself, but real signal that "probably fine
 * inline" was the wrong call: either number is well past what's safe to run synchronously
 * on the composition/main thread when the level-select screen first appears, which is
 * exactly what this off-main-thread + cache design avoids - confirmed on-device to add no
 * visible stutter/jank, the grid renders and stays responsive instantly either way, with
 * tiles simply picking up their "Best: N" footnote a little late on the very first launch
 * of a process.
 *
 * `object` = one instance for the life of the process, so the cached result survives
 * navigating back and forth between level-select and gameplay (not just recomposition),
 * and even activity recreation (e.g. rotation), without ever re-running the solver a
 * second time in the same app session. `synchronized` guards the compute step itself
 * (not just the cache read) so two callers racing to be first - e.g. a config change
 * landing mid-computation - await/share one BFS pass instead of each running their own.
 */
object LevelBestMoves {
    @Volatile private var cache: List<Int>? = null
    private val lock = Any()

    /**
     * Returns the cached result immediately if already computed; otherwise loads every
     * level's [Board] and runs the solver on [Dispatchers.Default] (never the caller's
     * thread), caching the result for the rest of the process's lifetime.
     */
    suspend fun getOrCompute(context: Context): List<Int> {
        cache?.let { return it }
        return withContext(Dispatchers.Default) {
            synchronized(lock) {
                cache ?: run {
                    val boards = LevelPack.ORDERED_LEVEL_FILES.map { fileName ->
                        LevelRepository(context).loadLevel(fileName)
                    }
                    bestMoveCounts(boards).also { cache = it }
                }
            }
        }
    }
}
