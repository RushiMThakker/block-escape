package com.rushi.blockescape.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.rushi.blockescape.domain.Board
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------------------
// Cached static background (Task 7's wood-grain rendering, restructured into a one-time
// "build" phase that produces plain data, and a cheap "draw" phase that just replays it).
//
// Self-contained procedural-rendering subsystem: no dependency on GameBoardScreen.kt's
// gesture state. Split into its own file (Task 8 review, issue 4); declarations are
// `internal` rather than `private` because Kotlin's top-level `private` is file-scoped,
// and these are used from GameBoardScreen.kt's `drawWithCache` block.
// ---------------------------------------------------------------------------------------

internal data class GrainStroke(val path: Path, val color: Color, val strokeWidth: Float)
internal data class PoreDash(val start: Offset, val end: Offset, val color: Color)
internal data class KnotVisual(
    val rings: List<Pair<Float, Color>>,
    val center: Offset,
    val dotRadius: Float,
    val dotColor: Color
)
internal data class FrameSegment(val start: Offset, val end: Offset, val color: Color, val strokeWidth: Float)
internal data class ExitGlowVisual(
    val brush: Brush,
    val center: Offset,
    val radius: Float,
    val sliverTopLeft: Offset,
    val sliverSize: Size
)

internal data class BoardBackground(
    val boardSize: Size,
    val baseGradientBrush: Brush,
    val sheenBrush: Brush,
    val grainStrokes: List<GrainStroke>,
    val poreDashes: List<PoreDash>,
    val knots: List<KnotVisual>,
    val vignetteBrush: Brush,
    val gridLines: List<Pair<Offset, Offset>>,
    val frameSegments: List<FrameSegment>,
    val frameHighlights: List<FrameSegment>,
    val exitGlow: ExitGlowVisual
)

internal fun buildBoardBackground(board: Board, size: Size): BoardBackground {
    // board.width is used for both axes: the JSON level format (LevelDto.gridSize) only
    // ever produces square boards (width == height), and the Canvas is always rendered at
    // aspectRatio(1f), so there is no separate "height in cells" to derive here.
    val cell = size.width / board.width

    val (baseGradientBrush, sheenBrush) = buildBaseGradient(size)
    val (grainStrokes, poreDashes) = buildWoodGrain(size)
    val knots = buildKnots(size)
    val vignetteBrush = buildVignette(size)
    val gridLines = buildGridLines(size, cell, board.width)
    val (frameSegments, frameHighlights) = buildFrame(board, size, cell)
    val exitGlow = buildExitGlow(board, size, cell)

    return BoardBackground(
        boardSize = size,
        baseGradientBrush = baseGradientBrush,
        sheenBrush = sheenBrush,
        grainStrokes = grainStrokes,
        poreDashes = poreDashes,
        knots = knots,
        vignetteBrush = vignetteBrush,
        gridLines = gridLines,
        frameSegments = frameSegments,
        frameHighlights = frameHighlights,
        exitGlow = exitGlow
    )
}

// Warm diagonal base: a lit honey corner sweeping down into a deep umber corner, plus a
// soft directional sheen so the surface reads as lit wood rather than a flat fill.
private fun buildBaseGradient(size: Size): Pair<Brush, Brush> {
    val base = Brush.linearGradient(
        colors = listOf(
            BoardColors.woodLight,
            BoardColors.woodBase,
            BoardColors.woodBase,
            BoardColors.woodDark
        ),
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height)
    )
    val sheen = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0f),
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0f)
        ),
        start = Offset(size.width * 0.05f, 0f),
        end = Offset(size.width * 0.45f, size.height)
    )
    return base to sheen
}

// Layered horizontal grain: broad wavy streaks for the plank pattern, plus a dense layer
// of short fine strokes for pore-level texture. Seeded so it's stable across recompositions,
// and now built once (not per-frame) since it's expensive (sin() evaluated per path point).
private fun buildWoodGrain(size: Size): Pair<List<GrainStroke>, List<PoreDash>> {
    val rng = Random(seed = 42)

    val strokes = ArrayList<GrainStroke>(70)
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
        strokes.add(GrainStroke(path, grainColor.copy(alpha = alpha), strokeWidth))
    }

    val dashes = ArrayList<PoreDash>(260)
    repeat(260) {
        val x = rng.nextFloat() * size.width
        val y = rng.nextFloat() * size.height
        val len = 3f + rng.nextFloat() * 9f
        val drift = (rng.nextFloat() - 0.5f) * 2f
        val isDark = rng.nextFloat() > 0.4f
        val grainColor = if (isDark) BoardColors.woodDark else BoardColors.woodLight
        val alpha = 0.05f + rng.nextFloat() * 0.10f
        dashes.add(PoreDash(Offset(x, y), Offset(x + len, y + drift), grainColor.copy(alpha = alpha)))
    }

    return strokes to dashes
}

// A couple of wood knots — concentric rings — for the kind of imperfection that makes a
// procedural texture read as a real material instead of a repeating pattern.
private fun buildKnots(size: Size): List<KnotVisual> {
    val rng = Random(seed = 99)
    val knotSpots = listOf(
        Offset(size.width * 0.18f, size.height * 0.72f),
        Offset(size.width * 0.80f, size.height * 0.22f)
    )
    return knotSpots.map { center ->
        val baseRadius = size.width * (0.035f + rng.nextFloat() * 0.02f)
        val rings = (0 until 4).map { ring ->
            val r = baseRadius * (1f - ring * 0.22f)
            r to BoardColors.woodDark.copy(alpha = 0.16f - ring * 0.02f)
        }
        KnotVisual(
            rings = rings,
            center = center,
            dotRadius = baseRadius * 0.28f,
            dotColor = BoardColors.woodDark.copy(alpha = 0.30f)
        )
    }
}

// Darkens the corners so the board has real depth instead of looking like a flat printout.
private fun buildVignette(size: Size): Brush =
    Brush.radialGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0f),
            Color.Black.copy(alpha = 0f),
            Color.Black.copy(alpha = 0.30f)
        ),
        center = Offset(size.width * 0.42f, size.height * 0.40f),
        radius = size.width * 0.78f
    )

private fun buildGridLines(size: Size, cell: Float, gridSize: Int): List<Pair<Offset, Offset>> {
    val lines = ArrayList<Pair<Offset, Offset>>((gridSize + 1) * 2)
    for (i in 0..gridSize) {
        lines.add(Offset(i * cell, 0f) to Offset(i * cell, size.height))
        lines.add(Offset(0f, i * cell) to Offset(size.width, i * cell))
    }
    return lines
}

// The routed wooden frame — drawn as separate segments so the right edge can leave a gap
// at the exit row, as if the wall has literally been cut open there.
private fun buildFrame(board: Board, size: Size, cell: Float): Pair<List<FrameSegment>, List<FrameSegment>> {
    val strokeWidth = 7f
    val half = strokeWidth / 2f
    val frameColor = BoardColors.woodDark.copy(alpha = 0.6f)
    val highlightColor = Color.White.copy(alpha = 0.12f)

    val segments = ArrayList<FrameSegment>(5)
    // Top, bottom, left — solid.
    segments.add(FrameSegment(Offset(0f, half), Offset(size.width, half), frameColor, strokeWidth))
    segments.add(
        FrameSegment(
            Offset(0f, size.height - half),
            Offset(size.width, size.height - half),
            frameColor,
            strokeWidth
        )
    )
    segments.add(FrameSegment(Offset(half, 0f), Offset(half, size.height), frameColor, strokeWidth))

    // Right edge, split around the exit row.
    val exitTop = board.exitRow * cell
    val exitBottom = exitTop + cell
    val x = size.width - half
    if (exitTop > 0f) {
        segments.add(FrameSegment(Offset(x, 0f), Offset(x, exitTop), frameColor, strokeWidth))
    }
    if (exitBottom < size.height) {
        segments.add(FrameSegment(Offset(x, exitBottom), Offset(x, size.height), frameColor, strokeWidth))
    }

    // Thin inner highlight along the top/left to suggest a beveled, lit edge.
    val highlights = listOf(
        FrameSegment(
            Offset(strokeWidth, strokeWidth),
            Offset(size.width - strokeWidth, strokeWidth),
            highlightColor,
            1.5f
        ),
        FrameSegment(
            Offset(strokeWidth, strokeWidth),
            Offset(strokeWidth, size.height - strokeWidth),
            highlightColor,
            1.5f
        )
    )

    return segments to highlights
}

// Warm light spilling through the gap in the wall at the exit row.
private fun buildExitGlow(board: Board, size: Size, cell: Float): ExitGlowVisual {
    val exitCenterY = board.exitRow * cell + cell / 2f
    val glowRadius = cell * 1.35f
    val center = Offset(size.width, exitCenterY)

    val brush = Brush.radialGradient(
        colors = listOf(
            BoardColors.exitGlow.copy(alpha = 0.85f),
            BoardColors.exitGlow.copy(alpha = 0.35f),
            BoardColors.exitGlow.copy(alpha = 0f)
        ),
        center = center,
        radius = glowRadius
    )

    return ExitGlowVisual(
        brush = brush,
        center = center,
        radius = glowRadius,
        sliverTopLeft = Offset(size.width - 5f, exitCenterY - cell / 2f),
        sliverSize = Size(5f, cell)
    )
}

// Replays the precomputed background. This is the part that actually runs every frame
// (including every frame of a drag) — it's cheap because it only issues draw calls
// against already-built Path/Brush/Offset objects, with no per-frame sin()/Random work.
internal fun DrawScope.drawBoardBackground(bg: BoardBackground) {
    drawRect(brush = bg.baseGradientBrush, size = bg.boardSize)
    drawRect(brush = bg.sheenBrush, size = bg.boardSize)

    for (stroke in bg.grainStrokes) {
        drawPath(path = stroke.path, color = stroke.color, style = Stroke(width = stroke.strokeWidth))
    }
    for (dash in bg.poreDashes) {
        drawLine(color = dash.color, start = dash.start, end = dash.end, strokeWidth = 1f)
    }

    for (knot in bg.knots) {
        for ((radius, color) in knot.rings) {
            drawCircle(color = color, radius = radius, center = knot.center, style = Stroke(width = 1.4f))
        }
        drawCircle(color = knot.dotColor, radius = knot.dotRadius, center = knot.center)
    }

    drawRect(brush = bg.vignetteBrush, size = bg.boardSize)

    for ((start, end) in bg.gridLines) {
        drawLine(color = BoardColors.gridLine, start = start, end = end, strokeWidth = 1.5f)
    }

    for (segment in bg.frameSegments) {
        drawLine(color = segment.color, start = segment.start, end = segment.end, strokeWidth = segment.strokeWidth)
    }
    for (segment in bg.frameHighlights) {
        drawLine(color = segment.color, start = segment.start, end = segment.end, strokeWidth = segment.strokeWidth)
    }

    // Explicitly clipped to the board's own bounds as a safety measure — custom
    // drawBehind content isn't guaranteed to stay inside the layout box, and an
    // un-clipped radial gradient here would risk leaking into whatever sits outside the
    // Canvas (e.g. the padding margin).
    clipRect(left = 0f, top = 0f, right = bg.boardSize.width, bottom = bg.boardSize.height) {
        drawCircle(brush = bg.exitGlow.brush, radius = bg.exitGlow.radius, center = bg.exitGlow.center)
        drawRect(color = BoardColors.exitGlow, topLeft = bg.exitGlow.sliverTopLeft, size = bg.exitGlow.sliverSize)
    }
}
