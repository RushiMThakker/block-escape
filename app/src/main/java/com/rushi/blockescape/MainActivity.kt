package com.rushi.blockescape

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.MobileAds
import com.rushi.blockescape.level.LevelPack
import com.rushi.blockescape.level.LevelRepository
import com.rushi.blockescape.progress.ProgressStore
import com.rushi.blockescape.ui.GameScreen
import com.rushi.blockescape.ui.GameViewModel
import com.rushi.blockescape.ui.LevelSelectScreen
import com.rushi.blockescape.ui.theme.BlockEscapeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() — this is the documented
        // core-splashscreen requirement (it installs the splash screen by adjusting the
        // activity's theme before the window is created).
        installSplashScreen()
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
 * Two-screen local navigation: level-select (the app's landing screen) and gameplay. No
 * Jetpack Navigation library — this project deliberately avoids over-engineering (see
 * LevelPack.kt's plain ordered-list approach, GameViewModel's direct-construction-not-
 * ViewModelProvider approach); a small local sealed class is enough here since the only
 * "back stack" that exists is "Game always returns to LevelSelect."
 */
private sealed class Screen {
    data object LevelSelect : Screen()
    data class Game(val levelIndex: Int) : Screen()
}

/**
 * Owns progress persistence + screen state above both screens. The app now opens to
 * level-select rather than straight into gameplay (a deliberate UX change, per the owner)
 * and only enters gameplay once a level is tapped.
 *
 * `clearedCount` (how many levels have been cleared) is the single source of truth for
 * lock/cleared state, hoisted here (not inside either screen) so it survives navigating
 * between them without extra plumbing. It's updated immediately in-memory when a level is
 * cleared (see onLevelCleared below) and additionally re-read from ProgressStore every
 * time `screen` settles back on LevelSelect, so the level-select grid is always showing
 * the freshest persisted value rather than trusting only the in-memory copy.
 *
 * Deliberately a COUNT, not a "highest unlocked index": an earlier version used an index
 * capped at the last valid level, which made clearing the final level in the pack
 * indistinguishable from merely having unlocked it — the last level could never show as
 * "cleared". See ProgressStore.kt's nextClearedCount doc for the full reasoning.
 */
@Composable
private fun BlockEscapeApp(context: Context) {
    val levelFiles = LevelPack.ORDERED_LEVEL_FILES
    val progressStore = remember { ProgressStore(context) }

    // The owner's decision: open here, not straight into gameplay like before.
    var screen by remember { mutableStateOf<Screen>(Screen.LevelSelect) }
    var clearedCount by remember { mutableIntStateOf(progressStore.clearedCount()) }

    LaunchedEffect(screen) {
        if (screen is Screen.LevelSelect) {
            clearedCount = progressStore.clearedCount()
        }
    }

    when (val current = screen) {
        is Screen.LevelSelect -> {
            LevelSelectScreen(
                totalLevels = levelFiles.size,
                clearedCount = clearedCount,
                onLevelSelected = { idx -> screen = Screen.Game(idx) }
            )
        }

        is Screen.Game -> {
            // Back button/gesture from inside a level returns to level-select instead of
            // exiting the app. LevelSelect itself gets no BackHandler, so back there
            // falls through to the platform default.
            BackHandler { screen = Screen.LevelSelect }

            val levelIndex = current.levelIndex
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
                    if (levelIndex < levelFiles.lastIndex) screen = Screen.Game(levelIndex + 1)
                },
                onBackToLevels = { screen = Screen.LevelSelect },
                onLevelCleared = {
                    progressStore.markLevelCleared(levelIndex, levelFiles.size)
                    clearedCount = progressStore.clearedCount()
                }
            )
        }
    }
}
