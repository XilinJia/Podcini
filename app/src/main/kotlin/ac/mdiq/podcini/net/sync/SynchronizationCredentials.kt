package ac.mdiq.podcini.net.sync

import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink.clearQueue
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs.pref_gpodnet_notifications
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.util.config.ClientConfig
import android.content.Context
import android.content.SharedPreferences

/**
 * Manages preferences for accessing gpodder.net service and other sync providers
 */
object SynchronizationCredentials {
    private const val PREF_NAME = "gpodder.net"
    private const val PREF_USERNAME = "ac.mdiq.podcini.preferences.gpoddernet.username"
    private const val PREF_PASSWORD = "ac.mdiq.podcini.preferences.gpoddernet.password"
    private const val PREF_DEVICEID = "ac.mdiq.podcini.preferences.gpoddernet.deviceID"
    private const val PREF_HOSTNAME = "prefGpodnetHostname"
    private const val PREF_HOSTPORT = "prefHostport"

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

    @JvmStatic
    var hostport: Int
        get() = preferences.getInt(PREF_HOSTPORT, 0)
        set(value) {
            preferences.edit().putInt(PREF_HOSTPORT, value).apply()
        }

    fun setGpodnetNotificationsEnabled() {
        putPref(pref_gpodnet_notifications, true)
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
