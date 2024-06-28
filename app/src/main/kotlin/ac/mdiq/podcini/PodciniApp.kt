package ac.mdiq.podcini

import ac.mdiq.podcini.preferences.PreferenceUpgrader
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.ui.activity.SplashActivity
import ac.mdiq.podcini.util.SPAUtil
import ac.mdiq.podcini.util.config.ApplicationCallbacks
import ac.mdiq.podcini.util.config.ClientConfig
import ac.mdiq.podcini.util.config.ClientConfigurator
import ac.mdiq.podcini.util.error.CrashReportWriter
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.StrictMode
import androidx.media3.common.util.UnstableApi
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Main application class.  */
class PodciniApp : Application() {
    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        ClientConfig.USER_AGENT = "Podcini/" + BuildConfig.VERSION_NAME
        ClientConfig.applicationCallbacks = ApplicationCallbacksImpl()

        Thread.setDefaultUncaughtExceptionHandler(CrashReportWriter())
//        RxJavaErrorHandlerSetup.setupRxJavaErrorHandler()

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
        SPAUtil.sendSPAppsQueryFeedsIntent(this)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    class ApplicationCallbacksImpl : ApplicationCallbacks {
        override fun getApplicationInstance(): Application {
            return PodciniApp.getInstance()
        }
    }

    companion object {
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
