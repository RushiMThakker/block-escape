package com.rushi.blockescape

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.gms.ads.MobileAds
import com.rushi.blockescape.level.LevelPack
import com.rushi.blockescape.level.LevelRepository
import com.rushi.blockescape.ui.GameScreen
import com.rushi.blockescape.ui.GameViewModel
import com.rushi.blockescape.ui.theme.BlockEscapeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fire-and-forget: doesn't need to block startup, just needs to happen before
        // BannerAdView requests its first ad.
        MobileAds.initialize(this) { }
        setContent {
            BlockEscapeTheme {
                Surface(modifier = Modifier) {
                    BlockEscapeApp(context = applicationContext)
                }
            }
        }
    }
}

/**
 * Minimal level-progression wiring for this vertical slice: an ordered list of level
 * files (LevelPack), play them in order, advance on win. No level-select UI, no
 * persistence across app restarts — deliberately out of scope for now (YAGNI).
 *
 * GameViewModel is constructed directly rather than via ViewModelProvider (an accepted
 * simplification already in place for this vertical slice — see GameViewModel.kt), so
 * advancing levels re-keys a `remember(levelIndex)` block to build a fresh Board and
 * GameViewModel whenever the index changes.
 */
@Composable
private fun BlockEscapeApp(context: Context) {
    val levelFiles = LevelPack.ORDERED_LEVEL_FILES
    var levelIndex by remember { mutableIntStateOf(0) }

    val viewModel = remember(levelIndex) {
        val board = LevelRepository(context).loadLevel(levelFiles[levelIndex])
        GameViewModel(board)
    }

    GameScreen(
        viewModel = viewModel,
        levelNumber = levelIndex + 1,
        totalLevels = levelFiles.size,
        hasNextLevel = levelIndex < levelFiles.lastIndex,
        onNextLevel = {
            if (levelIndex < levelFiles.lastIndex) levelIndex++
        }
    )
}
