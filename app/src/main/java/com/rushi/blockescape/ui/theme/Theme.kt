package com.rushi.blockescape.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import com.rushi.blockescape.ui.BoardColors
import com.rushi.blockescape.ui.festive.Festive
import com.rushi.blockescape.ui.festive.LocalReduceMotion
import com.rushi.blockescape.ui.festive.LocalSeasonal
import com.rushi.blockescape.ui.festive.rememberReduceMotion

// The app's overall chrome (everything outside the wood-grain board) is tuned to sit in
// the same warm/neutral visual family as BoardColors' wood palette, without literally
// reusing wood-brown for the whole screen — the board should still read as a distinct
// object (like a game board on a table), so the surrounding tones are desaturated,
// warm-neutral "tabletop" colors rather than more wood.
private val LightColors = lightColorScheme(
    primary = BoardColors.woodBase, // ties buttons/accents to the board
    onPrimary = Color(0xFFFFF8EF),
    secondary = BoardColors.woodLight,
    onSecondary = Color(0xFF3B2A1B),
    background = Color(0xFFE2D4BC), // warm linen "tabletop" surrounding the board
    onBackground = Color(0xFF3B2A1B),
    surface = Color(0xFFF4EBDC),
    onSurface = Color(0xFF3B2A1B),
    surfaceVariant = Color(0xFFDDCBAE),
    onSurfaceVariant = Color(0xFF4A3722)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC79A6B), // lighter wood tone for contrast against the dark background
    onPrimary = Color(0xFF2A1B0F),
    secondary = Color(0xFF9C6E45),
    onSecondary = Color(0xFFF3E7D6),
    background = Color(0xFF160F09), // deep warm umber, not a generic near-black
    onBackground = Color(0xFFEDE0D0),
    surface = Color(0xFF332417),
    onSurface = Color(0xFFEDE0D0),
    surfaceVariant = Color(0xFF3A2A1A),
    onSurfaceVariant = Color(0xFFD8C6AC)
)

// The festive look replaces the whole palette (light or dark): a winter night with frost-blue controls.
// Gold is kept for lights and trim only, so the amber Hint button still stands apart.
private val WinterColors = darkColorScheme(
    primary = Festive.frostSoft,
    onPrimary = Color(0xFF0B1630),
    secondary = Festive.frost,
    onSecondary = Color(0xFF0B1630),
    background = Festive.skyTop,
    onBackground = Color(0xFFEAF2FB),
    surface = Color(0xFF16305C),
    onSurface = Color(0xFFEAF2FB),
    surfaceVariant = Color(0xFF1D3A6E),
    onSurfaceVariant = Color(0xFFB9CCE6)
)

@Composable
fun BlockEscapeTheme(seasonal: Boolean = false, content: @Composable () -> Unit) {
    val colors = when {
        seasonal -> WinterColors
        isSystemInDarkTheme() -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalSeasonal provides seasonal,
        LocalReduceMotion provides rememberReduceMotion()
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
