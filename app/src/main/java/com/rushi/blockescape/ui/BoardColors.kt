package com.rushi.blockescape.ui

import androidx.compose.ui.graphics.Color

object BoardColors {
    val woodBase = Color(0xFF8B5A2B)
    val woodDark = Color(0xFF6B4423)
    val woodLight = Color(0xFFA6714A)
    val gridLine = Color(0x33000000)
    val exitGlow = Color(0xFFFFD54F)

    // A distinct-but-related amber for the hint highlight/arrow and its HUD button - warm
    // enough to sit naturally alongside exitGlow without being mistaken for "you've
    // reached the exit" (it's a touch more saturated/orange than exitGlow's soft yellow).
    val hintGlow = Color(0xFFFFB300)

    // Softened (Material "300"-tier) versions of the original vehicle colors - less
    // saturated/bright so the board reads calmer, while staying distinct enough from
    // each other and from woodBase/woodDark to stay readable at a glance.
    val vehiclePrimary = Color(0xFFE57373)
    val vehiclePalette = listOf(
        Color(0xFF64B5F6),
        Color(0xFF81C784),
        Color(0xFFFFB74D),
        Color(0xFFBA68C8),
        Color(0xFF4DB6AC),
        Color(0xFFFFF176)
    )
}
