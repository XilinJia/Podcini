package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeMedia
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.CurrentState.Companion.NO_MEDIA_PLAYING
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_OTHER
import ac.mdiq.podcini.util.Logd
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.*

object InTheatre {
    val TAG: String = InTheatre::class.simpleName ?: "Anonymous"

    var curQueue: PlayQueue     // unmanaged

    var curEpisode: Episode? = null     // unmanged
        set(value) {
            field = value
            if (curMedia != field?.media) curMedia = field?.media
        }

    var curMedia: Playable? = null      // unmanged if EpisodeMedia
        set(value) {
            field = value
            if (field is EpisodeMedia) {
                val media = (field as EpisodeMedia)
                if (curEpisode != media.episode) curEpisode = media.episode
            }
        }

    var curState: CurrentState      // unmanaged

    init {
        curQueue = PlayQueue()
        curState = CurrentState()

        CoroutineScope(Dispatchers.IO).launch {
            Logd(TAG, "starting curQueue")
            var curQueue_ = realm.query(PlayQueue::class).sort("updated", Sort.DESCENDING).first().find()
            if (curQueue_ != null) {
                curQueue = realm.copyFromRealm(curQueue_)
                curQueue.episodes.addAll(realm.copyFromRealm(realm.query(Episode::class, "id IN $0", curQueue.episodeIds)
                    .find().sortedBy { curQueue.episodeIds.indexOf(it.id) }))
            }
            else {
                Logd(TAG, "creating new curQueue")
                for (i in 0..4) {
                    curQueue_ = PlayQueue()
                    if (i == 0) {
                        curQueue_.name = "Default"
                        curQueue = curQueue_
                    } else {
                        curQueue_.id = i.toLong()
                        curQueue_.name = "Queue $i"
                    }
                    upsert(curQueue_) {}
                }
                curQueue.update()
                upsert(curQueue) {}
            }

            Logd(TAG, "starting curState")
            var curState_ = realm.query(CurrentState::class).first().find()
            if (curState_ != null) curState = realm.copyFromRealm(curState_)
            else {
                Logd(TAG, "creating new curState")
                curState_ = CurrentState()
                curState = curState_
                upsert(curState_) {}
            }
            loadPlayableFromPreferences()
        }
//        val curState_ = realm.query(CurrentState::class).first()
//        val job = CoroutineScope(Dispatchers.Default).launch {
//            val curStateFlow = curState_.asFlow()
//            curStateFlow.collect { changes: SingleQueryChange<CurrentState> ->
//                when (changes) {
//                    is UpdatedObject -> {
//                        if (changes.isFieldChanged("curPlayerStatus")) {
//                            Logd(TAG, "curPlayerStatus ${changes.obj.curPlayerStatus}")
////                            if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!))
//                        }
//                    }
//                    else -> {}
//                }
//            }
//        }
    }

    fun writeNoMediaPlaying() {
        curState.curMediaType = NO_MEDIA_PLAYING
        curState.curFeedId = NO_MEDIA_PLAYING
        curState.curMediaId = NO_MEDIA_PLAYING
        curState.curPlayerStatus = PLAYER_STATUS_OTHER
        upsertBlk(curState) {}
    }

    /**
     * Restores a playable object from a sharedPreferences file. This method might load data from the database,
     * depending on the type of playable that was restored.
     * @return The restored Playable object
     */
    fun loadPlayableFromPreferences() {
        Logd(TAG, "loadPlayableFromPreferences currentlyPlayingType: $curState.curMediaType")
        if (curState.curMediaType != NO_MEDIA_PLAYING) {
            val type = curState.curMediaType.toInt()
            if (type == EpisodeMedia.PLAYABLE_TYPE_FEEDMEDIA) {
                val mediaId = curState.curMediaId
                if (mediaId != 0L) {
                    curMedia = getEpisodeMedia(mediaId)
                    if (curEpisode != null) curEpisode = (curMedia as EpisodeMedia).episode
                }
            } else Log.e(TAG, "Could not restore Playable object from preferences")
        }
    }

    @OptIn(UnstableApi::class) @JvmStatic
    fun isCurrentlyPlaying(media: EpisodeMedia?): Boolean {
        return isCurMedia(media) && PlaybackService.isRunning && MediaPlayerBase.status == PlayerStatus.PLAYING
    }

    @JvmStatic
    fun isCurMedia(media: EpisodeMedia?): Boolean {
        return media != null && (curMedia as? EpisodeMedia)?.id == media.id
    }
}