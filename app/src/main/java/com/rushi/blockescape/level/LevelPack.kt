package com.rushi.blockescape.level

/**
 * The ordered set of levels shipped with the app. Progression is strictly linear (no
 * branching) — clearing level i unlocks level i+1 — which is what lets ProgressStore.kt
 * track unlock/cleared state with a single "levels cleared" count instead of per-level
 * state. See LevelSelectScreen.kt for the level-select grid and MainActivity.kt for how
 * this list drives it.
 */
object LevelPack {
    val ORDERED_LEVEL_FILES: List<String> = listOf(
        "level_001.json",
        "level_002.json",
        "level_003.json",
        "level_004.json",
        "level_005.json",
        "level_006.json",
        "level_007.json",
        "level_008.json",
        "level_009.json",
        "level_010.json"
    )
}
