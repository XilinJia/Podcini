package ac.mdiq.podcini.playback.cast

import android.content.Context
import ac.mdiq.podcini.playback.base.PlaybackServiceMediaPlayer
import ac.mdiq.podcini.playback.base.PlaybackServiceMediaPlayer.PSMPCallback

/**
 * Stub implementation of CastPsmp for Free build flavour
 */
object CastPsmp {
    @JvmStatic
    fun getInstanceIfConnected(context: Context, callback: PSMPCallback): PlaybackServiceMediaPlayer? {
        return null
    }
}
