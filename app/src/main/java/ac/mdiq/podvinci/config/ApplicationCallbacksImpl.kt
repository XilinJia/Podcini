package ac.mdiq.podvinci.config

import android.app.Application
import ac.mdiq.podvinci.PodVinciApp
import ac.mdiq.podvinci.core.ApplicationCallbacks


class ApplicationCallbacksImpl : ApplicationCallbacks {
    override fun getApplicationInstance(): Application {
        return PodVinciApp.getInstance()
    }
}
