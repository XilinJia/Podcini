package ac.mdiq.podcini.ui.actions.menuhandler

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.dialog.RemoveFeedDialog
import ac.mdiq.podcini.ui.dialog.CustomFeedNameDialog
import ac.mdiq.podcini.ui.dialog.TagSettingsDialog
import android.app.Activity
import androidx.fragment.app.Fragment

/**
 * Handles interactions with the FeedItemMenu.
 */
object FeedMenuHandler {
    private val TAG: String = FeedMenuHandler::class.simpleName ?: "Anonymous"

    @JvmStatic
    fun onMenuItemClicked(fragment: Fragment, menuItemId: Int, selectedFeed: Feed, callback: Runnable): Boolean {
        val context = fragment.requireContext()
        when (menuItemId) {
            R.id.rename_folder_item -> CustomFeedNameDialog(fragment.activity as Activity, selectedFeed).show()
            R.id.edit_tags -> if (selectedFeed.preferences != null) TagSettingsDialog.newInstance(listOf(selectedFeed))
                .show(fragment.childFragmentManager, TagSettingsDialog.TAG)
            R.id.rename_item -> CustomFeedNameDialog(fragment.activity as Activity, selectedFeed).show()
            R.id.remove_feed -> RemoveFeedDialog.show(context, selectedFeed, null)
            else -> return false
        }
        return true
    }
}
