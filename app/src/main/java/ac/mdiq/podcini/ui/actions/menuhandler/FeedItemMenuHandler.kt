package ac.mdiq.podcini.ui.actions.menuhandler

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.preferences.PlaybackPreferences
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.playback.service.PlaybackServiceInterface
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.dialog.ShareDialog
import ac.mdiq.podcini.ui.view.LocalDeleteModal
import ac.mdiq.podcini.util.*
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.snackbar.Snackbar
import kotlin.math.ceil


/**
 * Handles interactions with the FeedItemMenu.
 */
@OptIn(UnstableApi::class)
object FeedItemMenuHandler {
    private const val TAG = "FeedItemMenuHandler"

    /**
     * This method should be called in the prepare-methods of menus. It changes
     * the visibility of the menu items depending on a FeedItem's attributes.
     *
     * @param menu               An instance of Menu
     * @param selectedItem     The FeedItem for which the menu is supposed to be prepared
     * @return Returns true if selectedItem is not null.
     */
    @UnstableApi
    fun onPrepareMenu(menu: Menu?, selectedItem: FeedItem?): Boolean {
        if (menu == null || selectedItem == null) return false

        val hasMedia = selectedItem.media != null
        val isPlaying = hasMedia && PlaybackStatus.isPlaying(selectedItem.media)
        val isInQueue: Boolean = selectedItem.isTagged(FeedItem.TAG_QUEUE)
        val fileDownloaded = hasMedia && selectedItem.media?.fileExists()?:false
        val isLocalFile = hasMedia && selectedItem.feed?.isLocalFeed?:false
        val isFavorite: Boolean = selectedItem.isTagged(FeedItem.TAG_FAVORITE)

        setItemVisibility(menu, R.id.skip_episode_item, isPlaying)
        setItemVisibility(menu, R.id.remove_from_queue_item, isInQueue)
        setItemVisibility(menu, R.id.add_to_queue_item, !isInQueue && selectedItem.media != null)
        setItemVisibility(menu, R.id.visit_website_item, !(selectedItem.feed?.isLocalFeed?:false) && ShareUtils.hasLinkToShare(selectedItem))
        setItemVisibility(menu, R.id.share_item, !(selectedItem.feed?.isLocalFeed?:false))
        setItemVisibility(menu, R.id.mark_read_item, !selectedItem.isPlayed())
        setItemVisibility(menu, R.id.mark_unread_item, selectedItem.isPlayed())
        setItemVisibility(menu, R.id.reset_position, hasMedia && selectedItem.media?.getPosition() != 0)

        // Display proper strings when item has no media
        if (hasMedia) {
            setItemTitle(menu, R.id.mark_read_item, R.string.mark_read_label)
            setItemTitle(menu, R.id.mark_unread_item, R.string.mark_unread_label)
        } else {
            setItemTitle(menu, R.id.mark_read_item, R.string.mark_read_no_media_label)
            setItemTitle(menu, R.id.mark_unread_item, R.string.mark_unread_label_no_media)
        }

        setItemVisibility(menu, R.id.add_to_favorites_item, !isFavorite)
        setItemVisibility(menu, R.id.remove_from_favorites_item, isFavorite)
        setItemVisibility(menu, R.id.remove_item, fileDownloaded || isLocalFile)
        return true
    }

    /**
     * Used to set the viability of a menu item.
     * This method also does some null-checking so that neither menu nor the menu item are null
     * in order to prevent nullpointer exceptions.
     * @param menu The menu that should be used
     * @param menuId The id of the menu item that will be used
     * @param visibility The new visibility status of given menu item
     */
    private fun setItemVisibility(menu: Menu?, menuId: Int, visibility: Boolean) {
        if (menu == null) return

        val item = menu.findItem(menuId)
        item?.setVisible(visibility)
    }

    /**
     * This method allows to replace to String of a menu item with a different one.
     * @param menu Menu item that should be used
     * @param id The id of the string that is going to be replaced.
     * @param noMedia The id of the new String that is going to be used.
     */
    fun setItemTitle(menu: Menu, id: Int, noMedia: Int) {
        val item = menu.findItem(id)
        item?.setTitle(noMedia)
    }

    /**
     * The same method as [.onPrepareMenu], but lets the
     * caller also specify a list of menu items that should not be shown.
     *
     * @param excludeIds Menu item that should be excluded
     * @return true if selectedItem is not null.
     */
    @UnstableApi
    fun onPrepareMenu(menu: Menu?, selectedItem: FeedItem?, vararg excludeIds: Int): Boolean {
        if (menu == null || selectedItem == null) return false

        val rc = onPrepareMenu(menu, selectedItem)
        if (rc && excludeIds.isNotEmpty()) {
            for (id in excludeIds) {
                setItemVisibility(menu, id, false)
            }
        }
        return rc
    }

    /**
     * Default menu handling for the given FeedItem.
     *
     * A Fragment instance, (rather than the more generic Context), is needed as a parameter
     * to support some UI operations, e.g., creating a Snackbar.
     */
    fun onMenuItemClicked(fragment: Fragment, menuItemId: Int, selectedItem: FeedItem): Boolean {
        val context = fragment.requireContext()
        when (menuItemId) {
            R.id.skip_episode_item -> context.sendBroadcast(MediaButtonReceiver.createIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            R.id.remove_item -> {
                LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary(context, listOf(selectedItem)) {
                    if (selectedItem.media != null) DBWriter.deleteFeedMediaOfItem(context, selectedItem.media!!.id)
                }
            }
            R.id.mark_read_item -> {
                selectedItem.setPlayed(true)
                DBWriter.markItemPlayed(selectedItem, FeedItem.PLAYED, true)
                if (selectedItem.feed?.isLocalFeed != true && SynchronizationSettings.isProviderConnected) {
                    val media: FeedMedia? = selectedItem.media
                    // not all items have media, Gpodder only cares about those that do
                    if (media != null) {
                        val actionPlay: EpisodeAction = EpisodeAction.Builder(selectedItem, EpisodeAction.PLAY)
                            .currentTimestamp()
                            .started(media.getDuration() / 1000)
                            .position(media.getDuration() / 1000)
                            .total(media.getDuration() / 1000)
                            .build()
                        SynchronizationQueueSink.enqueueEpisodeActionIfSynchronizationIsActive(context, actionPlay)
                    }
                }
            }
            R.id.mark_unread_item -> {
                selectedItem.setPlayed(false)
                DBWriter.markItemPlayed(selectedItem, FeedItem.UNPLAYED, false)
                if (selectedItem.feed?.isLocalFeed != true && selectedItem.media != null) {
                    val actionNew: EpisodeAction = EpisodeAction.Builder(selectedItem, EpisodeAction.NEW)
                        .currentTimestamp()
                        .build()
                    SynchronizationQueueSink.enqueueEpisodeActionIfSynchronizationIsActive(context, actionNew)
                }
            }
            R.id.add_to_queue_item -> DBWriter.addQueueItem(context, selectedItem)
            R.id.remove_from_queue_item -> DBWriter.removeQueueItem(context, true, selectedItem)
            R.id.add_to_favorites_item -> DBWriter.addFavoriteItem(selectedItem)
            R.id.remove_from_favorites_item -> DBWriter.removeFavoriteItem(selectedItem)
            R.id.reset_position -> {
                selectedItem.media?.setPosition(0)
                if (PlaybackPreferences.currentlyPlayingFeedMediaId == (selectedItem.media?.id ?: "")) {
                    PlaybackPreferences.writeNoMediaPlaying()
                    IntentUtils.sendLocalBroadcast(context, PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE)
                }
                DBWriter.markItemPlayed(selectedItem, FeedItem.UNPLAYED, true)
            }
            R.id.visit_website_item -> {
                val url = FeedItemUtil.getLinkWithFallback(selectedItem)
                if (url != null) IntentUtils.openInBrowser(context, url)
            }
            R.id.share_item -> {
                val shareDialog: ShareDialog = ShareDialog.newInstance(selectedItem)
                shareDialog.show((fragment.requireActivity().supportFragmentManager), "ShareEpisodeDialog")
            }
            else -> {
                Log.d(TAG, "Unknown menuItemId: $menuItemId")
                return false
            }
        }

        // Refresh menu state
        return true
    }

    /**
     * Remove new flag with additional UI logic to allow undo with Snackbar.
     *
     * Undo is useful for Remove new flag, given there is no UI to undo it otherwise
     * ,i.e., there is (context) menu item for add new flag
     */
    fun markReadWithUndo(fragment: Fragment, item: FeedItem?, playState: Int, showSnackbar: Boolean) {
        if (item == null) return

        Log.d(TAG, "markReadWithUndo(" + item.id + ")")
        // we're marking it as unplayed since the user didn't actually play it
        // but they don't want it considered 'NEW' anymore
        DBWriter.markItemPlayed(playState, item.id)

        val h = Handler(fragment.requireContext().mainLooper)
        val r = Runnable {
            val media: FeedMedia? = item.media
            val shouldAutoDelete: Boolean = if (item.feed == null) false else FeedUtil.shouldAutoDeleteItemsOnThatFeed(item.feed!!)
            if (media != null && FeedItemUtil.hasAlmostEnded(media) && shouldAutoDelete)
                DBWriter.deleteFeedMediaOfItem(fragment.requireContext(), media.id)
        }
        val playStateStringRes: Int = when (playState) {
            FeedItem.UNPLAYED -> if (item.playState == FeedItem.NEW) R.string.removed_inbox_label    //was new
            else R.string.marked_as_unplayed_label   //was played
            FeedItem.PLAYED -> R.string.marked_as_played_label
            else -> if (item.playState == FeedItem.NEW) R.string.removed_inbox_label
            else R.string.marked_as_unplayed_label
        }
        val duration: Int = Snackbar.LENGTH_LONG

        if (showSnackbar) {
            (fragment.activity as MainActivity).showSnackbarAbovePlayer(
                playStateStringRes, duration)
                .setAction(fragment.getString(R.string.undo)) {
                    DBWriter.markItemPlayed(item.playState, item.id)
                    // don't forget to cancel the thing that's going to remove the media
                    h.removeCallbacks(r)
                }
        }

        h.postDelayed(r, ceil((duration * 1.05f).toDouble()).toInt().toLong())
    }

    fun removeNewFlagWithUndo(fragment: Fragment, item: FeedItem?) {
        markReadWithUndo(fragment, item, FeedItem.UNPLAYED, false)
    }
}
