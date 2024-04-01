package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.storage.model.download.ProxyConfig
import ac.mdiq.podcini.storage.model.feed.FeedCounter
import ac.mdiq.podcini.storage.model.feed.FeedPreferences.NewEpisodesAction
import ac.mdiq.podcini.storage.model.feed.SortOrder
import ac.mdiq.podcini.storage.model.feed.SubscriptionsFilter
import ac.mdiq.podcini.storage.model.playback.MediaType
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
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
    private const val TAG = "UserPreferences"

    // User Interface
    const val PREF_THEME: String = "prefTheme"
    const val PREF_THEME_BLACK: String = "prefThemeBlack"
    const val PREF_TINTED_COLORS: String = "prefTintedColors"
    const val PREF_HIDDEN_DRAWER_ITEMS: String = "prefHiddenDrawerItems"
    const val PREF_DRAWER_FEED_ORDER: String = "prefDrawerFeedOrder"
    const val PREF_DRAWER_FEED_COUNTER: String = "prefDrawerFeedIndicator"
    const val PREF_EXPANDED_NOTIFICATION: String = "prefExpandNotify"
    const val PREF_USE_EPISODE_COVER: String = "prefEpisodeCover"
    const val PREF_SHOW_TIME_LEFT: String = "showTimeLeft"
    private const val PREF_PERSISTENT_NOTIFICATION = "prefPersistNotify"
    const val PREF_FULL_NOTIFICATION_BUTTONS: String = "prefFullNotificationButtons"
    private const val PREF_SHOW_DOWNLOAD_REPORT = "prefShowDownloadReport"
    const val PREF_DEFAULT_PAGE: String = "prefDefaultPage"
    const val PREF_FILTER_FEED: String = "prefSubscriptionsFilter"
    const val PREF_SUBSCRIPTION_TITLE: String = "prefSubscriptionTitle"
    const val PREF_BACK_OPENS_DRAWER: String = "prefBackButtonOpensDrawer"

    const val PREF_QUEUE_KEEP_SORTED: String = "prefQueueKeepSorted"
    const val PREF_QUEUE_KEEP_SORTED_ORDER: String = "prefQueueKeepSortedOrder"
    const val PREF_NEW_EPISODES_ACTION: String = "prefNewEpisodesAction"    // not used
    private const val PREF_DOWNLOADS_SORTED_ORDER = "prefDownloadSortedOrder"
    private const val PREF_INBOX_SORTED_ORDER = "prefInboxSortedOrder"

    // Episode
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
    private const val PREF_FAVORITE_KEEPS_EPISODE = "prefFavoriteKeepsEpisode"
    private const val PREF_AUTO_DELETE = "prefAutoDelete"
    private const val PREF_AUTO_DELETE_LOCAL = "prefAutoDeleteLocal"
    const val PREF_SMART_MARK_AS_PLAYED_SECS: String = "prefSmartMarkAsPlayedSecs"
    private const val PREF_PLAYBACK_SPEED_ARRAY = "prefPlaybackSpeedArray"
    private const val PREF_FALLBACK_SPEED = "prefFallbackSpeed"
    const val PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS: String = "prefPauseForFocusLoss"
    private const val PREF_TIME_RESPECTS_SPEED = "prefPlaybackTimeRespectsSpeed"
    const val PREF_STREAM_OVER_DOWNLOAD: String = "prefStreamOverDownload"
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
    const val PREF_PLAYBACK_SKIP_SILENCE: String = "prefSkipSilence"
    private const val PREF_FAST_FORWARD_SECS = "prefFastForwardSecs"
    private const val PREF_REWIND_SECS = "prefRewindSecs"
    private const val PREF_QUEUE_LOCKED = "prefQueueLocked"

    // Experimental
    const val EPISODE_CLEANUP_QUEUE: Int = -1
    const val EPISODE_CLEANUP_NULL: Int = -2
    const val EPISODE_CLEANUP_EXCEPT_FAVORITE: Int = -3
    const val EPISODE_CLEANUP_DEFAULT: Int = 0

    // Constants
    const val NOTIFICATION_BUTTON_REWIND: Int = 0
    const val NOTIFICATION_BUTTON_FAST_FORWARD: Int = 1
    const val NOTIFICATION_BUTTON_SKIP: Int = 2

    const val NOTIFICATION_BUTTON_NEXT_CHAPTER: Int = 3
    const val NOTIFICATION_BUTTON_PLAYBACK_SPEED: Int = 4
    const val EPISODE_CACHE_SIZE_UNLIMITED: Int = -1
    const val FEED_ORDER_COUNTER: Int = 0
    const val FEED_ORDER_ALPHABETICAL: Int = 1
    const val FEED_ORDER_MOST_PLAYED: Int = 3
    const val FEED_ORDER_LAST_UPDATED: Int = 4
    const val FEED_ORDER_LAST_UNREAD_UPDATED: Int = 5
    const val DEFAULT_PAGE_REMEMBER: String = "remember"

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    /**
     * Sets up the UserPreferences class.
     *
     * @throws IllegalArgumentException if context is null
     */
    @JvmStatic
    fun init(context: Context) {
        Log.d(TAG, "Creating new instance of UserPreferences")

        UserPreferences.context = context.applicationContext
        prefs = PreferenceManager.getDefaultSharedPreferences(context)

        createNoMediaFile()
    }

    @JvmStatic
    var theme: ThemePreference?
        get() = when (prefs.getString(PREF_THEME, "system")) {
            "0" -> ThemePreference.LIGHT
            "1" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
        set(theme) {
            when (theme) {
                ThemePreference.LIGHT -> prefs.edit().putString(PREF_THEME, "0").apply()
                ThemePreference.DARK -> prefs.edit().putString(PREF_THEME, "1").apply()
                else -> prefs.edit().putString(PREF_THEME, "system").apply()
            }
        }

    val isBlackTheme: Boolean
        get() = prefs.getBoolean(PREF_THEME_BLACK, false)

    val isThemeColorTinted: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && prefs.getBoolean(PREF_TINTED_COLORS, false)

    @JvmStatic
    var hiddenDrawerItems: List<String?>
        get() {
            val hiddenItems = prefs.getString(PREF_HIDDEN_DRAWER_ITEMS, "")
            return ArrayList(listOf(*TextUtils.split(hiddenItems, ",")))
        }
        set(items) {
            val str = TextUtils.join(",", items)
            prefs.edit()
                .putString(PREF_HIDDEN_DRAWER_ITEMS, str)
                .apply()
        }

    @JvmStatic
    var fullNotificationButtons: List<Int>?
        get() {
            val buttons = TextUtils.split(
                prefs.getString(PREF_FULL_NOTIFICATION_BUTTONS,
                    "$NOTIFICATION_BUTTON_SKIP,$NOTIFICATION_BUTTON_PLAYBACK_SPEED"), ",")

            val notificationButtons: MutableList<Int> = ArrayList()
            for (button in buttons) {
                notificationButtons.add(button.toInt())
            }
            return notificationButtons
        }
        set(items) {
            val str = TextUtils.join(",", items!!)
            prefs.edit()
                .putString(PREF_FULL_NOTIFICATION_BUTTONS, str)
                .apply()
        }

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
        return fullNotificationButtons!!.contains(buttonId)
    }

    @JvmStatic
    fun showSkipOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON_SKIP)
    }

    @JvmStatic
    fun showNextChapterOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON_NEXT_CHAPTER)
    }

    @JvmStatic
    fun showPlaybackSpeedOnFullNotification(): Boolean {
        return showButtonOnFullNotification(NOTIFICATION_BUTTON_PLAYBACK_SPEED)
    }

    @JvmStatic
    val feedOrder: Int
        get() {
            val value = prefs.getString(PREF_DRAWER_FEED_ORDER, "" + FEED_ORDER_COUNTER)
            return value!!.toInt()
        }

    @JvmStatic
    fun setFeedOrder(selected: String?) {
        prefs.edit()
            .putString(PREF_DRAWER_FEED_ORDER, selected)
            .apply()
    }

    @JvmStatic
    val feedCounterSetting: FeedCounter
        get() {
            val value = prefs.getString(PREF_DRAWER_FEED_COUNTER, "" + FeedCounter.SHOW_NEW.id)
            return FeedCounter.fromOrdinal(value!!.toInt())
        }

    val useEpisodeCoverSetting: Boolean
        /**
         * @return `true` if episodes should use their own cover, `false`  otherwise
         */
        get() = prefs.getBoolean(PREF_USE_EPISODE_COVER, true)

    /**
     * @return `true` if we should show remaining time or the duration
     */
    @JvmStatic
    fun shouldShowRemainingTime(): Boolean {
        return prefs.getBoolean(PREF_SHOW_TIME_LEFT, false)
    }

    /**
     * Sets the preference for whether we show the remain time, if not show the duration. This will
     * send out events so the current playing screen, queue and the episode list would refresh
     *
     * @return `true` if we should show remaining time or the duration
     */
    @JvmStatic
    fun setShowRemainTimeSetting(showRemain: Boolean?) {
        prefs.edit().putBoolean(PREF_SHOW_TIME_LEFT, showRemain!!).apply()
    }

    val notifyPriority: Int
        /**
         * Returns notification priority.
         *
         * @return NotificationCompat.PRIORITY_MAX or NotificationCompat.PRIORITY_DEFAULT
         */
        get() = if (prefs.getBoolean(PREF_EXPANDED_NOTIFICATION, false)) {
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

    @JvmStatic
    val isPersistNotify: Boolean
        /**
         * Returns true if notifications are persistent
         *
         * @return `true` if notifications are persistent, `false`  otherwise
         */
        get() = prefs.getBoolean(PREF_PERSISTENT_NOTIFICATION, true)

    @JvmStatic
    val showDownloadReportRaw: Boolean
        /**
         * Used for migration of the preference to system notification channels.
         */
        get() = prefs.getBoolean(PREF_SHOW_DOWNLOAD_REPORT, true)

    fun enqueueDownloadedEpisodes(): Boolean {
        return prefs.getBoolean(PREF_ENQUEUE_DOWNLOADED, true)
    }

    @JvmStatic
    var enqueueLocation: EnqueueLocation
        get() {
            val valStr = prefs.getString(PREF_ENQUEUE_LOCATION, EnqueueLocation.BACK.name)
            try {
                return EnqueueLocation.valueOf(valStr!!)
            } catch (t: Throwable) {
                // should never happen but just in case
                Log.e(TAG, "getEnqueueLocation: invalid value '$valStr' Use default.", t)
                return EnqueueLocation.BACK
            }
        }
        set(location) {
            prefs.edit()
                .putString(PREF_ENQUEUE_LOCATION, location.name)
                .apply()
        }

    @JvmStatic
    val isPauseOnHeadsetDisconnect: Boolean
        get() = prefs.getBoolean(PREF_PAUSE_ON_HEADSET_DISCONNECT, true)

    @JvmStatic
    val isUnpauseOnHeadsetReconnect: Boolean
        get() = prefs.getBoolean(PREF_UNPAUSE_ON_HEADSET_RECONNECT, true)

    @JvmStatic
    val isUnpauseOnBluetoothReconnect: Boolean
        get() = prefs.getBoolean(PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT, false)

    @JvmStatic
    val hardwareForwardButton: Int
        get() = prefs.getString(PREF_HARDWARE_FORWARD_BUTTON,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD.toString())!!.toInt()

    @JvmStatic
    val hardwarePreviousButton: Int
        get() = prefs.getString(PREF_HARDWARE_PREVIOUS_BUTTON,
            KeyEvent.KEYCODE_MEDIA_REWIND.toString())!!.toInt()


    @JvmStatic
    @set:VisibleForTesting
    var isFollowQueue: Boolean
        get() = prefs.getBoolean(PREF_FOLLOW_QUEUE, true)
        /**
         * Set to true to enable Continuous Playback
         */
        set(value) {
            prefs.edit().putBoolean(PREF_FOLLOW_QUEUE, value).apply()
        }

    @JvmStatic
    fun shouldSkipKeepEpisode(): Boolean {
        return prefs.getBoolean(PREF_SKIP_KEEPS_EPISODE, true)
    }

    @JvmStatic
    fun shouldFavoriteKeepEpisode(): Boolean {
        return prefs.getBoolean(PREF_FAVORITE_KEEPS_EPISODE, true)
    }

    @JvmStatic
    val isAutoDelete: Boolean
        get() = prefs.getBoolean(PREF_AUTO_DELETE, false)

    @JvmStatic
    val isAutoDeleteLocal: Boolean
        get() = prefs.getBoolean(PREF_AUTO_DELETE_LOCAL, false)

    val smartMarkAsPlayedSecs: Int
        get() = prefs.getString(PREF_SMART_MARK_AS_PLAYED_SECS, "30")!!.toInt()

    @JvmStatic
    fun shouldDeleteRemoveFromQueue(): Boolean {
        return prefs.getBoolean(PREF_DELETE_REMOVES_FROM_QUEUE, false)
    }

    @JvmStatic
    fun getPlaybackSpeed(mediaType: MediaType): Float {
        return if (mediaType == MediaType.VIDEO) {
            videoPlaybackSpeed
        } else {
            audioPlaybackSpeed
        }
    }

    private val audioPlaybackSpeed: Float
        get() {
            try {
                return prefs.getString(PREF_PLAYBACK_SPEED, "1.00")!!.toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                setPlaybackSpeed(1.0f)
                return 1.0f
            }
        }

    @JvmStatic
    var videoPlaybackSpeed: Float
        get() {
            try {
                return prefs.getString(PREF_VIDEO_PLAYBACK_SPEED, "1.00")!!.toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                videoPlaybackSpeed = 1.0f
                return 1.0f
            }
        }
        set(speed) {
            prefs.edit()
                .putString(PREF_VIDEO_PLAYBACK_SPEED, speed.toString())
                .apply()
        }

    @JvmStatic
    var isSkipSilence: Boolean
        get() = prefs.getBoolean(PREF_PLAYBACK_SKIP_SILENCE, false)
        set(skipSilence) {
            prefs.edit()
                .putBoolean(PREF_PLAYBACK_SKIP_SILENCE, skipSilence)
                .apply()
        }

    @JvmStatic
    var playbackSpeedArray: List<Float>
        get() = readPlaybackSpeedArray(prefs.getString(PREF_PLAYBACK_SPEED_ARRAY, null))
        set(speeds) {
            val format = DecimalFormatSymbols(Locale.US)
            format.decimalSeparator = '.'
            val speedFormat = DecimalFormat("0.00", format)
            val jsonArray = JSONArray()
            for (speed in speeds) {
                jsonArray.put(speedFormat.format(speed.toDouble()))
            }
            prefs.edit()
                .putString(PREF_PLAYBACK_SPEED_ARRAY, jsonArray.toString())
                .apply()
        }

    @JvmStatic
    fun shouldPauseForFocusLoss(): Boolean {
        return prefs.getBoolean(PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true)
    }

    val updateInterval: Long
        get() = prefs.getString(PREF_UPDATE_INTERVAL, "12")!!.toInt().toLong()

    val isAutoUpdateDisabled: Boolean
        get() = updateInterval == 0L

    private fun isAllowMobileFor(type: String): Boolean {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val allowed = prefs.getStringSet(PREF_MOBILE_UPDATE, defaultValue)
        return allowed!!.contains(type)
    }

    @JvmStatic
    var isAllowMobileFeedRefresh: Boolean
        get() = isAllowMobileFor("feed_refresh")
        set(allow) {
            setAllowMobileFor("feed_refresh", allow)
        }

    @JvmStatic
    var isAllowMobileSync: Boolean
        get() = isAllowMobileFor("sync")
        set(allow) {
            setAllowMobileFor("sync", allow)
        }

    @JvmStatic
    var isAllowMobileEpisodeDownload: Boolean
        get() = isAllowMobileFor("episode_download")
        set(allow) {
            setAllowMobileFor("episode_download", allow)
        }

    @JvmStatic
    var isAllowMobileAutoDownload: Boolean
        get() = isAllowMobileFor("auto_download")
        set(allow) {
            setAllowMobileFor("auto_download", allow)
        }

    @JvmStatic
    var isAllowMobileStreaming: Boolean
        get() = isAllowMobileFor("streaming")
        set(allow) {
            setAllowMobileFor("streaming", allow)
        }

    @JvmStatic
    var isAllowMobileImages: Boolean
        get() = isAllowMobileFor("images")
        set(allow) {
            setAllowMobileFor("images", allow)
        }

    private fun setAllowMobileFor(type: String, allow: Boolean) {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val getValueStringSet = prefs.getStringSet(PREF_MOBILE_UPDATE, defaultValue)
        val allowed: MutableSet<String> = HashSet(getValueStringSet)
        if (allow) {
            allowed.add(type)
        } else {
            allowed.remove(type)
        }
        prefs.edit().putStringSet(PREF_MOBILE_UPDATE, allowed).apply()
    }

    @JvmStatic
    val episodeCacheSize: Int
        /**
         * Returns the capacity of the episode cache. This method will return the
         * negative integer EPISODE_CACHE_SIZE_UNLIMITED if the cache size is set to
         * 'unlimited'.
         */
        get() = prefs.getString(PREF_EPISODE_CACHE_SIZE, "20")!!.toInt()

    @JvmStatic
    @set:VisibleForTesting
    var isEnableAutodownload: Boolean
        get() = prefs.getBoolean(PREF_ENABLE_AUTODL, false)
        set(enabled) {
            prefs.edit().putBoolean(PREF_ENABLE_AUTODL, enabled).apply()
        }

    @JvmStatic
    val isEnableAutodownloadOnBattery: Boolean
        get() = prefs.getBoolean(PREF_ENABLE_AUTODL_ON_BATTERY, true)

    @JvmStatic
    val isEnableAutodownloadWifiFilter: Boolean
        get() = Build.VERSION.SDK_INT < 29 && prefs.getBoolean(PREF_ENABLE_AUTODL_WIFI_FILTER, false)

    @JvmStatic
    var speedforwardSpeed: Float
        get() {
            try {
                return prefs.getString(PREF_SPEEDFORWRD_SPEED, "0.00")!!.toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                speedforwardSpeed = 0.0f
                return 0.0f
            }
        }
        set(speed) {
            prefs.edit()
                .putString(PREF_SPEEDFORWRD_SPEED, speed.toString())
                .apply()
        }

    @JvmStatic
    var fallbackSpeed: Float
        get() {
            try {
                return prefs.getString(PREF_FALLBACK_SPEED, "0.00")!!.toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                fallbackSpeed = 0.0f
                return 0.0f
            }
        }
        set(speed) {
            prefs.edit()
                .putString(PREF_FALLBACK_SPEED, speed.toString())
                .apply()
        }

    @JvmStatic
    var fastForwardSecs: Int
        get() = prefs.getInt(PREF_FAST_FORWARD_SECS, 30)
        set(secs) {
            prefs.edit()
                .putInt(PREF_FAST_FORWARD_SECS, secs)
                .apply()
        }

    @JvmStatic
    var rewindSecs: Int
        get() = prefs.getInt(PREF_REWIND_SECS, 10)
        set(secs) {
            prefs.edit()
                .putInt(PREF_REWIND_SECS, secs)
                .apply()
        }

    @JvmStatic
    val autodownloadSelectedNetworks: Array<String>
        get() {
            val selectedNetWorks = prefs.getString(PREF_AUTODL_SELECTED_NETWORKS, "")
            return TextUtils.split(selectedNetWorks, ",")
        }

    @JvmStatic
    var proxyConfig: ProxyConfig
        get() {
            val type = Proxy.Type.valueOf(prefs.getString(PREF_PROXY_TYPE, Proxy.Type.DIRECT.name)!!)
            val host = prefs.getString(PREF_PROXY_HOST, null)
            val port = prefs.getInt(PREF_PROXY_PORT, 0)
            val username = prefs.getString(PREF_PROXY_USER, null)
            val password = prefs.getString(PREF_PROXY_PASSWORD, null)
            return ProxyConfig(type, host, port, username, password)
        }
        set(config) {
            val editor = prefs.edit()
            editor.putString(PREF_PROXY_TYPE, config.type.name)
            if (config.host.isNullOrEmpty()) {
                editor.remove(PREF_PROXY_HOST)
            } else {
                editor.putString(PREF_PROXY_HOST, config.host)
            }
            if (config.port <= 0 || config.port > 65535) {
                editor.remove(PREF_PROXY_PORT)
            } else {
                editor.putInt(PREF_PROXY_PORT, config.port)
            }
            if (config.username.isNullOrEmpty()) {
                editor.remove(PREF_PROXY_USER)
            } else {
                editor.putString(PREF_PROXY_USER, config.username)
            }
            if (config.password.isNullOrEmpty()) {
                editor.remove(PREF_PROXY_PASSWORD)
            } else {
                editor.putString(PREF_PROXY_PASSWORD, config.password)
            }
            editor.apply()
        }

    @JvmStatic
    var isQueueLocked: Boolean
        get() = prefs.getBoolean(PREF_QUEUE_LOCKED, false)
        set(locked) {
            prefs.edit()
                .putBoolean(PREF_QUEUE_LOCKED, locked)
                .apply()
        }

    @JvmStatic
    fun setPlaybackSpeed(speed: Float) {
        prefs.edit()
            .putString(PREF_PLAYBACK_SPEED, speed.toString())
            .apply()
    }

    @JvmStatic
    fun setAutodownloadSelectedNetworks(value: Array<String?>?) {
        prefs.edit()
            .putString(PREF_AUTODL_SELECTED_NETWORKS, TextUtils.join(",", value!!))
            .apply()
    }

    @JvmStatic
    fun gpodnetNotificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= 26) {
            return true // System handles notification preferences
        }
        return prefs.getBoolean(PREF_GPODNET_NOTIFICATIONS, true)
    }

    @JvmStatic
    val gpodnetNotificationsEnabledRaw: Boolean
        /**
         * Used for migration of the preference to system notification channels.
         */
        get() = prefs.getBoolean(PREF_GPODNET_NOTIFICATIONS, true)

    @JvmStatic
    fun setGpodnetNotificationsEnabled() {
        prefs.edit()
            .putBoolean(PREF_GPODNET_NOTIFICATIONS, true)
            .apply()
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

    @JvmStatic
    var episodeCleanupValue: Int
        get() = prefs.getString(PREF_EPISODE_CLEANUP, "" + EPISODE_CLEANUP_NULL)!!
            .toInt()
        set(episodeCleanupValue) {
            prefs.edit()
                .putString(PREF_EPISODE_CLEANUP, episodeCleanupValue.toString())
                .apply()
        }

    /**
     * Return the folder where the app stores all of its data. This method will
     * return the standard data folder if none has been set by the user.
     *
     * @param type The name of the folder inside the data folder. May be null
     * when accessing the root of the data folder.
     * @return The data folder that has been requested or null if the folder could not be created.
     */
    @JvmStatic
    fun getDataFolder(type: String?): File? {
        var dataFolder = getTypeDir(prefs.getString(PREF_DATA_FOLDER, null), type)
        if (dataFolder == null || !dataFolder.canWrite()) {
            Log.d(TAG, "User data folder not writable or not set. Trying default.")
            dataFolder = context.getExternalFilesDir(type)
        }
        if (dataFolder == null || !dataFolder.canWrite()) {
            Log.d(TAG, "Default data folder not available or not writable. Falling back to internal memory.")
            dataFolder = getTypeDir(context.filesDir.absolutePath, type)
        }
        return dataFolder
    }

    private fun getTypeDir(baseDirPath: String?, type: String?): File? {
        if (baseDirPath == null) {
            return null
        }
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

    @JvmStatic
    fun setDataFolder(dir: String) {
        Log.d(TAG, "setDataFolder(dir: $dir)")
        prefs.edit()
            .putString(PREF_DATA_FOLDER, dir)
            .apply()
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
            Log.d(TAG, ".nomedia file created")
        }
    }

    @JvmStatic
    var defaultPage: String?
        get() = prefs.getString(PREF_DEFAULT_PAGE, "SubscriptionFragment")
        set(defaultPage) {
            prefs.edit().putString(PREF_DEFAULT_PAGE, defaultPage).apply()
        }

    @JvmStatic
    fun backButtonOpensDrawer(): Boolean {
        return prefs.getBoolean(PREF_BACK_OPENS_DRAWER, false)
    }

    @JvmStatic
    fun timeRespectsSpeed(): Boolean {
        return prefs.getBoolean(PREF_TIME_RESPECTS_SPEED, false)
    }

    @JvmStatic
    var isStreamOverDownload: Boolean
        get() = prefs.getBoolean(PREF_STREAM_OVER_DOWNLOAD, false)
        set(stream) {
            prefs.edit().putBoolean(PREF_STREAM_OVER_DOWNLOAD, stream).apply()
        }

    @JvmStatic
    var isQueueKeepSorted: Boolean
        /**
         * Returns if the queue is in keep sorted mode.
         *
         * @see .getQueueKeepSortedOrder
         */
        get() = prefs.getBoolean(PREF_QUEUE_KEEP_SORTED, false)
        /**
         * Enables/disables the keep sorted mode of the queue.
         *
         * @see .setQueueKeepSortedOrder
         */
        set(keepSorted) {
            prefs.edit()
                .putBoolean(PREF_QUEUE_KEEP_SORTED, keepSorted)
                .apply()
        }

    @JvmStatic
    var queueKeepSortedOrder: SortOrder?
        /**
         * Returns the sort order for the queue keep sorted mode.
         * Note: This value is stored independently from the keep sorted state.
         *
         * @see .isQueueKeepSorted
         */
        get() {
            val sortOrderStr = prefs.getString(PREF_QUEUE_KEEP_SORTED_ORDER, "use-default")
            return SortOrder.parseWithDefault(sortOrderStr, SortOrder.DATE_NEW_OLD)
        }
        /**
         * Sets the sort order for the queue keep sorted mode.
         *
         * @see .setQueueKeepSorted
         */
        set(sortOrder) {
            if (sortOrder == null) {
                return
            }
            prefs.edit()
                .putString(PREF_QUEUE_KEEP_SORTED_ORDER, sortOrder.name)
                .apply()
        }

    @JvmStatic
    val newEpisodesAction: NewEpisodesAction
        get() {
            val str = prefs.getString(PREF_NEW_EPISODES_ACTION,
                "" + NewEpisodesAction.GLOBAL.code)
            return NewEpisodesAction.fromCode(str!!.toInt())
        }

    @JvmStatic
    var downloadsSortedOrder: SortOrder?
        /**
         * Returns the sort order for the downloads.
         */
        get() {
            val sortOrderStr = prefs.getString(PREF_DOWNLOADS_SORTED_ORDER, "" + SortOrder.DATE_NEW_OLD.code)
            return SortOrder.fromCodeString(sortOrderStr)
        }
        /**
         * Sets the sort order for the downloads.
         */
        set(sortOrder) {
            prefs.edit().putString(PREF_DOWNLOADS_SORTED_ORDER, "" + sortOrder!!.code).apply()
        }

//    @JvmStatic
//    var inboxSortedOrder: SortOrder?
//        get() {
//            val sortOrderStr = prefs.getString(PREF_INBOX_SORTED_ORDER, "" + SortOrder.DATE_NEW_OLD.code)
//            return SortOrder.fromCodeString(sortOrderStr)
//        }
//        set(sortOrder) {
//            prefs.edit().putString(PREF_INBOX_SORTED_ORDER, "" + sortOrder!!.code).apply()
//        }

//    @JvmStatic
    var subscriptionsFilter: SubscriptionsFilter
        get() {
            val value = prefs.getString(PREF_FILTER_FEED, "")
            return SubscriptionsFilter(value)
        }
        set(value) {
            prefs.edit()
                .putString(PREF_FILTER_FEED, value.serialize())
                .apply()
        }

    @JvmStatic
    fun shouldShowSubscriptionTitle(): Boolean {
        return true
//        return prefs.getBoolean(PREF_SUBSCRIPTION_TITLE, true)
    }

    @JvmStatic
    var allEpisodesSortOrder: SortOrder?
        get() = SortOrder.fromCodeString(prefs.getString(PREF_SORT_ALL_EPISODES,
            "" + SortOrder.DATE_NEW_OLD.code))
        set(s) {
            prefs.edit().putString(PREF_SORT_ALL_EPISODES, "" + s!!.code).apply()
        }

    @JvmStatic
    var prefFilterAllEpisodes: String
        get() = prefs.getString(PREF_FILTER_ALL_EPISODES, "")?:""
        set(filter) {
            prefs.edit().putString(PREF_FILTER_ALL_EPISODES, filter).apply()
        }

    enum class ThemePreference {
        LIGHT, DARK, BLACK, SYSTEM
    }

    enum class EnqueueLocation {
        BACK, FRONT, AFTER_CURRENTLY_PLAYING, RANDOM
    }
}
