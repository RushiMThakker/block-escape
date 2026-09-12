package com.rushi.blockescape.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [bestMovesTileLabel] - the pure formatting logic behind a level-select
 * tile's "Best: N" footnote, tested directly with plain JUnit rather than through Compose.
 */
class LevelSelectScreenTextTest {

    @Test
    fun `formats a non-null count as a short Best label`() {
        assertEquals("Best: 2", bestMovesTileLabel(2))
        assertEquals("Best: 17", bestMovesTileLabel(17))
    }

    @Test
    fun `null count renders nothing (locked tile, or not yet computed)`() {
        assertNull(bestMovesTileLabel(null))
    }
}
