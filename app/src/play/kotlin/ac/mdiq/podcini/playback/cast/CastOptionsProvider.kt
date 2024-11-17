package ac.mdiq.podcini.playback.cast

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

@Suppress("unused")
@SuppressLint("VisibleForTests")
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
//        return CastOptions.Builder().setReceiverApplicationId("BEBC1DB1").build()
//        return CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()
        return CastOptions.Builder().setReceiverApplicationId("C7113B39").build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}