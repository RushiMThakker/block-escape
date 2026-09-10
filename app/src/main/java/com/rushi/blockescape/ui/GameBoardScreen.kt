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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.domain.Orientation
import com.rushi.blockescape.domain.Vehicle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Shared with BoardBackground.kt's grid-line/cell-size math (internal, not private —
// Kotlin's top-level `private` is file-scoped and wouldn't be visible there).
internal const val GRID_SIZE = 6

@Composable
fun GameBoardScreen(board: Board, onMove: (String, Int) -> Unit = { _, _ -> }) {
    var cellPx by remember { mutableFloatStateOf(0f) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetCells by remember { mutableFloatStateOf(0f) }
    var isShaking by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Defensive reset: if `board` is swapped out for a new instance while idle (e.g. a
    // future restart()/undo() from Task 9's real GameViewModel), clear any leftover drag
    // state. pointerInput(board) re-keying only affects the gesture-detection coroutine,
    // not these separately-remember'ed values, so without this a stale draggingId/
    // dragOffsetCells could survive a board swap.
    LaunchedEffect(board) {
        draggingId = null
        dragOffsetCells = 0f
    }

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
                        // Edge-detected: onDrag fires on nearly every pointer-move event,
                        // and while the user holds the drag past the boundary this branch
                        // would otherwise re-enter every frame. Since shake mutations are
                        // serialized on the same Animatable, a fresh launch each frame
                        // would cancel the in-flight animation a few ms into its upswing
                        // and it would never complete its 1f -> -1f -> 0f arc. Only fire
                        // once on the not-blocked -> blocked transition.
                        if (proposed != clamped && !isShaking) {
                            isShaking = true
                            scope.launch {
                                shake.snapTo(0f)
                                shake.animateTo(1f, tween(60))
                                shake.animateTo(-1f, tween(60))
                                shake.animateTo(0f, tween(60))
                                isShaking = false
                            }
                        }
                        dragOffsetCells = clamped
                    },
                    onDragEnd = {
                        val id = draggingId
                        val finalDelta = dragOffsetCells.roundToInt()
                        if (id != null) {
                            // Captured synchronously, before the coroutine launches: this
                            // reads dragOffsetCells' current value now, not whatever it
                            // will be whenever the launched coroutine happens to run.
                            val settleFrom = dragOffsetCells - finalDelta
                            onMove(id, finalDelta)
                            // Visual-only settle: board state is already authoritative via
                            // onMove above (or unchanged, if onMove is the Task 8 no-op).
                            // This animates the leftover fractional drag distance back to
                            // zero with a springy flourish instead of an instant jump, by
                            // driving dragOffsetCells from the animation's live value each
                            // frame so drawVehicle actually renders the settle motion.
                            // draggingId is kept pointing at this vehicle (not nulled
                            // synchronously) so drawVehicle keeps treating it as "the
                            // dragging vehicle" for the duration of the settle.
                            scope.launch {
                                val anim = Animatable(settleFrom)
                                anim.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                ) {
                                    // Guard against a race: this coroutine runs in `scope`
                                    // (independent of the gesture-detection coroutine), so
                                    // a NEW drag can start on this or another vehicle
                                    // before this settle finishes. Only write state while
                                    // draggingId still belongs to this settle.
                                    if (draggingId == id) dragOffsetCells = value
                                }
                                if (draggingId == id) {
                                    draggingId = null
                                    dragOffsetCells = 0f
                                }
                            }
                        } else {
                            dragOffsetCells = 0f
                        }
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
