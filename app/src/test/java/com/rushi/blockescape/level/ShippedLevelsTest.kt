package com.rushi.blockescape.level

import com.rushi.blockescape.solver.Solver
import com.rushi.blockescape.tools.canonicalKey
import com.rushi.blockescape.tools.layoutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reads the real asset files that ship in the APK (unit tests run with the module directory as
 * working directory) and holds the whole pack to the rules the level tooling curates for.
 */
class ShippedLevelsTest {

    private val assetsDir = File("src/main/assets/levels")

    private fun load(fileName: String): LevelDto {
        val file = File(assetsDir, fileName)
        assertTrue("$fileName is listed in LevelPack but missing from assets", file.exists())
        return LevelParser.parse(file.readText())
    }

    private val levels: List<LevelDto> by lazy { LevelPack.ORDERED_LEVEL_FILES.map(::load) }

    @Test
    fun `every listed level file exists and every asset file is listed`() {
        val listed = LevelPack.ORDERED_LEVEL_FILES.toSet()
        val onDisk = assetsDir.listFiles { f -> f.name.startsWith("level_") }!!.map { it.name }.toSet()
        assertEquals("assets and LevelPack disagree", onDisk, listed)
    }

    @Test
    fun `every level is solvable and its recorded minMoves matches the real solver`() {
        for ((i, level) in levels.withIndex()) {
            val recorded = level.minMoves
            assertTrue("level ${i + 1} has no recorded minMoves", recorded != null)
            val real = Solver.solve(level.toBoard())
            assertTrue("level ${i + 1} must be solvable", real.solvable)
            assertEquals("level ${i + 1} (${level.id}) recorded minMoves drifted", real.minMoves, recorded)
        }
    }

    @Test
    fun `difficulty never goes down across the pack`() {
        val moves = levels.map { it.minMoves!! }
        for (i in 1 until moves.size) {
            assertTrue(
                "level ${i + 1} (${moves[i]} moves) is easier than level $i (${moves[i - 1]} moves)",
                moves[i] >= moves[i - 1]
            )
        }
    }

    @Test
    fun `the original ten levels keep their curve so existing progress stays meaningful`() {
        assertEquals(listOf(2, 3, 5, 7, 8, 9, 11, 13, 13, 17), levels.take(10).map { it.minMoves })
    }

    @Test
    fun `no two levels are the same board, mirrored or relabelled`() {
        val keys = levels.map { canonicalKey(it) }
        assertEquals("duplicate board in pack", keys.size, keys.toSet().size)
    }

    @Test
    fun `new levels never reuse a vehicle layout`() {
        // The first ten predate the tooling; everything after them must be a genuinely new puzzle.
        val ids = levels.drop(10).map { layoutId(it) }
        assertEquals("two new levels are the same blocks rearranged", ids.size, ids.toSet().size)
    }

    @Test
    fun `level ids match their file names`() {
        LevelPack.ORDERED_LEVEL_FILES.forEachIndexed { i, name ->
            assertEquals(name.removeSuffix(".json"), levels[i].id)
        }
    }
}
