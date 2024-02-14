package ac.mdiq.podvinci.core

import android.content.Context
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podvinci.core.preferences.PlaybackPreferences
import ac.mdiq.podvinci.core.preferences.SleepTimerPreferences
import ac.mdiq.podvinci.core.preferences.UsageStatistics
import ac.mdiq.podvinci.core.service.download.PodVinciHttpClient.setCacheDirectory
import ac.mdiq.podvinci.core.service.download.PodVinciHttpClient.setProxyConfig
import ac.mdiq.podvinci.core.service.download.DownloadServiceInterfaceImpl
import ac.mdiq.podvinci.core.sync.SyncService
import ac.mdiq.podvinci.core.sync.queue.SynchronizationQueueSink
import ac.mdiq.podvinci.core.util.NetworkUtils
import ac.mdiq.podvinci.core.util.download.NetworkConnectionChangeHandler
import ac.mdiq.podvinci.core.util.gui.NotificationUtils
import ac.mdiq.podvinci.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podvinci.net.ssl.SslProviderInstaller
import ac.mdiq.podvinci.storage.database.PodDBAdapter
import ac.mdiq.podvinci.storage.preferences.UserPreferences
import ac.mdiq.podvinci.storage.preferences.UserPreferences.proxyConfig
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
