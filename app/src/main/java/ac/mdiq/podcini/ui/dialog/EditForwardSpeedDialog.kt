package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.speedforwardSpeed
import android.app.Activity
import android.content.DialogInterface
import android.text.Editable
import android.text.InputType
import android.view.LayoutInflater
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference
import kotlin.math.round

@UnstableApi
 class EditForwardSpeedDialog(activity: Activity) {
    private val activityRef = WeakReference(activity)

    fun show() {
        val activity = activityRef.get() ?: return

        val binding = EditTextDialogBinding.inflate(LayoutInflater.from(activity))
        binding.editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        binding.editText.text = Editable.Factory.getInstance().newEditable(speedforwardSpeed.toString())

        MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setTitle(R.string.edit_fast_forward_speed)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                var speed = binding.editText.text.toString().toFloatOrNull() ?: 0.0f
                when {
                    speed < 0.0f -> speed = 0.0f
                    speed > 10.0f -> speed = 10.0f
                }
                speedforwardSpeed = round(10 * speed) / 10
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    companion object {
        const val TAG: String = "EditForwardSpeedDialog"
    }
}
