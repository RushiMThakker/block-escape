package com.rushi.blockescape.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Nobody can listen to a unit test, so these pin down what can go wrong with generated audio:
 * silence, clipping, clicks at the loop point, and gaps, plus determinism and the policy rule.
 */
class FestiveTuneTest {

    private val pcm: ShortArray by lazy { FestiveTune.render() }

    private fun rms(from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += pcm[i].toDouble() * pcm[i]
        return sqrt(sum / (to - from))
    }

    @Test
    fun `one loop is about fifty seconds`() {
        assertEquals(FestiveTune.totalSamples, pcm.size)
        val seconds = pcm.size / FestiveTune.SAMPLE_RATE.toDouble()
        assertTrue("loop is $seconds s", seconds in 45.0..56.0)
    }

    @Test
    fun `it is audible and never clips`() {
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak is too quiet", peak > 20000)
        assertTrue("peak $peak clips", peak < 32767)
        assertTrue("overall level ${rms(0, pcm.size)} is too low", rms(0, pcm.size) > 1500)
    }

    @Test
    fun `there is no stretch of silence`() {
        val window = FestiveTune.SAMPLE_RATE // one second
        var start = 0
        while (start + window <= pcm.size) {
            assertTrue("silent second starting at $start", rms(start, start + window) > 400)
            start += window
        }
    }

    @Test
    fun `the loop point does not click`() {
        // Tails wrap round the seam, so the end flows into the start. The jump across the seam must be no
        // bigger than a few ordinary neighbouring samples, a small fraction of the peak.
        val jump = abs(pcm.last().toInt() - pcm.first().toInt())
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("jump $jump at the seam (peak $peak)", jump < peak / 6)
        var typicalStep = 0
        for (i in 1000 until 3000) typicalStep = maxOf(typicalStep, abs(pcm[i].toInt() - pcm[i - 1].toInt()))
        assertTrue("seam jump $jump vs ordinary step $typicalStep", jump <= typicalStep * 3 + 200)
    }

    @Test
    fun `rendering is deterministic`() {
        assertArrayEquals(pcm, FestiveTune.render())
    }

    @Test
    fun `music plays only when the festive look is on and music is enabled`() {
        assertTrue(MusicPolicy.shouldPlay(festiveLookActive = true, musicEnabled = true))
        assertFalse(MusicPolicy.shouldPlay(festiveLookActive = true, musicEnabled = false))
        assertFalse(MusicPolicy.shouldPlay(festiveLookActive = false, musicEnabled = true))
        assertFalse(MusicPolicy.shouldPlay(festiveLookActive = false, musicEnabled = false))
    }
}
