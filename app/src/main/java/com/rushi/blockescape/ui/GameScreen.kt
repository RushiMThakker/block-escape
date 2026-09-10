package com.rushi.blockescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val board = viewModel.board.value
    val moveCount = viewModel.moveCount.value
    val isWon = viewModel.isWon.value

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Moves: $moveCount", style = MaterialTheme.typography.titleMedium)
                Row {
                    Button(onClick = { viewModel.undo() }) { Text("Undo") }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Button(onClick = { viewModel.restart() }) { Text("Restart") }
                }
            }
            GameBoardScreen(board = board, onMove = viewModel::attemptMove)
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
                    Button(onClick = { viewModel.restart() }) { Text("Play Again") }
                }
            }
        }
    }
}
