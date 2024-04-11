package ac.mdiq.podcini.ui.actions.menuhandler

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.ui.dialog.RemoveFeedDialog
import ac.mdiq.podcini.ui.dialog.RenameItemDialog
import ac.mdiq.podcini.ui.dialog.TagSettingsDialog
import android.app.Activity
import androidx.fragment.app.Fragment

/**
 * Handles interactions with the FeedItemMenu.
 */
object FeedMenuHandler {
    private const val TAG = "FeedMenuHandler"

    @JvmStatic
    fun onMenuItemClicked(fragment: Fragment, menuItemId: Int,
                          selectedFeed: Feed, callback: Runnable
    ): Boolean {
        val context = fragment.requireContext()
        when (menuItemId) {
            R.id.rename_folder_item -> {
                RenameItemDialog(fragment.activity as Activity, selectedFeed).show()
            }
            R.id.edit_tags -> {
                if (selectedFeed.preferences != null) TagSettingsDialog.newInstance(listOf(selectedFeed.preferences!!))
                    .show(fragment.childFragmentManager, TagSettingsDialog.TAG)
            }
            R.id.rename_item -> {
                RenameItemDialog(fragment.activity as Activity, selectedFeed).show()
            }
            R.id.remove_feed -> {
                RemoveFeedDialog.show(context, selectedFeed, null)
            }
            else -> {
                return false
            }
        }
        return true
    }
}
