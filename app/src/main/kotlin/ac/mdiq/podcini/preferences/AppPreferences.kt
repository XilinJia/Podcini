package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.ProxyConfig
import ac.mdiq.podcini.storage.utils.StorageUtils.createNoMediaFile
import ac.mdiq.podcini.ui.activity.MainActivity
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
object AppPreferences {
    private val TAG: String = AppPreferences::class.simpleName ?: "Anonymous"

    const val EPISODE_CACHE_SIZE_UNLIMITED: Int = 0

    lateinit var appPrefs: SharedPreferences
    val cachedPrefs = mutableMapOf<String, Any?>()

    var theme: ThemePreference
        get() = when (getPref(AppPrefs.prefTheme, "system")) {
            "0" -> ThemePreference.LIGHT
            "1" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
        set(theme) {
            when (theme) {
                ThemePreference.LIGHT -> putPref(AppPrefs.prefTheme, "0")
                ThemePreference.DARK -> putPref(AppPrefs.prefTheme, "1")
                else -> putPref(AppPrefs.prefTheme, "system")
            }
        }


    val isThemeColorTinted: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && getPref(AppPrefs.prefTintedColors, false)

    val videoPlayMode: Int
        get() {
            try { return getPref(AppPrefs.prefVideoPlaybackMode, "1").toInt()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                putPref(AppPrefs.prefVideoPlaybackMode, "1")
                return 1
            }
        }

    var isSkipSilence: Boolean
        get() = getPref(AppPrefs.prefSkipSilence, false)
        set(skipSilence) {
            putPref(AppPrefs.prefSkipSilence, skipSilence)
        }

    @set:VisibleForTesting
    var isEnableAutodownload: Boolean
        get() = getPref(AppPrefs.prefEnableAutoDl, false)
        set(enabled) {
            putPref(AppPrefs.prefEnableAutoDl, enabled)
        }

    var speedforwardSpeed: Float
        get() {
            try { return getPref(AppPrefs.prefSpeedforwardSpeed, "0.00").toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                speedforwardSpeed = 0.0f
                return 0.0f
            }
        }
        set(speed) {
            putPref(AppPrefs.prefSpeedforwardSpeed, speed.toString())
        }

    var fallbackSpeed: Float
        get() {
            try { return getPref(AppPrefs.prefFallbackSpeed, "0.00").toFloat()
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                fallbackSpeed = 0.0f
                return 0.0f
            }
        }
        set(speed) {
            putPref(AppPrefs.prefFallbackSpeed, speed.toString())
        }

    var fastForwardSecs: Int
        get() = getPref(AppPrefs.prefFastForwardSecs, 30)
        set(secs) {
            putPref(AppPrefs.prefFastForwardSecs, secs)
        }

    var rewindSecs: Int
        get() = getPref(AppPrefs.prefRewindSecs, 10)
        set(secs) {
            putPref(AppPrefs.prefRewindSecs, secs)
        }

    var proxyConfig: ProxyConfig
        get() {
            val type = Proxy.Type.valueOf(getPref(AppPrefs.prefProxyType, Proxy.Type.DIRECT.name))
            val host = getPrefOrNull<String>(AppPrefs.prefProxyHost, null)
            val port = getPref(AppPrefs.prefProxyPort, 0)
            val username = getPrefOrNull<String>(AppPrefs.prefProxyUser, null)
            val password = getPrefOrNull<String>(AppPrefs.prefProxyPassword, null)
            return ProxyConfig(type, host, port, username, password)
        }
        set(config) {
            putPref(AppPrefs.prefProxyType, config.type.name)
            if (config.host.isNullOrEmpty()) removePref(AppPrefs.prefProxyHost.name)
            else putPref(AppPrefs.prefProxyHost.name, config.host)
            if (config.port <= 0 || config.port > 65535) removePref(AppPrefs.prefProxyPort.name)
            else putPref(AppPrefs.prefProxyPort.name, config.port)
            if (config.username.isNullOrEmpty()) removePref(AppPrefs.prefProxyUser.name)
            else putPref(AppPrefs.prefProxyUser.name, config.username)
            if (config.password.isNullOrEmpty()) removePref(AppPrefs.prefProxyPassword.name)
            else putPref(AppPrefs.prefProxyPassword.name, config.password)
        }

    var defaultPage: String?
        get() = getPref(AppPrefs.prefDefaultPage, MainActivity.Screens.Subscriptions.name)
        set(defaultPage) {
            putPref(AppPrefs.prefDefaultPage, defaultPage)
        }

    var isStreamOverDownload: Boolean
        get() = getPref(AppPrefs.prefStreamOverDownload, false)
        set(stream) {
            putPref(AppPrefs.prefStreamOverDownload, stream)
        }

    var prefLowQualityMedia: Boolean
        get() = getPref(AppPrefs.prefLowQualityOnMobile, false)
        set(stream) {
            putPref(AppPrefs.prefLowQualityOnMobile, stream)
        }

    var prefAdaptiveProgressUpdate: Boolean
        get() = getPref(AppPrefs.prefUseAdaptiveProgressUpdate, false)
        set(value) {
            putPref(AppPrefs.prefUseAdaptiveProgressUpdate, value)
        }

//    private val preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
//        Log.d(TAG, "PreferenceChangeListener key: $key")
//        if (key != null) cachedPrefs[key] = appPrefs.all[key]
//    }

    /**
     * Sets up the UserPreferences class.
     * @throws IllegalArgumentException if context is null
     */
    fun init(context: Context) {
        Logd(TAG, "Creating new instance of UserPreferences")
        appPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        AppPrefs.entries.map { it.name }.forEach { key -> cachedPrefs[key] = appPrefs.all[key] }
//        appPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        createNoMediaFile()
    }

    inline fun <reified T> getPref(key: String, defaultValue: T): T {
        val value = cachedPrefs[key]
        return when (value) {
            is T -> value
            else -> defaultValue
        }
    }

    inline fun <reified T> getPref(key: AppPrefs, defaultValue: T): T = getPref(key.name, defaultValue)

    inline fun <reified T> getPrefOrNull(key: AppPrefs, defaultValue: T?): T? {
        val value = cachedPrefs[key.name]
        return when (value) {
            is T -> value
            else -> defaultValue
        }
    }

    inline fun <reified T> putPref(key: String, value: T) {
        cachedPrefs[key] = value
        val editor = appPrefs.edit()
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Long -> editor.putLong(key, value)
            else -> throw IllegalArgumentException("Unsupported type")
        }
        editor.apply()
    }

    inline fun <reified T> putPref(key: AppPrefs, value: T) {
        putPref(key.name, value)
    }

    fun removePref(key: String) {
        cachedPrefs.remove(key)
        appPrefs.edit().remove(key).apply()
    }

    enum class DefaultPages(val res: Int) {
        Subscriptions(R.string.subscriptions_label),
        Queues(R.string.queue_label),
        Episodes(R.string.episodes_label),
        OnlineSearch(R.string.add_feed_label),
        Statistics(R.string.statistics_label),
        Remember(R.string.remember_last_page);
    }

    @Suppress("EnumEntryName")
    enum class AppPrefs {
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
