package com.rushi.blockescape.level

/**
 * The ordered set of levels shipped with the app. Progression is strictly linear (no
 * branching) — clearing level i unlocks level i+1 — which is what lets ProgressStore.kt
 * track unlock/cleared state with a single "levels cleared" count instead of per-level
 * state. See LevelSelectScreen.kt for the level-select grid and MainActivity.kt for how
 * this list drives it.
 */
object LevelPack {
    /** level_001 .. level_025, in play order. ShippedLevelsTest checks every file exists, solves and ramps. */
    val ORDERED_LEVEL_FILES: List<String> = (1..25).map { "level_%03d.json".format(it) }
}
