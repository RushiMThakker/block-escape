package com.rushi.blockescape.ui.festive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * One bulb: a soft two-layer halo and a core, all plain alpha circles (no brushes, no objects
 * allocated per frame). [glow] is 0..1 and drives both brightness and halo size.
 */
internal fun DrawScope.drawBulb(center: Offset, radius: Float, color: Color, glow: Float) {
    drawCircle(color.copy(alpha = 0.07f + 0.10f * glow), radius * 3.0f, center)
    drawCircle(color.copy(alpha = 0.14f + 0.18f * glow), radius * 1.9f, center)
    drawCircle(color.copy(alpha = 0.60f + 0.40f * glow), radius, center)
    drawCircle(Color.White.copy(alpha = 0.35f + 0.4f * glow), radius * 0.38f, Offset(center.x - radius * 0.25f, center.y - radius * 0.25f))
}

/** Slow, uneven twinkle: each bulb has its own phase so the string never pulses in unison. */
internal fun twinkle(time: Float, index: Int): Float {
    val a = sin(time * 1.7f + index * 1.9f)
    val b = sin(time * 0.9f + index * 0.7f + 1.3f)
    return (0.5f + 0.25f * a + 0.25f * b).coerceIn(0f, 1f)
}

/**
 * A rectangle of lights threaded around a board. [inset] is the distance from the canvas edge to
 * the wire; bulbs sit every ~[spacing] px along it, with a few at the corners hanging slightly.
 */
fun DrawScope.drawLightsAround(
    inset: Float,
    spacing: Float,
    bulbRadius: Float,
    time: Float,
    // Where the exit is, on the right edge: the string leaves a gap there and a gold chevron points in.
    exitY: Float = -1f,
    exitHalfGap: Float = 0f
) {
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    val wire = Color(0xFF0A0F1E).copy(alpha = 0.75f)
    val wireWidth = bulbRadius * 0.28f
    drawLine(wire, Offset(left, top), Offset(right, top), wireWidth, StrokeCap.Round)
    drawLine(wire, Offset(right, top), Offset(right, bottom), wireWidth, StrokeCap.Round)
    drawLine(wire, Offset(right, bottom), Offset(left, bottom), wireWidth, StrokeCap.Round)
    drawLine(wire, Offset(left, bottom), Offset(left, top), wireWidth, StrokeCap.Round)

    val perSideX = maxOf(2, ((right - left) / spacing).toInt())
    val perSideY = maxOf(2, ((bottom - top) / spacing).toInt())
    val warm = Festive.warmBulbs
    var index = 0
    // top and bottom edges
    for (i in 0 until perSideX) {
        val x = left + (right - left) * (i + 0.5f) / perSideX
        drawBulb(Offset(x, top), bulbRadius, warm[index % warm.size], twinkle(time, index)); index++
        drawBulb(Offset(x, bottom), bulbRadius, warm[index % warm.size], twinkle(time, index)); index++
    }
    // left and right edges
    for (i in 0 until perSideY) {
        val y = top + (bottom - top) * (i + 0.5f) / perSideY
        drawBulb(Offset(left, y), bulbRadius, warm[index % warm.size], twinkle(time, index)); index++
        val atExit = exitY >= 0f && kotlin.math.abs(y - exitY) < exitHalfGap
        if (!atExit) drawBulb(Offset(right, y), bulbRadius, warm[index % warm.size], twinkle(time, index))
        index++
    }
    if (exitY >= 0f) {
        // a clear "this way out": a soft halo and a bold gold chevron in the gap
        val cx = right
        val arm = bulbRadius * 2.4f
        drawCircle(Festive.gold.copy(alpha = 0.16f), arm * 1.7f, Offset(cx, exitY))
        val w = bulbRadius * 0.95f
        drawLine(Festive.gold, Offset(cx - arm * 0.55f, exitY - arm), Offset(cx + arm * 0.45f, exitY), w, StrokeCap.Round)
        drawLine(Festive.gold, Offset(cx - arm * 0.55f, exitY + arm), Offset(cx + arm * 0.45f, exitY), w, StrokeCap.Round)
    }
}

/**
 * A string of lights draped across the top of a screen in [swags] gentle scallops. The wire is a
 * polyline (no Path), bulbs hang just below it.
 */
fun DrawScope.drawGarland(swags: Int, sag: Float, bulbRadius: Float, spacing: Float, time: Float) {
    val topY = bulbRadius * 2.2f
    val wire = Color(0xFF0A0F1E).copy(alpha = 0.7f)
    val steps = 56
    var prev = Offset(0f, topY)
    for (s in 1..steps) {
        val x = size.width * s / steps
        val u = (x / size.width) * swags
        val f = u - u.toInt()
        val y = topY + sag * 4f * f * (1f - f)
        val p = Offset(x, y)
        drawLine(wire, prev, p, bulbRadius * 0.26f, StrokeCap.Round)
        prev = p
    }
    val count = maxOf(swags * 3, (size.width / spacing).toInt())
    for (i in 0 until count) {
        val x = size.width * (i + 0.5f) / count
        val u = (x / size.width) * swags
        val f = u - u.toInt()
        val y = topY + sag * 4f * f * (1f - f) + bulbRadius * 1.4f
        drawBulb(Offset(x, y), bulbRadius, Festive.bulbs[i % Festive.bulbs.size], twinkle(time, i))
    }
}

/** Top-of-screen garland as a self-contained composable (24 fps twinkle, paused in the background). */
@Composable
fun StringGarland(modifier: Modifier = Modifier, height: Dp = 64.dp, clock: State<Float> = rememberFestiveClock(20)) {
    // Own render layer so a twinkle tick does not re-record the rest of the screen.
    Canvas(modifier = modifier.fillMaxWidth().height(height).graphicsLayer()) {
        val r = 3.6.dp.toPx()
        drawGarland(swags = 4, sag = 16.dp.toPx(), bulbRadius = r, spacing = 34.dp.toPx(), time = clock.value)
    }
}
