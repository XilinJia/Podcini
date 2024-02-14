package ac.mdiq.podvinci.core.sync.queue

import android.content.Context
import ac.mdiq.podvinci.core.sync.LockingAsyncExecutor
import ac.mdiq.podvinci.core.sync.SynchronizationSettings
import ac.mdiq.podvinci.model.feed.FeedMedia
import ac.mdiq.podvinci.net.sync.model.EpisodeAction

object SynchronizationQueueSink {
    // To avoid a dependency loop of every class to SyncService, and from SyncService back to every class.
    private var serviceStarterImpl = Runnable {}

    fun setServiceStarterImpl(serviceStarter: Runnable) {
        serviceStarterImpl = serviceStarter
    }

    fun syncNow() {
        serviceStarterImpl.run()
    }

    fun syncNowIfNotSyncedRecently() {
        if (System.currentTimeMillis() - SynchronizationSettings.lastSyncAttempt > 1000 * 60 * 10) {
            syncNow()
        }
    }

    @JvmStatic
    fun clearQueue(context: Context) {
        LockingAsyncExecutor.executeLockedAsync { SynchronizationQueueStorage(context).clearQueue() }
    }

    fun enqueueFeedAddedIfSynchronizationIsActive(context: Context, downloadUrl: String) {
        if (!SynchronizationSettings.isProviderConnected) {
            return
        }
        LockingAsyncExecutor.executeLockedAsync {
            SynchronizationQueueStorage(context).enqueueFeedAdded(downloadUrl)
            syncNow()
        }
    }

    fun enqueueFeedRemovedIfSynchronizationIsActive(context: Context, downloadUrl: String) {
        if (!SynchronizationSettings.isProviderConnected) {
            return
        }
        LockingAsyncExecutor.executeLockedAsync {
            SynchronizationQueueStorage(context).enqueueFeedRemoved(downloadUrl)
            syncNow()
        }
    }

    fun enqueueEpisodeActionIfSynchronizationIsActive(context: Context, action: EpisodeAction) {
        if (!SynchronizationSettings.isProviderConnected) {
            return
        }
        LockingAsyncExecutor.executeLockedAsync {
            SynchronizationQueueStorage(context).enqueueEpisodeAction(action)
            syncNow()
        }
    }

    fun enqueueEpisodePlayedIfSynchronizationIsActive(context: Context, media: FeedMedia,
                                                      completed: Boolean
    ) {
        if (!SynchronizationSettings.isProviderConnected) {
            return
        }
        if (media.getItem()?.feed == null || media.getItem()!!.feed!!.isLocalFeed) {
            return
        }
        if (media.startPosition < 0 || (!completed && media.startPosition >= media.getPosition())) {
            return
        }
        val action = EpisodeAction.Builder(media.getItem()!!, EpisodeAction.PLAY)
            .currentTimestamp()
            .started(media.startPosition / 1000)
            .position((if (completed) media.getDuration() else media.getPosition()) / 1000)
            .total(media.getDuration() / 1000)
            .build()
        enqueueEpisodeActionIfSynchronizationIsActive(context, action)
    }
}
