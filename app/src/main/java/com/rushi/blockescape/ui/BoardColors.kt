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

    val vehiclePrimary = Color(0xFFE53935)
    val vehiclePalette = listOf(
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFFB8C00),
        Color(0xFF8E24AA),
        Color(0xFF00897B),
        Color(0xFFFDD835)
    )
}
