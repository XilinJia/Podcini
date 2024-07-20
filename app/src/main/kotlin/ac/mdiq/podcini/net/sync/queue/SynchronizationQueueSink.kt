package ac.mdiq.podcini.net.sync.queue

import android.content.Context
import ac.mdiq.podcini.net.sync.LockingAsyncExecutor
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.lastSyncAttempt
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.net.sync.model.EpisodeAction

object SynchronizationQueueSink {
    // To avoid a dependency loop of every class to SyncService, and from SyncService back to every class.
    private var serviceStarterImpl = Runnable {}

    fun needSynch() : Boolean {
        return isProviderConnected
    }

    fun setServiceStarterImpl(serviceStarter: Runnable) {
        serviceStarterImpl = serviceStarter
    }

    fun syncNow() {
        serviceStarterImpl.run()
    }

    fun syncNowIfNotSyncedRecently() {
        if (System.currentTimeMillis() - lastSyncAttempt > 1000 * 60 * 10) syncNow()
    }

    @JvmStatic
    fun clearQueue(context: Context) {
        LockingAsyncExecutor.executeLockedAsync { SynchronizationQueueStorage(context).clearQueue() }
    }

    fun enqueueFeedAddedIfSyncActive(context: Context, downloadUrl: String) {
        if (!isProviderConnected) return

        LockingAsyncExecutor.executeLockedAsync {
            SynchronizationQueueStorage(context).enqueueFeedAdded(downloadUrl)
            syncNow()
        }
    }

    fun enqueueFeedRemovedIfSyncActive(context: Context, downloadUrl: String) {
        if (!isProviderConnected) return

        LockingAsyncExecutor.executeLockedAsync {
            SynchronizationQueueStorage(context).enqueueFeedRemoved(downloadUrl)
            syncNow()
        }
    }

    fun enqueueEpisodeActionIfSyncActive(context: Context, action: EpisodeAction) {
        if (!isProviderConnected) return

        LockingAsyncExecutor.executeLockedAsync {
            SynchronizationQueueStorage(context).enqueueEpisodeAction(action)
            syncNow()
        }
    }

    fun enqueueEpisodePlayedIfSyncActive(context: Context, media: EpisodeMedia, completed: Boolean) {
        if (!isProviderConnected) return

        if (media.episode?.feed == null || media.episode!!.feed!!.isLocalFeed) return
        if (media.startPosition < 0 || (!completed && media.startPosition >= media.getPosition())) return
        val action = EpisodeAction.Builder(media.episode!!, EpisodeAction.PLAY)
            .currentTimestamp()
            .started(media.startPosition / 1000)
            .position((if (completed) media.getDuration() else media.getPosition()) / 1000)
            .total(media.getDuration() / 1000)
            .build()
        enqueueEpisodeActionIfSyncActive(context, action)
    }
}
