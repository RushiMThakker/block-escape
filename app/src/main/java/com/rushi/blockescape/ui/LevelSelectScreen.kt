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
 * one of three states derived entirely from [clearedCount] (how many levels have been
 * cleared) - this project's level progression is strictly linear, so that single int is
 * enough to tell locked apart from unlocked apart from cleared for every index, no
 * separate per-level state needed:
 *
 *  - locked (index > clearedCount): dimmed, a lock glyph, not clickable.
 *  - unlocked, not yet cleared (index == clearedCount): normal warm wood tone, clickable,
 *    meant to read as "play me next."
 *  - cleared (index < clearedCount): a distinct moss-green accent + checkmark, still
 *    clickable (replaying a cleared level is allowed).
 *
 * `clearedCount` rather than a "highest unlocked index": see ProgressStore.kt's
 * nextClearedCount doc for why - in short, an index-based model can't distinguish
 * "unlocked the last level" from "cleared the last level," so the final level could never
 * show as cleared. A count has no such collision.
 *
 * Shares GameScreen's "BLOCK ESCAPE" wordmark and warm radial background wash so the two
 * screens read as one app rather than two stapled-together UIs.
 */
@Composable
fun LevelSelectScreen(
    totalLevels: Int,
    clearedCount: Int,
    bestMoveCounts: List<Int>,
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
                    val state = levelTileState(index, clearedCount)
                    LevelTile(
                        levelNumber = index + 1,
                        state = state,
                        // Locked tiles never reveal this - same "keep some mystery" rule
                        // as the lock glyph hiding everything else about a level the
                        // player hasn't reached yet. bestMoveCounts.getOrNull rather than
                        // indexing directly: while the background solve is still in
                        // flight (see LevelBestMoves.kt) this list may briefly be empty,
                        // and a tile with no number yet is a harmless no-op, not a crash.
                        bestMoves = if (state == LevelTileState.LOCKED) null else bestMoveCounts.getOrNull(index),
                        onClick = { onLevelSelected(index) }
                    )
                }
            }
        }
    }
}

private enum class LevelTileState { LOCKED, UNLOCKED, CLEARED }

private fun levelTileState(index: Int, clearedCount: Int): LevelTileState = when {
    index < clearedCount -> LevelTileState.CLEARED
    index <= clearedCount -> LevelTileState.UNLOCKED
    else -> LevelTileState.LOCKED
}

@Composable
private fun LevelTile(
    levelNumber: Int,
    state: LevelTileState,
    bestMoves: Int?,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    // A moss-green distinct from the warm wood/amber palette everywhere else in this
    // app, specifically so "cleared" reads unmistakably as its own third state rather
    // than a slightly-different shade of "unlocked" - see BoardColors.kt for the
    // palette this needs to sit alongside without being confused for a vehicle color.
    val clearedColor = Color(0xFF6E8B3D)
    val clearedBorder = Color(0xFF4F6B26)

    val style = when (state) {
        LevelTileState.LOCKED -> TileStyle(
            fill = colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = Color.Transparent,
            contentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            clickable = false
        )
        LevelTileState.UNLOCKED -> TileStyle(
            fill = BoardColors.woodBase,
            border = BoardColors.woodLight,
            contentColor = Color(0xFFFFF8EF),
            clickable = true
        )
        LevelTileState.CLEARED -> TileStyle(
            fill = clearedColor,
            border = clearedBorder,
            contentColor = Color(0xFFFFF8EF),
            clickable = true
        )
    }
    val (fill, border, contentColor, clickable) = style

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
            // The solver's best-possible move count for this level (see LevelBestMoves.kt)
            // - a third, smaller line so it reads as a footnote to the level number/state
            // above rather than competing with them. bestMoves is already null for locked
            // tiles (enforced at the call site), so this naturally never renders there.
            bestMovesTileLabel(bestMoves)?.let { label ->
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}

private data class TileStyle(val fill: Color, val border: Color, val contentColor: Color, val clickable: Boolean)

/**
 * Text for a level-select tile's best-possible-move-count footnote, or null to render
 * nothing. Pure and separately unit-testable (see LevelSelectScreenTextTest.kt) from the
 * Compose rendering around it - same pure-logic/UI-wrapper split as ProgressStore.kt's
 * nextClearedCount. [bestMoves] is null both for locked tiles (the call site never passes
 * a value there) and for any tile whose number hasn't been computed yet (see
 * LevelBestMoves.kt) - either way, omitting the line is the right behavior, not an error.
 */
fun bestMovesTileLabel(bestMoves: Int?): String? = bestMoves?.let { "Best: $it" }
