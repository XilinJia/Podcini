package ac.mdiq.podcini.ui.activity

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ac.mdiq.podcini.preferences.ThemeSwitcher.getTranslucentTheme
import ac.mdiq.podcini.ui.dialog.VariableSpeedDialog

// This is for widget
class PlaybackSpeedDialogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTranslucentTheme(this))
        super.onCreate(savedInstanceState)
        val speedDialog: VariableSpeedDialog? = VariableSpeedDialog.newInstance(booleanArrayOf(true, true, true), 2)
        speedDialog?.show(supportFragmentManager, null)
    }

    class InnerVariableSpeedDialog : VariableSpeedDialog() {
        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            requireActivity().finish()
        }
    }
}
