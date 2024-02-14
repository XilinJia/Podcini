package de.danoeh.antennapod.core.sync

import android.content.Context
import android.content.SharedPreferences
import de.danoeh.antennapod.core.ClientConfig
import de.danoeh.antennapod.core.sync.queue.SynchronizationQueueSink.clearQueue
import de.danoeh.antennapod.storage.preferences.UserPreferences.setGpodnetNotificationsEnabled

/**
 * Manages preferences for accessing gpodder.net service and other sync providers
 */
object SynchronizationCredentials {
    private const val PREF_NAME = "gpodder.net"
    private const val PREF_USERNAME = "de.danoeh.antennapod.preferences.gpoddernet.username"
    private const val PREF_PASSWORD = "de.danoeh.antennapod.preferences.gpoddernet.password"
    private const val PREF_DEVICEID = "de.danoeh.antennapod.preferences.gpoddernet.deviceID"
    private const val PREF_HOSTNAME = "prefGpodnetHostname"

    private val preferences: SharedPreferences
        get() = ClientConfig.applicationCallbacks!!.getApplicationInstance()!!.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    @JvmStatic
    var username: String?
        get() = preferences.getString(PREF_USERNAME, null)
        set(username) {
            preferences.edit().putString(PREF_USERNAME, username).apply()
        }

    @JvmStatic
    var password: String?
        get() = preferences.getString(PREF_PASSWORD, null)
        set(password) {
            preferences.edit().putString(PREF_PASSWORD, password).apply()
        }

    @JvmStatic
    var deviceID: String?
        get() = preferences.getString(PREF_DEVICEID, null)
        set(deviceID) {
            preferences.edit().putString(PREF_DEVICEID, deviceID).apply()
        }

    @JvmStatic
    var hosturl: String?
        get() = preferences.getString(PREF_HOSTNAME, null)
        set(value) {
            preferences.edit().putString(PREF_HOSTNAME, value).apply()
        }

    @Synchronized
    fun clear(context: Context) {
        username = null
        password = null
        deviceID = null
        clearQueue(context)
        setGpodnetNotificationsEnabled()
    }
}
