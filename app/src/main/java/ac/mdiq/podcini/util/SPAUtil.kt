package ac.mdiq.podcini.util

import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.receiver.SPAReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Provides methods related to PodciniSP (https://github.com/danieloeh/PodciniSP)
 */
object SPAUtil {
    private const val TAG = "SPAUtil"

    private const val PREF_HAS_QUERIED_SP_APPS = "prefSPAUtil.hasQueriedSPApps"

    /**
     * Sends an ACTION_SP_APPS_QUERY_FEEDS intent to all Podcini Single Purpose apps.
     * The receiving single purpose apps will then send their feeds back to Podcini via an
     * ACTION_SP_APPS_QUERY_FEEDS_RESPONSE intent.
     * This intent will only be sent once.
     *
     * @return True if an intent was sent, false otherwise (for example if the intent has already been
     * sent before.
     */
    @Synchronized
    fun sendSPAppsQueryFeedsIntent(context: Context): Boolean {
//        assert(context != null) { "context = null" }
        val appContext = context.applicationContext
        if (appContext == null) {
            Log.wtf(TAG, "Unable to get application context")
            return false
        }
//        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (!appPrefs.getBoolean(PREF_HAS_QUERIED_SP_APPS, false)) {
            appContext.sendBroadcast(Intent(SPAReceiver.ACTION_SP_APPS_QUERY_FEEDS))
            Logd(TAG, "Sending SP_APPS_QUERY_FEEDS intent")

            val editor = appPrefs.edit()
            editor.putBoolean(PREF_HAS_QUERIED_SP_APPS, true)
            editor.apply()

            return true
        } else return false
    }
}
