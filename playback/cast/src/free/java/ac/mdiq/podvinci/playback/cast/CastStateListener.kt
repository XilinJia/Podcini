package ac.mdiq.podvinci.playback.cast

import android.content.Context

open class CastStateListener(context: Context?) {
    fun destroy() {
    }

    open fun onSessionStartedOrEnded() {
    }
}
