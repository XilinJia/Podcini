package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.PlaybackController.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.PlaybackController.Companion.playbackService
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.widget.Toast
import androidx.media3.common.util.UnstableApi

class PlayLocalActionButton(item: Episode) : EpisodeActionButton(item) {
    override fun getLabel(): Int {
        return R.string.play_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_play_24dp
    }
    @UnstableApi override fun onClick(context: Context) {
        Logd("PlayLocalActionButton", "onClick called")
        val media = item.media
        if (media == null) {
            Toast.makeText(context, R.string.no_media_label, Toast.LENGTH_LONG).show()
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
}
