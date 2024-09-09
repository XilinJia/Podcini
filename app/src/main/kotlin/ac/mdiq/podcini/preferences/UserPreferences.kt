package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.storage.model.ProxyConfig
import ac.mdiq.podcini.storage.utils.FilesUtils
import ac.mdiq.podcini.storage.utils.FilesUtils.createNoMediaFile
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

    // Experimental
    const val EPISODE_CLEANUP_QUEUE: Int = -1
    const val EPISODE_CLEANUP_NULL: Int = -2
    const val EPISODE_CLEANUP_EXCEPT_FAVORITE: Int = -3
    const val EPISODE_CLEANUP_DEFAULT: Int = 0

    const val EPISODE_CACHE_SIZE_UNLIMITED: Int = -1

    const val DEFAULT_PAGE_REMEMBER: String = "remember"

    private lateinit var context: Context
    lateinit var appPrefs: SharedPreferences

    var theme: ThemePreference
        get() = when (appPrefs.getString(Prefs.prefTheme.name, "system")) {
            "0" -> ThemePreference.LIGHT
            "1" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
        set(theme) {
            when (theme) {
                ThemePreference.LIGHT -> appPrefs.edit().putString(Prefs.prefTheme.name, "0").apply()
                ThemePreference.DARK -> appPrefs.edit().putString(Prefs.prefTheme.name, "1").apply()
                else -> appPrefs.edit().putString(Prefs.prefTheme.name, "system").apply()
            }
        }

    val isBlackTheme: Boolean
        get() = appPrefs.getBoolean(Prefs.prefThemeBlack.name, false)

    val isThemeColorTinted: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && appPrefs.getBoolean(Prefs.prefTintedColors.name, false)

    var hiddenDrawerItems: List<String>
        get() {
            val hiddenItems = appPrefs.getString(Prefs.prefHiddenDrawerItems.name, "")
            return hiddenItems?.split(",") ?: listOf()
        }
        set(items) {
            val str = items.joinToString()
            appPrefs.edit().putString(Prefs.prefHiddenDrawerItems.name, str).apply()
        }

    var fullNotificationButtons: List<Int>
        get() {
            val buttons = appPrefs.getString(Prefs.prefFullNotificationButtons.name, "${NOTIFICATION_BUTTON.SKIP.ordinal},${NOTIFICATION_BUTTON.PLAYBACK_SPEED.ordinal}")?.split(",") ?: listOf()
            val notificationButtons: MutableList<Int> = ArrayList()
            for (button in buttons) {
                notificationButtons.add(button.toInt())
            }
            return notificationButtons
        }
        set(items) {
            val str = items.joinToString()
            appPrefs.edit().putString(Prefs.prefFullNotificationButtons.name, str).apply()
        }

    val isAutoDelete: Boolean
        get() = appPrefs.getBoolean(Prefs.prefAutoDelete.name, false)

    val isAutoDeleteLocal: Boolean
        get() = appPrefs.getBoolean(Prefs.prefAutoDeleteLocal.name, false)

    val videoPlayMode: Int
        get() {
            try { return appPrefs.getString(Prefs.prefVideoPlaybackMode.name, "1")!!.toInt()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                setVideoMode(1)
                return 1
            }
        }

    var isSkipSilence: Boolean
        get() = appPrefs.getBoolean(Prefs.prefSkipSilence.name, false)
        set(skipSilence) {
            appPrefs.edit().putBoolean(Prefs.prefSkipSilence.name, skipSilence).apply()
        }

    /**
     * Returns the capacity of the episode cache. This method will return the
     * negative integer EPISODE_CACHE_SIZE_UNLIMITED if the cache size is set to
     * 'unlimited'.
     */
    val episodeCacheSize: Int
        get() = appPrefs.getString(Prefs.prefEpisodeCacheSize.name, "20")!!.toInt()

    @set:VisibleForTesting
    var isEnableAutodownload: Boolean
        get() = appPrefs.getBoolean(Prefs.prefEnableAutoDl.name, false)
        set(enabled) {
            appPrefs.edit().putBoolean(Prefs.prefEnableAutoDl.name, enabled).apply()
        }

    val isEnableAutodownloadOnBattery: Boolean
        get() = appPrefs.getBoolean(Prefs.prefEnableAutoDownloadOnBattery.name, true)

    var speedforwardSpeed: Float
        get() {
            try { return appPrefs.getString(Prefs.prefSpeedforwardSpeed.name, "0.00")!!.toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                speedforwardSpeed = 0.0f
                return 0.0f
            }
        }
        set(speed) {
            appPrefs.edit().putString(Prefs.prefSpeedforwardSpeed.name, speed.toString()).apply()
        }

    var fallbackSpeed: Float
        get() {
            try { return appPrefs.getString(Prefs.prefFallbackSpeed.name, "0.00")!!.toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                fallbackSpeed = 0.0f
                return 0.0f
            }
        }
        set(speed) {
            appPrefs.edit().putString(Prefs.prefFallbackSpeed.name, speed.toString()).apply()
        }

    var fastForwardSecs: Int
        get() = appPrefs.getInt(Prefs.prefFastForwardSecs.name, 30)
        set(secs) {
            appPrefs.edit().putInt(Prefs.prefFastForwardSecs.name, secs).apply()
        }

    var rewindSecs: Int
        get() = appPrefs.getInt(Prefs.prefRewindSecs.name, 10)
        set(secs) {
            appPrefs.edit().putInt(Prefs.prefRewindSecs.name, secs).apply()
        }

    var proxyConfig: ProxyConfig
        get() {
            val type = Proxy.Type.valueOf(appPrefs.getString(Prefs.prefProxyType.name, Proxy.Type.DIRECT.name)!!)
            val host = appPrefs.getString(Prefs.prefProxyHost.name, null)
            val port = appPrefs.getInt(Prefs.prefProxyPort.name, 0)
            val username = appPrefs.getString(Prefs.prefProxyUser.name, null)
            val password = appPrefs.getString(Prefs.prefProxyPassword.name, null)
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
        get() = appPrefs.getString(Prefs.prefDefaultPage.name, "SubscriptionsFragment")
        set(defaultPage) {
            appPrefs.edit().putString(Prefs.prefDefaultPage.name, defaultPage).apply()
        }

    var isStreamOverDownload: Boolean
        get() = appPrefs.getBoolean(Prefs.prefStreamOverDownload.name, false)
        set(stream) {
            appPrefs.edit().putBoolean(Prefs.prefStreamOverDownload.name, stream).apply()
        }

    /**
     * Sets up the UserPreferences class.
     * @throws IllegalArgumentException if context is null
     */
    fun init(context: Context) {
        Logd(TAG, "Creating new instance of UserPreferences")
        UserPreferences.context = context.applicationContext
        FilesUtils.context = context.applicationContext
        appPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        createNoMediaFile()
    }

    /**
     * Helper function to return whether the specified button should be shown on full
     * notifications.
     * @param buttonId Either NOTIFICATION_BUTTON_REWIND, NOTIFICATION_BUTTON_FAST_FORWARD,
     * NOTIFICATION_BUTTON.SKIP.ordinal, NOTIFICATION_BUTTON.PLAYBACK_SPEED.ordinal
     * or NOTIFICATION_BUTTON.NEXT_CHAPTER.ordinal.
     * @return `true` if button should be shown, `false`  otherwise
     */
    private fun showButtonOnFullNotification(buttonId: Int): Boolean {
        return fullNotificationButtons.contains(buttonId)
    }

//    only used in test
    fun showSkipOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON.SKIP.ordinal)
    }

    //    only used in test
    fun showNextChapterOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON.NEXT_CHAPTER.ordinal)
    }

    //    only used in test
    fun showPlaybackSpeedOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON.PLAYBACK_SPEED.ordinal)
    }

    /**
     * @return `true` if we should show remaining time or the duration
     */
    fun shouldShowRemainingTime(): Boolean {
        return appPrefs.getBoolean(Prefs.showTimeLeft.name, false)
    }

    /**
     * Sets the preference for whether we show the remain time, if not show the duration. This will
     * send out events so the current playing screen, queue and the episode list would refresh
     * @return `true` if we should show remaining time or the duration
     */
    fun setShowRemainTimeSetting(showRemain: Boolean?) {
        appPrefs.edit().putBoolean(Prefs.showTimeLeft.name, showRemain!!).apply()
    }

//   only used in test
    fun shouldPauseForFocusLoss(): Boolean {
        return appPrefs.getBoolean(Prefs.prefPauseForFocusLoss.name, true)
    }

    fun backButtonOpensDrawer(): Boolean {
        return appPrefs.getBoolean(Prefs.prefBackButtonOpensDrawer.name, false)
    }

    fun timeRespectsSpeed(): Boolean {
        return appPrefs.getBoolean(Prefs.prefPlaybackTimeRespectsSpeed.name, false)
    }

    fun setPlaybackSpeed(speed: Float) {
        appPrefs.edit().putString(Prefs.prefPlaybackSpeed.name, speed.toString()).apply()
    }

    fun setVideoMode(mode: Int) {
        appPrefs.edit().putString(Prefs.prefVideoPlaybackMode.name, mode.toString()).apply()
    }

    @Suppress("EnumEntryName")
    enum class Prefs {
        prefOPMLBackup,
        prefOPMLRestore,

        // User Interface
        prefTheme,
        prefThemeBlack,
        prefTintedColors,
        prefHiddenDrawerItems,
        prefDrawerFeedOrder,
        prefDrawerFeedOrderDir,
        prefFeedGridLayout,
        prefExpandNotify,
        prefEpisodeCover,
        showTimeLeft,
        prefPersistNotify,
        prefFullNotificationButtons,
        prefShowDownloadReport,
        prefDefaultPage,
        prefBackButtonOpensDrawer,

        prefFeedFilter,

        prefQueueKeepSorted,
        prefQueueKeepSortedOrder,
        prefDownloadSortedOrder,

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
        prefAutoDelete,
        prefAutoDeleteLocal,
        prefSmartMarkAsPlayedSecs,
        prefPlaybackSpeedArray,
        prefFallbackSpeed,
        prefPauseForFocusLoss,
        prefPlaybackTimeRespectsSpeed,
        prefStreamOverDownload,
        prefSpeedforwardSpeed,

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

        // Other
//    prefDataFolder
        prefDeleteRemovesFromQueue,

        // Mediaplayer
        prefPlaybackSpeed,
        prefSkipSilence,
        prefFastForwardSecs,
        prefRewindSecs,
        prefQueueLocked,
        prefVideoPlaybackMode,
    }

    @Suppress("ClassName")
    enum class NOTIFICATION_BUTTON {
        REWIND,
        FAST_FORWARD,
        SKIP,
        NEXT_CHAPTER,
        PLAYBACK_SPEED,
    }

    enum class ThemePreference {
        LIGHT, DARK, BLACK, SYSTEM
    }
}
