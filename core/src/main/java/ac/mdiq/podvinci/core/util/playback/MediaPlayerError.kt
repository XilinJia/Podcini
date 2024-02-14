package ac.mdiq.podvinci.core.util.playback

import android.content.Context
import android.media.MediaPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import ac.mdiq.podvinci.core.R
import ac.mdiq.podvinci.core.service.playback.ExoPlayerWrapper

/** Utility class for MediaPlayer errors.  */
@UnstableApi
object MediaPlayerError {
    /** Get a human-readable string for a specific error code.  */
    fun getErrorString(context: Context, code: Int): String {
        val resId = when (code) {
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> R.string.playback_error_server_died
            MediaPlayer.MEDIA_ERROR_UNSUPPORTED, ExoPlayerWrapper.ERROR_CODE_OFFSET + ExoPlaybackException.TYPE_RENDERER -> R.string.playback_error_unsupported
            MediaPlayer.MEDIA_ERROR_TIMED_OUT -> R.string.playback_error_timeout
            ExoPlayerWrapper.ERROR_CODE_OFFSET + ExoPlaybackException.TYPE_SOURCE -> R.string.playback_error_source
            else -> R.string.playback_error_unknown
        }
        return context.getString(resId) + " (" + code + ")"
    }
}
