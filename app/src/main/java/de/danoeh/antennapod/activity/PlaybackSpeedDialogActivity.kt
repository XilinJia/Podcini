package de.danoeh.antennapod.activity

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.danoeh.antennapod.core.preferences.ThemeSwitcher.getTranslucentTheme
import de.danoeh.antennapod.dialog.VariableSpeedDialog

class PlaybackSpeedDialogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTranslucentTheme(this))
        super.onCreate(savedInstanceState)
        val speedDialog: VariableSpeedDialog = InnerVariableSpeedDialog()
        speedDialog.show(supportFragmentManager, null)
    }

    class InnerVariableSpeedDialog : VariableSpeedDialog() {
        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            requireActivity().finish()
        }
    }
}
