package ac.mdiq.podcini.net.sync

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
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
import ac.mdiq.podcini.net.utils.NetworkUtils.isAllowMobileFor
import ac.mdiq.podcini.net.utils.NetworkUtils.setAllowMobileFor
import ac.mdiq.podcini.net.utils.UrlChecker.containsUrl
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Feeds.deleteFeedSync
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.getFeedListDownloadUrls
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.EpisodeUtil.hasAlmostEnded
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.*
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.collection.ArrayMap
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.work.*
import androidx.work.Constraints.Builder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.apache.commons.lang3.StringUtils
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
open class SyncService(context: Context, params: WorkerParameters) : Worker(context, params) {
    val TAG = this::class.simpleName ?: "Anonymous"

    protected val synchronizationQueueStorage = SynchronizationQueueStorage(context)

    @UnstableApi override fun doWork(): Result {
        Logd(TAG, "doWork() called")
        val activeSyncProvider = getActiveSyncProvider() ?: return Result.failure()
        Logd(TAG, "doWork() got syn provider")

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
            EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_success))
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(true)
            return Result.success()
        } catch (e: Exception) {
            EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_error))
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
        Logd(TAG, "syncSubscriptions called")
        val lastSync = SynchronizationSettings.lastSubscriptionSynchronizationTimestamp
        EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_subscriptions))
        val localSubscriptions: List<String> = getFeedListDownloadUrls()
        val subscriptionChanges = syncServiceImpl.getSubscriptionChanges(lastSync)
        var newTimeStamp = subscriptionChanges?.timestamp?:0L

        val queuedRemovedFeeds: MutableList<String> = synchronizationQueueStorage.queuedRemovedFeeds
        var queuedAddedFeeds: List<String> = synchronizationQueueStorage.queuedAddedFeeds

        Logd(TAG, "Downloaded subscription changes: $subscriptionChanges")
        if (subscriptionChanges != null) {
            for (downloadUrl in subscriptionChanges.added) {
                if (!downloadUrl.startsWith("http")) { // Also matches https
                    Logd(TAG, "Skipping url: $downloadUrl")
                    continue
                }
                if (!containsUrl(localSubscriptions, downloadUrl) && !queuedRemovedFeeds.contains(downloadUrl)) {
                    val feed = Feed(downloadUrl, null, "Unknown podcast")
                    feed.episodes.clear()
                    val newFeed = updateFeed(applicationContext, feed, false)
                    runOnce(applicationContext, newFeed)
                }
            }

            // remove subscription if not just subscribed (again)
            for (downloadUrl in subscriptionChanges.removed) {
                if (!queuedAddedFeeds.contains(downloadUrl)) removeFeedWithDownloadUrl(applicationContext, downloadUrl)
            }

            if (lastSync == 0L) {
                Logd(TAG, "First sync. Adding all local subscriptions.")
                queuedAddedFeeds = localSubscriptions.toMutableList()
                queuedAddedFeeds.removeAll(subscriptionChanges.added)
                queuedRemovedFeeds.removeAll(subscriptionChanges.removed)
            }
        }

        if (queuedAddedFeeds.isNotEmpty() || queuedRemovedFeeds.size > 0) {
            Logd(TAG, "Added: " + StringUtils.join(queuedAddedFeeds, ", "))
            Logd(TAG, "Removed: " + StringUtils.join(queuedRemovedFeeds, ", "))

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

    @UnstableApi
    private fun removeFeedWithDownloadUrl(context: Context, downloadUrl: String) {
        Logd(TAG, "removeFeedWithDownloadUrl called")
        var feedID: Long? = null
        val feeds = getFeedList()
        for (f in feeds) {
            val url = f.downloadUrl
            if (url != null && !url.startsWith(Feed.PREFIX_LOCAL_FOLDER)) feedID = f.id
        }
        if (feedID != null) {
            try {
                runBlocking {
                    deleteFeedSync(context, feedID)
                    EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.REMOVED, feedID))
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }
        } else Log.w(TAG, "removeFeedWithDownloadUrl: Could not find feed with url: $downloadUrl")
    }

    private fun waitForDownloadServiceCompleted() {
        Logd(TAG, "waitForDownloadServiceCompleted called")
        EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_wait_for_downloads))
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedUpdatingEvent -> if (!event.isRunning) return@collectLatest
                    else -> {}
                }
            }
            return@launch
        }
        scope.cancel()
    }

    fun getEpisodeActions(syncServiceImpl: ISyncService) : Pair<Long, Long> {
        val lastSync = SynchronizationSettings.lastEpisodeActionSynchronizationTimestamp
        EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_episodes_download))
        val getResponse = syncServiceImpl.getEpisodeActionChanges(lastSync)
        val newTimeStamp = getResponse?.timestamp?:0L
        val remoteActions = getResponse?.episodeActions?: listOf()
        processEpisodeActions(remoteActions)
        return Pair(lastSync, newTimeStamp)
    }

    open fun pushEpisodeActions(syncServiceImpl: ISyncService, lastSync: Long, newTimeStamp_: Long): Long {
        var newTimeStamp = newTimeStamp_
        EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_episodes_upload))
        val queuedEpisodeActions: MutableList<EpisodeAction> = synchronizationQueueStorage.queuedEpisodeActions
        if (lastSync == 0L) {
            EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_upload_played))
            val readItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.played.name), EpisodeSortOrder.DATE_NEW_OLD)
            Logd(TAG, "First sync. Upload state for all " + readItems.size + " played episodes")
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
                Logd(TAG, "Uploading ${queuedEpisodeActions.size} actions: ${StringUtils.join(queuedEpisodeActions, ", ")}")
                val postResponse = syncServiceImpl.uploadEpisodeActions(queuedEpisodeActions)
                newTimeStamp = postResponse?.timestamp?:0L
                Logd(TAG, "Upload episode response: $postResponse")
                synchronizationQueueStorage.clearEpisodeActionQueue()
            } finally {
                LockingAsyncExecutor.lock.unlock()
            }
        }
        return newTimeStamp
    }

    @UnstableApi @Throws(SyncServiceException::class)
    private fun syncEpisodeActions(syncServiceImpl: ISyncService) {
        Logd(TAG, "syncEpisodeActions called")
        var (lastSync, newTimeStamp) = getEpisodeActions(syncServiceImpl)

        // upload local actions
        newTimeStamp = pushEpisodeActions(syncServiceImpl, lastSync, newTimeStamp)
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(newTimeStamp)
    }

    open fun processEpisodeAction(action: EpisodeAction): Pair<Long, Episode>? {
        val guid = if (isValidGuid(action.guid)) action.guid else null
        val feedItem = getEpisodeByGuidOrUrl(guid, action.episode?:"")
        if (feedItem == null) {
            Logd(TAG, "Unknown feed item: $action")
            return null
        }
        if (feedItem.media == null) {
            Logd(TAG, "Feed item has no media: $action")
            return null
        }
        var idRemove: Long? = null
        feedItem.media!!.setPosition(action.position * 1000)
        if (hasAlmostEnded(feedItem.media!!)) {
            Logd(TAG, "Marking as played: $action")
            feedItem.setPlayed(true)
            feedItem.media!!.setPosition(0)
            idRemove = feedItem.id
        } else Logd(TAG, "Setting position: $action")

        return if (idRemove != null) Pair(idRemove, feedItem) else null
    }

    @UnstableApi @Synchronized
    fun processEpisodeActions(remoteActions: List<EpisodeAction>) {
        Logd(TAG, "Processing " + remoteActions.size + " actions")
        if (remoteActions.isEmpty()) return

        val playActionsToUpdate = getRemoteActionsOverridingLocalActions(remoteActions, synchronizationQueueStorage.queuedEpisodeActions)
//        val queueToBeRemoved = mutableListOf<FeedItem>()
        val updatedItems: MutableList<Episode> = ArrayList()
        for (action in playActionsToUpdate.values) {
            val result = processEpisodeAction(action) ?: continue
//            if (result.first != null) queueToBeRemoved.add(result.second)
            updatedItems.add(result.second)
        }
        removeFromQueue(*updatedItems.toTypedArray())
        runOnIOScope {
            for (episode in updatedItems) {
                upsert(episode) {}
            }
            EventFlow.postEvent(FlowEvent.EpisodeEvent(updatedItems))
        }
    }

    protected fun clearErrorNotifications() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(R.id.notification_gpodnet_sync_error)
        nm.cancel(R.id.notification_gpodnet_sync_autherror)
    }

    fun gpodnetNotificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= 26) return true // System handles notification preferences
        return appPrefs.getBoolean(UserPreferences.Prefs.pref_gpodnet_notifications.name, true)
    }

    protected fun updateErrorNotification(exception: Exception) {
        Logd(TAG, "Posting sync error notification")
        val description = ("${applicationContext.getString(R.string.gpodnetsync_error_descr)}${exception.message}")

        if (!gpodnetNotificationsEnabled()) {
            Logd(TAG, "Skipping sync error notification because of user setting")
            return
        }
//        TODO:
//        if (EventBus.getDefault().hasSubscriberForEvent(FlowEvent.MessageEvent::class.java)) {
//            EventFlow.postEvent(FlowEvent.MessageEvent(description))
//            return
//        }

        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getActivity(applicationContext,
            R.id.pending_intent_sync_error, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext,
            NotificationUtils.CHANNEL_ID.sync_error.name)
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
            SynchronizationProviderViewData.GPODDER_NET -> GpodnetService(getHttpClient(), hosturl, deviceID?:"", username?:"", password?:"")
            SynchronizationProviderViewData.NEXTCLOUD_GPODDER -> NextcloudSyncService(getHttpClient(), hosturl, username?:"", password?:"")
        }
    }

    companion object {
        private val TAG = SyncService::class.simpleName ?: "Anonymous"

        private const val WORK_ID_SYNC = "SyncServiceWorkId"

        private var isCurrentlyActive = false
        internal fun setCurrentlyActive(active: Boolean) {
            isCurrentlyActive = active
        }
        var isAllowMobileSync: Boolean
            get() = isAllowMobileFor("sync")
            set(allow) {
                setAllowMobileFor("sync", allow)
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
                EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_started))
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
