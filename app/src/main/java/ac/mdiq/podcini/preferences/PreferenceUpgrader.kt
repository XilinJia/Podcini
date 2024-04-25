package ac.mdiq.podcini.preferences

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.SleepTimerPreferences.lastTimerValue
import ac.mdiq.podcini.preferences.SleepTimerPreferences.setLastTimer
import ac.mdiq.podcini.util.error.CrashReportWriter.Companion.file
import ac.mdiq.podcini.ui.fragment.AllEpisodesFragment
import ac.mdiq.podcini.ui.fragment.QueueFragment
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeAction
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.preferences.UserPreferences.EnqueueLocation
import ac.mdiq.podcini.preferences.UserPreferences.episodeCleanupValue
import ac.mdiq.podcini.preferences.UserPreferences.isAllowMobileAutoDownload
import ac.mdiq.podcini.preferences.UserPreferences.isAllowMobileEpisodeDownload
import ac.mdiq.podcini.preferences.UserPreferences.isAllowMobileFeedRefresh
import ac.mdiq.podcini.preferences.UserPreferences.isAllowMobileImages
import ac.mdiq.podcini.preferences.UserPreferences.isAllowMobileSync
import ac.mdiq.podcini.preferences.UserPreferences.isQueueLocked
import ac.mdiq.podcini.preferences.UserPreferences.isStreamOverDownload
import ac.mdiq.podcini.preferences.UserPreferences.theme
import org.apache.commons.lang3.StringUtils
import java.util.concurrent.TimeUnit

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

    private fun upgrade(oldVersion: Int, context: Context) {
        //New installation
        if (oldVersion == -1) return

        if (oldVersion < 1070196) {
            // migrate episode cleanup value (unit changed from days to hours)
            val oldValueInDays = episodeCleanupValue
            if (oldValueInDays > 0) {
                episodeCleanupValue = oldValueInDays * 24
            } // else 0 or special negative values, no change needed
        }
        if (oldVersion < 1070197) {
            if (prefs.getBoolean("prefMobileUpdate", false)) prefs.edit().putString("prefMobileUpdateAllowed", "everything").apply()
        }
        if (oldVersion < 1070300) {
            if (prefs.getBoolean("prefEnableAutoDownloadOnMobile", false)) isAllowMobileAutoDownload = true
            when (prefs.getString("prefMobileUpdateAllowed", "images")) {
                "everything" -> {
                    isAllowMobileFeedRefresh = true
                    isAllowMobileEpisodeDownload = true
                    isAllowMobileImages = true
                }
                "images" -> isAllowMobileImages = true
                "nothing" -> isAllowMobileImages = false
            }
        }
        if (oldVersion < 1070400) {
            val theme = theme
            if (theme == UserPreferences.ThemePreference.LIGHT) prefs.edit().putString(UserPreferences.PREF_THEME, "system").apply()

            isQueueLocked = false
            isStreamOverDownload = false

            if (!prefs.contains(UserPreferences.PREF_ENQUEUE_LOCATION)) {
                val keyOldPrefEnqueueFront = "prefQueueAddToFront"
                val enqueueAtFront = prefs.getBoolean(keyOldPrefEnqueueFront, false)
                val enqueueLocation = if (enqueueAtFront) EnqueueLocation.FRONT else EnqueueLocation.BACK
                UserPreferences.enqueueLocation = enqueueLocation
            }
        }
        if (oldVersion < 2010300) {
            // Migrate hardware button preferences
            if (prefs.getBoolean("prefHardwareForwardButtonSkips", false))
                prefs.edit().putString(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON, KeyEvent.KEYCODE_MEDIA_NEXT.toString()).apply()

            if (prefs.getBoolean("prefHardwarePreviousButtonRestarts", false))
                prefs.edit().putString(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON, KeyEvent.KEYCODE_MEDIA_PREVIOUS.toString()).apply()
        }
        if (oldVersion < 2040000) {
            val swipePrefs = context.getSharedPreferences(SwipeActions.PREF_NAME, Context.MODE_PRIVATE)
            swipePrefs.edit().putString(SwipeActions.KEY_PREFIX_SWIPEACTIONS + QueueFragment.TAG,
                SwipeAction.REMOVE_FROM_QUEUE + "," + SwipeAction.REMOVE_FROM_QUEUE).apply()
        }
        if (oldVersion < 2050000) prefs.edit().putBoolean(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true).apply()

        if (oldVersion < 2080000) {
            // Migrate drawer feed counter setting to reflect removal of
            // "unplayed and in inbox" (0), by changing it to "unplayed" (2)
            val feedCounterSetting = prefs.getString(UserPreferences.PREF_DRAWER_FEED_COUNTER, "2")
            if (feedCounterSetting == "0") prefs.edit().putString(UserPreferences.PREF_DRAWER_FEED_COUNTER, "2").apply()

            val sleepTimerPreferences = context.getSharedPreferences(SleepTimerPreferences.PREF_NAME, Context.MODE_PRIVATE)
            val timeUnits = arrayOf(TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS)
            val value = lastTimerValue()!!.toLong()
            val unit = timeUnits[sleepTimerPreferences.getInt("LastTimeUnit", 1)]
            setLastTimer(unit.toMinutes(value).toString())

            if (prefs.getString(UserPreferences.PREF_EPISODE_CACHE_SIZE, "20") == context.getString(R.string.pref_episode_cache_unlimited))
                prefs.edit().putString(UserPreferences.PREF_EPISODE_CACHE_SIZE, "" + UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED).apply()
        }
        if (oldVersion < 3000007) {
            if (prefs.getString("prefBackButtonBehavior", "") == "drawer")
                prefs.edit().putBoolean(UserPreferences.PREF_BACK_OPENS_DRAWER, true).apply()
        }
        if (oldVersion < 3010000) {
            if (prefs.getString(UserPreferences.PREF_THEME, "system") == "2") {
                prefs.edit()
                    .putString(UserPreferences.PREF_THEME, "1")
                    .putBoolean(UserPreferences.PREF_THEME_BLACK, true)
                    .apply()
            }
            isAllowMobileSync = true
            // Unset or "time of day"
            if (prefs.getString(UserPreferences.PREF_UPDATE_INTERVAL, ":")!!.contains(":"))
                prefs.edit().putString(UserPreferences.PREF_UPDATE_INTERVAL, "12").apply()

        }
        if (oldVersion < 3020000) NotificationManagerCompat.from(context).deleteNotificationChannel("auto_download")

        if (oldVersion < 3030000) {
            val allEpisodesPreferences = context.getSharedPreferences(AllEpisodesFragment.PREF_NAME, Context.MODE_PRIVATE)
            val oldEpisodeSort = allEpisodesPreferences.getString(UserPreferences.PREF_SORT_ALL_EPISODES, "")
            if (!StringUtils.isAllEmpty(oldEpisodeSort)) prefs.edit().putString(UserPreferences.PREF_SORT_ALL_EPISODES, oldEpisodeSort).apply()

            val oldEpisodeFilter = allEpisodesPreferences.getString("filter", "")
            if (!StringUtils.isAllEmpty(oldEpisodeFilter)) prefs.edit().putString(UserPreferences.PREF_FILTER_ALL_EPISODES, oldEpisodeFilter).apply()
        }
    }
}
