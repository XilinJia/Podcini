package ac.mdiq.podcini.dialog

import android.app.Activity
import android.content.DialogInterface
import android.os.CountDownTimer
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.storage.DBWriter
import ac.mdiq.podcini.core.util.download.FeedUpdateManager.runOnce
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.model.feed.Feed
import androidx.media3.common.util.UnstableApi
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ExecutionException

 @UnstableApi
 abstract class EditUrlSettingsDialog(activity: Activity, private val feed: Feed) {
    private val activityRef = WeakReference(activity)

    fun show() {
        val activity = activityRef.get() ?: return

        val binding = EditTextDialogBinding.inflate(LayoutInflater.from(activity))

        binding.urlEditText.setText(feed.download_url)

        MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setTitle(R.string.edit_url_menu)
            .setPositiveButton(android.R.string.ok) { d: DialogInterface?, input: Int -> showConfirmAlertDialog(binding.urlEditText.text.toString()) }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    @UnstableApi private fun onConfirmed(original: String, updated: String) {
        try {
            DBWriter.updateFeedDownloadURL(original, updated).get()
            feed.download_url = updated
            runOnce(activityRef.get(), feed)
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    @UnstableApi private fun showConfirmAlertDialog(url: String) {
        val activity = activityRef.get()

        val alertDialog = MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.edit_url_menu)
            .setMessage(R.string.edit_url_confirmation_msg)
            .setPositiveButton(android.R.string.ok) { d: DialogInterface?, input: Int ->
                onConfirmed(feed.download_url?:"", url)
                setUrl(url)
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

        object : CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).text = String.format(Locale.getDefault(), "%s (%d)",
                    activity.getString(android.R.string.ok), millisUntilFinished / 1000 + 1)
            }

            override fun onFinish() {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(android.R.string.ok)
            }
        }.start()
    }

    protected abstract fun setUrl(url: String?)

    companion object {
        const val TAG: String = "EditUrlSettingsDialog"
    }
}
