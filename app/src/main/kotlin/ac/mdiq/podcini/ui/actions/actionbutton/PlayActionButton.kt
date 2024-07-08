package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.PlaybackController.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.PlaybackController.Companion.playbackService
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.media3.common.util.UnstableApi

class PlayActionButton(item: Episode) : EpisodeActionButton(item) {
    override fun getLabel(): Int {
        return R.string.play_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_play_24dp
    }

    @UnstableApi override fun onClick(context: Context) {
        Logd("PlayActionButton", "onClick called")
        val media = item.media
        if (media == null) {
            Toast.makeText(context, R.string.no_media_label, Toast.LENGTH_LONG).show()
            return
        }
        if (!media.fileExists()) {
            Toast.makeText(context, R.string.error_file_not_found, Toast.LENGTH_LONG).show()
            notifyMissingEpisodeMediaFile(context, media)
            return
        }

        if (playbackService?.isServiceReady() == true && InTheatre.isCurMedia(media)) {
            playbackService?.mPlayer?.resume()
            playbackService?.taskManager?.restartSleepTimer()
        } else {
            PlaybackServiceStarter(context, media).callEvenIfRunning(true).start()
            EventFlow.postEvent(FlowEvent.PlayEvent(item))
        }

        if (media.getMediaType() == MediaType.VIDEO) context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
    }

    /**
     * Notifies the database about a missing EpisodeMedia file. This method will correct the EpisodeMedia object's
     * values in the DB and send a FeedItemEvent.
     */
    fun notifyMissingEpisodeMediaFile(context: Context, media: EpisodeMedia) {
        Logd(TAG, "notifyMissingEpisodeMediaFile called")
        Log.i(TAG, "The feedmanager was notified about a missing episode. It will update its database now.")
        val episode = media.episode
        if (episode != null) {
            episode.media = media
            episode.media?.downloaded = false
            episode.media?.fileUrl = null
            upsertBlk(episode) {}
            EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(episode))
        }
        EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.error_file_not_found)))
    }
}
