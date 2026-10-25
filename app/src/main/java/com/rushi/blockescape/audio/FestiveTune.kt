package com.rushi.blockescape.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The festive background music: an ORIGINAL gentle music-box loop, generated in code, so there is no
 * audio file to license or ship. About 50 seconds, 16 bars at 76 bpm in C major.
 *
 *  - Melody: a pentatonic tune (C D E G A), which cannot clash with the chords underneath.
 *  - Chords: C - Am - F - G, then a variation (C - Em - Dm - G) that falls back into the start.
 *  - Sound: bell-like partials with a fast decay for the tune and a rolling arpeggio, a soft sine
 *    pad, a faint sleigh-bell shimmer, and a little reverb.
 *
 * The loop is seamless: every note is written with wrap-around (so tails ring on across the seam
 * into the start), the reverb runs on a circular buffer, and the pad is silent at bar lines.
 * Pure and deterministic (a fixed pseudo-random sequence for the shimmer), so it is unit-tested.
 */
object FestiveTune {
    const val SAMPLE_RATE = 22050
    private const val BPM = 76.0
    private const val BARS = 16
    private const val EIGHTHS_PER_BAR = 8

    private val eighthSamples = (SAMPLE_RATE * 60.0 / BPM / 2.0).toInt()
    val totalSamples: Int = eighthSamples * EIGHTHS_PER_BAR * BARS

    private class Chord(val bass: Int, val arp: IntArray)

    private val c = Chord(48, intArrayOf(60, 64, 67))
    private val am = Chord(45, intArrayOf(57, 60, 64))
    private val f = Chord(41, intArrayOf(57, 60, 65))
    private val g = Chord(43, intArrayOf(59, 62, 67))
    private val em = Chord(40, intArrayOf(59, 64, 67))
    private val dm = Chord(38, intArrayOf(57, 62, 65))

    private val chords = listOf(c, am, f, g, c, am, f, g, c, am, f, g, c, em, dm, g)

    /** The 8-bar tune as flat triples: position in the bar (in eighths), MIDI note, length in eighths. */
    private val melody: List<IntArray> = listOf(
        intArrayOf(0, 76, 2, 2, 79, 1, 3, 76, 1, 4, 74, 2, 6, 72, 2),
        intArrayOf(0, 76, 2, 2, 81, 1, 3, 79, 1, 4, 76, 3),
        intArrayOf(0, 84, 2, 2, 81, 1, 3, 79, 1, 4, 81, 2, 6, 79, 2),
        intArrayOf(0, 76, 2, 2, 74, 1, 3, 76, 1, 4, 74, 4),
        intArrayOf(0, 79, 2, 2, 76, 1, 3, 79, 1, 4, 84, 2, 6, 81, 2),
        intArrayOf(0, 79, 2, 2, 81, 1, 3, 76, 1, 4, 79, 3),
        intArrayOf(0, 81, 2, 2, 79, 1, 3, 76, 1, 4, 74, 2, 6, 72, 2),
        intArrayOf(0, 74, 2, 2, 76, 2, 4, 74, 4)
    )

    private val arpPattern = intArrayOf(0, 1, 2, 1, 0, 1, 2, 1)

    private val partialRatio = doubleArrayOf(1.0, 2.0, 4.0)
    private val partialGain = doubleArrayOf(1.0, 0.25, 0.08)
    private val partialDecaySeconds = doubleArrayOf(0.9, 0.45, 0.2)

    /** Renders one full loop as 16-bit mono PCM at [SAMPLE_RATE]. */
    fun render(): ShortArray {
        val n = totalSamples
        val dry = FloatArray(n)

        for (bar in 0 until BARS) {
            val chord = chords[bar]
            val barStart = bar * EIGHTHS_PER_BAR * eighthSamples
            addPad(dry, barStart, chord)
            // rolling arpeggio, softer than the tune
            for (step in 0 until EIGHTHS_PER_BAR) {
                val note = chord.arp[arpPattern[step]]
                addBell(dry, barStart + step * eighthSamples, note, 0.16)
            }
            // the tune, twice through the 8 bars; the second pass is doubled an octave up for sparkle
            val tune = melody[bar % melody.size]
            var i = 0
            while (i < tune.size) {
                val start = barStart + tune[i] * eighthSamples
                addBell(dry, start, tune[i + 1], 0.42)
                if (bar >= melody.size) addBell(dry, start, tune[i + 1] + 12, 0.12)
                i += 3
            }
            // a faint sleigh-bell shimmer once every four bars
            if (bar % 4 == 1) addShimmer(dry, barStart)
        }

        val wet = reverb(dry)
        var peak = 0f
        for (v in wet) peak = max(peak, abs(v))
        val scale = if (peak > 0f) 0.8f * 32767f / peak else 0f
        return ShortArray(n) { (wet[it] * scale).toInt().coerceIn(-32767, 32767).toShort() }
    }

    private fun frequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** One music-box note: a few decaying partials with a 4 ms attack, written with wrap-around. */
    private fun addBell(buf: FloatArray, start: Int, midi: Int, gain: Double) {
        val n = buf.size
        val length = (SAMPLE_RATE * 2.2).toInt()
        val attack = (SAMPLE_RATE * 0.004).toInt()
        val base = frequency(midi)
        for (p in partialRatio.indices) {
            val w = 2.0 * PI * base * partialRatio[p] / SAMPLE_RATE
            val tau = partialDecaySeconds[p] * SAMPLE_RATE
            val amp = gain * partialGain[p]
            // sine by recurrence: s[k] = 2cos(w) s[k-1] - s[k-2]
            val coeff = 2.0 * cos(w)
            var s1 = sin(-w)
            var s2 = sin(-2.0 * w)
            for (k in 0 until length) {
                val s = coeff * s1 - s2
                s2 = s1
                s1 = s
                val env = (if (k < attack) k / attack.toDouble() else 1.0) * exp(-k / tau)
                buf[(start + k) % n] += (amp * env * s).toFloat()
            }
        }
    }

    /** A soft two-note pad (root and fifth), silent at both bar lines so it never clicks. */
    private fun addPad(buf: FloatArray, barStart: Int, chord: Chord) {
        val n = buf.size
        val length = EIGHTHS_PER_BAR * eighthSamples
        val rise = SAMPLE_RATE * 0.7
        val fall = SAMPLE_RATE * 0.9
        for (semitones in intArrayOf(0, 7)) {
            val w = 2.0 * PI * frequency(chord.bass + 12 + semitones) / SAMPLE_RATE
            for (k in 0 until length) {
                val env = min(min(k / rise, (length - k) / fall), 1.0)
                buf[(barStart + k) % n] += (0.085 * env * sin(w * k)).toFloat()
            }
        }
    }

    /** Bright, quickly fading noise: a hint of sleigh bells. Deterministic. */
    private fun addShimmer(buf: FloatArray, barStart: Int) {
        val n = buf.size
        var seed = 12345L + barStart
        var previous = 0.0
        val length = (SAMPLE_RATE * 0.35).toInt()
        for (k in 0 until length) {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            val noise = seed / 1073741824.0 - 1.0
            val bright = noise - previous // differencing removes the low end
            previous = noise
            val env = exp(-k / (SAMPLE_RATE * 0.07))
            buf[(barStart + k) % n] += (0.05 * env * bright).toFloat()
        }
    }

    /** Three feedback combs on a circular buffer, so the tail rolls over the loop point. */
    private fun reverb(dry: FloatArray): FloatArray {
        val n = dry.size
        val delays = intArrayOf((SAMPLE_RATE * 0.047).toInt(), (SAMPLE_RATE * 0.061).toInt(), (SAMPLE_RATE * 0.083).toInt())
        val gains = floatArrayOf(0.30f, 0.24f, 0.18f)
        val out = dry.copyOf()
        repeat(3) {
            for (i in 0 until n) {
                var v = dry[i]
                for (d in delays.indices) v += gains[d] * out[(i - delays[d] + n) % n]
                out[i] = v
            }
        }
        return out
    }
}
