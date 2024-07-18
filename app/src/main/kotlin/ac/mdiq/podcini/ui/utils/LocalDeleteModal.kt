package ac.mdiq.podcini.ui.utils

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.Episodes.deleteMediaOfEpisode
import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.storage.model.Episode

object LocalDeleteModal {

    fun deleteEpisodesWarnLocal(context: Context, items: Iterable<Episode>) {
        val localItems: MutableList<Episode> = mutableListOf()
        for (item in items) {
            if (item.feed?.isLocalFeed == true) localItems.add(item)
            else deleteMediaOfEpisode(context, item)
        }

        if (localItems.isNotEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_episode_label)
                .setMessage(R.string.delete_local_feed_warning_body)
                .setPositiveButton(R.string.delete_label) { dialog: DialogInterface?, which: Int ->
                    for (item in localItems) {
                        deleteMediaOfEpisode(context, item)
                    }
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }
    }
}
