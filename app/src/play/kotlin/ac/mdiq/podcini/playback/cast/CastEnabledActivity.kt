package ac.mdiq.podcini.playback.cast

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Activity that allows for showing the MediaRouter button whenever there's a cast device in the network.
 */
abstract class CastEnabledActivity : AppCompatActivity() {
    private var canCast by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        canCast = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
//        if (canCast) {
//            try { CastContext.getSharedInstance(this)
//            } catch (e: Exception) {
//                e.printStackTrace()
//                canCast = false
//            }
//        }
    }
//
//    fun requestCastButton(menu: Menu?) {
//        if (!canCast) return
//        menuInflater.inflate(R.menu.cast_button, menu)
//        CastButtonFactory.setUpMediaRouteButton(applicationContext, menu!!, R.id.media_route_menu_item)
//    }

    @Composable
    fun CastIconButton() {
        if (canCast) AndroidView(modifier = Modifier.size(24.dp), factory = { ctx -> MediaRouteButton(ctx).apply { CastButtonFactory.setUpMediaRouteButton(ctx, this) } })
    }
}
