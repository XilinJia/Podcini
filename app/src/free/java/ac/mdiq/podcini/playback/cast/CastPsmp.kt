package ac.mdiq.podcini.playback.cast

import android.content.Context
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerBase.PSMPCallback

/**
 * Stub implementation of CastPsmp for Free build flavour
 */
object CastPsmp {
    @JvmStatic
    fun getInstanceIfConnected(context: Context, callback: PSMPCallback): MediaPlayerBase? {
        return null
    }
}
