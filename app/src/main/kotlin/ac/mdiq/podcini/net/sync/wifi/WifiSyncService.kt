package ac.mdiq.podcini.net.sync.wifi

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.sync.LockingAsyncExecutor
import ac.mdiq.podcini.net.sync.LockingAsyncExecutor.executeLockedAsync
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.model.*
import ac.mdiq.podcini.net.sync.model.EpisodeAction.Companion.readFromJsonObject
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.storage.database.Episodes.hasAlmostEnded
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat.getString
import androidx.work.*
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

class WifiSyncService(val context: Context, params: WorkerParameters) : SyncService(context, params), ISyncService {

    var loginFail = false

    override fun doWork(): Result {
        Logd(TAG, "doWork() called")

        SynchronizationSettings.updateLastSynchronizationAttempt()
        setCurrentlyActive(true)

        login()

        if (socket != null && !loginFail) {
            if (isGuest) {
                Thread.sleep(1000)
//                TODO: not using lastSync
                val lastSync = SynchronizationSettings.lastEpisodeActionSynchronizationTimestamp
                val newTimeStamp = pushEpisodeActions(this, 0L, System.currentTimeMillis())
                SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(newTimeStamp)
                EventFlow.postEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_in_progress, "50"))
                sendToPeer("AllSent", "AllSent")

                var receivedBye = false
                while (!receivedBye) {
                    try { receivedBye = receiveFromPeer()
                    } catch (e: SocketTimeoutException) {
                        Log.e("Guest", getString(context, R.string.sync_error_host_not_respond))
                        logout()
                        EventFlow.postEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_in_progress, "100"))
                        EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_error, getString(context, R.string.sync_error_host_not_respond)))
                        SynchronizationSettings.setLastSynchronizationAttemptSuccess(false)
                        return Result.failure()
                    }
                }
            } else {
                var receivedBye = false
                while (!receivedBye) {
                    try { receivedBye = receiveFromPeer()
                    } catch (e: SocketTimeoutException) {
                        Log.e("Host", getString(context, R.string.sync_error_guest_not_respond))
                        logout()
                        EventFlow.postEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_in_progress, "100"))
                        EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_error, getString(context, R.string.sync_error_guest_not_respond)))
                        SynchronizationSettings.setLastSynchronizationAttemptSuccess(false)
                        return Result.failure()
                    }
                }
                EventFlow.postEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_in_progress, "50"))
                //                TODO: not using lastSync
                val lastSync = SynchronizationSettings.lastEpisodeActionSynchronizationTimestamp
                val newTimeStamp = pushEpisodeActions(this, 0L, System.currentTimeMillis())
                SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(newTimeStamp)
                sendToPeer("AllSent", "AllSent")
            }
        } else {
            logout()
            EventFlow.postEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_in_progress, "100"))
            EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_error, "Login failure"))
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(false)
            return Result.failure()
        }

        logout()
        EventFlow.postEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_in_progress, "100"))
        EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_success))
        SynchronizationSettings.setLastSynchronizationAttemptSuccess(true)
        return Result.success()
    }

    private var socket: Socket? = null

     override fun login() {
        Logd(TAG, "serverIp: $hostIp serverPort: $hostPort $isGuest")
        EventFlow.postEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_in_progress, "2"))
        if (!isPortInUse(hostPort)) {
            if (isGuest) {
                val maxTries = 120
                var numTries = 0
                while (numTries < maxTries) {
                    try {
                        socket = Socket(hostIp, hostPort)
                        break
                    } catch (e: ConnectException) { Thread.sleep(1000) }
                    numTries++
                }
                if (numTries >= maxTries) loginFail = true
                if (socket != null) {
                    sendToPeer("Hello", "Hello, Server!")
                    receiveFromPeer()
                }
            } else {
                try {
                    if (serverSocket == null) serverSocket = ServerSocket(hostPort)
                    serverSocket!!.soTimeout = 120000
                    try {
                        socket = serverSocket!!.accept()
                        while (true) {
                            Logd(TAG, "waiting for guest message")
                            try {
                                receiveFromPeer()
                                sendToPeer("Hello", "Hello, Client")
                                break
                            } catch (e: SocketTimeoutException) {
                                Log.e("Server", "Guest not responding in 120 seconds, giving up")
                                loginFail = true
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Server", "No guest connecing in 120 seconds, giving up")
                        loginFail = true
                    }
                } catch (e: BindException) {
                    Log.e("Server", "Failed to start server: Port $hostPort already in use")
                    loginFail = true
                }
            }
        } else {
            Logd(TAG, "port $hostPort in use, ignored")
            loginFail = true
        }
        EventFlow.postEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_in_progress, "5"))
    }

     private fun isPortInUse(port: Int): Boolean {
        val command = "netstat -tlnp"
        val process = Runtime.getRuntime().exec(command)
        val output = process.inputStream.bufferedReader().use { it.readText() }
//        Log.d(TAG, "isPortInUse: $output")
        return output.contains(":$port") // Check if output contains the port
    }

    private fun sendToPeer(messageType: String, message: String) {
        val writer = PrintWriter(socket!!.getOutputStream(), true)
        writer.println("$messageType|$message")
    }

    @Throws(SocketTimeoutException::class)
    private fun receiveFromPeer() : Boolean {
        val reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
        val message: String?
        socket!!.soTimeout = 120000
        try { message = reader.readLine() } catch (e: SocketTimeoutException) { throw e }
        if (message != null) {
            val parts = message.split("|")
            if (parts.size == 2) {
                val messageType = parts[0]
                val messageData = parts[1]
                // Process the message based on the type
                when (messageType) {
                    "Hello" -> Logd(TAG, "Received Hello message: $messageData")
                    "EpisodeActions" -> {
                        val remoteActions = mutableListOf<EpisodeAction>()
                        val jsonArray = JSONArray(messageData)
                        for (i in 0 until jsonArray.length()) {
                            val jsonAction = jsonArray.getJSONObject(i)

                            Logd(TAG, "Received EpisodeActions message: $i $jsonAction")
                            val action = readFromJsonObject(jsonAction)
                            if (action != null) remoteActions.add(action)
                        }
                        processEpisodeActions(remoteActions)
                    }
                    "AllSent" -> {
                        Logd(TAG, "Received AllSent message: $messageData")
                        return true
                    }
                    else -> Logd(TAG, "Received unknown message: $messageData")
                }
            }
        }
        return false
    }

    @Throws(SyncServiceException::class)
    override fun getSubscriptionChanges(lastSync: Long): SubscriptionChanges? {
        Logd(TAG, "getSubscriptionChanges does nothing")
        return null
    }

    @Throws(SyncServiceException::class)
    override fun uploadSubscriptionChanges(added: List<String>, removed: List<String>): UploadChangesResponse? {
        Logd(TAG, "uploadSubscriptionChanges does nothing")
        return null
    }

    @Throws(SyncServiceException::class)
    override fun getEpisodeActionChanges(timestamp: Long): EpisodeActionChanges? {
        Logd(TAG, "getEpisodeActionChanges does nothing")
        return null
    }

    override fun pushEpisodeActions(syncServiceImpl: ISyncService, lastSync: Long, newTimeStamp_: Long): Long {
        var newTimeStamp = newTimeStamp_
        EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_episodes_upload))
        val queuedEpisodeActions: MutableList<EpisodeAction> = synchronizationQueueStorage.queuedEpisodeActions
        Logd(TAG, "pushEpisodeActions queuedEpisodeActions: ${queuedEpisodeActions.size}")

        if (lastSync == 0L) {
            EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_upload_played))
//            only push downloaded items
            val pausedItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.paused.name), EpisodeSortOrder.DATE_NEW_OLD)
            val readItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.played.name), EpisodeSortOrder.DATE_NEW_OLD)
            val favoriteItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_NEW_OLD)
            val comItems = mutableSetOf<Episode>()
            comItems.addAll(pausedItems)
            comItems.addAll(readItems)
            comItems.addAll(favoriteItems)
            Logd(TAG, "First sync. Upload state for all " + comItems.size + " played episodes")
            for (item in comItems) {
                val media = item.media ?: continue
                val played = EpisodeAction.Builder(item, EpisodeAction.PLAY)
                    .timestamp(Date(media.getLastPlayedTime()))
                    .started(media.startPosition / 1000)
                    .position(media.getPosition() / 1000)
                    .playedDuration(media.playedDuration / 1000)
                    .total(media.getDuration() / 1000)
                    .isFavorite(item.isSUPER)
                    .playState(item.playState)
                    .build()
                queuedEpisodeActions.add(played)
            }
        }
        if (queuedEpisodeActions.isNotEmpty()) {
            LockingAsyncExecutor.lock.lock()
            try {
                Logd(TAG, "Uploading ${queuedEpisodeActions.size} actions: ${StringUtils.join(queuedEpisodeActions, ", ")}")
                val postResponse = uploadEpisodeActions(queuedEpisodeActions)
                newTimeStamp = postResponse.timestamp
                Logd(TAG, "Upload episode response: $postResponse")
                synchronizationQueueStorage.clearEpisodeActionQueue()
            } finally { LockingAsyncExecutor.lock.unlock() }
        }
        return newTimeStamp
    }

    @Throws(SyncServiceException::class)
    override fun uploadEpisodeActions(queuedEpisodeActions: List<EpisodeAction>): UploadChangesResponse {
//        Log.d(TAG, "uploadEpisodeActions called")
        var i = 0
        while (i < queuedEpisodeActions.size) {
            uploadEpisodeActionsPartial(queuedEpisodeActions, i, min(queuedEpisodeActions.size.toDouble(), (i + UPLOAD_BULK_SIZE).toDouble()).toInt())
            i += UPLOAD_BULK_SIZE
            Thread.sleep(1000)
        }
        return WifiEpisodeActionPostResponse(System.currentTimeMillis() / 1000)
    }

    @Throws(SyncServiceException::class)
    private fun uploadEpisodeActionsPartial(queuedEpisodeActions: List<EpisodeAction>, from: Int, to: Int) {
//        Log.d(TAG, "uploadEpisodeActionsPartial called")
        try {
            val list = JSONArray()
            for (i in from until to) {
                val episodeAction = queuedEpisodeActions[i]
                val obj = episodeAction.writeToJsonObject()
                if (obj != null) {
                    Logd(TAG, "sending EpisodeAction: $obj")
                    list.put(obj)
                }
            }
            sendToPeer("EpisodeActions", list.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            throw SyncServiceException(e)
        }
    }

    override fun processEpisodeAction(action: EpisodeAction): Pair<Long, Episode>? {
        val guid = if (isValidGuid(action.guid)) action.guid else null
        var feedItem = getEpisodeByGuidOrUrl(guid, action.episode?:"", false)
        if (feedItem == null) {
            Logd(TAG, "Unknown feed item: $action")
            return null
        }
        if (feedItem.media == null) {
            Logd(TAG, "Feed item has no media: $action")
            return null
        }
//        feedItem.media = getFeedMedia(feedItem.media!!.id)
        var idRemove: Long? = null
        Logd(TAG, "processEpisodeAction ${feedItem.media!!.getLastPlayedTime()} ${(action.timestamp?.time?:0L)} ${action.position} ${feedItem.title}")
        if (feedItem.media!!.getLastPlayedTime() < (action.timestamp?.time?:0L)) {
            feedItem = upsertBlk(feedItem) {
                it.media!!.startPosition = action.started * 1000
                it.media!!.setPosition(action.position * 1000)
                it.media!!.playedDuration = action.playedDuration * 1000
                it.media!!.setLastPlayedTime(action.timestamp!!.time)
                it.rating = if (action.isFavorite) Rating.SUPER.code else Rating.UNRATED.code
                it.playState = action.playState
                if (hasAlmostEnded(it.media!!)) {
                    Logd(TAG, "Marking as played")
                    it.setPlayed(true)
                    it.media!!.setPosition(0)
                    idRemove = it.id
                } else Logd(TAG, "Setting position")
            }
            EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(feedItem))
        } else Logd(TAG, "local is newer, no change")
        return if (idRemove != null) Pair(idRemove!!, feedItem) else null
    }

    override fun logout() {
        socket?.close()
    }

    private class WifiEpisodeActionPostResponse(epochSecond: Long) : UploadChangesResponse(epochSecond)

    companion object {
        private const val WORK_ID_SYNC = "SyncServiceWorkId"
        private const val UPLOAD_BULK_SIZE = 30

        var serverSocket:  ServerSocket? = null
        var isGuest = false
        var hostIp : String = ""
        var hostPort: Int = 54628

        private var isCurrentlyActive = false
        internal fun setCurrentlyActive(active: Boolean) {
            isCurrentlyActive = active
        }

        fun startInstantSync(context: Context, hostPort_: Int = 54628, hostIp_: String="", isGuest_: Boolean = false) {
            hostIp = hostIp_
            isGuest = isGuest_
            hostPort = hostPort_
            executeLockedAsync {
                SynchronizationSettings.resetTimestamps()
                val builder: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(WifiSyncService::class.java)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)

                // Give it some time, so other possible actions can be queued.
                builder.setInitialDelay(20L, TimeUnit.SECONDS)
                EventFlow.postStickyEvent(FlowEvent.SyncServiceEvent(R.string.sync_status_started))

                val workRequest: OneTimeWorkRequest = builder.setInitialDelay(0L, TimeUnit.SECONDS).build()
                WorkManager.getInstance(context).enqueueUniqueWork(hostIp_, ExistingWorkPolicy.REPLACE, workRequest)
            }
        }
    }
}