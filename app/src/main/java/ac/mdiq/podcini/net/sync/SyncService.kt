package ac.mdiq.podcini.net.sync

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.common.UrlChecker.containsUrl
import ac.mdiq.podcini.net.download.FeedUpdateManager.runOnce
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.sync.LockingAsyncExecutor.executeLockedAsync
import ac.mdiq.podcini.net.sync.SynchronizationCredentials.deviceID
import ac.mdiq.podcini.net.sync.SynchronizationCredentials.hosturl
import ac.mdiq.podcini.net.sync.SynchronizationCredentials.password
import ac.mdiq.podcini.net.sync.SynchronizationCredentials.username
import ac.mdiq.podcini.net.sync.SynchronizationProviderViewData.Companion.fromIdentifier
import ac.mdiq.podcini.net.sync.gpoddernet.GpodnetService
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.ISyncService
import ac.mdiq.podcini.net.sync.model.SyncServiceException
import ac.mdiq.podcini.net.sync.nextcloud.NextcloudSyncService
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueStorage
import ac.mdiq.podcini.preferences.UserPreferences.gpodnetNotificationsEnabled
import ac.mdiq.podcini.preferences.UserPreferences.isAllowMobileSync
import ac.mdiq.podcini.storage.DBReader.getEpisodes
import ac.mdiq.podcini.storage.DBReader.getFeedItemByGuidOrEpisodeUrl
import ac.mdiq.podcini.storage.DBReader.getFeedListDownloadUrls
import ac.mdiq.podcini.storage.DBReader.loadAdditionalFeedItemListData
import ac.mdiq.podcini.storage.DBTasks.removeFeedWithDownloadUrl
import ac.mdiq.podcini.storage.DBTasks.updateFeed
import ac.mdiq.podcini.storage.DBWriter.persistItemList
import ac.mdiq.podcini.storage.DBWriter.removeQueueItem
import ac.mdiq.podcini.storage.model.feed.*
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.util.FeedItemUtil.hasAlmostEnded
import ac.mdiq.podcini.util.LongList
import ac.mdiq.podcini.util.event.FeedUpdateRunningEvent
import ac.mdiq.podcini.util.event.MessageEvent
import ac.mdiq.podcini.util.event.SyncServiceEvent
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.collection.ArrayMap
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.work.*
import androidx.work.Constraints.Builder
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
open class SyncService(context: Context, params: WorkerParameters) : Worker(context, params) {
    protected val synchronizationQueueStorage = SynchronizationQueueStorage(context)

    @UnstableApi override fun doWork(): Result {
        Log.d(TAG, "doWork() called")
        val activeSyncProvider = getActiveSyncProvider() ?: return Result.failure()

        SynchronizationSettings.updateLastSynchronizationAttempt()
        setCurrentlyActive(true)
        try {
            activeSyncProvider.login()
            syncSubscriptions(activeSyncProvider)
            waitForDownloadServiceCompleted()

//            sync Episode changes
//            syncEpisodeActions(activeSyncProvider)
            var (lastSync, newTimeStamp) = getEpisodeActions(activeSyncProvider)
            // upload local actions
            newTimeStamp = pushEpisodeActions(activeSyncProvider, lastSync, newTimeStamp)
            SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(newTimeStamp)

            activeSyncProvider.logout()
            clearErrorNotifications()
            EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_success))
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(true)
            return Result.success()
        } catch (e: Exception) {
            EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_error))
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(false)
            Log.e(TAG, Log.getStackTraceString(e))

            if (e is SyncServiceException) {
                // Do not spam users with notification and retry before notifying
                if (runAttemptCount % 3 == 2) updateErrorNotification(e)
                return Result.retry()
            } else {
                updateErrorNotification(e)
                return Result.failure()
            }
        } finally {
            setCurrentlyActive(false)
        }
    }

    @UnstableApi @Throws(SyncServiceException::class)
    private fun syncSubscriptions(syncServiceImpl: ISyncService) {
        Log.d(TAG, "syncSubscriptions called")
        val lastSync = SynchronizationSettings.lastSubscriptionSynchronizationTimestamp
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_subscriptions))
        val localSubscriptions: List<String> = getFeedListDownloadUrls()
        val subscriptionChanges = syncServiceImpl.getSubscriptionChanges(lastSync)
        var newTimeStamp = subscriptionChanges?.timestamp?:0L

        val queuedRemovedFeeds: MutableList<String> = synchronizationQueueStorage.queuedRemovedFeeds
        var queuedAddedFeeds: List<String> = synchronizationQueueStorage.queuedAddedFeeds

        Log.d(TAG, "Downloaded subscription changes: $subscriptionChanges")
        if (subscriptionChanges != null) {
            for (downloadUrl in subscriptionChanges.added) {
                if (!downloadUrl.startsWith("http")) { // Also matches https
                    Log.d(TAG, "Skipping url: $downloadUrl")
                    continue
                }
                if (!containsUrl(localSubscriptions, downloadUrl) && !queuedRemovedFeeds.contains(downloadUrl)) {
                    val feed = Feed(downloadUrl, null, "Unknown podcast")
                    feed.items = mutableListOf()
                    val newFeed = updateFeed(applicationContext, feed, false)
                    runOnce(applicationContext, newFeed)
                }
            }

            // remove subscription if not just subscribed (again)
            for (downloadUrl in subscriptionChanges.removed) {
                if (!queuedAddedFeeds.contains(downloadUrl)) removeFeedWithDownloadUrl(applicationContext, downloadUrl)
            }

            if (lastSync == 0L) {
                Log.d(TAG, "First sync. Adding all local subscriptions.")
                queuedAddedFeeds = localSubscriptions.toMutableList()
                queuedAddedFeeds.removeAll(subscriptionChanges.added)
                queuedRemovedFeeds.removeAll(subscriptionChanges.removed)
            }
        }

        if (queuedAddedFeeds.isNotEmpty() || queuedRemovedFeeds.size > 0) {
            Log.d(TAG, "Added: " + StringUtils.join(queuedAddedFeeds, ", "))
            Log.d(TAG, "Removed: " + StringUtils.join(queuedRemovedFeeds, ", "))

            LockingAsyncExecutor.lock.lock()
            try {
                val uploadResponse = syncServiceImpl.uploadSubscriptionChanges(queuedAddedFeeds, queuedRemovedFeeds)
                synchronizationQueueStorage.clearFeedQueues()
                if (uploadResponse != null) newTimeStamp = uploadResponse.timestamp
            } finally {
                LockingAsyncExecutor.lock.unlock()
            }
        }
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(newTimeStamp)
    }

    private fun waitForDownloadServiceCompleted() {
        Log.d(TAG, "waitForDownloadServiceCompleted called")
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_wait_for_downloads))
        try {
            while (true) {
                Thread.sleep(1000)
                val event = EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent::class.java)
                if (event == null || !event.isFeedUpdateRunning) return
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun getEpisodeActions(syncServiceImpl: ISyncService) : Pair<Long, Long> {
        val lastSync = SynchronizationSettings.lastEpisodeActionSynchronizationTimestamp
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_episodes_download))
        val getResponse = syncServiceImpl.getEpisodeActionChanges(lastSync)
        val newTimeStamp = getResponse?.timestamp?:0L
        val remoteActions = getResponse?.episodeActions?: listOf()
        processEpisodeActions(remoteActions)
        return Pair(lastSync, newTimeStamp)
    }

    open fun pushEpisodeActions(syncServiceImpl: ISyncService, lastSync: Long, newTimeStamp_: Long): Long {
        var newTimeStamp = newTimeStamp_
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_episodes_upload))
        val queuedEpisodeActions: MutableList<EpisodeAction> = synchronizationQueueStorage.queuedEpisodeActions
        if (lastSync == 0L) {
            EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_upload_played))
            val readItems = getEpisodes(0, Int.MAX_VALUE, FeedItemFilter(FeedItemFilter.PLAYED), SortOrder.DATE_NEW_OLD)
            Log.d(TAG, "First sync. Upload state for all " + readItems.size + " played episodes")
            for (item in readItems) {
                val media = item.media ?: continue
                val played = EpisodeAction.Builder(item, EpisodeAction.PLAY)
                    .currentTimestamp()
                    .started(media.getDuration() / 1000)
                    .position(media.getPosition() / 1000)
                    .total(media.getDuration() / 1000)
                    .build()
                queuedEpisodeActions.add(played)
            }
        }
        if (queuedEpisodeActions.isNotEmpty()) {
            LockingAsyncExecutor.lock.lock()
            try {
                Log.d(TAG, "Uploading ${queuedEpisodeActions.size} actions: ${StringUtils.join(queuedEpisodeActions, ", ")}")
                val postResponse = syncServiceImpl.uploadEpisodeActions(queuedEpisodeActions)
                newTimeStamp = postResponse?.timestamp?:0L
                Log.d(TAG, "Upload episode response: $postResponse")
                synchronizationQueueStorage.clearEpisodeActionQueue()
            } finally {
                LockingAsyncExecutor.lock.unlock()
            }
        }
        return newTimeStamp
    }

    @UnstableApi @Throws(SyncServiceException::class)
    private fun syncEpisodeActions(syncServiceImpl: ISyncService) {
        Log.d(TAG, "syncEpisodeActions called")
        var (lastSync, newTimeStamp) = getEpisodeActions(syncServiceImpl)

        // upload local actions
        newTimeStamp = pushEpisodeActions(syncServiceImpl, lastSync, newTimeStamp)
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(newTimeStamp)
    }

    open fun processEpisodeAction(action: EpisodeAction): Pair<Long, FeedItem>? {
        val guid = if (isValidGuid(action.guid)) action.guid else null
        val feedItem = getFeedItemByGuidOrEpisodeUrl(guid, action.episode?:"")
        if (feedItem == null) {
            Log.i(TAG, "Unknown feed item: $action")
            return null
        }
        if (feedItem.media == null) {
            Log.i(TAG, "Feed item has no media: $action")
            return null
        }
        var idRemove = 0L
        feedItem.media!!.setPosition(action.position * 1000)
        if (hasAlmostEnded(feedItem.media!!)) {
            Log.d(TAG, "Marking as played: $action")
            feedItem.setPlayed(true)
            feedItem.media!!.setPosition(0)
            idRemove = feedItem.id
        } else Log.d(TAG, "Setting position: $action")

        return Pair(idRemove, feedItem)
    }

    @UnstableApi @Synchronized
    fun processEpisodeActions(remoteActions: List<EpisodeAction>) {
        Log.d(TAG, "Processing " + remoteActions.size + " actions")
        if (remoteActions.isEmpty()) return

        val playActionsToUpdate = getRemoteActionsOverridingLocalActions(remoteActions, synchronizationQueueStorage.queuedEpisodeActions)
        val queueToBeRemoved = LongList()
        val updatedItems: MutableList<FeedItem> = ArrayList()
        for (action in playActionsToUpdate.values) {
            val result = processEpisodeAction(action) ?: continue
            if (result.first != 0L) queueToBeRemoved.add(result.first)
            updatedItems.add(result.second)
        }
        removeQueueItem(applicationContext, false, *queueToBeRemoved.toArray())
        loadAdditionalFeedItemListData(updatedItems)
        persistItemList(updatedItems)
    }

    protected fun clearErrorNotifications() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(R.id.notification_gpodnet_sync_error)
        nm.cancel(R.id.notification_gpodnet_sync_autherror)
    }

    protected fun updateErrorNotification(exception: Exception) {
        Log.d(TAG, "Posting sync error notification")
        val description = ("${applicationContext.getString(R.string.gpodnetsync_error_descr)}${exception.message}")

        if (!gpodnetNotificationsEnabled()) {
            Log.d(TAG, "Skipping sync error notification because of user setting")
            return
        }
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent::class.java)) {
            EventBus.getDefault().post(MessageEvent(description))
            return
        }

        val intent = applicationContext.packageManager.getLaunchIntentForPackage(
            applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(applicationContext,
            R.id.pending_intent_sync_error, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext,
            NotificationUtils.CHANNEL_ID_SYNC_ERROR)
            .setContentTitle(applicationContext.getString(R.string.gpodnetsync_error_title))
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_notification_sync_error)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(R.id.notification_gpodnet_sync_error, notification)
    }

    private fun getActiveSyncProvider(): ISyncService? {
        val selectedSyncProviderKey = SynchronizationSettings.selectedSyncProviderKey
        val selectedService = fromIdentifier(selectedSyncProviderKey?:"")
        if (selectedService == null) return null

        return when (selectedService) {
//            SynchronizationProviderViewData.WIFI -> {
////                if (hosturl != null) WifiImplSyncService(hosturl!!, hostport)
////                else null
//                null
//            }
            SynchronizationProviderViewData.GPODDER_NET -> GpodnetService(getHttpClient(), hosturl, deviceID?:"", username?:"", password?:"")
            SynchronizationProviderViewData.NEXTCLOUD_GPODDER -> NextcloudSyncService(getHttpClient(), hosturl, username?:"", password?:"")
        }
    }

    companion object {
        const val TAG: String = "SyncService"
        private const val WORK_ID_SYNC = "SyncServiceWorkId"

        private var isCurrentlyActive = false
        internal fun setCurrentlyActive(active: Boolean) {
            isCurrentlyActive = active
        }

        private fun getWorkRequest(): OneTimeWorkRequest.Builder {
            val constraints = Builder()
            if (isAllowMobileSync) constraints.setRequiredNetworkType(NetworkType.CONNECTED)
            else constraints.setRequiredNetworkType(NetworkType.UNMETERED)

            val builder: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(SyncService::class.java)
                .setConstraints(constraints.build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)

            if (isCurrentlyActive) {
                // Debounce: don't start sync again immediately after it was finished.
                builder.setInitialDelay(2L, TimeUnit.MINUTES)
            } else {
                // Give it some time, so other possible actions can be queued.
                builder.setInitialDelay(20L, TimeUnit.SECONDS)
                EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_started))
            }
            return builder
        }

        fun sync(context: Context) {
            val workRequest: OneTimeWorkRequest = getWorkRequest().build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_SYNC, ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun syncImmediately(context: Context) {
            val workRequest: OneTimeWorkRequest = getWorkRequest()
                .setInitialDelay(0L, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_SYNC, ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun fullSync(context: Context) {
            executeLockedAsync {
                SynchronizationSettings.resetTimestamps()
                val workRequest: OneTimeWorkRequest = getWorkRequest()
                    .setInitialDelay(0L, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_SYNC, ExistingWorkPolicy.REPLACE, workRequest)
            }
        }

        fun getRemoteActionsOverridingLocalActions(remoteActions: List<EpisodeAction>, queuedEpisodeActions: List<EpisodeAction>): Map<Pair<String, String>, EpisodeAction> {
            // make sure more recent local actions are not overwritten by older remote actions
            val remoteActionsThatOverrideLocalActions: MutableMap<Pair<String, String>, EpisodeAction> = ArrayMap()
            val localMostRecentPlayActions = createUniqueLocalMostRecentPlayActions(queuedEpisodeActions)
            for (remoteAction in remoteActions) {
                if (remoteAction.podcast == null || remoteAction.episode == null) continue
                val key = Pair(remoteAction.podcast, remoteAction.episode)
                when (remoteAction.action) {
                    EpisodeAction.Action.NEW, EpisodeAction.Action.DOWNLOAD -> {}
                    EpisodeAction.Action.PLAY -> {
                        val localMostRecent = localMostRecentPlayActions[key]
                        if (secondActionOverridesFirstAction(remoteAction, localMostRecent)) break
                        val remoteMostRecentAction = remoteActionsThatOverrideLocalActions[key]
                        if (secondActionOverridesFirstAction(remoteAction, remoteMostRecentAction)) break
                        remoteActionsThatOverrideLocalActions[key] = remoteAction
                    }
                    EpisodeAction.Action.DELETE -> {}
                    else -> Log.e(TAG, "Unknown remoteAction: $remoteAction")
                }
            }

            return remoteActionsThatOverrideLocalActions
        }

        private fun createUniqueLocalMostRecentPlayActions(queuedEpisodeActions: List<EpisodeAction>): Map<Pair<String, String>, EpisodeAction> {
            val localMostRecentPlayAction: MutableMap<Pair<String, String>, EpisodeAction> = ArrayMap()
            for (action in queuedEpisodeActions) {
                if (action.podcast == null || action.episode == null) continue
                val key = Pair(action.podcast, action.episode)
                val mostRecent = localMostRecentPlayAction[key]
                when {
                    mostRecent?.timestamp == null -> localMostRecentPlayAction[key] = action
                    mostRecent.timestamp.before(action.timestamp) -> localMostRecentPlayAction[key] = action
                }
            }
            return localMostRecentPlayAction
        }

        private fun secondActionOverridesFirstAction(firstAction: EpisodeAction, secondAction: EpisodeAction?): Boolean {
            return secondAction?.timestamp != null && (firstAction.timestamp == null || secondAction.timestamp.after(firstAction.timestamp))
        }

        fun isValidGuid(guid: String?): Boolean {
            return (guid != null && guid.trim { it <= ' ' }.isNotEmpty() && guid != "null")
        }
    }
}
