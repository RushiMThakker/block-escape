package com.rushi.blockescape.settings

import android.content.Context

/**
 * Player-facing switches that are independent of the seasonal look (that one lives in SeasonStore):
 * vibration and the festive background music. Both default to on. Reads go straight to
 * SharedPreferences, which keeps its own in-memory copy, so checking on every haptic tick is cheap.
 */
class PlayerSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun vibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION, true)

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    fun musicEnabled(): Boolean = prefs.getBoolean(KEY_MUSIC, true)

    fun setMusicEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MUSIC, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "block_escape_settings"
        private const val KEY_VIBRATION = "vibration"
        private const val KEY_MUSIC = "festive_music"
    }
}
