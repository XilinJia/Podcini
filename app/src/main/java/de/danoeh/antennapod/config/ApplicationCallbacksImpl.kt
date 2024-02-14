package de.danoeh.antennapod.config

import android.app.Application
import de.danoeh.antennapod.PodcastApp
import de.danoeh.antennapod.core.ApplicationCallbacks


class ApplicationCallbacksImpl : ApplicationCallbacks {
    override fun getApplicationInstance(): Application {
        return PodcastApp.getInstance()
    }
}
