package com.rushi.blockescape.tools

import com.rushi.blockescape.level.LevelDto
import com.rushi.blockescape.level.LevelParser
import com.rushi.blockescape.level.toBoard
import com.rushi.blockescape.solver.Solver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the offline level tooling, and doubles as its entry point: with
 * `-PgenMode=pool|select -PgenOut=<dir>` (see app/build.gradle.kts) the same JVM runs the
 * generator or the curator. Without those properties only the fast checks below run.
 */
class LevelToolsTest {

    private val pretty = Json { prettyPrint = true; encodeDefaults = true }

    private fun existingLevels(): List<LevelDto> =
        File("src/main/assets/levels").listFiles { f -> f.name.startsWith("level_") }!!
            .sortedBy { it.name }.map { LevelParser.parse(it.readText()) }

    @Test
    fun `fast engine agrees with the real solver`() {
        val pool = PoolGenerator.generate(
            seconds = 6, seed = 7, minMovesFloor = 4, perBucket = 2, existing = emptySet()
        )
        assertTrue("generator produced nothing in 6s", pool.isNotEmpty())
        // Check a spread of difficulties, including the hardest found.
        val sample = pool.groupBy { it.minMoves }.values.map { it.first() }.sortedBy { it.minMoves }
            .let { it.take(6) + it.takeLast(2) }.distinct()
        for (entry in sample) {
            val real = Solver.solve(entry.level.toBoard())
            assertTrue(real.solvable)
            assertEquals("minMoves mismatch for ${entry.level}", entry.minMoves, real.minMoves)
        }
    }

    @Test
    fun `canonical key ignores ids, order and vertical mirror`() {
        val a = LevelParser.parse(
            """{"id":"a","gridSize":6,"exitRow":2,"vehicles":[
              {"id":"primary","orientation":"horizontal","length":2,"row":2,"col":0,"isPrimary":true},
              {"id":"x","orientation":"vertical","length":3,"row":0,"col":3,"isPrimary":false},
              {"id":"y","orientation":"horizontal","length":2,"row":5,"col":1,"isPrimary":false}]}"""
        )
        // Same board, ids renamed and vehicles reordered.
        val renamed = LevelParser.parse(
            """{"id":"b","gridSize":6,"exitRow":2,"vehicles":[
              {"id":"q","orientation":"horizontal","length":2,"row":5,"col":1,"isPrimary":false},
              {"id":"primary","orientation":"horizontal","length":2,"row":2,"col":0,"isPrimary":true},
              {"id":"z","orientation":"vertical","length":3,"row":0,"col":3,"isPrimary":false}]}"""
        )
        // Mirrored top-to-bottom: exitRow 2 -> 3, vertical length 3 at row 0 -> row 3, row 5 -> row 0.
        val mirrored = LevelParser.parse(
            """{"id":"c","gridSize":6,"exitRow":3,"vehicles":[
              {"id":"primary","orientation":"horizontal","length":2,"row":3,"col":0,"isPrimary":true},
              {"id":"x","orientation":"vertical","length":3,"row":3,"col":3,"isPrimary":false},
              {"id":"y","orientation":"horizontal","length":2,"row":0,"col":1,"isPrimary":false}]}"""
        )
        val different = LevelParser.parse(
            """{"id":"d","gridSize":6,"exitRow":2,"vehicles":[
              {"id":"primary","orientation":"horizontal","length":2,"row":2,"col":0,"isPrimary":true},
              {"id":"x","orientation":"vertical","length":3,"row":0,"col":4,"isPrimary":false},
              {"id":"y","orientation":"horizontal","length":2,"row":5,"col":1,"isPrimary":false}]}"""
        )
        assertEquals(canonicalKey(a), canonicalKey(renamed))
        assertEquals(canonicalKey(a), canonicalKey(mirrored))
        assertNotEquals(canonicalKey(a), canonicalKey(different))
    }

    @Test
    fun `curator picks distinct boards on a rising ramp`() {
        val pool = PoolGenerator.generate(
            seconds = 8, seed = 11, minMovesFloor = 6, perBucket = 3, existing = emptySet()
        )
        val spec = PackSpec("t", count = 4, fromMoves = pool.minOf { it.minMoves }, toMoves = pool.minOf { it.minMoves } + 3)
        val picked = Curator.select(pool, spec)
        assertEquals(4, picked.size)
        assertEquals(picked.size, picked.map { canonicalKey(it.level) }.toSet().size)
        assertEquals(picked.map { it.minMoves }, picked.map { it.minMoves }.sorted())
    }

    /** Runner: not a check - only does anything when launched with -PgenMode. */
    @Test
    fun `tool runner`() {
        val mode = System.getProperty("blockescape.gen.mode") ?: return
        val outDir = File(System.getProperty("blockescape.gen.out") ?: error("genOut required")).apply { mkdirs() }
        when (mode) {
            "pool" -> {
                val seconds = (System.getProperty("blockescape.gen.seconds") ?: "120").toInt()
                val seed = (System.getProperty("blockescape.gen.seed") ?: "1").toLong()
                val floor = (System.getProperty("blockescape.gen.floor") ?: "6").toInt()
                val existing = existingLevels().map { canonicalKey(it) }.toSet()
                val pool = PoolGenerator.generate(seconds, seed, floor, perBucket = 3, existing = existing) { println("GEN $it") }
                File(outDir, "pool-$seed.json").writeText(Json.encodeToString(pool))
                println("GEN wrote ${pool.size} candidates; minMoves range ${pool.minOfOrNull { it.minMoves }}..${pool.maxOfOrNull { it.minMoves }}")
            }
            "select" -> {
                val specs = (System.getProperty("blockescape.gen.specs") ?: error("genSpecs required"))
                    .split(",").map { s ->
                        val f = s.split(":")
                        PackSpec(f[0], f[1].toInt(), f[2].toInt(), f[3].toInt(), f.getOrNull(4)?.toInt() ?: 1)
                    }
                val pool = outDir.listFiles { f -> f.name.startsWith("pool-") }!!
                    .flatMap { Json.decodeFromString<List<PoolEntry>>(it.readText()) }
                    .distinctBy { canonicalKey(it.level) }
                val used = HashSet<String>(existingLevels().map { canonicalKey(it) })
                val remaining = pool.filter { canonicalKey(it.level) !in used }.toMutableList()
                val usedLayouts = HashSet<String>(existingLevels().map { layoutId(it) })
                val selDir = File(outDir, "selected").apply { deleteRecursively(); mkdirs() }
                for (spec in specs) {
                    val picked = Curator.select(remaining, spec, usedLayouts)
                    remaining.removeAll(picked.toSet())
                    picked.forEach { usedLayouts.add(layoutId(it.level)) }
                    picked.forEachIndexed { i, e ->
                        // Final authority: the real solver must agree with the recorded number.
                        val real = Solver.solve(e.level.toBoard())
                        check(real.solvable && real.minMoves == e.minMoves) { "solver disagrees: ${real.minMoves} vs ${e.minMoves}" }
                        val number = spec.startIndex + i
                        val id = if (spec.name == "level") "level_%03d".format(number) else "${spec.name}_%02d".format(number)
                        File(selDir, "$id.json").writeText(pretty.encodeToString(e.level.copy(id = id)))
                        println("SEL $id minMoves=${e.minMoves} search=${e.searchSize} vehicles=${e.vehicleCount} long=${e.longVehicles} exitRow=${e.level.exitRow}")
                    }
                }
            }
            "solve" -> {
                // Writes the full optimal move list of shipped boards ("all" = every level and advent
                // board): "<H|V> row col length delta" per move, state at that moment, for scripted
                // on-device playthroughs.
                val requested = System.getProperty("blockescape.gen.level") ?: error("genLevel required")
                val names = if (requested == "all") {
                    listOf("levels", "advent").flatMap { d ->
                        File("src/main/assets/$d").listFiles { f -> f.name.endsWith(".json") }!!.map { it.nameWithoutExtension }
                    }.sorted()
                } else requested.split(",")
                for (name in names) {
                    val dir = if (name.startsWith("advent")) "advent" else "levels"
                    var board = LevelParser.parse(File("src/main/assets/$dir/$name.json").readText()).toBoard()
                    val lines = ArrayList<String>()
                    while (!board.isSolved) {
                        val move = Solver.hintMove(board) ?: error("unsolvable")
                        val v = board.vehicle(move.vehicleId)
                        lines.add("${if (v.orientation.name == "HORIZONTAL") "H" else "V"} ${v.row} ${v.col} ${v.length} ${move.delta}")
                        board = board.move(move.vehicleId, move.delta)
                    }
                    File(outDir, "solution-$name.txt").writeText(lines.joinToString("\n"))
                }
                println("GEN solved ${names.size} boards")
            }
            else -> error("unknown mode $mode")
        }
    }
}
