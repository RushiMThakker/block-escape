package com.rushi.blockescape.ui.festive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rushi.blockescape.audio.FestiveMusic

/**
 * Keeps the festive music in step with the app: it plays while [play] is true AND the app is in
 * the foreground, and pauses the moment the app is backgrounded or [play] turns false.
 */
@Composable
fun FestiveMusicEffect(play: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val music = remember(context) { FestiveMusic(context) }

    DisposableEffect(lifecycleOwner, play, music) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (play) music.start()
                Lifecycle.Event.ON_PAUSE -> music.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!play) music.pause()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            music.pause()
        }
    }
    DisposableEffect(music) { onDispose { music.release() } }
}
