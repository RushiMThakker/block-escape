package com.rushi.blockescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.rushi.blockescape.ui.festive.FestiveBackdrop
import com.rushi.blockescape.ui.festive.LocalSeasonal

/** A screen's backdrop: the usual warm wash, or the festive night (garland, frost, snow) when the seasonal look is on. */
@Composable
fun ScreenSurface(seasonal: Boolean, brush: Brush, garland: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    if (seasonal) {
        FestiveBackdrop(trees = false, garland = garland, content = content)
    } else {
        Box(modifier = Modifier.fillMaxSize().background(brush), content = content)
    }
}

/** Room for the string of lights hanging under the status bar; nothing in the normal look. */
@Composable
fun FestiveTopInset() {
    if (LocalSeasonal.current) Spacer(Modifier.height(34.dp))
}
