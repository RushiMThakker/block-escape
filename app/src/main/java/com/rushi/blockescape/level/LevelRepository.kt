package com.rushi.blockescape.level

import android.content.Context
import com.rushi.blockescape.domain.Board

class LevelRepository(private val context: Context) {
    fun loadLevel(fileName: String): Board {
        val text = context.assets.open("levels/$fileName").bufferedReader().use { it.readText() }
        return LevelParser.parse(text).toBoard()
    }
}
