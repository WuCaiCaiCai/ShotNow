package com.shotnow.settings

import android.content.Context

object ShotPrefs {
    private const val FILE = "shotnow_settings"
    private const val KEY_BALL_SIZE = "ball_visible_size_dp"
    private const val KEY_SPEN_ONLY = "spen_only"
    private const val KEY_AUTO_REPAIR = "auto_repair_enabled"
    private const val KEY_THEME = "theme_id"
    private const val KEY_BALL_OPACITY = "ball_opacity_percent"

    const val DEFAULT_BALL_SIZE = 44
    const val MIN_BALL_SIZE = 32
    const val MAX_BALL_SIZE = 72
    const val DEFAULT_BALL_OPACITY = 100
    const val MIN_BALL_OPACITY = 20
    const val MAX_BALL_OPACITY = 100

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun ballSize(context: Context): Int =
        prefs(context).getInt(KEY_BALL_SIZE, DEFAULT_BALL_SIZE)
            .coerceIn(MIN_BALL_SIZE, MAX_BALL_SIZE)

    fun setBallSize(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_BALL_SIZE, value.coerceIn(MIN_BALL_SIZE, MAX_BALL_SIZE))
            .apply()
    }

    fun sPenOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SPEN_ONLY, false)

    fun setSPenOnly(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SPEN_ONLY, value).apply()
    }

    fun autoRepairEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_REPAIR, true)

    fun setAutoRepairEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_REPAIR, value).apply()
    }

    fun themeId(context: Context): String =
        ThemeColors.byId(prefs(context).getString(KEY_THEME, ThemeColors.default.id)).id

    fun setThemeId(context: Context, id: String) {
        if (ThemeColors.all.none { it.id == id }) return
        prefs(context).edit().putString(KEY_THEME, id).apply()
    }

    fun ballOpacity(context: Context): Int =
        prefs(context).getInt(KEY_BALL_OPACITY, DEFAULT_BALL_OPACITY)
            .coerceIn(MIN_BALL_OPACITY, MAX_BALL_OPACITY)

    fun setBallOpacity(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_BALL_OPACITY, value.coerceIn(MIN_BALL_OPACITY, MAX_BALL_OPACITY))
            .apply()
    }
}
