package de.danoeh.antennapod.view

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.ui.i18n.R

object LocalDeleteModal {
    fun showLocalFeedDeleteWarningIfNecessary(context: Context?, items: Iterable<FeedItem>,
                                              deleteCommand: Runnable
    ) {
        var anyLocalFeed = false
        for (item in items) {
            if (item.feed?.isLocalFeed == true) {
                anyLocalFeed = true
                break
            }
        }

        if (!anyLocalFeed) {
            deleteCommand.run()
            return
        }

        MaterialAlertDialogBuilder(context!!)
            .setTitle(R.string.delete_episode_label)
            .setMessage(R.string.delete_local_feed_warning_body)
            .setPositiveButton(R.string.delete_label) { dialog: DialogInterface?, which: Int -> deleteCommand.run() }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }
}
