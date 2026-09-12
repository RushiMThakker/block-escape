package com.rushi.blockescape.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rushi.blockescape.ads.BannerAdView
import com.rushi.blockescape.haptics.HapticFeedback
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    levelNumber: Int = 1,
    totalLevels: Int = 1,
    hasNextLevel: Boolean = false,
    onNextLevel: () -> Unit = {},
    onBackToLevels: () -> Unit = {},
    onLevelCleared: () -> Unit = {}
) {
    val board = viewModel.board.value
    val moveCount = viewModel.moveCount.value
    val isWon = viewModel.isWon.value
    val hint = viewModel.hint.value

    val context = LocalContext.current
    val haptics = remember(context) { HapticFeedback(context) }

    // Win haptic: fires only on the false -> true transition of isWon, so it never
    // repeats while the win overlay stays up across recompositions. This is the only
    // "positive" haptic left in the game - move-tap, button-tap, and hint-tap were all
    // removed after Rushi found them too frequent/noisy in practice; blockedMove (in
    // GameBoardScreen.kt) is the only other one still wired in.
    var previousIsWon by remember { mutableStateOf(isWon) }
    LaunchedEffect(isWon) {
        if (isWon && !previousIsWon) {
            haptics.win()
            onLevelCleared()
        }
        previousIsWon = isWon
    }

    // Delays the win overlay's own appearance by a short beat after isWon flips true, so
    // GameBoardScreen's car-exit-through-the-glow flourish gets a moment to actually be
    // visible before this full-screen overlay covers the board. Resets instantly (no
    // delay) the moment isWon goes back to false, e.g. via restart()/Play Again.
    var showWinOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(isWon) {
        if (isWon) {
            delay(280)
            showWinOverlay = true
        } else {
            showWinOverlay = false
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    // A soft warm-to-warm radial wash (surface -> background) instead of a flat fill, so
    // the "tabletop" the board sits on has the same kind of gentle depth the board's own
    // vignette has, rather than reading as a single dead color. Cheap: two color stops,
    // built once per theme (colors only change when the system theme flips).
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

            if (totalLevels > 1) {
                Text(
                    text = "Level $levelNumber of $totalLevels",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Nav-back-to-level-select gets its own top line paired with the move
            // counter, rather than crowding into the Hint/Undo/Restart action row below -
            // that row already fills the available width at three buttons on a typical
            // phone, and a fourth button there ran past the screen edge (found during
            // on-device testing). This keeps "Levels" close to the other HUD controls and
            // identically styled, just on its own line.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Moves: $moveCount", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBackToLevels) { Text("Levels") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Tied visually to the on-board highlight: same warm amber, so the
                // button reads as "the source" of the glow that appears on the board.
                Button(
                    onClick = { viewModel.requestHint() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BoardColors.hintGlow,
                        contentColor = Color(0xFF3B2A1B)
                    )
                ) { Text("Hint") }
                Button(onClick = { viewModel.undo() }) { Text("Undo") }
                Button(onClick = { viewModel.restart() }) { Text("Restart") }
            }

            // The board is pinned to a 1:1 aspect ratio (GameBoardScreen.kt) so on a tall
            // phone it can never fill the remaining height — that's expected. Rather than
            // leaving the leftover space as a dead block below it, this Box claims all the
            // remaining vertical space via weight(1f) and centers the board inside it, so
            // the extra room becomes balanced breathing room above and below the board
            // instead of one big void pinned to the bottom.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Soft ambient shadow grounding the board against the flat surrounding
                // space, like a game board resting on a table rather than floating in it.
                // A radial-gradient blob (not a real blur — keeps this cheap and
                // consistent on minSdk 24), drawn manually with drawWithCache instead of
                // Modifier.background(brush=...): a plain background-brush radial
                // gradient defaults its radius to the shape's own half-width, so by the
                // time it reaches this box's edge (where the downward offset below
                // actually exposes it past the board) the gradient has already faded to
                // fully transparent and nothing was visible. Using a radius wider than
                // the box keeps meaningful alpha all the way to the edge.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .offset(y = 14.dp)
                        .drawWithCache {
                            val brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.30f),
                                    Color.Black.copy(alpha = 0.12f),
                                    Color.Black.copy(alpha = 0f)
                                ),
                                radius = size.width * 0.85f
                            )
                            onDrawBehind {
                                drawRoundRect(brush = brush, cornerRadius = CornerRadius(22.dp.toPx()))
                            }
                        }
                )
                GameBoardScreen(board = board, hint = hint, onMove = viewModel::attemptMove)
            }

            // Ad stub (see ads/AdConfig.kt) pinned as the last item in this Column, so it
            // takes its natural height at the very bottom without disturbing the board's
            // centering logic above. A plain white/gray AdMob banner would look jarring
            // dropped directly on the warm tabletop background, so it gets a subtle
            // rounded "tray" in the theme's own surface tone to sit in instead.
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                BannerAdView()
            }
        }

        // Real entrance transition for the win moment instead of an instant appearance:
        // fades in while scaling up from a slightly-shrunk 0.85 to full size, reading as
        // a satisfying "pop" consistent with the board's own glossy/pulsing visual style
        // (drawVehicle's highlight, drawHintHighlight's breathing glow) rather than a
        // flat fade. `showWinOverlay` (not `isWon` directly) drives visibility so this
        // gets its short delayed beat after winning - see the LaunchedEffect(isWon)
        // above.
        AnimatedVisibility(
            visible = showWinOverlay,
            enter = fadeIn(animationSpec = tween(durationMillis = 320)) +
                scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                scaleOut(targetScale = 0.9f, animationSpec = tween(durationMillis = 150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Solved in $moveCount moves!", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Spacer(modifier = Modifier.padding(8.dp))
                    if (hasNextLevel) {
                        Button(onClick = { onNextLevel() }) { Text("Next Level") }
                        Spacer(modifier = Modifier.padding(4.dp))
                        Button(onClick = { viewModel.restart() }) { Text("Play Again") }
                    } else {
                        Button(onClick = { viewModel.restart() }) { Text("Play Again") }
                    }
                }
            }
        }
    }
}
