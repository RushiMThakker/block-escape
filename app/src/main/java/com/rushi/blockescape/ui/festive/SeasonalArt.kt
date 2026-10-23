package com.rushi.blockescape.ui.festive

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.rushi.blockescape.domain.Orientation
import com.rushi.blockescape.domain.Vehicle

/**
 * The festive vehicle art. The primary block becomes a sleigh; every other block becomes a
 * gift box. All of it is drawn from vector geometry built ONCE for a given cell size, then replayed
 * with cheap translate/rotate transforms each frame, so dragging a block allocates nothing here.
 *
 * Geometry is expressed in a block's own body rectangle (top-left origin), where the body is the
 * block's cells minus the standard 8% margin used by the classic look, so both looks occupy
 * exactly the same footprint and hit-testing/drag maths stay untouched.
 *
 * Design rule from the visual review: the sleigh is the hero. It fills nearly the whole cell
 * height, carries a soft gold glow and a dark outline, and no gift box uses red.
 */
class SeasonalArt(private val cell: Float) {

    private val margin = cell * 0.08f
    private val h = cell - margin * 2f

    private class SleighShape(val w: Float, val body: Path, val runner: Path, val topTrim: Path)

    private val sleighByLength = HashMap<Int, SleighShape>()

    private val runnerStroke = Stroke(width = cell * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val trimStroke = Stroke(width = cell * 0.04f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val outlineStroke = Stroke(width = cell * 0.025f, join = StrokeJoin.Round)
    private val outlineColor = Color(0xFF2A0707).copy(alpha = 0.55f)

    private fun sleigh(lengthCells: Int): SleighShape = sleighByLength.getOrPut(lengthCells) {
        val w = lengthCells * cell - margin * 2f
        val body = Path().apply {
            moveTo(0.10f * w, 0.10f * h)
            quadraticTo(0.02f * w, 0.28f * h, 0.11f * w, 0.52f * h)
            lineTo(0.17f * w, 0.78f * h)
            lineTo(0.83f * w, 0.78f * h)
            cubicTo(0.94f * w, 0.74f * h, 0.99f * w, 0.54f * h, 0.955f * w, 0.30f * h)
            lineTo(0.89f * w, 0.33f * h)
            close()
        }
        val runner = Path().apply {
            moveTo(0.05f * w, 0.95f * h)
            lineTo(0.78f * w, 0.95f * h)
            cubicTo(0.93f * w, 0.95f * h, 1.0f * w, 0.84f * h, 0.985f * w, 0.62f * h)
            quadraticTo(0.975f * w, 0.50f * h, 0.93f * w, 0.55f * h)
        }
        val trim = Path().apply {
            moveTo(0.10f * w, 0.10f * h)
            lineTo(0.89f * w, 0.33f * h)
        }
        SleighShape(w, body, runner, trim)
    }

    /** The primary block as a sleigh pointing at the exit on the right. [left]/[top] = the block's cell origin. */
    fun DrawScope.drawSleigh(vehicle: Vehicle, left: Float, top: Float) {
        val s = sleigh(vehicle.length)
        val w = s.w
        translate(left + margin, top + margin) {
            // a soft gold glow under the sleigh marks it as the piece to move
            drawOval(Festive.gold.copy(alpha = 0.10f), Offset(-0.04f * w, 0.30f * h), Size(1.08f * w, 0.78f * h))
            drawOval(Festive.gold.copy(alpha = 0.14f), Offset(0.02f * w, 0.42f * h), Size(0.96f * w, 0.56f * h))
            // runner and struts first, so the body overlaps their tops
            drawPath(s.runner, Festive.goldDeep, style = runnerStroke)
            drawLine(Festive.goldDeep, Offset(0.30f * w, 0.76f * h), Offset(0.30f * w, 0.94f * h), cell * 0.06f, StrokeCap.Round)
            drawLine(Festive.goldDeep, Offset(0.66f * w, 0.76f * h), Offset(0.66f * w, 0.94f * h), cell * 0.06f, StrokeCap.Round)
            drawPath(s.body, Festive.sleighRed)
            drawPath(s.body, outlineColor, style = outlineStroke)
            // seat cushion and a lighter upper panel for depth
            drawRoundRect(
                Festive.sleighRedDark,
                topLeft = Offset(0.24f * w, 0.38f * h),
                size = Size(0.34f * w, 0.24f * h),
                cornerRadius = CornerRadius(cell * 0.08f)
            )
            drawRoundRect(
                Festive.sleighRedLight.copy(alpha = 0.55f),
                topLeft = Offset(0.62f * w, 0.40f * h),
                size = Size(0.22f * w, 0.12f * h),
                cornerRadius = CornerRadius(cell * 0.05f)
            )
            // gold trim: upper edge and a belt line, plus a small lantern at the prow
            drawPath(s.topTrim, Festive.gold, style = trimStroke)
            drawLine(Festive.gold, Offset(0.15f * w, 0.67f * h), Offset(0.91f * w, 0.67f * h), trimStroke.width, StrokeCap.Round)
            drawCircle(Festive.warmWhite.copy(alpha = 0.35f), cell * 0.11f, Offset(0.955f * w, 0.30f * h))
            drawCircle(Festive.warmWhite, cell * 0.05f, Offset(0.955f * w, 0.30f * h))
        }
    }

    /**
     * Any other block as a gift box with a ribbon cross and a bow. [index] is the block's place on the
     * board: it picks the box colour, and a second set of boxes (index 7 and up) gets ivory polka dots,
     * so two boxes of the same colour are never identical.
     */
    fun DrawScope.drawGift(vehicle: Vehicle, left: Float, top: Float, index: Int, width: Float, height: Float) {
        val gift = Festive.gifts[index.mod(Festive.gifts.size)]
        val dotted = (index / Festive.gifts.size) % 2 == 1
        val bx = left + margin
        val by = top + margin
        val bw = width - margin * 2f
        val bh = height - margin * 2f
        val radius = CornerRadius(cell * 0.2f)
        // body with a darker lower edge for depth
        drawRoundRect(gift.boxDark, Offset(bx, by + cell * 0.035f), Size(bw, bh - cell * 0.035f), radius)
        drawRoundRect(gift.box, Offset(bx, by), Size(bw, bh - cell * 0.05f), radius)
        drawRoundRect(
            Color.White.copy(alpha = 0.10f),
            Offset(bx + cell * 0.04f, by + cell * 0.03f),
            Size(bw - cell * 0.08f, (bh - cell * 0.05f) * 0.34f),
            CornerRadius(cell * 0.16f)
        )

        val horizontal = vehicle.orientation == Orientation.HORIZONTAL
        val band = cell * 0.15f
        val cx = bx + bw / 2f
        val cy = by + (bh - cell * 0.05f) / 2f

        if (dotted) {
            // polka dots on the sides of the ribbon cross, never over it
            val lengthCells = vehicle.length
            val dot = Color.White.copy(alpha = 0.30f)
            for (t in 0 until lengthCells * 2) {
                val along = (t + 0.5f) / (lengthCells * 2)
                if (kotlin.math.abs(along - 0.5f) < 0.16f / lengthCells + 0.08f) continue
                for (side in floatArrayOf(0.2f, 0.8f)) {
                    val px = if (horizontal) bx + bw * along else bx + bw * side
                    val py = if (horizontal) by + (bh - cell * 0.05f) * side else by + bh * along
                    drawCircle(dot, cell * 0.035f, Offset(px, py))
                }
            }
        }

        val ribbon = gift.ribbon.copy(alpha = 0.96f)
        // ribbon along the block's length, then across its middle
        if (horizontal) {
            drawRect(ribbon, Offset(bx + cell * 0.05f, cy - band * 0.45f), Size(bw - cell * 0.10f, band * 0.9f))
            drawRect(ribbon, Offset(cx - band / 2f, by + cell * 0.03f), Size(band, bh - cell * 0.10f))
        } else {
            drawRect(ribbon, Offset(cx - band * 0.45f, by + cell * 0.05f), Size(band * 0.9f, bh - cell * 0.15f))
            drawRect(ribbon, Offset(bx + cell * 0.03f, cy - band / 2f), Size(bw - cell * 0.06f, band))
        }
        // bow: two angled loops and a knot at the crossing
        val loopW = cell * 0.24f
        val loopH = cell * 0.13f
        val edge = Color.Black.copy(alpha = 0.18f)
        for (side in intArrayOf(-1, 1)) {
            val pivot = Offset(cx + side * cell * 0.11f, cy - cell * 0.03f)
            rotate(degrees = side * 28f, pivot = pivot) {
                drawOval(ribbon, Offset(pivot.x - loopW / 2f, pivot.y - loopH / 2f), Size(loopW, loopH))
                drawOval(edge, Offset(pivot.x - loopW / 2f, pivot.y - loopH / 2f), Size(loopW, loopH), style = Stroke(cell * 0.015f))
            }
        }
        drawCircle(ribbon, cell * 0.075f, Offset(cx, cy - cell * 0.03f))
        drawCircle(edge, cell * 0.075f, Offset(cx, cy - cell * 0.03f), style = Stroke(cell * 0.015f))
    }
}
