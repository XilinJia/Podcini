package ac.mdiq.podcini.playback.cast

import ac.mdiq.podcini.R
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Activity that allows for showing the MediaRouter button whenever there's a cast device in the
 * network.
 */
abstract class CastEnabledActivity : AppCompatActivity() {
    private var canCast = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        canCast = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        if (canCast) {
            try { CastContext.getSharedInstance(this)
            } catch (e: Exception) {
                e.printStackTrace()
                canCast = false
            }
        }
    }

    fun requestCastButton(menu: Menu?) {
        if (!canCast) return
        menuInflater.inflate(R.menu.cast_button, menu)
        CastButtonFactory.setUpMediaRouteButton(applicationContext, menu!!, R.id.media_route_menu_item)
    }
}
