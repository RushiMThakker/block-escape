package com.rushi.unblock.level

import com.rushi.unblock.domain.Orientation
import com.rushi.unblock.solver.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelParserTest {

    private val sampleJson = """
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

    @Test
    fun `parses vehicle fields and orientation correctly`() {
        val level = LevelParser.parse(sampleJson)
        assertEquals("level_001", level.id)
        assertEquals(6, level.gridSize)
        assertEquals(2, level.vehicles.size)
        assertEquals(Orientation.HORIZONTAL, level.vehicles[0].orientation)
        assertTrue(level.vehicles[0].isPrimary)
    }

    @Test
    fun `converts to a solvable Board`() {
        val board = LevelParser.parse(sampleJson).toBoard()
        val result = Solver.solve(board)
        assertTrue(result.solvable)
        assertEquals(2, result.minMoves)
    }
}
