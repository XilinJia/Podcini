package ac.mdiq.podcini.config

import android.app.Application
import ac.mdiq.podcini.PodciniApp
import ac.mdiq.podcini.core.ApplicationCallbacks


class ApplicationCallbacksImpl : ApplicationCallbacks {
    override fun getApplicationInstance(): Application {
        return PodciniApp.getInstance()
    }
}
