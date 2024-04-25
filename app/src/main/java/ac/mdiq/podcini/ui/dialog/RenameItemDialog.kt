package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.NavDrawerData.FeedDrawerItem
import ac.mdiq.podcini.storage.model.feed.Feed
import android.app.Activity
import android.content.DialogInterface
import android.util.Log
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference

@OptIn(UnstableApi::class)
class RenameItemDialog {
    private val activityRef: WeakReference<Activity>
    private var feed: Feed? = null
    private var drawerItem: FeedDrawerItem? = null

    constructor(activity: Activity, feed: Feed?) {
        this.activityRef = WeakReference(activity)
        this.feed = feed
    }

    constructor(activity: Activity, drawerItem: FeedDrawerItem?) {
        this.activityRef = WeakReference(activity)
        this.drawerItem = drawerItem
    }

    fun show() {
        val activity = activityRef.get() ?: return

        val binding = EditTextDialogBinding.inflate(LayoutInflater.from(activity))
        val title = if (feed != null) feed!!.title else drawerItem!!.title

        binding.editText.setText(title)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setTitle(if (feed != null) R.string.rename_feed_label else R.string.rename_tag_label)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                val newTitle = binding.editText.text.toString()
                if (feed != null) {
                    feed!!.setCustomTitle(newTitle)
                    DBWriter.setFeedCustomTitle(feed!!)
                } else renameTag(newTitle)
            }
            .setNeutralButton(R.string.reset, null)
            .setNegativeButton(R.string.cancel_label, null)
            .show()

        // To prevent cancelling the dialog on button click
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            .setOnClickListener { binding.editText.setText(title) }
    }

    private fun renameTag(title: String) {
//        if (DrawerItem.Type.TAG == drawerItem!!.type) {
//            val feedPreferences: MutableList<FeedPreferences?> = ArrayList()
//            for (item in (drawerItem as TagDrawerItem?)!!.children) {
//                feedPreferences.add((item as FeedDrawerItem).feed.preferences)
//            }
//
//            for (preferences in feedPreferences) {
//                preferences!!.getTags().remove(drawerItem!!.title)
//                preferences.getTags().add(title)
//                DBWriter.setFeedPreferences(preferences)
//            }
//        }
        Log.d("RenameDialog", "rename tag not needed here")
    }
}
