package ac.mdiq.podcini

import ac.mdiq.podcini.net.download.VistaDownloaderImpl
import ac.mdiq.podcini.preferences.PreferenceUpgrader
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.receiver.SPAReceiver
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.ui.activity.SplashActivity
import ac.mdiq.podcini.util.Localization.Companion.getPreferredContentCountry
import ac.mdiq.podcini.util.Localization.Companion.getPreferredLocalization
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.config.ApplicationCallbacks
import ac.mdiq.podcini.util.config.ClientConfig
import ac.mdiq.podcini.util.config.ClientConfigurator
import ac.mdiq.podcini.util.error.CrashReportWriter
import ac.mdiq.vista.extractor.ServiceList
import ac.mdiq.vista.extractor.Vista
import ac.mdiq.vista.extractor.downloader.Downloader
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.StrictMode
import android.util.Log

import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Main application class.  */
class PodciniApp : Application() {

    private val vistaDownloader: Downloader
        get() {
            val downloader = VistaDownloaderImpl.init(null)
            setCookiesToDownloader(downloader)
            return downloader
        }

    
    override fun onCreate() {
        super.onCreate()
        ClientConfig.USER_AGENT = "Podcini/" + BuildConfig.VERSION_NAME
        ClientConfig.applicationCallbacks = ApplicationCallbacksImpl()

        Thread.setDefaultUncaughtExceptionHandler(CrashReportWriter())

        if (BuildConfig.DEBUG) {
            val builder: StrictMode.VmPolicy.Builder = StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyDropBox()
            StrictMode.setVmPolicy(builder.build())
        }

        singleton = this

        runBlocking {
            withContext(Dispatchers.IO) {
                ClientConfigurator.initialize(this@PodciniApp)
                PreferenceUpgrader.checkUpgrades(this@PodciniApp)
            }
        }
        Vista.init(vistaDownloader, getPreferredLocalization(this), getPreferredContentCountry(this))
        initServices(this)

        sendSPAppsQueryFeedsIntent(this)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    private fun setCookiesToDownloader(downloader: VistaDownloaderImpl?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val key = "recaptcha_cookies_key"
        downloader?.setCookie("recaptcha_cookies", prefs.getString(key, null)?:"")
        downloader?.updateYoutubeRestrictedModeCookies(applicationContext)
    }

    private fun initServices(context: Context) {
        for (s in ServiceList.all()) {
            if (s.serviceId == ServiceList.PeerTube.serviceId) {
//                not doing anything now
            }
        }
    }

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
            Log.wtf("App", "Unable to get application context")
            return false
        }
//        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (!appPrefs.getBoolean(PREF_HAS_QUERIED_SP_APPS, false)) {
            appContext.sendBroadcast(Intent(SPAReceiver.ACTION_SP_APPS_QUERY_FEEDS))
            Logd("App", "Sending SP_APPS_QUERY_FEEDS intent")
            val editor = appPrefs.edit()
            editor.putBoolean(PREF_HAS_QUERIED_SP_APPS, true)
            editor.apply()
            return true
        } else return false
    }

    class ApplicationCallbacksImpl : ApplicationCallbacks {
        override fun getApplicationInstance(): Application {
            return getInstance()
        }
    }

    companion object {
        private const val PREF_HAS_QUERIED_SP_APPS = "prefSPAUtil.hasQueriedSPApps"

        private lateinit var singleton: PodciniApp

        fun getInstance(): PodciniApp {
            return singleton
        }

        @JvmStatic
        fun forceRestart() {
            val intent = Intent(getInstance(), SplashActivity::class.java)
            val cn: ComponentName? = intent.component
            val mainIntent: Intent = Intent.makeRestartActivityTask(cn)
            realm.close()
            getInstance().startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
