package com.rushi.blockescape.ui.festive

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first

/** True while the festive look is active (setting + date). Provided once at the app root. */
val LocalSeasonal = compositionLocalOf { false }

/**
 * True while the player is dragging a block: ambient motion (snow, twinkle) stops requesting frames
 * so every frame goes to the drag, and picks up where it left off afterwards.
 */
val LocalAmbientPaused = compositionLocalOf<State<Boolean>> { NeverPaused }
private val NeverPaused: State<Boolean> = mutableStateOf(false)

/** True when the system "remove animations" setting is on: decorative motion stays still. */
val LocalReduceMotion = compositionLocalOf { false }

/** Elegant winter night: deep navy, frost, a single warm gold. Restraint: gold is only light and trim. */
object Festive {
    val skyTop = Color(0xFF09122D)
    val skyBottom = Color(0xFF173362)
    val frost = Color(0xFFD7EAFB)
    val frostSoft = Color(0xFF9CC3EE)
    val gold = Color(0xFFF4C95D)
    val goldDeep = Color(0xFFC9962B)
    val warmWhite = Color(0xFFFFF0C8)
    val evergreen = Color(0xFF14412F)
    val evergreenDeep = Color(0xFF0B2A1E)
    val snow = Color(0xFFF2F8FF)

    val sleighRed = Color(0xFFC62828)
    val sleighRedDark = Color(0xFF8E1B1B)
    val sleighRedLight = Color(0xFFE35A4F)

    /** Light bulb colours, kept soft so the string reads as warm white with a few accents. */
    val bulbs = listOf(
        Color(0xFFFFE2A0),
        Color(0xFFF4C95D),
        Color(0xFFFF9E8A),
        Color(0xFF9CD3FF),
        Color(0xFFFFE2A0),
        Color(0xFFB7E8B0)
    )

    /** The ring of lights round the board: warm white and amber only, so it frames the puzzle without competing with it. */
    val warmBulbs = listOf(
        Color(0xFFFFE2A0),
        Color(0xFFF4C95D),
        Color(0xFFFFE9C0),
        Color(0xFFFFC27A)
    )

    /** Gift box fill + ribbon, none of them red or red-ribboned so the red sleigh always stands out. */
    class Gift(val box: Color, val boxDark: Color, val ribbon: Color)

    val gifts = listOf(
        Gift(Color(0xFF1F7A4D), Color(0xFF14573A), Color(0xFFF1E4B8)),  // emerald, ivory ribbon
        Gift(Color(0xFF2D5FA8), Color(0xFF1F4781), Color(0xFFC9D6E6)),  // royal blue, silver ribbon
        Gift(Color(0xFF7A3E8E), Color(0xFF572B67), Color(0xFFF4C95D)),  // plum, gold ribbon
        Gift(Color(0xFF1C7C80), Color(0xFF14595C), Color(0xFFF1E4B8)),  // teal, ivory ribbon
        Gift(Color(0xFFB8742A), Color(0xFF8A5420), Color(0xFFF7F0DC)),  // bronze, ivory ribbon
        Gift(Color(0xFFE8E2D0), Color(0xFFC4BCA3), Color(0xFF2E7D4F)),  // pearl, green ribbon (never red: red is the sleigh)
        Gift(Color(0xFFC9B037), Color(0xFF9B8624), Color(0xFF1F5D43))   // citrine, evergreen ribbon
    )
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * A clock (in seconds) that advances at most [fps] times a second, only while the app is
 * RESUMED, and not at all under "remove animations". Drawing code reads `.value` inside a draw
 * scope, so each tick redraws the canvas without recomposing anything.
 */
@Composable
fun rememberFestiveClock(fps: Int): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val reduceMotion = LocalReduceMotion.current
    val paused = LocalAmbientPaused.current
    if (!reduceMotion) {
        LaunchedEffect(lifecycleOwner, fps) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val intervalNanos = 1_000_000_000L / fps
                var last = 0L
                var origin = -1L
                while (true) {
                    if (paused.value) {
                        // Suspend without asking for frames, then continue from the time we stopped at.
                        snapshotFlow { paused.value }.first { !it }
                        origin = -1L
                    }
                    withFrameNanos { now ->
                        if (origin < 0L) origin = now - (time.floatValue * 1_000_000_000f).toLong()
                        if (now - last >= intervalNanos) {
                            last = now
                            time.floatValue = (now - origin) / 1_000_000_000f
                        }
                    }
                }
            }
        }
    }
    return time
}
