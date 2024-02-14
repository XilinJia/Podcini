package de.danoeh.antennapod.playback.cast

import android.content.Context
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer.PSMPCallback

/**
 * Stub implementation of CastPsmp for Free build flavour
 */
object CastPsmp {
    @JvmStatic
    fun getInstanceIfConnected(context: Context,
                               callback: PSMPCallback
    ): PlaybackServiceMediaPlayer? {
        return null
    }
}
