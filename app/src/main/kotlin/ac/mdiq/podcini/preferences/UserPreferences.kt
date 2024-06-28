package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.storage.model.ProxyConfig
import ac.mdiq.podcini.storage.utils.SortOrder
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

/**
 * Provides access to preferences set by the user in the settings screen. A
 * private instance of this class must first be instantiated via
 * init() or otherwise every public method will throw an Exception
 * when called.
 */
object UserPreferences {
    private val TAG: String = UserPreferences::class.simpleName ?: "Anonymous"

    private const val PREF_OPML_BACKUP = "prefOPMLBackup"

    // User Interface
    const val PREF_THEME: String = "prefTheme"
    const val PREF_THEME_BLACK: String = "prefThemeBlack"
    const val PREF_TINTED_COLORS: String = "prefTintedColors"
    const val PREF_HIDDEN_DRAWER_ITEMS: String = "prefHiddenDrawerItems"
    const val PREF_DRAWER_FEED_ORDER: String = "prefDrawerFeedOrder"
    const val PREF_FEED_GRID_LAYOUT: String = "prefFeedGridLayout"
    const val PREF_DRAWER_FEED_COUNTER: String = "prefDrawerFeedIndicator"
    const val PREF_EXPANDED_NOTIFICATION: String = "prefExpandNotify"
    private const val PREF_USE_EPISODE_COVER: String = "prefEpisodeCover"
    const val PREF_SHOW_TIME_LEFT: String = "showTimeLeft"
    private const val PREF_PERSISTENT_NOTIFICATION = "prefPersistNotify"
    const val PREF_FULL_NOTIFICATION_BUTTONS: String = "prefFullNotificationButtons"
    private const val PREF_SHOW_DOWNLOAD_REPORT = "prefShowDownloadReport"
    const val PREF_DEFAULT_PAGE: String = "prefDefaultPage"
    private const val PREF_BACK_OPENS_DRAWER: String = "prefBackButtonOpensDrawer"

    private const val PREF_QUEUE_KEEP_SORTED: String = "prefQueueKeepSorted"
    private const val PREF_QUEUE_KEEP_SORTED_ORDER: String = "prefQueueKeepSortedOrder"
    private const val PREF_DOWNLOADS_SORTED_ORDER = "prefDownloadSortedOrder"
    private const val PREF_HISTORY_SORTED_ORDER = "prefHistorySortedOrder"
    private const val PREF_INBOX_SORTED_ORDER = "prefInboxSortedOrder"

    // Episodes
    private const val PREF_SORT_ALL_EPISODES: String = "prefEpisodesSort"
    private const val PREF_FILTER_ALL_EPISODES: String = "prefEpisodesFilter"

    // Playback
    private const val PREF_PAUSE_ON_HEADSET_DISCONNECT: String = "prefPauseOnHeadsetDisconnect"
    const val PREF_UNPAUSE_ON_HEADSET_RECONNECT: String = "prefUnpauseOnHeadsetReconnect"
    const val PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT: String = "prefUnpauseOnBluetoothReconnect"
    private const val PREF_HARDWARE_FORWARD_BUTTON: String = "prefHardwareForwardButton"
    private const val PREF_HARDWARE_PREVIOUS_BUTTON: String = "prefHardwarePreviousButton"
    const val PREF_FOLLOW_QUEUE: String = "prefFollowQueue"
    const val PREF_SKIP_KEEPS_EPISODE: String = "prefSkipKeepsEpisode"
    private const val PREF_FAVORITE_KEEPS_EPISODE = "prefFavoriteKeepsEpisode"
    private const val PREF_AUTO_DELETE = "prefAutoDelete"
    private const val PREF_AUTO_DELETE_LOCAL = "prefAutoDeleteLocal"
    const val PREF_SMART_MARK_AS_PLAYED_SECS: String = "prefSmartMarkAsPlayedSecs"
    private const val PREF_PLAYBACK_SPEED_ARRAY = "prefPlaybackSpeedArray"
    private const val PREF_FALLBACK_SPEED = "prefFallbackSpeed"
    private const val PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS: String = "prefPauseForFocusLoss"
    private const val PREF_TIME_RESPECTS_SPEED = "prefPlaybackTimeRespectsSpeed"
    private const val PREF_STREAM_OVER_DOWNLOAD: String = "prefStreamOverDownload"
    private const val PREF_SPEEDFORWRD_SPEED = "prefSpeedforwardSpeed"

    // Network
    private const val PREF_ENQUEUE_DOWNLOADED = "prefEnqueueDownloaded"
    const val PREF_ENQUEUE_LOCATION: String = "prefEnqueueLocation"
    const val PREF_UPDATE_INTERVAL: String = "prefAutoUpdateIntervall"
    private const val PREF_MOBILE_UPDATE = "prefMobileUpdateTypes"
    const val PREF_EPISODE_CLEANUP: String = "prefEpisodeCleanup"
    const val PREF_EPISODE_CACHE_SIZE: String = "prefEpisodeCacheSize"
    const val PREF_ENABLE_AUTODL: String = "prefEnableAutoDl"
    const val PREF_ENABLE_AUTODL_ON_BATTERY: String = "prefEnableAutoDownloadOnBattery"
    const val PREF_ENABLE_AUTODL_WIFI_FILTER: String = "prefEnableAutoDownloadWifiFilter"
    private const val PREF_AUTODL_SELECTED_NETWORKS = "prefAutodownloadSelectedNetworks"
    private const val PREF_PROXY_TYPE = "prefProxyType"
    private const val PREF_PROXY_HOST = "prefProxyHost"
    private const val PREF_PROXY_PORT = "prefProxyPort"
    private const val PREF_PROXY_USER = "prefProxyUser"
    private const val PREF_PROXY_PASSWORD = "prefProxyPassword"

    // Services
    private const val PREF_GPODNET_NOTIFICATIONS = "pref_gpodnet_notifications"

    // Other
    private const val PREF_DATA_FOLDER = "prefDataFolder"
    const val PREF_DELETE_REMOVES_FROM_QUEUE: String = "prefDeleteRemovesFromQueue"

    // Mediaplayer
    private const val PREF_PLAYBACK_SPEED = "prefPlaybackSpeed"
    private const val PREF_VIDEO_PLAYBACK_SPEED = "prefVideoPlaybackSpeed"
    private const val PREF_PLAYBACK_SKIP_SILENCE: String = "prefSkipSilence"
    private const val PREF_FAST_FORWARD_SECS = "prefFastForwardSecs"
    private const val PREF_REWIND_SECS = "prefRewindSecs"
    private const val PREF_QUEUE_LOCKED = "prefQueueLocked"
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

    /**
     * Sets up the UserPreferences class.
     *
     * @throws IllegalArgumentException if context is null
     */
    fun init(context: Context) {
        Logd(TAG, "Creating new instance of UserPreferences")
        UserPreferences.context = context.applicationContext
        appPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        createNoMediaFile()
    }

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

    val isAutoBackupOPML: Boolean
        get() = appPrefs.getBoolean(PREF_OPML_BACKUP, true)

    /**
     * Helper function to return whether the specified button should be shown on full
     * notifications.
     *
     * @param buttonId Either NOTIFICATION_BUTTON_REWIND, NOTIFICATION_BUTTON_FAST_FORWARD,
     * NOTIFICATION_BUTTON_SKIP, NOTIFICATION_BUTTON_PLAYBACK_SPEED
     * or NOTIFICATION_BUTTON_NEXT_CHAPTER.
     * @return `true` if button should be shown, `false`  otherwise
     */
    private fun showButtonOnFullNotification(buttonId: Int): Boolean {
        return fullNotificationButtons.contains(buttonId)
    }

    fun showSkipOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON_SKIP)
    }

    fun showNextChapterOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON_NEXT_CHAPTER)
    }
    
    fun showPlaybackSpeedOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON_PLAYBACK_SPEED)
    }

    val feedOrder: Int
        get() {
            val value = appPrefs.getString(PREF_DRAWER_FEED_ORDER, "" + FEED_ORDER_UNPLAYED)
            return value!!.toInt()
        }

    fun setFeedOrder(selected: String?) {
        appPrefs.edit()
            .putString(PREF_DRAWER_FEED_ORDER, selected)
            .apply()
    }

    val useGridLayout: Boolean
        get() = appPrefs.getBoolean(PREF_FEED_GRID_LAYOUT, false)

    /**
     * @return `true` if episodes should use their own cover, `false`  otherwise
     */
    val useEpisodeCoverSetting: Boolean
        get() = appPrefs.getBoolean(PREF_USE_EPISODE_COVER, true)

    /**
     * @return `true` if we should show remaining time or the duration
     */
    fun shouldShowRemainingTime(): Boolean {
        return appPrefs.getBoolean(PREF_SHOW_TIME_LEFT, false)
    }

    /**
     * Sets the preference for whether we show the remain time, if not show the duration. This will
     * send out events so the current playing screen, queue and the episode list would refresh
     *
     * @return `true` if we should show remaining time or the duration
     */
    
    fun setShowRemainTimeSetting(showRemain: Boolean?) {
        appPrefs.edit().putBoolean(PREF_SHOW_TIME_LEFT, showRemain!!).apply()
    }

    /**
     * @return `true` if notifications are persistent, `false`  otherwise
     */
    val isPersistNotify: Boolean
        get() = appPrefs.getBoolean(PREF_PERSISTENT_NOTIFICATION, true)

    /**
     * Used for migration of the preference to system notification channels.
     */
    val showDownloadReportRaw: Boolean
        get() = appPrefs.getBoolean(PREF_SHOW_DOWNLOAD_REPORT, true)

    fun enqueueDownloadedEpisodes(): Boolean {
        return appPrefs.getBoolean(PREF_ENQUEUE_DOWNLOADED, true)
    }

    var enqueueLocation: EnqueueLocation
        get() {
            val valStr = appPrefs.getString(PREF_ENQUEUE_LOCATION, EnqueueLocation.BACK.name)
            try {
                return EnqueueLocation.valueOf(valStr!!)
            } catch (t: Throwable) {
                // should never happen but just in case
                Log.e(TAG, "getEnqueueLocation: invalid value '$valStr' Use default.", t)
                return EnqueueLocation.BACK
            }
        }
        set(location) {
            appPrefs.edit().putString(PREF_ENQUEUE_LOCATION, location.name).apply()
        }

    val isPauseOnHeadsetDisconnect: Boolean
        get() = appPrefs.getBoolean(PREF_PAUSE_ON_HEADSET_DISCONNECT, true)

    val isUnpauseOnHeadsetReconnect: Boolean
        get() = appPrefs.getBoolean(PREF_UNPAUSE_ON_HEADSET_RECONNECT, true)

    val isUnpauseOnBluetoothReconnect: Boolean
        get() = appPrefs.getBoolean(PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT, false)

    val hardwareForwardButton: Int
        get() = appPrefs.getString(PREF_HARDWARE_FORWARD_BUTTON, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD.toString())!!.toInt()

    val hardwarePreviousButton: Int
        get() = appPrefs.getString(PREF_HARDWARE_PREVIOUS_BUTTON, KeyEvent.KEYCODE_MEDIA_REWIND.toString())!!.toInt()

    /**
     * Set to true to enable Continuous Playback
     */
    @set:VisibleForTesting
    var isFollowQueue: Boolean
        get() = appPrefs.getBoolean(PREF_FOLLOW_QUEUE, true)
        set(value) {
            appPrefs.edit().putBoolean(PREF_FOLLOW_QUEUE, value).apply()
        }

    fun shouldSkipKeepEpisode(): Boolean {
        return appPrefs.getBoolean(PREF_SKIP_KEEPS_EPISODE, true)
    }

    fun shouldFavoriteKeepEpisode(): Boolean {
        return appPrefs.getBoolean(PREF_FAVORITE_KEEPS_EPISODE, true)
    }

    val isAutoDelete: Boolean
        get() = appPrefs.getBoolean(PREF_AUTO_DELETE, false)

    val isAutoDeleteLocal: Boolean
        get() = appPrefs.getBoolean(PREF_AUTO_DELETE_LOCAL, false)

    val smartMarkAsPlayedSecs: Int
        get() = appPrefs.getString(PREF_SMART_MARK_AS_PLAYED_SECS, "30")!!.toInt()

    fun shouldDeleteRemoveFromQueue(): Boolean {
        return appPrefs.getBoolean(PREF_DELETE_REMOVES_FROM_QUEUE, false)
    }
    
    fun getPlaybackSpeed(mediaType: MediaType): Float {
        return if (mediaType == MediaType.VIDEO) videoPlaybackSpeed else audioPlaybackSpeed
    }

    private val audioPlaybackSpeed: Float
        get() {
            try {
                return appPrefs.getString(PREF_PLAYBACK_SPEED, "1.00")!!.toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                setPlaybackSpeed(1.0f)
                return 1.0f
            }
        }

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

    var playbackSpeedArray: List<Float>
        get() = readPlaybackSpeedArray(appPrefs.getString(PREF_PLAYBACK_SPEED_ARRAY, null))
        set(speeds) {
            val format = DecimalFormatSymbols(Locale.US)
            format.decimalSeparator = '.'
            val speedFormat = DecimalFormat("0.00", format)
            val jsonArray = JSONArray()
            for (speed in speeds) {
                jsonArray.put(speedFormat.format(speed.toDouble()))
            }
            appPrefs.edit().putString(PREF_PLAYBACK_SPEED_ARRAY, jsonArray.toString()).apply()
        }

    fun shouldPauseForFocusLoss(): Boolean {
        return appPrefs.getBoolean(PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true)
    }

    val updateInterval: Long
        get() = appPrefs.getString(PREF_UPDATE_INTERVAL, "12")!!.toInt().toLong()

    val isAutoUpdateDisabled: Boolean
        get() = updateInterval == 0L

    private fun isAllowMobileFor(type: String): Boolean {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val allowed = appPrefs.getStringSet(PREF_MOBILE_UPDATE, defaultValue)
        return allowed!!.contains(type)
    }

    var isAllowMobileFeedRefresh: Boolean
        get() = isAllowMobileFor("feed_refresh")
        set(allow) {
            setAllowMobileFor("feed_refresh", allow)
        }

    var isAllowMobileSync: Boolean
        get() = isAllowMobileFor("sync")
        set(allow) {
            setAllowMobileFor("sync", allow)
        }

    var isAllowMobileEpisodeDownload: Boolean
        get() = isAllowMobileFor("episode_download")
        set(allow) {
            setAllowMobileFor("episode_download", allow)
        }

    var isAllowMobileAutoDownload: Boolean
        get() = isAllowMobileFor("auto_download")
        set(allow) {
            setAllowMobileFor("auto_download", allow)
        }

    var isAllowMobileStreaming: Boolean
        get() = isAllowMobileFor("streaming")
        set(allow) {
            setAllowMobileFor("streaming", allow)
        }

    var isAllowMobileImages: Boolean
        get() = isAllowMobileFor("images")
        set(allow) {
            setAllowMobileFor("images", allow)
        }

    private fun setAllowMobileFor(type: String, allow: Boolean) {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val getValueStringSet = appPrefs.getStringSet(PREF_MOBILE_UPDATE, defaultValue)
        val allowed: MutableSet<String> = HashSet(getValueStringSet!!)
        if (allow) allowed.add(type)
        else allowed.remove(type)

        appPrefs.edit().putStringSet(PREF_MOBILE_UPDATE, allowed).apply()
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

    val isEnableAutodownloadWifiFilter: Boolean
        get() = Build.VERSION.SDK_INT < 29 && appPrefs.getBoolean(PREF_ENABLE_AUTODL_WIFI_FILTER, false)

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

    val autodownloadSelectedNetworks: Array<String>
        get() {
            val selectedNetWorks = appPrefs.getString(PREF_AUTODL_SELECTED_NETWORKS, "")
            return selectedNetWorks?.split(",")?.toTypedArray() ?: arrayOf()
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

    var isQueueLocked: Boolean
        get() = appPrefs.getBoolean(PREF_QUEUE_LOCKED, false)
        set(locked) {
            appPrefs.edit().putBoolean(PREF_QUEUE_LOCKED, locked).apply()
        }

    fun setPlaybackSpeed(speed: Float) {
        appPrefs.edit().putString(PREF_PLAYBACK_SPEED, speed.toString()).apply()
    }

    fun setVideoMode(mode: Int) {
        appPrefs.edit().putString(PREF_VIDEO_MODE, mode.toString()).apply()
    }

    fun setAutodownloadSelectedNetworks(value: Array<String?>?) {
        appPrefs.edit().putString(PREF_AUTODL_SELECTED_NETWORKS, value!!.joinToString()).apply()
    }
    
    fun gpodnetNotificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= 26) return true // System handles notification preferences
        return appPrefs.getBoolean(PREF_GPODNET_NOTIFICATIONS, true)
    }

    /**
     * Used for migration of the preference to system notification channels.
     */
    val gpodnetNotificationsEnabledRaw: Boolean
        get() = appPrefs.getBoolean(PREF_GPODNET_NOTIFICATIONS, true)

    fun setGpodnetNotificationsEnabled() {
        appPrefs.edit().putBoolean(PREF_GPODNET_NOTIFICATIONS, true).apply()
    }

    private fun readPlaybackSpeedArray(valueFromPrefs: String?): List<Float> {
        if (valueFromPrefs != null) {
            try {
                val jsonArray = JSONArray(valueFromPrefs)
                val selectedSpeeds: MutableList<Float> = ArrayList()
                for (i in 0 until jsonArray.length()) {
                    selectedSpeeds.add(jsonArray.getDouble(i).toFloat())
                }
                return selectedSpeeds
            } catch (e: JSONException) {
                Log.e(TAG, "Got JSON error when trying to get speeds from JSONArray")
                e.printStackTrace()
            }
        }
        // If this preference hasn't been set yet, return the default options
        return mutableListOf(1.0f, 1.25f, 1.5f)
    }

    var episodeCleanupValue: Int
        get() = appPrefs.getString(PREF_EPISODE_CLEANUP, "" + EPISODE_CLEANUP_NULL)!!.toInt()
        set(episodeCleanupValue) {
            appPrefs.edit().putString(PREF_EPISODE_CLEANUP, episodeCleanupValue.toString()).apply()
        }

    /**
     * Return the folder where the app stores all of its data. This method will
     * return the standard data folder if none has been set by the user.
     *
     * @param type The name of the folder inside the data folder. May be null
     * when accessing the root of the data folder.
     * @return The data folder that has been requested or null if the folder could not be created.
     */
    fun getDataFolder(type: String?): File? {
        var dataFolder = getTypeDir(appPrefs.getString(PREF_DATA_FOLDER, null), type)
        if (dataFolder == null || !dataFolder.canWrite()) {
            Logd(TAG, "User data folder not writable or not set. Trying default.")
            dataFolder = context.getExternalFilesDir(type)
        }
        if (dataFolder == null || !dataFolder.canWrite()) {
            Logd(TAG, "Default data folder not available or not writable. Falling back to internal memory.")
            dataFolder = getTypeDir(context.filesDir.absolutePath, type)
        }
        return dataFolder
    }

    private fun getTypeDir(baseDirPath: String?, type: String?): File? {
        if (baseDirPath == null) return null

        val baseDir = File(baseDirPath)
        val typeDir = if (type == null) baseDir else File(baseDir, type)
        if (!typeDir.exists()) {
            if (!baseDir.canWrite()) {
                Log.e(TAG, "Base dir is not writable " + baseDir.absolutePath)
                return null
            }
            if (!typeDir.mkdirs()) {
                Log.e(TAG, "Could not create type dir " + typeDir.absolutePath)
                return null
            }
        }
        return typeDir
    }

    fun setDataFolder(dir: String) {
        Logd(TAG, "setDataFolder(dir: $dir)")
        appPrefs.edit().putString(PREF_DATA_FOLDER, dir).apply()
    }

    /**
     * Create a .nomedia file to prevent scanning by the media scanner.
     */
    private fun createNoMediaFile() {
        val f = File(context.getExternalFilesDir(null), ".nomedia")
        if (!f.exists()) {
            try {
                f.createNewFile()
            } catch (e: IOException) {
                Log.e(TAG, "Could not create .nomedia file")
                e.printStackTrace()
            }
            Logd(TAG, ".nomedia file created")
        }
    }

    var defaultPage: String?
        get() = appPrefs.getString(PREF_DEFAULT_PAGE, "SubscriptionsFragment")
        set(defaultPage) {
            appPrefs.edit().putString(PREF_DEFAULT_PAGE, defaultPage).apply()
        }

    fun backButtonOpensDrawer(): Boolean {
        return appPrefs.getBoolean(PREF_BACK_OPENS_DRAWER, false)
    }

    fun timeRespectsSpeed(): Boolean {
        return appPrefs.getBoolean(PREF_TIME_RESPECTS_SPEED, false)
    }

    var isStreamOverDownload: Boolean
        get() = appPrefs.getBoolean(PREF_STREAM_OVER_DOWNLOAD, false)
        set(stream) {
            appPrefs.edit().putBoolean(PREF_STREAM_OVER_DOWNLOAD, stream).apply()
        }

    var isQueueKeepSorted: Boolean
        /**
         * Returns if the queue is in keep sorted mode.
         *
         * @see .queueKeepSortedOrder
         */
        get() = appPrefs.getBoolean(PREF_QUEUE_KEEP_SORTED, false)
        /**
         * Enables/disables the keep sorted mode of the queue.
         *
         * @see .queueKeepSortedOrder
         */
        set(keepSorted) {
            appPrefs.edit().putBoolean(PREF_QUEUE_KEEP_SORTED, keepSorted).apply()
        }
    
    var queueKeepSortedOrder: SortOrder?
        /**
         * Returns the sort order for the queue keep sorted mode.
         * Note: This value is stored independently from the keep sorted state.
         *
         * @see .isQueueKeepSorted
         */
        get() {
            val sortOrderStr = appPrefs.getString(PREF_QUEUE_KEEP_SORTED_ORDER, "use-default")
            return SortOrder.parseWithDefault(sortOrderStr, SortOrder.DATE_NEW_OLD)
        }
        /**
         * Sets the sort order for the queue keep sorted mode.
         *
         * @see .setQueueKeepSorted
         */
        set(sortOrder) {
            if (sortOrder == null) return
            appPrefs.edit().putString(PREF_QUEUE_KEEP_SORTED_ORDER, sortOrder.name).apply()
        }

//    the sort order for the downloads.
    var downloadsSortedOrder: SortOrder?
        get() {
            val sortOrderStr = appPrefs.getString(PREF_DOWNLOADS_SORTED_ORDER, "" + SortOrder.DATE_NEW_OLD.code)
            return SortOrder.fromCodeString(sortOrderStr)
        }
        set(sortOrder) {
            appPrefs.edit().putString(PREF_DOWNLOADS_SORTED_ORDER, "" + sortOrder!!.code).apply()
        }

    var allEpisodesSortOrder: SortOrder?
        get() = SortOrder.fromCodeString(appPrefs.getString(PREF_SORT_ALL_EPISODES, "" + SortOrder.DATE_NEW_OLD.code))
        set(s) {
            appPrefs.edit().putString(PREF_SORT_ALL_EPISODES, "" + s!!.code).apply()
        }

    var prefFilterAllEpisodes: String
        get() = appPrefs.getString(PREF_FILTER_ALL_EPISODES, "")?:""
        set(filter) {
            appPrefs.edit().putString(PREF_FILTER_ALL_EPISODES, filter).apply()
        }

    enum class ThemePreference {
        LIGHT, DARK, BLACK, SYSTEM
    }

    enum class EnqueueLocation {
        BACK, FRONT, AFTER_CURRENTLY_PLAYING, RANDOM
    }
}
