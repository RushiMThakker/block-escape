package com.rushi.blockescape.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.domain.Orientation
import com.rushi.blockescape.domain.Vehicle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val GRID_SIZE = 6

@Composable
fun GameBoardScreen(board: Board, onMove: (String, Int) -> Unit = { _, _ -> }) {
    var cellPx by remember { mutableFloatStateOf(0f) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetCells by remember { mutableFloatStateOf(0f) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .aspectRatio(1f)
            .pointerInput(board) {
                cellPx = size.width / GRID_SIZE.toFloat()
                detectDragGestures(
                    onDragStart = { offset ->
                        draggingId = vehicleAt(board, offset, cellPx)
                        dragOffsetCells = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val id = draggingId ?: return@detectDragGestures
                        val vehicle = board.vehicle(id)
                        val range = board.legalMoves(id)
                        val deltaCells = if (vehicle.orientation == Orientation.HORIZONTAL) {
                            dragAmount.x / cellPx
                        } else {
                            dragAmount.y / cellPx
                        }
                        val proposed = dragOffsetCells + deltaCells
                        val clamped = proposed.coerceIn(range.first.toFloat(), range.last.toFloat())
                        if (proposed != clamped) {
                            scope.launch {
                                shake.snapTo(0f)
                                shake.animateTo(1f, tween(60))
                                shake.animateTo(-1f, tween(60))
                                shake.animateTo(0f, tween(60))
                            }
                        }
                        dragOffsetCells = clamped
                    },
                    onDragEnd = {
                        val id = draggingId
                        val finalDelta = dragOffsetCells.roundToInt()
                        if (id != null) {
                            onMove(id, finalDelta)
                        }
                        // Visual-only settle: board state is already authoritative via
                        // onMove above (or unchanged, if onMove is the Task 8 no-op).
                        // This just animates the leftover fractional drag distance back
                        // to zero with a springy flourish instead of an instant jump.
                        scope.launch {
                            val settleFrom = dragOffsetCells - finalDelta
                            val anim = Animatable(settleFrom)
                            anim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                        draggingId = null
                        dragOffsetCells = 0f
                    },
                    onDragCancel = {
                        draggingId = null
                        dragOffsetCells = 0f
                    }
                )
            }
            // Cache the expensive static wood-grain background (70 seeded Path builds +
            // 260 pore dashes + knots/vignette/frame/glow geometry) so it's computed once
            // per board size instead of every drag frame. The block below runs only when
            // `size` (or `board`, since it's captured in the enclosing closure and a new
            // Modifier chain is built whenever the composable recomposes with a new board)
            // changes; the returned onDrawBehind lambda just replays the precomputed
            // Path/Brush/Offset objects, which is cheap enough to run every frame.
            .drawWithCache {
                val background = buildBoardBackground(board, size)
                onDrawBehind {
                    drawBoardBackground(background)
                }
            }
    ) {
        // Also set here (not just in the pointerInput block above): pointerInput's
        // suspend coroutine isn't guaranteed to have run by the time the first frame
        // draws, and drawVehicle below divides by cellPx — leaving it at its initial 0f
        // on that first frame would size every vehicle to zero and crash the radial
        // highlight gradient (`ending radius must be > 0`). This is a cheap assignment,
        // safe to repeat every frame.
        cellPx = size.width / GRID_SIZE.toFloat()

        // This part is NOT cached — it's cheap (one rounded rect + one gradient overlay
        // per vehicle) and needs to redraw every frame during a drag to track the finger.
        board.vehicles.forEach { vehicle ->
            val offsetCells = if (vehicle.id == draggingId) dragOffsetCells else 0f
            val shakePx = if (vehicle.id == draggingId) shake.value * 6f else 0f
            drawVehicle(vehicle, cellPx, offsetCells, shakePx, isPrimary = vehicle.isPrimary)
        }
    }
}

// Hit-tests a screen offset (in px, in the Canvas's own coordinate space) against the
// board's vehicles, returning the id of whichever vehicle occupies that cell, if any.
private fun vehicleAt(board: Board, offset: Offset, cellPx: Float): String? {
    if (cellPx <= 0f) return null
    val col = (offset.x / cellPx).toInt()
    val row = (offset.y / cellPx).toInt()
    return board.vehicles.firstOrNull { v ->
        when (v.orientation) {
            Orientation.HORIZONTAL -> row == v.row && col in v.col until (v.col + v.length)
            Orientation.VERTICAL -> col == v.col && row in v.row until (v.row + v.length)
        }
    }?.id
}

// Rounded-rect vehicle body with a glossy radial-gradient highlight, offset by the
// current drag progress (offsetCells, in whole/fractional cells along the vehicle's own
// axis) and a small shake jitter (shakePx) used as blocked-move feedback.
private fun DrawScope.drawVehicle(
    vehicle: Vehicle,
    cellPx: Float,
    offsetCells: Float,
    shakePx: Float,
    isPrimary: Boolean
) {
    val margin = cellPx * 0.08f
    val baseX = vehicle.col * cellPx
    val baseY = vehicle.row * cellPx
    val (x, y) = when (vehicle.orientation) {
        Orientation.HORIZONTAL -> Offset(baseX + offsetCells * cellPx + shakePx, baseY)
        Orientation.VERTICAL -> Offset(baseX + shakePx, baseY + offsetCells * cellPx)
    }
    val w = if (vehicle.orientation == Orientation.HORIZONTAL) vehicle.length * cellPx else cellPx
    val h = if (vehicle.orientation == Orientation.VERTICAL) vehicle.length * cellPx else cellPx
    val color = if (isPrimary) {
        BoardColors.vehiclePrimary
    } else {
        BoardColors.vehiclePalette[vehicle.id.hashCode().mod(BoardColors.vehiclePalette.size)]
    }

    val topLeft = Offset(x + margin, y + margin)
    val bodySize = Size(w - margin * 2, h - margin * 2)
    val cornerRadius = CornerRadius(cellPx * 0.25f, cellPx * 0.25f)

    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = bodySize,
        cornerRadius = cornerRadius
    )
    // Glossy highlight overlay.
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0f)),
            center = Offset(topLeft.x + bodySize.width * 0.3f, topLeft.y + bodySize.height * 0.25f),
            radius = maxOf(bodySize.width, bodySize.height) * 0.6f
        ),
        topLeft = topLeft,
        size = bodySize,
        cornerRadius = cornerRadius
    )
}

// ---------------------------------------------------------------------------------------
// Cached static background (Task 7's wood-grain rendering, restructured into a one-time
// "build" phase that produces plain data, and a cheap "draw" phase that just replays it).
// ---------------------------------------------------------------------------------------

private data class GrainStroke(val path: Path, val color: Color, val strokeWidth: Float)
private data class PoreDash(val start: Offset, val end: Offset, val color: Color)
private data class KnotVisual(
    val rings: List<Pair<Float, Color>>,
    val center: Offset,
    val dotRadius: Float,
    val dotColor: Color
)
private data class FrameSegment(val start: Offset, val end: Offset, val color: Color, val strokeWidth: Float)
private data class ExitGlowVisual(
    val brush: Brush,
    val center: Offset,
    val radius: Float,
    val sliverTopLeft: Offset,
    val sliverSize: Size
)

private data class BoardBackground(
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

private fun buildBoardBackground(board: Board, size: Size): BoardBackground {
    val cell = size.width / GRID_SIZE

    val (baseGradientBrush, sheenBrush) = buildBaseGradient(size)
    val (grainStrokes, poreDashes) = buildWoodGrain(size)
    val knots = buildKnots(size)
    val vignetteBrush = buildVignette(size)
    val gridLines = buildGridLines(size, cell)
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

private fun buildGridLines(size: Size, cell: Float): List<Pair<Offset, Offset>> {
    val lines = ArrayList<Pair<Offset, Offset>>((GRID_SIZE + 1) * 2)
    for (i in 0..GRID_SIZE) {
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
private fun DrawScope.drawBoardBackground(bg: BoardBackground) {
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
