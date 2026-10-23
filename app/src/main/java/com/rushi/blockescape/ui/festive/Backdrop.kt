package com.rushi.blockescape.ui.festive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val MAX_FLAKES = 48 // the brief caps particles at about 60

/**
 * Falling snow: at most [MAX_FLAKES] soft dots. Per-flake data lives in primitive arrays built once;
 * each frame only does arithmetic and drawCircle (no allocation). Motion is a function of the
 * clock, so pausing the clock (background, "remove animations") stops it with no extra code.
 */
@Composable
fun Snowfall(modifier: Modifier = Modifier, count: Int = MAX_FLAKES, clock: State<Float> = rememberFestiveClock(30)) {
    val flakes = remember(count) { SnowData(minOf(count, MAX_FLAKES)) }
    val reduce = LocalReduceMotion.current
    // Own render layer: a snow tick re-records only this layer, not the screen behind and in front of it.
    Canvas(modifier = modifier.fillMaxSize().graphicsLayer()) {
        val t = clock.value
        val w = size.width
        val h = size.height
        val unit = 1.dp.toPx()
        for (i in 0 until flakes.n) {
            val fall = (t * flakes.speed[i] + flakes.y0[i])
            val y = (fall - fall.toInt()) * (h + 12 * unit) - 6 * unit
            val x = (flakes.x0[i] + sin(t * flakes.sway[i] + flakes.phase[i]) * flakes.amp[i]) * w
            // Under "remove animations" the flakes hang still as a faint frosted speckle.
            val a = if (reduce) flakes.alpha[i] * 0.5f else flakes.alpha[i]
            drawCircle(Festive.snow.copy(alpha = a * 0.35f), flakes.radius[i] * unit * 1.9f, Offset(x, y))
            drawCircle(Festive.snow.copy(alpha = a), flakes.radius[i] * unit, Offset(x, y))
        }
    }
}

private class SnowData(val n: Int) {
    private val rnd = Random(2026)
    val x0 = FloatArray(n) { rnd.nextFloat() }
    val y0 = FloatArray(n) { rnd.nextFloat() }
    val speed = FloatArray(n) { 0.025f + rnd.nextFloat() * 0.055f } // screen-heights per second
    val radius = FloatArray(n) { 0.9f + rnd.nextFloat() * 1.9f }
    val sway = FloatArray(n) { 0.4f + rnd.nextFloat() * 0.8f }
    val amp = FloatArray(n) { 0.004f + rnd.nextFloat() * 0.014f }
    val phase = FloatArray(n) { rnd.nextFloat() * 6.28f }
    val alpha = FloatArray(n) { 0.35f + rnd.nextFloat() * 0.55f }
}

/** Six-spoke ice crystal built from lines only; used faintly in corners as frost. */
private fun DrawScope.drawCrystal(center: Offset, radius: Float, color: Color, width: Float) {
    for (k in 0 until 6) {
        val ang = k * (PI / 3.0).toFloat() + 0.2f
        val dx = cos(ang)
        val dy = sin(ang)
        val tip = Offset(center.x + dx * radius, center.y + dy * radius)
        drawLine(color, center, tip, width, StrokeCap.Round)
        // two small side branches at 55% and 78% of each spoke
        for ((frac, len) in listOf(0.55f to 0.30f, 0.78f to 0.20f)) {
            val b = Offset(center.x + dx * radius * frac, center.y + dy * radius * frac)
            val ox = -dy
            val oy = dx
            drawLine(color, b, Offset(b.x + (dx * 0.6f + ox) * radius * len * 0.8f, b.y + (dy * 0.6f + oy) * radius * len * 0.8f), width * 0.8f, StrokeCap.Round)
            drawLine(color, b, Offset(b.x + (dx * 0.6f - ox) * radius * len * 0.8f, b.y + (dy * 0.6f - oy) * radius * len * 0.8f), width * 0.8f, StrokeCap.Round)
        }
    }
}

/** A tiered evergreen silhouette with a snow-dusted edge. Pure geometry, cached by the caller. */
private fun treePath(cx: Float, base: Float, width: Float, height: Float, tiers: Int): Path = Path().apply {
    val tierH = height / tiers
    moveTo(cx, base - height)
    for (i in 0 until tiers) {
        val yBottom = base - height + tierH * (i + 1.35f).coerceAtMost(tiers.toFloat())
        val half = width * 0.5f * (0.38f + 0.62f * (i + 1f) / tiers)
        lineTo(cx + half, yBottom)
        lineTo(cx + half * 0.55f, yBottom)
    }
    lineTo(cx + width * 0.06f, base)
    lineTo(cx - width * 0.06f, base)
    for (i in tiers - 1 downTo 0) {
        val yBottom = base - height + tierH * (i + 1.35f).coerceAtMost(tiers.toFloat())
        val half = width * 0.5f * (0.38f + 0.62f * (i + 1f) / tiers)
        lineTo(cx - half * 0.55f, yBottom)
        lineTo(cx - half, yBottom)
    }
    close()
}

/**
 * The festive night: a deep-blue sky, a few faint ice crystals in the corners, and a row of dark
 * evergreens along the bottom. Everything static is built once per size in drawWithCache.
 * Snow and the garland are optional layers on top; [content] sits above all of it.
 */
@Composable
fun FestiveBackdrop(
    modifier: Modifier = Modifier,
    trees: Boolean = true,
    snow: Boolean = true,
    garland: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val sky = Brush.verticalGradient(listOf(Festive.skyTop, Festive.skyBottom), startY = 0f, endY = size.height)
                val glow = Brush.radialGradient(
                    listOf(Festive.frostSoft.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.30f),
                    radius = size.width * 0.9f
                )
                val u = 1.dp.toPx()
                val forest = ArrayList<Pair<Path, Color>>()
                val caps = ArrayList<Path>()
                if (trees) {
                    val base = size.height + 4 * u
                    // back row (lighter, smaller), then front row (darker, taller): depth without gradients
                    val back = listOf(0.08f to 120f, 0.30f to 150f, 0.55f to 130f, 0.78f to 160f, 0.97f to 125f)
                    val front = listOf(0.00f to 175f, 0.20f to 140f, 0.42f to 190f, 0.66f to 150f, 0.88f to 185f)
                    for ((fx, hDp) in back) forest.add(treePath(size.width * fx, base, hDp * 0.62f * u, hDp * u, 6) to Festive.evergreen)
                    for ((fx, hDp) in front) forest.add(treePath(size.width * fx, base + 8 * u, hDp * 0.64f * u, hDp * u, 7) to Festive.evergreenDeep)
                }
                onDrawBehind {
                    drawRect(sky)
                    drawRect(glow)
                    val w = size.width
                    // Frost: three crystals tucked into the corners, barely there.
                    drawCrystal(Offset(w * 0.08f, size.height * 0.07f), 46 * u, Festive.frost.copy(alpha = 0.14f), 1.4f * u)
                    drawCrystal(Offset(w * 0.94f, size.height * 0.36f), 30 * u, Festive.frost.copy(alpha = 0.12f), 1.2f * u)
                    drawCrystal(Offset(w * 0.86f, size.height * 0.52f), 22 * u, Festive.frost.copy(alpha = 0.08f), 1.0f * u)
                    for ((path, color) in forest) drawPath(path, color)
                    for (p in caps) drawPath(p, Festive.snow.copy(alpha = 0.4f), style = Stroke(width = 1.2f * u))
                }
            }
    ) {
        if (snow) Snowfall()
        if (garland) StringGarland(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())
        content()
    }
}
