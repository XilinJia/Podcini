package ac.mdiq.podcini

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.StrictMode
import androidx.media3.common.util.UnstableApi
import com.google.android.material.color.DynamicColors
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import com.joanzapata.iconify.fonts.MaterialModule
import ac.mdiq.podcini.ui.activity.SplashActivity
import ac.mdiq.podcini.util.config.ApplicationCallbacksImpl
import ac.mdiq.podcini.util.config.ClientConfig
import ac.mdiq.podcini.util.config.ClientConfigurator
import ac.mdiq.podcini.util.error.CrashReportWriter
import ac.mdiq.podcini.util.error.RxJavaErrorHandlerSetup
import ac.mdiq.podcini.preferences.PreferenceUpgrader
import ac.mdiq.podcini.util.SPAUtil
import org.greenrobot.eventbus.EventBus

/** Main application class.  */
class PodciniApp : Application() {
    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        ClientConfig.USER_AGENT = "Podcini/" + BuildConfig.VERSION_NAME
        ClientConfig.applicationCallbacks = ApplicationCallbacksImpl()

        Thread.setDefaultUncaughtExceptionHandler(CrashReportWriter())
        RxJavaErrorHandlerSetup.setupRxJavaErrorHandler()

        if (BuildConfig.DEBUG) {
            val builder: StrictMode.VmPolicy.Builder = StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .penaltyDropBox()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
            StrictMode.setVmPolicy(builder.build())
        }

        singleton = this

        ClientConfigurator.initialize(this)
        PreferenceUpgrader.checkUpgrades(this)

        Iconify.with(FontAwesomeModule())
        Iconify.with(MaterialModule())

        SPAUtil.sendSPAppsQueryFeedsIntent(this)
        EventBus.builder()
            .addIndex(ApEventBusIndex())
//            .addIndex(ApCoreEventBusIndex())
            .logNoSubscriberMessages(false)
            .sendNoSubscriberEvent(false)
            .installDefaultEventBus()

        DynamicColors.applyToActivitiesIfAvailable(this)
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
            getInstance().startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
