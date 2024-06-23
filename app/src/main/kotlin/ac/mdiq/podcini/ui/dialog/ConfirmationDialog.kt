package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Creates an AlertDialog which asks the user to confirm something. Other
 * classes can handle events like confirmation or cancellation.
 */
abstract class ConfirmationDialog(private val context: Context, private val titleId: Int, private val message: String) {
    private var positiveText = 0

    constructor(context: Context, titleId: Int, messageId: Int) : this(context, titleId, context.getString(messageId))

    private fun onCancelButtonPressed(dialog: DialogInterface) {
        Logd(TAG, "Dialog was cancelled")
        dialog.dismiss()
    }

    fun setPositiveText(id: Int) {
        this.positiveText = id
    }

    abstract fun onConfirmButtonPressed(dialog: DialogInterface)

    fun createNewDialog(): AlertDialog {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(titleId)
        builder.setMessage(message)
        builder.setPositiveButton(if (positiveText != 0) positiveText else R.string.confirm_label) {
            dialog: DialogInterface, _: Int -> onConfirmButtonPressed(dialog) }
        builder.setNegativeButton(R.string.cancel_label) { dialog: DialogInterface, _: Int ->
            onCancelButtonPressed(dialog)
        }
        builder.setOnCancelListener { dialog: DialogInterface -> this@ConfirmationDialog.onCancelButtonPressed(dialog) }
        return builder.create()
    }

    companion object {
        private val TAG: String = ConfirmationDialog::class.simpleName ?: "Anonymous"
    }
}
