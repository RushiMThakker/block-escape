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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.rushi.blockescape.level.LevelBestMoves
import com.rushi.blockescape.level.LevelPack
import com.rushi.blockescape.level.LevelRepository
import com.rushi.blockescape.progress.ProgressStore
import com.rushi.blockescape.ui.GameScreen
import com.rushi.blockescape.ui.GameViewModel
import com.rushi.blockescape.ui.LevelSelectScreen
import com.rushi.blockescape.ui.theme.BlockEscapeTheme

/**
 * AdMob test-device IDs for developer/owner phones used for on-device ad verification.
 * Registering a device here forces the Mobile Ads SDK to serve clearly-labeled "Test Ad"
 * creatives on it instead of real ad inventory, so tapping an ad during our own testing
 * can never register as a real (accidental self-click) impression/click against the live
 * AdMob account — a real risk once real ad unit/app IDs are wired in (see AdConfig.kt).
 *
 * This does NOT affect the 12 external closed-testing users: their devices are not in
 * this list, so they see real ads as intended (normal, expected, low-volume usage).
 *
 * To add a new developer test device (e.g. testing on a different phone later): run this
 * app once on that device with the real AdMob IDs in place, open a screen with a banner,
 * then read logcat for a line from `RequestConfiguration.Builder`/`setTestDeviceIds`
 * reporting that device's specific hashed ID, and add it to this list.
 *
 * Standard Android emulators do NOT need an entry here - per Google's AdMob docs
 * ("Android emulators are automatically configured as test devices"), only real
 * physical devices need to be registered explicitly.
 */
private val ADMOB_TEST_DEVICE_IDS: List<String> = listOf(
    // Rushi's Galaxy M32 (SM_M325F, adb id RZ8R60N0D0T). Captured from logcat's own
    // "Use RequestConfiguration.Builder().setTestDeviceIds(...)" line, logged by the
    // Mobile Ads SDK (tag "Ads") the first time this device requested a real ad.
    "A12B9D116A22E6B48A5B1435306183EC"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() — this is the documented
        // core-splashscreen requirement (it installs the splash screen by adjusting the
        // activity's theme before the window is created).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Register developer test devices BEFORE the first ad request so banners on
        // those devices always render as "Test Ad" rather than real inventory — see
        // ADMOB_TEST_DEVICE_IDS doc above for why this matters now that real AdMob IDs
        // are live. Safe to apply even while the list is empty/incomplete.
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(ADMOB_TEST_DEVICE_IDS)
                .build()
        )
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
 * Makes `screen` survive an Activity recreation (rotation), not just recomposition.
 * Plain `remember` is discarded when the Activity is destroyed/recreated (this app has
 * no android:configChanges, so rotation does exactly that) - only rememberSaveable
 * round-trips through the Activity's saved-instance-state Bundle. Without this, rotating
 * mid-level would reset `screen` back to its initial LevelSelect value and bounce the
 * player out of the level entirely, regardless of any ViewModel-level fix. Screen itself
 * isn't Parcelable (it's a small local sealed class, deliberately - see the doc above),
 * so it's saved as a plain Int: -1 for LevelSelect, else the level index for Game.
 */
private val ScreenSaver = Saver<Screen, Int>(
    save = { screen -> if (screen is Screen.Game) screen.levelIndex else -1 },
    restore = { saved -> if (saved < 0) Screen.LevelSelect else Screen.Game(saved) }
)

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
    // rememberSaveable (not remember) so this - and therefore which screen is showing -
    // survives an Activity recreation from rotation; see ScreenSaver's doc above.
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.LevelSelect) }
    var clearedCount by remember { mutableIntStateOf(progressStore.clearedCount()) }

    // Monotonically increases every time gameplay is freshly entered for a level (from
    // level-select, or via "next level"), and is folded into that Game screen's
    // viewModel() key below. This is what makes "replay a level after navigating away
    // and back" get a genuinely fresh GameViewModel: the Activity's ViewModelStore (and
    // therefore any GameViewModel already created for a given key) survives ordinary
    // in-app navigation, not just rotation, so keying purely by levelIndex would hand a
    // replay the SAME stale instance from the previous playthrough. rememberSaveable so
    // the counter (and hence the key of the currently-active level) stays IDENTICAL
    // across a rotation of the same still-active level - which is exactly what lets
    // viewModel() find and reuse that same instance on rotation instead of creating a
    // new one. Only incremented from event handlers (enterLevel below), never during
    // composition, so recomposition/rotation alone never bumps it.
    var levelSessionCounter by rememberSaveable { mutableIntStateOf(0) }
    fun enterLevel(idx: Int) {
        levelSessionCounter++
        screen = Screen.Game(idx)
    }

    LaunchedEffect(screen) {
        if (screen is Screen.LevelSelect) {
            clearedCount = progressStore.clearedCount()
        }
    }

    // The solver's best-possible move count for every level, one entry per index into
    // `levelFiles` - null until the background solve finishes. LevelBestMoves.getOrCompute
    // runs the real BFS solver off the main thread and caches the result for the rest of
    // the process's lifetime (see its doc for the measured cost/why this isn't done
    // synchronously), so LaunchedEffect(Unit) - fires exactly once for as long as this
    // composable stays in composition, which for the app's single root composable means
    // once per process, matching the cache's own lifetime - is enough; navigating back and
    // forth between level-select and gameplay never re-triggers it. Both screens below
    // degrade gracefully to "no number yet" while this is still null/incomplete, rather
    // than blocking on it.
    var bestMoveCounts by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(Unit) {
        bestMoveCounts = LevelBestMoves.getOrCompute(context)
    }

    when (val current = screen) {
        is Screen.LevelSelect -> {
            LevelSelectScreen(
                totalLevels = levelFiles.size,
                clearedCount = clearedCount,
                bestMoveCounts = bestMoveCounts ?: emptyList(),
                onLevelSelected = { idx -> enterLevel(idx) }
            )
        }

        is Screen.Game -> {
            // Back button/gesture from inside a level returns to level-select instead of
            // exiting the app. LevelSelect itself gets no BackHandler, so back there
            // falls through to the platform default.
            BackHandler { screen = Screen.LevelSelect }

            val levelIndex = current.levelIndex
            // viewModel() (not remember) so this GameViewModel - move count, undo
            // history, current board - is backed by the Activity's ViewModelStore and
            // survives rotation instead of being discarded and rebuilt from scratch.
            // The key combines levelIndex (so switching levels via onNextLevel always
            // gets a distinct instance) with levelSessionCounter (so replaying the SAME
            // level after a level-select round-trip also gets a distinct, fresh
            // instance instead of the ViewModelStore handing back the old one) - see
            // levelSessionCounter's doc above for why both are needed.
            val viewModel = viewModel(key = "level_${levelIndex}_$levelSessionCounter") {
                val board = LevelRepository(context).loadLevel(levelFiles[levelIndex])
                GameViewModel(board)
            }

            GameScreen(
                viewModel = viewModel,
                levelNumber = levelIndex + 1,
                totalLevels = levelFiles.size,
                hasNextLevel = levelIndex < levelFiles.lastIndex,
                onNextLevel = {
                    if (levelIndex < levelFiles.lastIndex) enterLevel(levelIndex + 1)
                },
                onBackToLevels = { screen = Screen.LevelSelect },
                onLevelCleared = {
                    progressStore.markLevelCleared(levelIndex, levelFiles.size)
                    clearedCount = progressStore.clearedCount()
                },
                bestMoves = bestMoveCounts?.getOrNull(levelIndex)
            )
        }
    }
}
