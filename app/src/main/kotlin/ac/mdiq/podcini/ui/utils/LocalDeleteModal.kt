package ac.mdiq.podcini.ui.utils

import ac.mdiq.podcini.R
import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.storage.model.Episode

object LocalDeleteModal {

    fun showLocalFeedDeleteWarningIfNecessary(context: Context, items: Iterable<Episode>, deleteCommand: Runnable) {
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

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete_episode_label)
            .setMessage(R.string.delete_local_feed_warning_body)
            .setPositiveButton(R.string.delete_label) { dialog: DialogInterface?, which: Int -> deleteCommand.run() }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }
}
