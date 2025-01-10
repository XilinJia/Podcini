package ac.mdiq.podcini.playback.cast

import android.view.Menu
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity

/**
 * Activity that allows for showing the MediaRouter button whenever there's a cast device in the network.
 */
abstract class CastEnabledActivity : ComponentActivity() {
    val TAG = this::class.simpleName ?: "Anonymous"

    fun requestCastButton(menu: Menu?) {}

    @Composable
    fun CastIconButton() {}
}
