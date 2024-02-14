package ac.mdiq.podvinci.spa

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import ac.mdiq.podvinci.BuildConfig
import ac.mdiq.podvinci.receiver.SPAReceiver

/**
 * Provides methods related to PodVinciSP (https://github.com/danieloeh/PodVinciSP)
 */
object SPAUtil {
    private const val TAG = "SPAUtil"

    private const val PREF_HAS_QUERIED_SP_APPS = "prefSPAUtil.hasQueriedSPApps"

    /**
     * Sends an ACTION_SP_APPS_QUERY_FEEDS intent to all PodVinci Single Purpose apps.
     * The receiving single purpose apps will then send their feeds back to PodVinci via an
     * ACTION_SP_APPS_QUERY_FEEDS_RESPONSE intent.
     * This intent will only be sent once.
     *
     * @return True if an intent was sent, false otherwise (for example if the intent has already been
     * sent before.
     */
    @Synchronized
    fun sendSPAppsQueryFeedsIntent(context: Context?): Boolean {
        assert(context != null) { "context = null" }
        val appContext = context!!.applicationContext
        if (appContext == null) {
            Log.wtf(TAG, "Unable to get application context")
            return false
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (!prefs.getBoolean(PREF_HAS_QUERIED_SP_APPS, false)) {
            appContext.sendBroadcast(Intent(SPAReceiver.ACTION_SP_APPS_QUERY_FEEDS))
            if (BuildConfig.DEBUG) Log.d(TAG, "Sending SP_APPS_QUERY_FEEDS intent")

            val editor = prefs.edit()
            editor.putBoolean(PREF_HAS_QUERIED_SP_APPS, true)
            editor.apply()

            return true
        } else {
            return false
        }
    }
}
