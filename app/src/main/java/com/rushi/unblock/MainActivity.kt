package com.rushi.unblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rushi.unblock.domain.Board
import com.rushi.unblock.domain.Orientation
import com.rushi.unblock.domain.Vehicle
import com.rushi.unblock.ui.GameBoardScreen
import com.rushi.unblock.ui.theme.UnblockTheme

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
            UnblockTheme {
                Surface(modifier = Modifier) {
                    GameBoardScreen(board = previewBoard)
                }
            }
        }
    }
}
