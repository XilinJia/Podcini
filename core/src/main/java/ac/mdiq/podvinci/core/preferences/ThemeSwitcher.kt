package ac.mdiq.podvinci.core.preferences

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StyleRes
import ac.mdiq.podvinci.core.R
import ac.mdiq.podvinci.storage.preferences.UserPreferences

object ThemeSwitcher {
    @JvmStatic
    @StyleRes
    fun getTheme(context: Context): Int {
        val dynamic = UserPreferences.isThemeColorTinted
        return when (readThemeValue(context)) {
            UserPreferences.ThemePreference.DARK -> if (dynamic) R.style.Theme_PodVinci_Dynamic_Dark else R.style.Theme_PodVinci_Dark
            UserPreferences.ThemePreference.BLACK -> if (dynamic) R.style.Theme_PodVinci_Dynamic_TrueBlack else R.style.Theme_PodVinci_TrueBlack
            UserPreferences.ThemePreference.LIGHT -> if (dynamic) R.style.Theme_PodVinci_Dynamic_Light else R.style.Theme_PodVinci_Light
            else -> if (dynamic) R.style.Theme_PodVinci_Dynamic_Light else R.style.Theme_PodVinci_Light
        }
    }

    @JvmStatic
    @StyleRes
    fun getNoTitleTheme(context: Context): Int {
        val dynamic = UserPreferences.isThemeColorTinted
        return when (readThemeValue(context)) {
            UserPreferences.ThemePreference.DARK -> if (dynamic) R.style.Theme_PodVinci_Dynamic_Dark_NoTitle else R.style.Theme_PodVinci_Dark_NoTitle
            UserPreferences.ThemePreference.BLACK -> if (dynamic) R.style.Theme_PodVinci_Dynamic_TrueBlack_NoTitle
            else R.style.Theme_PodVinci_TrueBlack_NoTitle
            UserPreferences.ThemePreference.LIGHT -> if (dynamic) R.style.Theme_PodVinci_Dynamic_Light_NoTitle
            else R.style.Theme_PodVinci_Light_NoTitle
            else -> if (dynamic) R.style.Theme_PodVinci_Dynamic_Light_NoTitle
            else R.style.Theme_PodVinci_Light_NoTitle
        }
    }

    @JvmStatic
    @StyleRes
    fun getTranslucentTheme(context: Context): Int {
        val dynamic = UserPreferences.isThemeColorTinted
        return when (readThemeValue(context)) {
            UserPreferences.ThemePreference.DARK -> if (dynamic) R.style.Theme_PodVinci_Dynamic_Dark_Translucent
            else R.style.Theme_PodVinci_Dark_Translucent
            UserPreferences.ThemePreference.BLACK -> if (dynamic) R.style.Theme_PodVinci_Dynamic_TrueBlack_Translucent
            else R.style.Theme_PodVinci_TrueBlack_Translucent
            UserPreferences.ThemePreference.LIGHT -> if (dynamic) R.style.Theme_PodVinci_Dynamic_Light_Translucent
            else R.style.Theme_PodVinci_Light_Translucent
            else -> if (dynamic) R.style.Theme_PodVinci_Dynamic_Light_Translucent
            else R.style.Theme_PodVinci_Light_Translucent
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
