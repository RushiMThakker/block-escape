package com.rushi.blockescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rushi.blockescape.domain.Board
import com.rushi.blockescape.domain.Orientation
import com.rushi.blockescape.domain.Vehicle
import com.rushi.blockescape.ui.GameBoardScreen
import com.rushi.blockescape.ui.theme.BlockEscapeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previewBoard = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
                Vehicle("blocker", Orientation.VERTICAL, length = 3, row = 1, col = 4)
            )
        )
        setContent {
            BlockEscapeTheme {
                Surface(modifier = Modifier) {
                    GameBoardScreen(board = previewBoard)
                }
            }
        }
    }
}
