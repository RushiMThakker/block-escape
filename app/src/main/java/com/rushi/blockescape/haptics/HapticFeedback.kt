package com.rushi.blockescape.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Thin, Context-based wrapper around Android's vibration APIs for short UI feedback.
 *
 * Originally had five effects (move/button/blocked/hint/win); Rushi found the frequent
 * ones (a buzz on every single move and every button tap) too noisy in practice, so only
 * two remain - blocked-move and win - both toned down from their original intensity too.
 * This app's minSdk is 24 and targetSdk/compileSdk is 35, so this class has to bridge
 * three API-level splits:
 *
 *  - Obtaining the vibrator: API 31+ (S) requires going through [VibratorManager]
 *    (`Context.getSystemService(VibratorManager::class.java)` /
 *    `VIBRATOR_MANAGER_SERVICE`) and reading its `defaultVibrator`; below that, a
 *    [Vibrator] is obtained directly via `Context.getSystemService(VIBRATOR_SERVICE)`
 *    (that constant/service still resolves fine on 31+ too, but Google's own docs mark
 *    it deprecated there in favor of VibratorManager).
 *  - Firing a vibration: API 26+ (O) supports [VibrationEffect], which is the only way
 *    to control amplitude (intensity) and to describe multi-step waveforms cleanly.
 *    Below O, only the older `Vibrator.vibrate(long)` / `vibrate(long[], Int)`
 *    overloads exist - amplitude-less (full-strength, on/off only) but still safe to
 *    call on every device back to API 24.
 *
 * There is deliberately no mute/settings toggle here: `Vibrator.vibrate` already no-ops
 * correctly when the user has disabled vibration system-wide, so respecting that is
 * "do nothing extra."
 *
 * All calls are wrapped in try/catch and null-checked - haptics are a nice-to-have
 * polish layer, and some devices (and most emulators) either lack a vibrator or behave
 * unpredictably when queried for one. A failure here must never crash gameplay.
 */
class HapticFeedback(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Blocked move: a brief, gentle buzz signaling "no" - softened from an earlier,
     * sharper version that felt too aggressive for something that can happen many times
     * per puzzle.
     */
    fun blockedMove() = oneShot(durationMs = 30, amplitude = 110)

    /**
     * Win: the payoff moment. A short two-pulse pattern that builds (a light pulse, a
     * beat, then a slightly stronger one) rather than a single generic buzz - softened
     * from an earlier, more intense version, but still distinct from blockedMove's flat
     * single tap.
     */
    fun win() {
        val v = vibrator ?: return
        if (!safeHasVibrator(v)) return
        try {
            // timings: [wait, on, off, on, off, on] in ms. amplitudes: 0 during the
            // "off" gaps, ramping up on each successive pulse.
            val timings = longArrayOf(0, 35, 70, 40, 80, 60)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 70, 0, 110, 0, 150)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        } catch (_: Exception) {
            // Best-effort only.
        }
    }

    private fun oneShot(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!safeHasVibrator(v)) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (_: Exception) {
            // Best-effort only.
        }
    }

    private fun safeHasVibrator(v: Vibrator): Boolean =
        try {
            v.hasVibrator()
        } catch (_: Exception) {
            // If we can't even ask, assume yes and let the vibrate() call itself be the
            // (equally guarded) final word - erring toward "try it" rather than silently
            // never firing due to a flaky hasVibrator() implementation.
            true
        }
}
