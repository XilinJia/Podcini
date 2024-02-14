package de.danoeh.antennapod.dialog

import android.app.Activity
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.core.storage.NavDrawerData.*
import de.danoeh.antennapod.databinding.EditTextDialogBinding
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedPreferences
import java.lang.ref.WeakReference

class RenameItemDialog {
    private val activityRef: WeakReference<Activity>
    private var feed: Feed? = null
    private var drawerItem: DrawerItem? = null

    constructor(activity: Activity, feed: Feed?) {
        this.activityRef = WeakReference(activity)
        this.feed = feed
    }

    constructor(activity: Activity, drawerItem: DrawerItem?) {
        this.activityRef = WeakReference(activity)
        this.drawerItem = drawerItem
    }

    fun show() {
        val activity = activityRef.get() ?: return

        val binding = EditTextDialogBinding.inflate(LayoutInflater.from(activity))
        val title = if (feed != null) feed!!.title else drawerItem!!.title

        binding.urlEditText.setText(title)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setTitle(if (feed != null) R.string.rename_feed_label else R.string.rename_tag_label)
            .setPositiveButton(android.R.string.ok) { d: DialogInterface?, input: Int ->
                val newTitle = binding.urlEditText.text.toString()
                if (feed != null) {
                    feed!!.setCustomTitle(newTitle)
                    DBWriter.setFeedCustomTitle(feed!!)
                } else {
                    renameTag(newTitle)
                }
            }
            .setNeutralButton(de.danoeh.antennapod.core.R.string.reset, null)
            .setNegativeButton(de.danoeh.antennapod.core.R.string.cancel_label, null)
            .show()

        // To prevent cancelling the dialog on button click
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            .setOnClickListener { view: View? -> binding.urlEditText.setText(title) }
    }

    private fun renameTag(title: String) {
        if (DrawerItem.Type.TAG == drawerItem!!.type) {
            val feedPreferences: MutableList<FeedPreferences?> = ArrayList()
            for (item in (drawerItem as TagDrawerItem?)!!.children) {
                feedPreferences.add((item as FeedDrawerItem).feed.preferences)
            }

            for (preferences in feedPreferences) {
                preferences!!.getTags().remove(drawerItem!!.title)
                preferences.getTags().add(title)
                DBWriter.setFeedPreferences(preferences)
            }
        }
    }
}
