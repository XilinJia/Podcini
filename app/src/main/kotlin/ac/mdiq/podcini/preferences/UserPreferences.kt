package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.ProxyConfig
import ac.mdiq.podcini.storage.utils.StorageUtils.createNoMediaFile
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceManager
import java.net.Proxy

/**
 * Provides access to preferences set by the user in the settings screen. A
 * private instance of this class must first be instantiated via
 * init() or otherwise every public method will throw an Exception
 * when called.
 */
@SuppressLint("StaticFieldLeak")
object UserPreferences {
    private val TAG: String = UserPreferences::class.simpleName ?: "Anonymous"

    const val EPISODE_CACHE_SIZE_UNLIMITED: Int = 0

    lateinit var appPrefs: SharedPreferences
    val cachedPrefs = mutableMapOf<String, Any?>()

    var theme: ThemePreference
        get() = when (getPref(Prefs.prefTheme, "system")) {
            "0" -> ThemePreference.LIGHT
            "1" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
        set(theme) {
            when (theme) {
                ThemePreference.LIGHT -> putPref(Prefs.prefTheme, "0")
                ThemePreference.DARK -> putPref(Prefs.prefTheme, "1")
                else -> putPref(Prefs.prefTheme, "system")
            }
        }

    val isBlackTheme: Boolean
        get() = getPref(Prefs.prefThemeBlack, false)

    val isThemeColorTinted: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && getPref(Prefs.prefTintedColors, false)

    val showSkipOnNotification: Boolean
        get() = getPref(Prefs.prefShowSkip, true)

    val isAutoDelete: Boolean
        get() = getPref(Prefs.prefAutoDelete, false)

    val isAutoDeleteLocal: Boolean
        get() = getPref(Prefs.prefAutoDeleteLocal, false)

    val videoPlayMode: Int
        get() {
            try { return getPref(Prefs.prefVideoPlaybackMode, "1").toInt()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                setVideoMode(1)
                return 1
            }
        }

    var isSkipSilence: Boolean
        get() = getPref(Prefs.prefSkipSilence, false)
        set(skipSilence) {
            putPref(Prefs.prefSkipSilence, skipSilence)
        }

    /**
     * Returns the capacity of the episode cache. This method will return the
     * EPISODE_CACHE_SIZE_UNLIMITED (0) if the cache size is set to 'unlimited'.
     */
    val episodeCacheSize: Int
        get() = getPref(Prefs.prefEpisodeCacheSize, "20").toInt()

    @set:VisibleForTesting
    var isEnableAutodownload: Boolean
        get() = getPref(Prefs.prefEnableAutoDl, false)
        set(enabled) {
            putPref(Prefs.prefEnableAutoDl, enabled)
        }

    val isEnableAutodownloadOnBattery: Boolean
        get() = getPref(Prefs.prefEnableAutoDownloadOnBattery, true)

    var speedforwardSpeed: Float
        get() {
            try { return getPref(Prefs.prefSpeedforwardSpeed, "0.00").toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                speedforwardSpeed = 0.0f
                return 0.0f
            }
        }
        set(speed) {
            putPref(Prefs.prefSpeedforwardSpeed, speed.toString())
        }

    var fallbackSpeed: Float
        get() {
            try { return getPref(Prefs.prefFallbackSpeed, "0.00").toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                fallbackSpeed = 0.0f
                return 0.0f
            }
        }
        set(speed) {
            putPref(Prefs.prefFallbackSpeed, speed.toString())
        }

    var fastForwardSecs: Int
        get() = getPref(Prefs.prefFastForwardSecs, 30)
        set(secs) {
            putPref(Prefs.prefFastForwardSecs, secs)
        }

    var rewindSecs: Int
        get() = getPref(Prefs.prefRewindSecs, 10)
        set(secs) {
            putPref(Prefs.prefRewindSecs, secs)
        }

    var proxyConfig: ProxyConfig
        get() {
            val type = Proxy.Type.valueOf(getPref(Prefs.prefProxyType, Proxy.Type.DIRECT.name))
            val host = getPrefOrNull<String>(Prefs.prefProxyHost, null)
            val port = getPref(Prefs.prefProxyPort, 0)
            val username = getPrefOrNull<String>(Prefs.prefProxyUser, null)
            val password = getPrefOrNull<String>(Prefs.prefProxyPassword, null)
            return ProxyConfig(type, host, port, username, password)
        }
        set(config) {
            val editor = appPrefs.edit()
            editor.putString(Prefs.prefProxyType.name, config.type.name)
            if (config.host.isNullOrEmpty()) editor.remove(Prefs.prefProxyHost.name)
            else editor.putString(Prefs.prefProxyHost.name, config.host)

            if (config.port <= 0 || config.port > 65535) editor.remove(Prefs.prefProxyPort.name)
            else editor.putInt(Prefs.prefProxyPort.name, config.port)

            if (config.username.isNullOrEmpty()) editor.remove(Prefs.prefProxyUser.name)
            else editor.putString(Prefs.prefProxyUser.name, config.username)

            if (config.password.isNullOrEmpty()) editor.remove(Prefs.prefProxyPassword.name)
            else editor.putString(Prefs.prefProxyPassword.name, config.password)

            editor.apply()
        }

    var defaultPage: String?
        get() = getPref(Prefs.prefDefaultPage, "SubscriptionsFragment")
        set(defaultPage) {
            putPref(Prefs.prefDefaultPage, defaultPage)
        }

    var isStreamOverDownload: Boolean
        get() = getPref(Prefs.prefStreamOverDownload, false)
        set(stream) {
            putPref(Prefs.prefStreamOverDownload, stream)
        }

    var prefLowQualityMedia: Boolean
        get() = getPref(Prefs.prefLowQualityOnMobile, false)
        set(stream) {
            putPref(Prefs.prefLowQualityOnMobile, stream)
        }

    var prefAdaptiveProgressUpdate: Boolean
        get() = getPref(Prefs.prefUseAdaptiveProgressUpdate, false)
        set(value) {
            putPref(Prefs.prefUseAdaptiveProgressUpdate, value)
        }

    private val preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        Log.d(TAG, "PreferenceChangeListener key: $key")
        if (key != null) cachedPrefs[key] = appPrefs.all[key]
    }

    /**
     * Sets up the UserPreferences class.
     * @throws IllegalArgumentException if context is null
     */
    fun init(context: Context) {
        Logd(TAG, "Creating new instance of UserPreferences")
//        UserPreferences.context = context.applicationContext
        appPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        Prefs.entries.map { it.name }.forEach { key -> cachedPrefs[key] = appPrefs.all[key] }
        appPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        createNoMediaFile()
    }

    inline fun <reified T> getPref(key: String, defaultValue: T): T {
        val value = cachedPrefs[key]
        return when (value) {
            is T -> value
            else -> defaultValue
        }
    }

    inline fun <reified T> getPref(key: Prefs, defaultValue: T): T {
        val value = cachedPrefs[key.name]
        return when (value) {
            is T -> value
            else -> defaultValue
        }
    }

    inline fun <reified T> getPrefOrNull(key: Prefs, defaultValue: T?): T? {
        val value = cachedPrefs[key.name]
        return when (value) {
            is T -> value
            else -> defaultValue
        }
    }

    inline fun <reified T> putPref(key: Prefs, value: T) {
        cachedPrefs[key.name] = value

        val editor = appPrefs.edit()
        when (value) {
            is String -> editor.putString(key.name, value)
            is Int -> editor.putInt(key.name, value)
            is Boolean -> editor.putBoolean(key.name, value)
            is Float -> editor.putFloat(key.name, value)
            is Long -> editor.putLong(key.name, value)
            else -> throw IllegalArgumentException("Unsupported type")
        }
        editor.apply()
    }

    /**
     * Helper function to return whether the specified button should be shown on full
     * notifications.
     * @param buttonId Either NOTIFICATION_BUTTON_REWIND, NOTIFICATION_BUTTON_FAST_FORWARD,
     * NOTIFICATION_BUTTON.SKIP.ordinal, NOTIFICATION_BUTTON.PLAYBACK_SPEED.ordinal
     * or NOTIFICATION_BUTTON.NEXT_CHAPTER.ordinal.
     * @return `true` if button should be shown, `false`  otherwise
     */
//    private fun showButtonOnFullNotification(buttonId: Int): Boolean {
//        return fullNotificationButtons.contains(buttonId)
//    }

    /**
     * @return `true` if we should show remaining time or the duration
     */
    val shouldShowRemainingTime: Boolean
        get() = getPref(Prefs.showTimeLeft, false)

    /**
     * Sets the preference for whether we show the remain time, if not show the duration. This will
     * send out events so the current playing screen, queue and the episode list would refresh
     * @return `true` if we should show remaining time or the duration
     */
    fun setShowRemainTimeSetting(showRemain: Boolean?) {
        putPref(Prefs.showTimeLeft, showRemain!!)
    }

    val backButtonOpensDrawer: Boolean
        get() = getPref(Prefs.prefBackButtonOpensDrawer, false)

    val timeRespectsSpeed: Boolean
        get() = getPref(Prefs.prefPlaybackTimeRespectsSpeed, false)

    fun setPlaybackSpeed(speed: Float) {
        putPref(Prefs.prefPlaybackSpeed, speed.toString())
    }

    fun setVideoMode(mode: Int) {
        putPref(Prefs.prefVideoPlaybackMode, mode.toString())
    }

    enum class DefaultPages(val res: Int) {
        SubscriptionsFragment(R.string.subscriptions_label),
        QueuesFragment(R.string.queue_label),
        EpisodesFragment(R.string.episodes_label),
        AddFeedFragment(R.string.add_feed_label),
        StatisticsFragment(R.string.statistics_label),
        Remember(R.string.remember_last_page);
    }

    @Suppress("EnumEntryName")
    enum class Prefs {
        prefOPMLBackup,
        prefOPMLRestore,

        // User Interface
        prefTheme,
        prefThemeBlack,
        prefTintedColors,
        prefFeedGridLayout,
        prefSwipeToRefreshAll,
        prefExpandNotify,
        prefEpisodeCover,
        showTimeLeft,
        prefShowSkip,
        prefShowDownloadReport,
        prefDefaultPage,
        prefBackButtonOpensDrawer,
        prefQueueKeepSorted,
        prefQueueKeepSortedOrder,
        prefDownloadsFilter,

        // Episodes
        prefEpisodesSort,
        prefEpisodesFilter,

        // Playback
        prefPauseOnHeadsetDisconnect,
        prefUnpauseOnHeadsetReconnect,
        prefUnpauseOnBluetoothReconnect,
        prefHardwareForwardButton,
        prefHardwarePreviousButton,
        prefFollowQueue,
        prefSkipKeepsEpisode,
        prefRemoveFromQueueMarkedPlayed,
        prefFavoriteKeepsEpisode,

        prefAutoBackup,
        prefAutoBackupIntervall,
        prefAutoBackupFolder,
        prefAutoBackupLimit,
        prefAutoBackupTimeStamp,

        prefUseCustomMediaFolder,
        prefCustomMediaUri,

        prefAutoDelete,
        prefAutoDeleteLocal,
        prefPlaybackSpeedArray,
        prefFallbackSpeed,
        prefPlaybackTimeRespectsSpeed,
        prefStreamOverDownload,
        prefLowQualityOnMobile,
        prefSpeedforwardSpeed,
        prefUseAdaptiveProgressUpdate,

        // Network
        prefEnqueueDownloaded,
        prefEnqueueLocation,
        prefAutoUpdateIntervall,
        prefMobileUpdateTypes,
        prefEpisodeCleanup,
        prefEpisodeCacheSize,
        prefEnableAutoDl,
        prefEnableAutoDownloadOnBattery,
        prefEnableAutoDownloadWifiFilter,
        prefAutodownloadSelectedNetworks,
        prefProxyType,
        prefProxyHost,
        prefProxyPort,
        prefProxyUser,
        prefProxyPassword,

        // Services
        pref_gpodnet_notifications,
        pref_nextcloud_server_address,

        // Other
        prefDeleteRemovesFromQueue,

        // Mediaplayer
        prefPlaybackSpeed,
        prefSkipSilence,
        prefFastForwardSecs,
        prefRewindSecs,
        prefQueueLocked,
        prefVideoPlaybackMode,
    }

    enum class ThemePreference {
        LIGHT, DARK, BLACK, SYSTEM
    }
}
