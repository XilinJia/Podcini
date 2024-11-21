package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.preferences.ThemeSwitcher.getTranslucentTheme
import ac.mdiq.podcini.ui.compose.PlaybackSpeedFullDialog
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView

// This is for widget
class PlaybackSpeedDialogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTranslucentTheme(this))
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(this).apply {
            setContent {
                var showSpeedDialog by remember { mutableStateOf(true) }
                if (showSpeedDialog) PlaybackSpeedFullDialog(settingCode = booleanArrayOf(true, true, true), indexDefault = 0, maxSpeed = 3f,
                    onDismiss = {
                        showSpeedDialog = false
                        (parent as? ViewGroup)?.removeView(this)
                        finish()
                    })
            }
        }
        (window.decorView as? ViewGroup)?.addView(composeView)
    }
}
