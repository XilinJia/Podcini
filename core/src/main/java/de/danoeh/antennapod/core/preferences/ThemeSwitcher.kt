package de.danoeh.antennapod.core.preferences

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StyleRes
import de.danoeh.antennapod.core.R
import de.danoeh.antennapod.storage.preferences.UserPreferences

object ThemeSwitcher {
    @JvmStatic
    @StyleRes
    fun getTheme(context: Context): Int {
        val dynamic = UserPreferences.isThemeColorTinted
        return when (readThemeValue(context)) {
            UserPreferences.ThemePreference.DARK -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_Dark else R.style.Theme_AntennaPod_Dark
            UserPreferences.ThemePreference.BLACK -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_TrueBlack else R.style.Theme_AntennaPod_TrueBlack
            UserPreferences.ThemePreference.LIGHT -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_Light else R.style.Theme_AntennaPod_Light
            else -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_Light else R.style.Theme_AntennaPod_Light
        }
    }

    @JvmStatic
    @StyleRes
    fun getNoTitleTheme(context: Context): Int {
        val dynamic = UserPreferences.isThemeColorTinted
        return when (readThemeValue(context)) {
            UserPreferences.ThemePreference.DARK -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_Dark_NoTitle else R.style.Theme_AntennaPod_Dark_NoTitle
            UserPreferences.ThemePreference.BLACK -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_TrueBlack_NoTitle
            else R.style.Theme_AntennaPod_TrueBlack_NoTitle
            UserPreferences.ThemePreference.LIGHT -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_Light_NoTitle
            else R.style.Theme_AntennaPod_Light_NoTitle
            else -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_Light_NoTitle
            else R.style.Theme_AntennaPod_Light_NoTitle
        }
    }

    @JvmStatic
    @StyleRes
    fun getTranslucentTheme(context: Context): Int {
        val dynamic = UserPreferences.isThemeColorTinted
        return when (readThemeValue(context)) {
            UserPreferences.ThemePreference.DARK -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_Dark_Translucent
            else R.style.Theme_AntennaPod_Dark_Translucent
            UserPreferences.ThemePreference.BLACK -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_TrueBlack_Translucent
            else R.style.Theme_AntennaPod_TrueBlack_Translucent
            UserPreferences.ThemePreference.LIGHT -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_Light_Translucent
            else R.style.Theme_AntennaPod_Light_Translucent
            else -> if (dynamic) R.style.Theme_AntennaPod_Dynamic_Light_Translucent
            else R.style.Theme_AntennaPod_Light_Translucent
        }
    }

    private fun readThemeValue(context: Context): UserPreferences.ThemePreference {
        var theme = UserPreferences.theme
        if (theme == UserPreferences.ThemePreference.SYSTEM) {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            theme = if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                UserPreferences.ThemePreference.DARK
            } else {
                UserPreferences.ThemePreference.LIGHT
            }
        }
        if (theme == UserPreferences.ThemePreference.DARK && UserPreferences.isBlackTheme) {
            theme = UserPreferences.ThemePreference.BLACK
        }
        return theme!!
    }
}
