package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.util.error.CrashReportWriter.Companion.file
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.preference.PreferenceManager

object PreferenceUpgrader {
    private const val PREF_CONFIGURED_VERSION = "version_code"
    private const val PREF_NAME = "app_version"

    private lateinit var prefs: SharedPreferences

    fun checkUpgrades(context: Context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val upgraderPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val oldVersion = upgraderPrefs.getInt(PREF_CONFIGURED_VERSION, -1)
        val newVersion = BuildConfig.VERSION_CODE

        if (oldVersion != newVersion) {
            file.delete()

            upgrade(oldVersion, context)
            upgraderPrefs.edit().putInt(PREF_CONFIGURED_VERSION, newVersion).apply()
        }
    }

    @OptIn(UnstableApi::class) private fun upgrade(oldVersion: Int, context: Context) {
        //New installation
        if (oldVersion == -1) return
    }
}
