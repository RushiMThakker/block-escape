package com.rushi.blockescape.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rushi.blockescape.ads.BannerAdView

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    levelNumber: Int = 1,
    totalLevels: Int = 1,
    hasNextLevel: Boolean = false,
    onNextLevel: () -> Unit = {}
) {
    val board = viewModel.board.value
    val moveCount = viewModel.moveCount.value
    val isWon = viewModel.isWon.value
    val hint = viewModel.hint.value

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Moves: $moveCount", style = MaterialTheme.typography.titleMedium)
                Row {
                    // Tied visually to the on-board highlight: same warm amber, so the
                    // button reads as "the source" of the glow that appears on the board.
                    Button(
                        onClick = { viewModel.requestHint() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BoardColors.hintGlow,
                            contentColor = Color(0xFF3B2A1B)
                        )
                    ) { Text("Hint") }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Button(onClick = { viewModel.undo() }) { Text("Undo") }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Button(onClick = { viewModel.restart() }) { Text("Restart") }
                }
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

        if (isWon) {
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
                        Button(onClick = onNextLevel) { Text("Next Level") }
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
