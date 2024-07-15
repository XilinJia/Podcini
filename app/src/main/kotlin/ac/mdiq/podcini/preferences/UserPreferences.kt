package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.ProxyConfig
import ac.mdiq.podcini.storage.utils.FilesUtils
import ac.mdiq.podcini.storage.utils.FilesUtils.createNoMediaFile
import ac.mdiq.podcini.util.Logd
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
object UserPreferences {
    private val TAG: String = UserPreferences::class.simpleName ?: "Anonymous"

    const val PREF_OPML_BACKUP = "prefOPMLBackup"

    // User Interface
    const val PREF_THEME: String = "prefTheme"
    const val PREF_THEME_BLACK: String = "prefThemeBlack"
    const val PREF_TINTED_COLORS: String = "prefTintedColors"
    const val PREF_HIDDEN_DRAWER_ITEMS: String = "prefHiddenDrawerItems"
    const val PREF_DRAWER_FEED_ORDER: String = "prefDrawerFeedOrder"
    const val PREF_DRAWER_FEED_ORDER_DIRECTION: String = "prefDrawerFeedOrderDir"
    const val PREF_FEED_GRID_LAYOUT: String = "prefFeedGridLayout"
//    const val PREF_DRAWER_FEED_COUNTER: String = "prefDrawerFeedIndicator"
    const val PREF_EXPANDED_NOTIFICATION: String = "prefExpandNotify"
    const val PREF_USE_EPISODE_COVER: String = "prefEpisodeCover"
    const val PREF_SHOW_TIME_LEFT: String = "showTimeLeft"
    const val PREF_PERSISTENT_NOTIFICATION = "prefPersistNotify"
    const val PREF_FULL_NOTIFICATION_BUTTONS: String = "prefFullNotificationButtons"
    const val PREF_SHOW_DOWNLOAD_REPORT = "prefShowDownloadReport"
    const val PREF_DEFAULT_PAGE: String = "prefDefaultPage"
    private const val PREF_BACK_OPENS_DRAWER: String = "prefBackButtonOpensDrawer"

    const val PREF_QUEUE_KEEP_SORTED: String = "prefQueueKeepSorted"
    const val PREF_QUEUE_KEEP_SORTED_ORDER: String = "prefQueueKeepSortedOrder"
    const val PREF_DOWNLOADS_SORTED_ORDER = "prefDownloadSortedOrder"
//    private const val PREF_HISTORY_SORTED_ORDER = "prefHistorySortedOrder"

    // Episodes
    const val PREF_SORT_ALL_EPISODES: String = "prefEpisodesSort"
    const val PREF_FILTER_ALL_EPISODES: String = "prefEpisodesFilter"

    // Playback
    const val PREF_PAUSE_ON_HEADSET_DISCONNECT: String = "prefPauseOnHeadsetDisconnect"
    const val PREF_UNPAUSE_ON_HEADSET_RECONNECT: String = "prefUnpauseOnHeadsetReconnect"
    const val PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT: String = "prefUnpauseOnBluetoothReconnect"
    const val PREF_HARDWARE_FORWARD_BUTTON: String = "prefHardwareForwardButton"
    const val PREF_HARDWARE_PREVIOUS_BUTTON: String = "prefHardwarePreviousButton"
    const val PREF_FOLLOW_QUEUE: String = "prefFollowQueue"
    const val PREF_SKIP_KEEPS_EPISODE: String = "prefSkipKeepsEpisode"
    const val PREF_REMOVDE_FROM_QUEUE_MARKED_PLAYED: String = "prefRemoveFromQueueMarkedPlayed"
    const val PREF_FAVORITE_KEEPS_EPISODE = "prefFavoriteKeepsEpisode"
    private const val PREF_AUTO_DELETE = "prefAutoDelete"
    private const val PREF_AUTO_DELETE_LOCAL = "prefAutoDeleteLocal"
    const val PREF_SMART_MARK_AS_PLAYED_SECS: String = "prefSmartMarkAsPlayedSecs"
    const val PREF_PLAYBACK_SPEED_ARRAY = "prefPlaybackSpeedArray"
    private const val PREF_FALLBACK_SPEED = "prefFallbackSpeed"
    private const val PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS: String = "prefPauseForFocusLoss"
    private const val PREF_TIME_RESPECTS_SPEED = "prefPlaybackTimeRespectsSpeed"
    private const val PREF_STREAM_OVER_DOWNLOAD: String = "prefStreamOverDownload"
    private const val PREF_SPEEDFORWRD_SPEED = "prefSpeedforwardSpeed"

    // Network
    const val PREF_ENQUEUE_DOWNLOADED = "prefEnqueueDownloaded"
    const val PREF_ENQUEUE_LOCATION: String = "prefEnqueueLocation"
    const val PREF_UPDATE_INTERVAL: String = "prefAutoUpdateIntervall"
    const val PREF_MOBILE_UPDATE = "prefMobileUpdateTypes"
    const val PREF_EPISODE_CLEANUP: String = "prefEpisodeCleanup"
    const val PREF_EPISODE_CACHE_SIZE: String = "prefEpisodeCacheSize"
    const val PREF_ENABLE_AUTODL: String = "prefEnableAutoDl"
    const val PREF_ENABLE_AUTODL_ON_BATTERY: String = "prefEnableAutoDownloadOnBattery"
    const val PREF_ENABLE_AUTODL_WIFI_FILTER: String = "prefEnableAutoDownloadWifiFilter"
    const val PREF_AUTODL_SELECTED_NETWORKS = "prefAutodownloadSelectedNetworks"
    private const val PREF_PROXY_TYPE = "prefProxyType"
    private const val PREF_PROXY_HOST = "prefProxyHost"
    private const val PREF_PROXY_PORT = "prefProxyPort"
    private const val PREF_PROXY_USER = "prefProxyUser"
    private const val PREF_PROXY_PASSWORD = "prefProxyPassword"

    // Services
    const val PREF_GPODNET_NOTIFICATIONS = "pref_gpodnet_notifications"

    // Other
//    const val PREF_DATA_FOLDER = "prefDataFolder"
    const val PREF_DELETE_REMOVES_FROM_QUEUE: String = "prefDeleteRemovesFromQueue"

    // Mediaplayer
    const val PREF_PLAYBACK_SPEED = "prefPlaybackSpeed"
    private const val PREF_VIDEO_PLAYBACK_SPEED = "prefVideoPlaybackSpeed"
    private const val PREF_PLAYBACK_SKIP_SILENCE: String = "prefSkipSilence"
    private const val PREF_FAST_FORWARD_SECS = "prefFastForwardSecs"
    private const val PREF_REWIND_SECS = "prefRewindSecs"
    const val PREF_QUEUE_LOCKED = "prefQueueLocked"
    private const val PREF_VIDEO_MODE = "prefVideoPlaybackMode"

    // Experimental
    const val EPISODE_CLEANUP_QUEUE: Int = -1
    const val EPISODE_CLEANUP_NULL: Int = -2
    const val EPISODE_CLEANUP_EXCEPT_FAVORITE: Int = -3
    const val EPISODE_CLEANUP_DEFAULT: Int = 0

    // Constants
    const val NOTIFICATION_BUTTON_REWIND: Int = 0
    const val NOTIFICATION_BUTTON_FAST_FORWARD: Int = 1
    private const val NOTIFICATION_BUTTON_SKIP: Int = 2

    private const val NOTIFICATION_BUTTON_NEXT_CHAPTER: Int = 3
    private const val NOTIFICATION_BUTTON_PLAYBACK_SPEED: Int = 4
    const val EPISODE_CACHE_SIZE_UNLIMITED: Int = -1
//    should match those defined in arrays
//    const val FEED_ORDER_COUNTER: Int = 0
    const val FEED_ORDER_UNPLAYED: Int = 0
    const val FEED_ORDER_ALPHABETICAL: Int = 1
    const val FEED_ORDER_LAST_UPDATED: Int = 2
    const val FEED_ORDER_LAST_UNREAD_UPDATED: Int = 3
    const val FEED_ORDER_MOST_PLAYED: Int = 4
    const val FEED_ORDER_DOWNLOADED: Int = 5
    const val FEED_ORDER_DOWNLOADED_UNPLAYED: Int = 6
    const val FEED_ORDER_NEW: Int = 7
    const val DEFAULT_PAGE_REMEMBER: String = "remember"

    private lateinit var context: Context
    lateinit var appPrefs: SharedPreferences

    var theme: ThemePreference
        get() = when (appPrefs.getString(PREF_THEME, "system")) {
            "0" -> ThemePreference.LIGHT
            "1" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
        set(theme) {
            when (theme) {
                ThemePreference.LIGHT -> appPrefs.edit().putString(PREF_THEME, "0").apply()
                ThemePreference.DARK -> appPrefs.edit().putString(PREF_THEME, "1").apply()
                else -> appPrefs.edit().putString(PREF_THEME, "system").apply()
            }
        }

    val isBlackTheme: Boolean
        get() = appPrefs.getBoolean(PREF_THEME_BLACK, false)

    val isThemeColorTinted: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && appPrefs.getBoolean(PREF_TINTED_COLORS, false)

    var hiddenDrawerItems: List<String>
        get() {
            val hiddenItems = appPrefs.getString(PREF_HIDDEN_DRAWER_ITEMS, "")
            return hiddenItems?.split(",") ?: listOf()
        }
        set(items) {
            val str = items.joinToString()
            appPrefs.edit()
                .putString(PREF_HIDDEN_DRAWER_ITEMS, str)
                .apply()
        }

    var fullNotificationButtons: List<Int>
        get() {
            val buttons = appPrefs.getString(PREF_FULL_NOTIFICATION_BUTTONS, "$NOTIFICATION_BUTTON_SKIP,$NOTIFICATION_BUTTON_PLAYBACK_SPEED")?.split(",") ?: listOf()
            val notificationButtons: MutableList<Int> = ArrayList()
            for (button in buttons) {
                notificationButtons.add(button.toInt())
            }
            return notificationButtons
        }
        set(items) {
            val str = items.joinToString()
            appPrefs.edit()
                .putString(PREF_FULL_NOTIFICATION_BUTTONS, str)
                .apply()
        }

    val isAutoDelete: Boolean
        get() = appPrefs.getBoolean(PREF_AUTO_DELETE, false)

    val isAutoDeleteLocal: Boolean
        get() = appPrefs.getBoolean(PREF_AUTO_DELETE_LOCAL, false)

    val videoPlayMode: Int
        get() {
            try {
                return appPrefs.getString(PREF_VIDEO_MODE, "1")!!.toInt()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                setVideoMode(1)
                return 1
            }
        }

    var videoPlaybackSpeed: Float
        get() {
            try {
                return appPrefs.getString(PREF_VIDEO_PLAYBACK_SPEED, "1.00")!!.toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                videoPlaybackSpeed = 1.0f
                return 1.0f
            }
        }
        set(speed) {
            appPrefs.edit()
                .putString(PREF_VIDEO_PLAYBACK_SPEED, speed.toString())
                .apply()
        }

    var isSkipSilence: Boolean
        get() = appPrefs.getBoolean(PREF_PLAYBACK_SKIP_SILENCE, false)
        set(skipSilence) {
            appPrefs.edit().putBoolean(PREF_PLAYBACK_SKIP_SILENCE, skipSilence).apply()
        }

    /**
     * Returns the capacity of the episode cache. This method will return the
     * negative integer EPISODE_CACHE_SIZE_UNLIMITED if the cache size is set to
     * 'unlimited'.
     */
    val episodeCacheSize: Int
        get() = appPrefs.getString(PREF_EPISODE_CACHE_SIZE, "20")!!.toInt()

    @set:VisibleForTesting
    var isEnableAutodownload: Boolean
        get() = appPrefs.getBoolean(PREF_ENABLE_AUTODL, false)
        set(enabled) {
            appPrefs.edit().putBoolean(PREF_ENABLE_AUTODL, enabled).apply()
        }

    val isEnableAutodownloadOnBattery: Boolean
        get() = appPrefs.getBoolean(PREF_ENABLE_AUTODL_ON_BATTERY, true)

    var speedforwardSpeed: Float
        get() {
            try {
                return appPrefs.getString(PREF_SPEEDFORWRD_SPEED, "0.00")!!.toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                speedforwardSpeed = 0.0f
                return 0.0f
            }
        }
        set(speed) {
            appPrefs.edit().putString(PREF_SPEEDFORWRD_SPEED, speed.toString()).apply()
        }

    var fallbackSpeed: Float
        get() {
            try {
                return appPrefs.getString(PREF_FALLBACK_SPEED, "0.00")!!.toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                fallbackSpeed = 0.0f
                return 0.0f
            }
        }
        set(speed) {
            appPrefs.edit().putString(PREF_FALLBACK_SPEED, speed.toString()).apply()
        }

    var fastForwardSecs: Int
        get() = appPrefs.getInt(PREF_FAST_FORWARD_SECS, 30)
        set(secs) {
            appPrefs.edit().putInt(PREF_FAST_FORWARD_SECS, secs).apply()
        }

    var rewindSecs: Int
        get() = appPrefs.getInt(PREF_REWIND_SECS, 10)
        set(secs) {
            appPrefs.edit().putInt(PREF_REWIND_SECS, secs).apply()
        }

    var proxyConfig: ProxyConfig
        get() {
            val type = Proxy.Type.valueOf(appPrefs.getString(PREF_PROXY_TYPE, Proxy.Type.DIRECT.name)!!)
            val host = appPrefs.getString(PREF_PROXY_HOST, null)
            val port = appPrefs.getInt(PREF_PROXY_PORT, 0)
            val username = appPrefs.getString(PREF_PROXY_USER, null)
            val password = appPrefs.getString(PREF_PROXY_PASSWORD, null)
            return ProxyConfig(type, host, port, username, password)
        }
        set(config) {
            val editor = appPrefs.edit()
            editor.putString(PREF_PROXY_TYPE, config.type.name)
            if (config.host.isNullOrEmpty()) editor.remove(PREF_PROXY_HOST)
            else editor.putString(PREF_PROXY_HOST, config.host)

            if (config.port <= 0 || config.port > 65535) editor.remove(PREF_PROXY_PORT)
            else editor.putInt(PREF_PROXY_PORT, config.port)

            if (config.username.isNullOrEmpty()) editor.remove(PREF_PROXY_USER)
            else editor.putString(PREF_PROXY_USER, config.username)

            if (config.password.isNullOrEmpty()) editor.remove(PREF_PROXY_PASSWORD)
            else editor.putString(PREF_PROXY_PASSWORD, config.password)

            editor.apply()
        }

    var defaultPage: String?
        get() = appPrefs.getString(PREF_DEFAULT_PAGE, "SubscriptionsFragment")
        set(defaultPage) {
            appPrefs.edit().putString(PREF_DEFAULT_PAGE, defaultPage).apply()
        }

    var isStreamOverDownload: Boolean
        get() = appPrefs.getBoolean(PREF_STREAM_OVER_DOWNLOAD, false)
        set(stream) {
            appPrefs.edit().putBoolean(PREF_STREAM_OVER_DOWNLOAD, stream).apply()
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
     * NOTIFICATION_BUTTON_SKIP, NOTIFICATION_BUTTON_PLAYBACK_SPEED
     * or NOTIFICATION_BUTTON_NEXT_CHAPTER.
     * @return `true` if button should be shown, `false`  otherwise
     */
    private fun showButtonOnFullNotification(buttonId: Int): Boolean {
        return fullNotificationButtons.contains(buttonId)
    }

    @JvmStatic
    fun shouldAutoDeleteItem(feed: Feed): Boolean {
        if (!isAutoDelete) return false
        return !feed.isLocalFeed || isAutoDeleteLocal
    }

//    only used in test
    fun showSkipOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON_SKIP)
    }

    //    only used in test
    fun showNextChapterOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON_NEXT_CHAPTER)
    }

    //    only used in test
    fun showPlaybackSpeedOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON_PLAYBACK_SPEED)
    }

    /**
     * @return `true` if we should show remaining time or the duration
     */
    fun shouldShowRemainingTime(): Boolean {
        return appPrefs.getBoolean(PREF_SHOW_TIME_LEFT, false)
    }

    /**
     * Sets the preference for whether we show the remain time, if not show the duration. This will
     * send out events so the current playing screen, queue and the episode list would refresh
     * @return `true` if we should show remaining time or the duration
     */
    fun setShowRemainTimeSetting(showRemain: Boolean?) {
        appPrefs.edit().putBoolean(PREF_SHOW_TIME_LEFT, showRemain!!).apply()
    }

    fun shouldDeleteRemoveFromQueue(): Boolean {
        return appPrefs.getBoolean(PREF_DELETE_REMOVES_FROM_QUEUE, false)
    }

//   only used in test
    fun shouldPauseForFocusLoss(): Boolean {
        return appPrefs.getBoolean(PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true)
    }

    fun backButtonOpensDrawer(): Boolean {
        return appPrefs.getBoolean(PREF_BACK_OPENS_DRAWER, false)
    }

    fun timeRespectsSpeed(): Boolean {
        return appPrefs.getBoolean(PREF_TIME_RESPECTS_SPEED, false)
    }

    fun setPlaybackSpeed(speed: Float) {
        appPrefs.edit().putString(PREF_PLAYBACK_SPEED, speed.toString()).apply()
    }

    fun setVideoMode(mode: Int) {
        appPrefs.edit().putString(PREF_VIDEO_MODE, mode.toString()).apply()
    }

    enum class ThemePreference {
        LIGHT, DARK, BLACK, SYSTEM
    }
}
