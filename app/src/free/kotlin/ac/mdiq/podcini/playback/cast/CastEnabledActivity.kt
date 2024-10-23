package ac.mdiq.podcini.playback.cast

import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable

/**
 * Activity that allows for showing the MediaRouter button whenever there's a cast device in the
 * network.
 */
abstract class CastEnabledActivity : AppCompatActivity() {
    val TAG = this::class.simpleName ?: "Anonymous"

    fun requestCastButton(menu: Menu?) {}

    @Composable
    fun CastIconButton() {}
}
