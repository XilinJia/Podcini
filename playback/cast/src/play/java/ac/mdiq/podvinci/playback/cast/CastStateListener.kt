package ac.mdiq.podvinci.playback.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

open class CastStateListener(context: Context?) : SessionManagerListener<CastSession> {
    private var castContext: CastContext?

    init {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context!!) != ConnectionResult.SUCCESS) {
            castContext = null
        } else {
            var castCtx: CastContext?
            try {
                castCtx = CastContext.getSharedInstance(context)
                castCtx.sessionManager.addSessionManagerListener(this, CastSession::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                castCtx = null
            }
            castContext = castCtx
        }
    }

    fun destroy() {
        if (castContext != null) {
            castContext!!.sessionManager.removeSessionManagerListener(this, CastSession::class.java)
        }
    }

    override fun onSessionStarting(castSession: CastSession) {
    }

    override fun onSessionStarted(session: CastSession, sessionId: String) {
        onSessionStartedOrEnded()
    }

    override fun onSessionStartFailed(castSession: CastSession, i: Int) {
    }

    override fun onSessionEnding(castSession: CastSession) {
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
    }

    override fun onSessionResumeFailed(castSession: CastSession, i: Int) {
    }

    override fun onSessionSuspended(castSession: CastSession, i: Int) {
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
        onSessionStartedOrEnded()
    }

    override fun onSessionResuming(castSession: CastSession, s: String) {
    }

    open fun onSessionStartedOrEnded() {
    }
}
