package ac.mdiq.podcini.playback.cast

import android.view.Menu
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity that allows for showing the MediaRouter button whenever there's a cast device in the
 * network.
 */
abstract class CastEnabledActivity : AppCompatActivity() {
    val TAG = this::class.simpleName ?: "Anonymous"

    fun requestCastButton(menu: Menu?) {
        // no-op
    }

}
