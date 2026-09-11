package com.rushi.blockescape.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.domain.Orientation
import com.rushi.blockescape.domain.Vehicle
import com.rushi.blockescape.solver.Solver
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GameBoardScreen(board: Board, hint: Solver.Move? = null, onMove: (String, Int) -> Unit = { _, _ -> }) {
    var cellPx by remember { mutableFloatStateOf(0f) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetCells by remember { mutableFloatStateOf(0f) }
    var isShaking by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Drives the hint highlight's breathing glow/arrow. Declared unconditionally (cheap -
    // one float animation) rather than only when a hint is active, so entering/leaving
    // the hinted state never has to start/stop/rebuild an infinite-transition - it simply
    // starts being read (or stops being read) inside the draw block below. Compose's
    // snapshot system only invalidates drawing on `pulse` changes while something is
    // actually reading it in the draw phase, so this costs nothing extra while no hint is
    // shown.
    val hintPulseTransition = rememberInfiniteTransition(label = "hintPulse")
    val hintPulse by hintPulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintPulseValue"
    )

    // Distinguishes a SELF-caused board change (the direct result of this composable's
    // own onMove(id, finalDelta) call in onDragEnd below) from a genuinely EXTERNAL one
    // (a future restart()/undo()/level-change triggered from outside, e.g. a HUD button).
    // Armed immediately before the onMove call and consumed (cleared) the first time
    // LaunchedEffect(board) below observes it, so it can only ever affect the very next
    // board-change firing.
    //
    // It is deliberately only armed when finalDelta != 0: GameViewModel.attemptMove
    // returns immediately without touching board state when delta == 0 (see
    // GameViewModel.kt), which happens whenever the user drags less than half a cell and
    // releases. If we armed the flag unconditionally, that case would leave it stuck
    // `true` with no board-change ever arriving to consume it - and it would then
    // incorrectly suppress the reset for some later, unrelated EXTERNAL board change
    // (e.g. the user immediately hitting "restart" after a tiny no-op drag). Gating on
    // finalDelta != 0 is sound here because finalDelta is already clamped to
    // board.legalMoves(id) computed from this same board (see onDrag below), so a
    // nonzero finalDelta is guaranteed to be accepted by attemptMove's own re-check of
    // legalMoves against the current board.
    var selfTriggeredMove by remember { mutableStateOf(false) }

    // Defensive reset: if `board` is swapped out for a new instance (e.g. a future
    // restart()/undo() from Task 9's real GameViewModel) while drag state is lingering,
    // clear it - unless this composable's own onMove call is what caused the change, in
    // which case the settle animation (guarded separately via `draggingId == id` checks)
    // should be left to play out uninterrupted. pointerInput(board) re-keying only
    // affects the gesture-detection coroutine, not these separately-remember'ed values,
    // so without this a stale draggingId/dragOffsetCells could survive a board swap.
    LaunchedEffect(board) {
        if (selfTriggeredMove) {
            selfTriggeredMove = false
        } else {
            draggingId = null
            dragOffsetCells = 0f
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .aspectRatio(1f)
            .pointerInput(board) {
                cellPx = size.width / board.width.toFloat()
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
                            // Arm the self-triggered flag only when we're actually
                            // requesting a move (see the declaration above for why
                            // finalDelta == 0 must NOT arm it - that case is a
                            // guaranteed no-op in GameViewModel.attemptMove and would
                            // leave the flag stuck true with nothing to consume it).
                            if (finalDelta != 0) {
                                selfTriggeredMove = true
                            }
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
        cellPx = size.width / board.width.toFloat()

        // This part is NOT cached — it's cheap (one rounded rect + one gradient overlay
        // per vehicle) and needs to redraw every frame during a drag to track the finger.
        board.vehicles.forEach { vehicle ->
            val offsetCells = if (vehicle.id == draggingId) dragOffsetCells else 0f
            val shakePx = if (vehicle.id == draggingId) shake.value * 6f else 0f
            // Drawn BEHIND the vehicle body (glow) so the vehicle itself stays fully
            // legible on top; tracks the same offset/shake as the vehicle so it stays
            // glued to it even mid-drag, in the rare case the hinted vehicle is also the
            // one currently being dragged.
            if (hint != null && vehicle.id == hint.vehicleId) {
                drawHintHighlight(vehicle, cellPx, offsetCells, shakePx, hintPulse, direction = if (hint.delta >= 0) 1 else -1)
            }
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

// Hint indicator for the vehicle the solver says to move next: a soft breathing amber
// glow hugging its body, plus a small directional chevron pointing which way to slide it.
// Geometry mirrors drawVehicle's own topLeft/bodySize/cornerRadius math exactly (so the
// glow sits flush against the vehicle it's for) but this function never mutates any drag/
// gesture state - it only reads the same offsetCells/shakePx values GameBoardScreen
// already computed for the vehicle this frame, purely to stay visually glued to it.
private fun DrawScope.drawHintHighlight(
    vehicle: Vehicle,
    cellPx: Float,
    offsetCells: Float,
    shakePx: Float,
    pulse: Float,
    direction: Int
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

    val topLeft = Offset(x + margin, y + margin)
    val bodySize = Size(w - margin * 2, h - margin * 2)
    val cornerRadius = CornerRadius(cellPx * 0.25f, cellPx * 0.25f)

    // A couple of concentric, low-alpha strokes stepping outward from the body - a cheap
    // stand-in for a real blur (consistent with how BoardBackground fakes soft edges
    // elsewhere in this project) - breathing outward/brighter with `pulse` so it reads as
    // a gentle beacon rather than a static debug rectangle.
    for (ring in 0..2) {
        val expand = cellPx * (0.05f + ring * 0.06f) * (0.75f + pulse * 0.25f)
        drawRoundRect(
            color = BoardColors.hintGlow.copy(alpha = (0.22f + pulse * 0.18f) * (1f - ring * 0.32f)),
            topLeft = Offset(topLeft.x - expand, topLeft.y - expand),
            size = Size(bodySize.width + expand * 2f, bodySize.height + expand * 2f),
            cornerRadius = CornerRadius(cornerRadius.x + expand, cornerRadius.y + expand),
            style = Stroke(width = cellPx * 0.045f)
        )
    }

    // Crisp outline hugging the body itself, brightening at the top of the pulse.
    drawRoundRect(
        color = BoardColors.hintGlow.copy(alpha = 0.55f + pulse * 0.45f),
        topLeft = topLeft,
        size = bodySize,
        cornerRadius = cornerRadius,
        style = Stroke(width = cellPx * (0.05f + pulse * 0.02f))
    )

    drawHintArrow(vehicle, cellPx, topLeft, bodySize, direction, pulse)
}

// Small solid chevron just beyond the hinted vehicle's leading edge, pointing the way it
// should slide, gently bobbing outward on the same pulse phase as the glow so the two
// read as one animated indicator rather than two unrelated effects.
private fun DrawScope.drawHintArrow(
    vehicle: Vehicle,
    cellPx: Float,
    topLeft: Offset,
    bodySize: Size,
    direction: Int,
    pulse: Float
) {
    val arrowHalfSpan = cellPx * 0.16f
    val bob = cellPx * 0.09f * pulse
    val standoff = cellPx * 0.14f + bob

    val center = if (vehicle.orientation == Orientation.HORIZONTAL) {
        val cy = topLeft.y + bodySize.height / 2f
        val cx = if (direction > 0) topLeft.x + bodySize.width + standoff else topLeft.x - standoff
        Offset(cx, cy)
    } else {
        val cx = topLeft.x + bodySize.width / 2f
        val cy = if (direction > 0) topLeft.y + bodySize.height + standoff else topLeft.y - standoff
        Offset(cx, cy)
    }

    val path = Path().apply {
        if (vehicle.orientation == Orientation.HORIZONTAL) {
            val tipX = center.x + arrowHalfSpan * direction
            val backX = center.x - arrowHalfSpan * direction
            moveTo(tipX, center.y)
            lineTo(backX, center.y - arrowHalfSpan)
            lineTo(backX, center.y + arrowHalfSpan)
            close()
        } else {
            val tipY = center.y + arrowHalfSpan * direction
            val backY = center.y - arrowHalfSpan * direction
            moveTo(center.x, tipY)
            lineTo(center.x - arrowHalfSpan, backY)
            lineTo(center.x + arrowHalfSpan, backY)
            close()
        }
    }

    // Thin dark outline first so the chevron stays legible over any vehicle color or the
    // wood-grain background, then the warm fill (brightening at the top of the pulse) on top.
    drawPath(path = path, color = Color.Black.copy(alpha = 0.28f), style = Stroke(width = 2.5f))
    drawPath(path = path, color = BoardColors.hintGlow.copy(alpha = 0.85f + pulse * 0.15f))
}
