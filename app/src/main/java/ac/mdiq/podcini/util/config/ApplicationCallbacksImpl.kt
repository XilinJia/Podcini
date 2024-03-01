package ac.mdiq.podcini.util.config

import android.app.Application
import ac.mdiq.podcini.PodciniApp


class ApplicationCallbacksImpl : ApplicationCallbacks {
    override fun getApplicationInstance(): Application {
        return PodciniApp.getInstance()
    }
}
