package de.danoeh.antennapod.core

import android.content.Context
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.core.preferences.PlaybackPreferences
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences
import de.danoeh.antennapod.core.preferences.UsageStatistics
import de.danoeh.antennapod.core.service.download.AntennapodHttpClient.setCacheDirectory
import de.danoeh.antennapod.core.service.download.AntennapodHttpClient.setProxyConfig
import de.danoeh.antennapod.core.service.download.DownloadServiceInterfaceImpl
import de.danoeh.antennapod.core.sync.SyncService
import de.danoeh.antennapod.core.sync.queue.SynchronizationQueueSink
import de.danoeh.antennapod.core.util.NetworkUtils
import de.danoeh.antennapod.core.util.download.NetworkConnectionChangeHandler
import de.danoeh.antennapod.core.util.gui.NotificationUtils
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.net.ssl.SslProviderInstaller
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences.proxyConfig
import java.io.File

@UnstableApi
object ClientConfigurator {
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) {
            return
        }
        PodDBAdapter.init(context)
        UserPreferences.init(context)
        UsageStatistics.init(context)
        PlaybackPreferences.init(context)
        SslProviderInstaller.install(context)
        NetworkUtils.init(context)
        NetworkConnectionChangeHandler.init(context)
        DownloadServiceInterface.setImpl(DownloadServiceInterfaceImpl())
        SynchronizationQueueSink.setServiceStarterImpl { SyncService.sync(context) }
        setCacheDirectory(File(context.cacheDir, "okhttp"))
        setProxyConfig(proxyConfig)
        SleepTimerPreferences.init(context)
        NotificationUtils.createChannels(context)
        initialized = true
    }
}
