package com.rushi.blockescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rushi.blockescape.level.LevelRepository
import com.rushi.blockescape.ui.GameScreen
import com.rushi.blockescape.ui.GameViewModel
import com.rushi.blockescape.ui.theme.BlockEscapeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val board = LevelRepository(applicationContext).loadLevel("level_001.json")
        val viewModel = GameViewModel(board)
        setContent {
            BlockEscapeTheme {
                Surface(modifier = Modifier) {
                    GameScreen(viewModel = viewModel)
                }
            }
        }
    }
}
