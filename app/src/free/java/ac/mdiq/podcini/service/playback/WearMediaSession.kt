package ac.mdiq.podcini.service.playback

import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

internal object WearMediaSession {
    /**
     * Take a custom action builder and add no extras, because this is not the Play version of the app.
     */
    fun addWearExtrasToAction(actionBuilder: PlaybackStateCompat.CustomAction.Builder?) {
        // no-op
    }

    fun mediaSessionSetExtraForWear(mediaSession: MediaSessionCompat?) {
        // no-op
    }
}
