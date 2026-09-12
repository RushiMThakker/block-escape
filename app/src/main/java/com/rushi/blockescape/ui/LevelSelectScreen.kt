package com.rushi.blockescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Landing screen (see MainActivity.kt): a grid of every level in the pack, each tile in
 * one of three states derived entirely from [highestUnlockedIndex] - this project's
 * level progression is strictly linear, so that single int is enough to tell locked
 * apart from unlocked apart from cleared for every index, no separate per-level state
 * needed:
 *
 *  - locked (index > highestUnlockedIndex): dimmed, a lock glyph, not clickable.
 *  - unlocked, not yet cleared (index == highestUnlockedIndex): normal warm wood tone,
 *    clickable, meant to read as "play me next."
 *  - cleared (index < highestUnlockedIndex): a distinct moss-green accent + checkmark,
 *    still clickable (replaying a cleared level is allowed).
 *
 * Shares GameScreen's "BLOCK ESCAPE" wordmark and warm radial background wash so the two
 * screens read as one app rather than two stapled-together UIs.
 */
@Composable
fun LevelSelectScreen(
    totalLevels: Int,
    highestUnlockedIndex: Int,
    onLevelSelected: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    // Same soft warm radial wash GameScreen uses for its "tabletop" background, so
    // switching between the two screens doesn't feel like leaving the app.
    val backgroundBrush = remember(colorScheme.surface, colorScheme.background) {
        Brush.radialGradient(listOf(colorScheme.surface, colorScheme.background))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = "BLOCK ESCAPE",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Select a Level",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                items(totalLevels) { index ->
                    LevelTile(
                        levelNumber = index + 1,
                        state = levelTileState(index, highestUnlockedIndex),
                        onClick = { onLevelSelected(index) }
                    )
                }
            }
        }
    }
}

private enum class LevelTileState { LOCKED, UNLOCKED, CLEARED }

private fun levelTileState(index: Int, highestUnlockedIndex: Int): LevelTileState = when {
    index < highestUnlockedIndex -> LevelTileState.CLEARED
    index <= highestUnlockedIndex -> LevelTileState.UNLOCKED
    else -> LevelTileState.LOCKED
}

@Composable
private fun LevelTile(
    levelNumber: Int,
    state: LevelTileState,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    // A moss-green distinct from the warm wood/amber palette everywhere else in this
    // app, specifically so "cleared" reads unmistakably as its own third state rather
    // than a slightly-different shade of "unlocked" - see BoardColors.kt for the
    // palette this needs to sit alongside without being confused for a vehicle color.
    val clearedColor = Color(0xFF6E8B3D)
    val clearedBorder = Color(0xFF4F6B26)

    val (fill, border, contentColor, clickable) = when (state) {
        LevelTileState.LOCKED -> Quad(
            colorScheme.surfaceVariant.copy(alpha = 0.55f),
            Color.Transparent,
            colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            false
        )
        LevelTileState.UNLOCKED -> Quad(
            BoardColors.woodBase,
            BoardColors.woodLight,
            Color(0xFFFFF8EF),
            true
        )
        LevelTileState.CLEARED -> Quad(
            clearedColor,
            clearedBorder,
            Color(0xFFFFF8EF),
            true
        )
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .border(width = 2.dp, color = border, shape = RoundedCornerShape(16.dp))
            .then(
                if (clickable) Modifier.clickable(onClick = onClick) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = levelNumber.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            // A tiny second line under the number distinguishing locked (a lock glyph)
            // from cleared (a checkmark) at a glance; unlocked-not-cleared gets no
            // second line so it doesn't compete visually with the two "already has a
            // story" states.
            when (state) {
                LevelTileState.LOCKED -> Text(
                    text = "🔒", // lock emoji - no material-icons dependency in this project
                    fontSize = 13.sp
                )
                LevelTileState.CLEARED -> Text(
                    text = "✓", // checkmark
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                LevelTileState.UNLOCKED -> Unit
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
