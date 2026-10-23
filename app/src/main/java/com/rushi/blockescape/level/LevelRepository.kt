package com.rushi.blockescape.level

import android.content.Context
import com.rushi.blockescape.domain.Board

class LevelRepository(private val context: Context) {
    fun loadLevel(fileName: String): Board = loadDto(fileName).toBoard()

    fun loadDto(fileName: String): LevelDto = readDto("levels/$fileName")

    /** The board behind Advent door [day] (1..24), kept apart from the main level pack. */
    fun loadAdventDto(day: Int): LevelDto = readDto("advent/advent_%02d.json".format(day))

    private fun readDto(assetPath: String): LevelDto {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return LevelParser.parse(text)
    }
}
