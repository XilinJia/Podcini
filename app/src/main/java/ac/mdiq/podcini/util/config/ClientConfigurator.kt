package ac.mdiq.podcini.util.config

import android.content.Context
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.preferences.PlaybackPreferences
import ac.mdiq.podcini.preferences.SleepTimerPreferences
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.setCacheDirectory
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.setProxyConfig
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.util.NetworkUtils
import ac.mdiq.podcini.net.download.NetworkConnectionChangeHandler
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.net.ssl.SslProviderInstaller
import ac.mdiq.podcini.storage.database.PodDBAdapter
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.proxyConfig
import ac.mdiq.podcini.net.download.service.DownloadServiceInterfaceImpl
import java.io.File

@UnstableApi
object ClientConfigurator {
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

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
