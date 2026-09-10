package com.rushi.unblock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.rushi.unblock.domain.Board
import kotlin.math.sin
import kotlin.random.Random

private const val GRID_SIZE = 6

@Composable
fun GameBoardScreen(board: Board) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .aspectRatio(1f)
    ) {
        drawWoodBoard(board)
    }
}

private fun DrawScope.drawWoodBoard(board: Board) {
    val cell = size.width / GRID_SIZE

    drawBaseGradient()
    drawWoodGrain()
    drawKnots()
    drawVignette()
    drawGridLines(cell)
    drawFrameWithExitGap(board, cell)
    drawExitGlow(board, cell)
}

// Warm diagonal base: a lit honey corner sweeping down into a deep umber corner, plus a
// soft directional sheen so the surface reads as lit wood rather than a flat fill.
private fun DrawScope.drawBaseGradient() {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                BoardColors.woodLight,
                BoardColors.woodBase,
                BoardColors.woodBase,
                BoardColors.woodDark
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        ),
        size = size
    )

    // Soft sheen band, like light raking across varnish.
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0f)
            ),
            start = Offset(size.width * 0.05f, 0f),
            end = Offset(size.width * 0.45f, size.height)
        ),
        size = size
    )
}

// Layered horizontal grain: broad wavy streaks for the plank pattern, plus a dense layer
// of short fine strokes for pore-level texture. Seeded so it's stable across recompositions.
private fun DrawScope.drawWoodGrain() {
    val rng = Random(seed = 42)

    // Broad wavy streaks running the width of the board.
    repeat(70) {
        val y = rng.nextFloat() * size.height
        val amplitude = 2f + rng.nextFloat() * 7f
        val wavelength = 14f + rng.nextFloat() * 36f
        val phase = rng.nextFloat() * 6.28f
        val strokeWidth = 1f + rng.nextFloat() * 3.2f
        val isDark = rng.nextFloat() > 0.3f
        val grainColor = if (isDark) BoardColors.woodDark else BoardColors.woodLight
        val alpha = 0.10f + rng.nextFloat() * 0.22f

        val path = Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x <= size.width) {
                lineTo(x, y + sin(x / wavelength + phase) * amplitude)
                x += 6f
            }
        }
        drawPath(
            path = path,
            color = grainColor.copy(alpha = alpha),
            style = Stroke(width = strokeWidth)
        )
    }

    // Fine pore texture: short scattered dashes for close-up detail.
    repeat(260) {
        val x = rng.nextFloat() * size.width
        val y = rng.nextFloat() * size.height
        val len = 3f + rng.nextFloat() * 9f
        val drift = (rng.nextFloat() - 0.5f) * 2f
        val isDark = rng.nextFloat() > 0.4f
        val grainColor = if (isDark) BoardColors.woodDark else BoardColors.woodLight
        val alpha = 0.05f + rng.nextFloat() * 0.10f
        drawLine(
            color = grainColor.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x + len, y + drift),
            strokeWidth = 1f
        )
    }
}

// A couple of wood knots — concentric rings — for the kind of imperfection that makes a
// procedural texture read as a real material instead of a repeating pattern.
private fun DrawScope.drawKnots() {
    val rng = Random(seed = 99)
    val knotSpots = listOf(
        Offset(size.width * 0.18f, size.height * 0.72f),
        Offset(size.width * 0.80f, size.height * 0.22f)
    )
    for (center in knotSpots) {
        val baseRadius = size.width * (0.035f + rng.nextFloat() * 0.02f)
        for (ring in 0 until 4) {
            val r = baseRadius * (1f - ring * 0.22f)
            drawCircle(
                color = BoardColors.woodDark.copy(alpha = 0.16f - ring * 0.02f),
                radius = r,
                center = center,
                style = Stroke(width = 1.4f)
            )
        }
        drawCircle(
            color = BoardColors.woodDark.copy(alpha = 0.30f),
            radius = baseRadius * 0.28f,
            center = center
        )
    }
}

// Darkens the corners so the board has real depth instead of looking like a flat printout.
private fun DrawScope.drawVignette() {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0f),
                Color.Black.copy(alpha = 0f),
                Color.Black.copy(alpha = 0.30f)
            ),
            center = Offset(size.width * 0.42f, size.height * 0.40f),
            radius = size.width * 0.78f
        ),
        size = size
    )
}

private fun DrawScope.drawGridLines(cell: Float) {
    for (i in 0..GRID_SIZE) {
        drawLine(
            color = BoardColors.gridLine,
            start = Offset(i * cell, 0f),
            end = Offset(i * cell, size.height),
            strokeWidth = 1.5f
        )
        drawLine(
            color = BoardColors.gridLine,
            start = Offset(0f, i * cell),
            end = Offset(size.width, i * cell),
            strokeWidth = 1.5f
        )
    }
}

// The routed wooden frame — drawn as separate segments so the right edge can leave a gap
// at the exit row, as if the wall has literally been cut open there.
private fun DrawScope.drawFrameWithExitGap(board: Board, cell: Float) {
    val strokeWidth = 7f
    val half = strokeWidth / 2f
    val frameColor = BoardColors.woodDark.copy(alpha = 0.6f)
    val highlightColor = Color.White.copy(alpha = 0.12f)

    // Top, bottom, left — solid.
    drawLine(frameColor, Offset(0f, half), Offset(size.width, half), strokeWidth)
    drawLine(frameColor, Offset(0f, size.height - half), Offset(size.width, size.height - half), strokeWidth)
    drawLine(frameColor, Offset(half, 0f), Offset(half, size.height), strokeWidth)

    // Right edge, split around the exit row.
    val exitTop = board.exitRow * cell
    val exitBottom = exitTop + cell
    val x = size.width - half
    if (exitTop > 0f) {
        drawLine(frameColor, Offset(x, 0f), Offset(x, exitTop), strokeWidth)
    }
    if (exitBottom < size.height) {
        drawLine(frameColor, Offset(x, exitBottom), Offset(x, size.height), strokeWidth)
    }

    // Thin inner highlight along the top/left to suggest a beveled, lit edge.
    drawLine(highlightColor, Offset(strokeWidth, strokeWidth), Offset(size.width - strokeWidth, strokeWidth), 1.5f)
    drawLine(highlightColor, Offset(strokeWidth, strokeWidth), Offset(strokeWidth, size.height - strokeWidth), 1.5f)
}

// Warm light spilling through the gap in the wall at the exit row. Explicitly clipped
// to the board's own bounds as a safety measure — custom drawBehind content isn't
// guaranteed to stay inside the layout box, and an un-clipped radial gradient here
// would risk leaking into whatever sits outside the Canvas (e.g. the padding margin).
private fun DrawScope.drawExitGlow(board: Board, cell: Float) {
    val exitCenterY = board.exitRow * cell + cell / 2f
    val glowRadius = cell * 1.35f

    clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    BoardColors.exitGlow.copy(alpha = 0.85f),
                    BoardColors.exitGlow.copy(alpha = 0.35f),
                    BoardColors.exitGlow.copy(alpha = 0f)
                ),
                center = Offset(size.width, exitCenterY),
                radius = glowRadius
            ),
            radius = glowRadius,
            center = Offset(size.width, exitCenterY)
        )

        // Bright sliver right at the opening itself.
        drawRect(
            color = BoardColors.exitGlow,
            topLeft = Offset(size.width - 5f, exitCenterY - cell / 2f),
            size = Size(5f, cell)
        )
    }
}
