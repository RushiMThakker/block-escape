package com.rushi.blockescape.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlin.math.roundToInt

/**
 * An adaptive-width AdMob banner (see [AdConfig.BANNER_AD_UNIT_ID] for which ad unit this
 * loads — currently Google's public test unit). Wraps the classic Android `AdView` via
 * Compose's `AndroidView` interop, which is the Google-documented way to host AdMob
 * banners in Compose.
 *
 * Sized via [AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize] using the actual
 * measured width available to this composable, rather than the older fixed
 * `AdSize.BANNER` (320x50dp). A fixed-size banner risks being squeezed/clipped on any
 * screen narrower than 320dp of available width once surrounding padding/chrome is
 * subtracted — adaptive sizing asks the SDK for whatever height best fits the width it's
 * actually given, so it can never be wider than its container.
 *
 * Lifecycle: the underlying `AdView` is recreated only when the available width changes
 * (e.g. a rotation), via [remember] keyed on that width, and torn down when this
 * composable leaves composition or that key changes. It's also paused/resumed alongside
 * the host Activity/Fragment lifecycle (so it stops refreshing while backgrounded),
 * matching Google's own AdView lifecycle guidance.
 */
@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val adWidthDp = maxWidth.value.roundToInt()
        if (adWidthDp <= 0) return@BoxWithConstraints

        val adSize = remember(adWidthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
        }

        val adView = remember(adSize) {
            AdView(context).apply {
                setAdSize(adSize)
                adUnitId = AdConfig.BANNER_AD_UNIT_ID
            }
        }

        DisposableEffect(lifecycleOwner, adView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            adView.loadAd(AdRequest.Builder().build())

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                adView.destroy()
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { adView }
        )
    }
}
