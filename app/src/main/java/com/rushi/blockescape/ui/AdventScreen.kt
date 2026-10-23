package com.rushi.blockescape.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rushi.blockescape.haptics.HapticFeedback
import com.rushi.blockescape.season.AdventBits
import com.rushi.blockescape.season.CalendarDate
import com.rushi.blockescape.season.Season
import com.rushi.blockescape.season.Season.DoorState
import com.rushi.blockescape.ui.festive.Festive
import com.rushi.blockescape.ui.festive.FestiveBackdrop
import com.rushi.blockescape.ui.festive.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COLUMNS = 4
private val DoorOpenEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f) // ease-out with a small overshoot

/** One-line status under the title. Pure, so the wording is unit-tested. */
fun adventSubtitle(today: CalendarDate, solved: Int): String {
    val open = Season.doorsOpen(today)
    return when {
        solved >= Season.DOORS -> "All 24 doors solved. Happy holidays!"
        today.month == 12 && open < Season.DOORS -> "A new door opens each day  ·  $solved of 24 solved"
        else -> "Every door is open until 6 January  ·  $solved of 24 solved"
    }
}

/** Accessibility text for a door. */
fun doorDescription(day: Int, state: DoorState): String = when (state) {
    DoorState.LOCKED -> "Door $day, locked until 1 December plus ${day - 1} days"
    DoorState.TODAY -> "Door $day, today's door, tap to open"
    DoorState.AVAILABLE -> "Door $day, tap to open"
    DoorState.CLEARED -> "Door $day, solved, tap to play again"
}

@Composable
fun AdventScreen(
    today: CalendarDate,
    clearedBits: Int,
    onOpenDoor: (Int) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    val reduce = LocalReduceMotion.current
    val pulse: State<Float> = if (reduce) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        rememberInfiniteTransition(label = "advent-pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "advent-pulse-value"
        )
    }
    val solved = AdventBits.count(clearedBits)

    FestiveBackdrop(trees = true, snow = true, garland = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(30.dp)) // clear of the garland
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("‹ Levels", color = Festive.frost, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onShare) {
                    Text("Share", color = Festive.frost, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "Advent calendar",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                color = Festive.warmWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                adventSubtitle(today, solved),
                fontSize = 13.sp,
                color = Festive.warmWhite.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                (1..Season.DOORS).chunked(COLUMNS).forEach { rowDays ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        rowDays.forEach { day ->
                            val state = Season.doorState(day, today, AdventBits.isCleared(clearedBits, day))
                            DoorCell(
                                day = day,
                                state = state,
                                pulse = pulse,
                                // Staggered entrance: ~28 ms per door, capped so the last one lands inside 0.7 s.
                                entranceDelayMs = minOf((day - 1) * 28, 640),
                                onOpen = onOpenDoor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoorCell(
    day: Int,
    state: DoorState,
    pulse: State<Float>,
    entranceDelayMs: Int,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val reduce = LocalReduceMotion.current
    val context = LocalContext.current
    val haptics = remember(context) { HapticFeedback(context) }
    val scope = rememberCoroutineScope()

    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    val open = remember { Animatable(if (state == DoorState.CLEARED) 1f else 0f) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(entranceDelayMs.toLong())
            enter.animateTo(1f, tween(240, easing = LinearOutSlowInEasing))
        }
    }

    val locked = state == DoorState.LOCKED
    Box(
        modifier = modifier
            .aspectRatio(0.86f)
            .graphicsLayer {
                val e = enter.value
                alpha = e
                scaleX = 0.88f + 0.12f * e
                scaleY = 0.88f + 0.12f * e
                translationX = shake.value * 6.dp.toPx()
            }
            .semantics { contentDescription = doorDescription(day, state) }
            .clickable {
                scope.launch {
                    when (state) {
                        DoorState.LOCKED -> {
                            // A short "not yet" wobble (about 160 ms) plus the same tick as a blocked move.
                            haptics.blockedMove()
                            if (!reduce) {
                                shake.animateTo(1f, tween(50))
                                shake.animateTo(-1f, tween(60))
                                shake.animateTo(0f, tween(50))
                            }
                        }
                        DoorState.CLEARED -> onOpen(day)
                        DoorState.TODAY, DoorState.AVAILABLE -> {
                            if (!reduce) open.animateTo(1f, tween(420, easing = DoorOpenEasing))
                            onOpen(day)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawDoor(state, open.value, pulse.value) }
        // The numeral fades as the door swings open (read in the layer block: no recomposition).
        Text(
            text = day.toString(),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = if (locked) Festive.frost.copy(alpha = 0.55f) else Festive.gold,
            modifier = Modifier.graphicsLayer {
                alpha = if (state == DoorState.CLEARED) 0f else (1f - open.value * 2.4f).coerceIn(0f, 1f)
            }
        )
        if (state == DoorState.CLEARED) {
            Text(
                text = day.toString(),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Festive.warmWhite.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 7.dp)
            )
        }
    }
}

/**
 * A door as vector art. [open] 0..1 swings the leaf away on its left hinge, revealing a lit
 * interior with a sparkle; [pulse] only affects today's door glow. Plain primitives, no Path or
 * Brush, so redrawing during the open animation allocates nothing.
 */
private fun DrawScope.drawDoor(state: DoorState, open: Float, pulse: Float) {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    val corner = CornerRadius(w * 0.14f)
    val locked = state == DoorState.LOCKED

    // Interior, visible as the leaf swings away.
    if (open > 0.02f) clipRect(0f, 0f, w, h) {
        drawRoundRect(Color(0xFF2A1F0E), Offset.Zero, Size(w, h), corner)
        val c = Offset(w * 0.55f, h * 0.48f)
        drawCircle(Festive.gold.copy(alpha = 0.10f * open), w * 0.62f, c)
        drawCircle(Festive.gold.copy(alpha = 0.18f * open), w * 0.42f, c)
        drawCircle(Festive.warmWhite.copy(alpha = 0.30f * open), w * 0.22f, c)
        val s = w * 0.22f * open.coerceAtMost(1.15f)
        val spark = Festive.warmWhite.copy(alpha = open.coerceAtMost(1f))
        drawLine(spark, Offset(c.x - s, c.y), Offset(c.x + s, c.y), 2f * u, StrokeCap.Round)
        drawLine(spark, Offset(c.x, c.y - s), Offset(c.x, c.y + s), 2f * u, StrokeCap.Round)
        val d = s * 0.55f
        drawLine(spark, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), 1.4f * u, StrokeCap.Round)
        drawLine(spark, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), 1.4f * u, StrokeCap.Round)
    }

    // The door leaf, hinged on the left.
    val leafW = w * (1f - 0.84f * open.coerceIn(0f, 1f))
    val outer = if (locked) Color(0xFF13264C).copy(alpha = 0.72f)
    else if (state == DoorState.TODAY) Color(0xFF3A7BC8) else Color(0xFF214D86)
    val inner = if (locked) Color(0xFF10203F).copy(alpha = 0.6f)
    else if (state == DoorState.TODAY) Color(0xFF2F6AB5) else Color(0xFF1B4278)

    if (state == DoorState.TODAY) {
        // soft gold beacon breathing around today's door
        for (ring in 0..2) {
            val e = (3 + ring * 3) * u * (0.8f + 0.2f * pulse)
            drawRoundRect(
                Festive.gold.copy(alpha = (0.16f + 0.22f * pulse) * (1f - ring * 0.3f)),
                Offset(-e, -e), Size(w + e * 2f, h + e * 2f), CornerRadius(w * 0.14f + e), style = Stroke(1.6f * u)
            )
        }
    }
    if (leafW > 1f) {
        drawRoundRect(outer, Offset.Zero, Size(leafW, h), corner)
        val inset = w * 0.12f
        if (leafW > inset * 2.4f) {
            drawRoundRect(inner, Offset(inset, inset), Size(leafW - inset * 2f, h - inset * 2f), CornerRadius(w * 0.07f))
            drawRoundRect(
                Color.Black.copy(alpha = 0.22f), Offset(inset, inset), Size(leafW - inset * 2f, h - inset * 2f),
                CornerRadius(w * 0.07f), style = Stroke(1.2f * u)
            )
        }
        val frame = if (locked) Festive.frost.copy(alpha = 0.16f) else Festive.gold.copy(alpha = 0.85f)
        drawRoundRect(frame, Offset.Zero, Size(leafW, h), corner, style = Stroke(1.5f * u))
        if (!locked && open < 0.85f) {
            drawCircle(Festive.gold, w * 0.045f, Offset(leafW - w * 0.15f, h * 0.84f))
        }
        if (locked) {
            // a small padlock: body and shackle
            val cx = leafW * 0.5f
            val cy = h * 0.74f
            val lw = w * 0.17f
            val lh = w * 0.13f
            val col = Festive.frost.copy(alpha = 0.6f)
            drawRoundRect(col, Offset(cx - lw / 2f, cy), Size(lw, lh), CornerRadius(2f * u))
            drawRoundRect(col, Offset(cx - lw * 0.32f, cy - lh * 0.75f), Size(lw * 0.64f, lh * 1.4f), CornerRadius(lw * 0.3f), style = Stroke(1.6f * u))
        }
    }
}
