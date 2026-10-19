package com.rushi.blockescape.solver

import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.level.LevelParser
import com.rushi.blockescape.level.toBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** The fast solver must give the reference solver's exact answer, including the hinted first move. */
class PackedSolverTest {

    private fun shippedBoards(): List<Pair<String, Board>> =
        listOf("src/main/assets/levels", "src/main/assets/advent").flatMap { dir ->
            File(dir).listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
                .map { it.name to LevelParser.parse(it.readText()).toBoard() }
        }

    @Test
    fun `identical to the reference on every shipped board`() {
        val boards = shippedBoards()
        assertTrue("expected 25 levels + 24 advent boards", boards.size >= 49)
        for ((name, board) in boards) {
            val fast = PackedSolver.solve(board)
            assertNotNull("$name should be inside the fast path's limits", fast)
            assertEquals("$name", Solver.solveGeneric(board), fast)
        }
    }

    @Test
    fun `identical to the reference on random mid-game positions`() {
        val rnd = Random(42)
        for ((name, start) in shippedBoards().filterIndexed { i, _ -> i % 4 == 0 }) {
            var board = start
            repeat(6) { step ->
                // Wander a few legal moves away from the start, as a player would.
                val movable = board.vehicles.filter { board.legalMoves(it.id).let { r -> r.first != 0 || r.last != 0 } }
                if (movable.isNotEmpty()) {
                    val v = movable.random(rnd)
                    val range = board.legalMoves(v.id)
                    val deltas = range.filter { it != 0 }
                    board = board.move(v.id, deltas.random(rnd))
                }
                if (!board.isSolved) {
                    assertEquals("$name after $step moves", Solver.solveGeneric(board), PackedSolver.solve(board))
                }
            }
        }
    }

    @Test
    fun `falls back instead of failing outside its limits`() {
        // 9-wide boards exceed the 3-bit position encoding: the public entry point must still answer.
        val wide = Board(
            width = 9, height = 6, exitRow = 2,
            vehicles = listOf(
                com.rushi.blockescape.domain.Vehicle("primary", com.rushi.blockescape.domain.Orientation.HORIZONTAL, 2, 2, 0, true),
                com.rushi.blockescape.domain.Vehicle("b", com.rushi.blockescape.domain.Orientation.VERTICAL, 3, 1, 4)
            )
        )
        assertEquals(null, PackedSolver.solve(wide))
        assertEquals(2, Solver.solve(wide).minMoves)
    }

    @Test
    fun `hardest shipped board solves quickly`() {
        val hardest = shippedBoards().maxByOrNull { Solver.solveGeneric(it.second).minMoves }!!.second
        Solver.solve(hardest) // warm up
        val t = System.nanoTime()
        Solver.solve(hardest)
        val ms = (System.nanoTime() - t) / 1_000_000
        println("PACKED hardest board: $ms ms")
        // Generous bound: the reference needs ~700 ms here; this only fails if the fast path regresses badly.
        assertTrue("fast solver took $ms ms", ms < 400)
    }
}
