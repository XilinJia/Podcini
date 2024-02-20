package ac.mdiq.podcini.core.service.playback

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.wearable.media.MediaControlConstants

object WearMediaSession {
    /**
     * Take a custom action builder and make sure the custom action shows on Wear OS because this is the Play version
     * of the app.
     */
    fun addWearExtrasToAction(actionBuilder: PlaybackStateCompat.CustomAction.Builder) {
        val actionExtras = Bundle()
        actionExtras.putBoolean(MediaControlConstants.EXTRA_CUSTOM_ACTION_SHOW_ON_WEAR, true)
        actionBuilder.setExtras(actionExtras)
    }

    fun mediaSessionSetExtraForWear(mediaSession: MediaSessionCompat) {
        val sessionExtras = Bundle()
        sessionExtras.putBoolean(MediaControlConstants.EXTRA_RESERVE_SLOT_SKIP_TO_PREVIOUS, false)
        sessionExtras.putBoolean(MediaControlConstants.EXTRA_RESERVE_SLOT_SKIP_TO_NEXT, false)
        mediaSession.setExtras(sessionExtras)
    }
}
