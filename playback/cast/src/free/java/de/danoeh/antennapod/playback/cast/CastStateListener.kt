package de.danoeh.antennapod.playback.cast

import android.content.Context

open class CastStateListener(context: Context?) {
    fun destroy() {
    }

    open fun onSessionStartedOrEnded() {
    }
}
