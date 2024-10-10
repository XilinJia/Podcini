package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Feed
import android.app.Activity
import android.content.DialogInterface
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference

@OptIn(UnstableApi::class)
class CustomFeedNameDialog(activity: Activity, private var feed: Feed) {
    private val activityRef: WeakReference<Activity> = WeakReference(activity)

    fun show() {
        val activity = activityRef.get() ?: return

        val binding = EditTextDialogBinding.inflate(LayoutInflater.from(activity))
        val title = feed.title

        binding.editText.setText(title)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setTitle(R.string.rename_feed_label)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                val newTitle = binding.editText.text.toString()
//                feed = unmanaged(feed)
                feed = upsertBlk(feed) {
                    it.setCustomTitle1(newTitle)
                }
            }
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(R.string.cancel_label, null)
            .show()

        // To prevent cancelling the dialog on button click
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { binding.editText.setText(title) }
    }
}
