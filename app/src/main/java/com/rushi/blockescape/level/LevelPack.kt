package com.rushi.blockescape.level

/**
 * The ordered set of levels shipped with the app. Minimal on purpose for this vertical
 * slice: just "play them in order, advance on win" — no level-select UI, no persistence
 * of progress across app restarts.
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
