package ac.mdiq.podcini.core

import android.content.Context
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.core.preferences.PlaybackPreferences
import ac.mdiq.podcini.core.preferences.SleepTimerPreferences
import ac.mdiq.podcini.core.preferences.UsageStatistics
import ac.mdiq.podcini.core.service.download.PodciniHttpClient.setCacheDirectory
import ac.mdiq.podcini.core.service.download.PodciniHttpClient.setProxyConfig
import ac.mdiq.podcini.core.service.download.DownloadServiceInterfaceImpl
import ac.mdiq.podcini.core.sync.SyncService
import ac.mdiq.podcini.core.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.core.util.NetworkUtils
import ac.mdiq.podcini.core.util.download.NetworkConnectionChangeHandler
import ac.mdiq.podcini.core.util.gui.NotificationUtils
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.net.ssl.SslProviderInstaller
import ac.mdiq.podcini.storage.database.PodDBAdapter
import ac.mdiq.podcini.storage.preferences.UserPreferences
import ac.mdiq.podcini.storage.preferences.UserPreferences.proxyConfig
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
