package com.rushi.blockescape.level

import com.rushi.blockescape.solver.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the full shipped level pack (assets/levels/level_00N.json) using the real
 * BFS solver — every level must actually be solvable, and difficulty (minMoves) should
 * genuinely progress rather than being guessed by hand. JSON is duplicated here as test
 * fixture strings (same pattern as LevelParserTest) rather than read from Android assets,
 * since this is a plain JVM unit test with no asset manager available.
 */
class LevelPackTest {

    private val level001 = """
        {
          "id": "level_001",
          "gridSize": 6,
          "exitRow": 2,
          "vehicles": [
            {"id": "primary", "orientation": "horizontal", "length": 2, "row": 2, "col": 0, "isPrimary": true},
            {"id": "blocker", "orientation": "vertical", "length": 3, "row": 1, "col": 4, "isPrimary": false}
          ]
        }
    """.trimIndent()

    private val level002 = """
        {
          "id": "level_002",
          "gridSize": 6,
          "exitRow": 2,
          "vehicles": [
            {"id": "primary", "orientation": "horizontal", "length": 2, "row": 2, "col": 0, "isPrimary": true},
            {"id": "blockerA", "orientation": "vertical", "length": 2, "row": 2, "col": 3, "isPrimary": false},
            {"id": "blockerC", "orientation": "horizontal", "length": 2, "row": 4, "col": 2, "isPrimary": false},
            {"id": "blockerD", "orientation": "horizontal", "length": 2, "row": 0, "col": 3, "isPrimary": false}
          ]
        }
    """.trimIndent()

    private val level003 = """
        {
          "id": "level_003",
          "gridSize": 6,
          "exitRow": 3,
          "vehicles": [
            {"id": "primary", "orientation": "horizontal", "length": 3, "row": 3, "col": 0, "isPrimary": true},
            {"id": "blockerA", "orientation": "vertical", "length": 2, "row": 3, "col": 3, "isPrimary": false},
            {"id": "blockerUp", "orientation": "horizontal", "length": 2, "row": 1, "col": 2, "isPrimary": false},
            {"id": "downBlockA", "orientation": "horizontal", "length": 2, "row": 5, "col": 3, "isPrimary": false},
            {"id": "blockerB", "orientation": "vertical", "length": 2, "row": 2, "col": 5, "isPrimary": false},
            {"id": "helperB", "orientation": "horizontal", "length": 2, "row": 1, "col": 4, "isPrimary": false},
            {"id": "fixedDown", "orientation": "horizontal", "length": 2, "row": 4, "col": 4, "isPrimary": false}
          ]
        }
    """.trimIndent()

    private val level004 = """
        {
          "id": "level_004",
          "gridSize": 6,
          "exitRow": 3,
          "vehicles": [
            {"id": "primary", "orientation": "horizontal", "length": 3, "row": 3, "col": 0, "isPrimary": true},
            {"id": "blockerA", "orientation": "vertical", "length": 2, "row": 3, "col": 3, "isPrimary": false},
            {"id": "blockerUp", "orientation": "horizontal", "length": 2, "row": 1, "col": 2, "isPrimary": false},
            {"id": "downBlockA", "orientation": "horizontal", "length": 2, "row": 5, "col": 3, "isPrimary": false},
            {"id": "blockerB", "orientation": "vertical", "length": 2, "row": 2, "col": 5, "isPrimary": false},
            {"id": "helperB", "orientation": "horizontal", "length": 2, "row": 1, "col": 4, "isPrimary": false},
            {"id": "fixedDown", "orientation": "horizontal", "length": 2, "row": 4, "col": 4, "isPrimary": false},
            {"id": "blockerMid", "orientation": "vertical", "length": 2, "row": 2, "col": 4, "isPrimary": false}
          ]
        }
    """.trimIndent()

    // Ordered the same way LevelPack.ORDERED_LEVEL_FILES ships them, so this test doubles
    // as a guard that the pack's difficulty genuinely progresses.
    private val pack = listOf(level001, level002, level003, level004)

    @Test
    fun `every shipped level parses`() {
        pack.forEach { json ->
            val level = LevelParser.parse(json)
            assertTrue(level.id.isNotBlank())
            assertTrue(level.vehicles.any { it.isPrimary })
        }
    }

    @Test
    fun `every shipped level is solvable by the real solver`() {
        pack.forEach { json ->
            val board = LevelParser.parse(json).toBoard()
            val result = Solver.solve(board)
            assertTrue("${LevelParser.parse(json).id} should be solvable", result.solvable)
            assertTrue("${LevelParser.parse(json).id} minMoves should be positive", result.minMoves > 0)
        }
    }

    @Test
    fun `difficulty progresses across the level pack`() {
        val minMoves = pack.map { Solver.solve(LevelParser.parse(it).toBoard()).minMoves }

        // level_001 is the known 2-move starter puzzle.
        assertEquals(2, minMoves[0])

        // Each subsequent level should require strictly more moves than the previous one —
        // a real (solver-verified) difficulty curve, not a guessed one.
        for (i in 1 until minMoves.size) {
            assertTrue(
                "level_00${i + 1} (minMoves=${minMoves[i]}) should be harder than level_00$i (minMoves=${minMoves[i - 1]})",
                minMoves[i] > minMoves[i - 1]
            )
        }

        // Sanity bounds on the overall curve per the design brief: nothing trivial (0/1),
        // and the last level should be a genuinely multi-step puzzle.
        assertTrue(minMoves.all { it >= 2 })
        assertTrue("final level should be a substantial puzzle", minMoves.last() >= 6)
    }
}
