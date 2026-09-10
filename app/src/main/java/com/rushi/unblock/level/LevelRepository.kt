package com.rushi.unblock.level

import android.content.Context
import com.rushi.unblock.domain.Board

class LevelRepository(private val context: Context) {
    fun loadLevel(fileName: String): Board {
        val text = context.assets.open("levels/$fileName").bufferedReader().use { it.readText() }
        return LevelParser.parse(text).toBoard()
    }
}
