package ac.mdiq.podcini.util.config

import android.app.Application
import ac.mdiq.podcini.PodciniApp


class ApplicationCallbacksImpl : ac.mdiq.podcini.util.config.ApplicationCallbacks {
    override fun getApplicationInstance(): Application {
        return PodciniApp.getInstance()
    }
}
