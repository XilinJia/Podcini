package ac.mdiq.podcini.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.EXTRA_ALLOW_STREAM_THIS_TIME
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent

@UnstableApi
class PlaybackServiceStarter(private val context: Context, private val media: Playable) {

    private var shouldStreamThisTime = false
    private var callEvenIfRunning = false

    /**
     * Default value: false
     */
    fun callEvenIfRunning(callEvenIfRunning: Boolean): PlaybackServiceStarter {
        this.callEvenIfRunning = callEvenIfRunning
        return this
    }

    fun shouldStreamThisTime(shouldStreamThisTime: Boolean): PlaybackServiceStarter {
        this.shouldStreamThisTime = shouldStreamThisTime
        return this
    }

    val intent: Intent
        get() {
            val launchIntent = Intent(context, PlaybackService::class.java)
//            launchIntent.putExtra(PlaybackServiceConstants.EXTRA_PLAYABLE, media as Parcelable)
            launchIntent.putExtra(EXTRA_ALLOW_STREAM_THIS_TIME, shouldStreamThisTime)
            return launchIntent
        }

    fun start() {
        Logd("PlaybackServiceStarter", "starting PlaybackService")
        if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!, FlowEvent.PlayEvent.Action.END))
        if (media is EpisodeMedia) {
            curMedia = media
//            curEpisode = if (media.episode != null) unmanaged(media.episode!!) else null
            curEpisode = media.episode
//            curMedia = curEpisode?.media
        } else curMedia = media

        if (PlaybackService.isRunning && !callEvenIfRunning) return
        ContextCompat.startForegroundService(context, intent)
    }
}
