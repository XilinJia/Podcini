package ac.mdiq.podcini.ui.actions.handler

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.wifiSyncEnabledKey
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink.needSynch
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.writeNoMediaPlaying
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.ACTION_SHUTDOWN_PLAYBACK_SERVICE
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.database.Episodes.setFavorite
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Queues.addToQueue
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.ui.dialog.ShareDialog
import ac.mdiq.podcini.ui.utils.LocalDeleteModal
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.ShareUtils
import android.view.KeyEvent
import android.view.Menu
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Handles interactions with the FeedItemMenu.
 */
@OptIn(UnstableApi::class)
object EpisodeMenuHandler {
    private val TAG: String = EpisodeMenuHandler::class.simpleName ?: "Anonymous"

    /**
     * This method should be called in the prepare-methods of menus. It changes
     * the visibility of the menu items depending on a FeedItem's attributes.
     * @param menu               An instance of Menu
     * @param selectedItem     The FeedItem for which the menu is supposed to be prepared
     * @return Returns true if selectedItem is not null.
     */
    @UnstableApi
    fun onPrepareMenu(menu: Menu?, selectedItem: Episode?): Boolean {
        if (menu == null || selectedItem == null) return false

        val hasMedia = selectedItem.media != null
        val isPlaying = hasMedia && InTheatre.isCurMedia(selectedItem.media)
        val isInQueue: Boolean = curQueue.contains(selectedItem)
        val isLocalFile = hasMedia && selectedItem.feed?.isLocalFeed?:false
        val isFavorite: Boolean = selectedItem.isFavorite

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

        CoroutineScope(Dispatchers.Main).launch {
            val fileDownloaded = withContext(Dispatchers.IO) { hasMedia && selectedItem.media?.fileExists() ?: false }
            setItemVisibility(menu, R.id.remove_item, fileDownloaded || isLocalFile)
        }
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
    private fun setItemTitle(menu: Menu, id: Int, noMedia: Int) {
        val item = menu.findItem(id)
        item?.setTitle(noMedia)
    }

    /**
     * The same method as [.onPrepareMenu], but lets the
     * caller also specify a list of menu items that should not be shown.
     * @param excludeIds Menu item that should be excluded
     * @return true if selectedItem is not null.
     */
    @UnstableApi
    fun onPrepareMenu(menu: Menu?, selectedItem: Episode?, vararg excludeIds: Int): Boolean {
        if (menu == null || selectedItem == null) return false
        val rc = onPrepareMenu(menu, selectedItem)
        if (rc && excludeIds.isNotEmpty()) {
            for (id in excludeIds) setItemVisibility(menu, id, false)
        }
        return rc
    }

    /**
     * Default menu handling for the given FeedItem.
     * A Fragment instance, (rather than the more generic Context), is needed as a parameter
     * to support some UI operations, e.g., creating a Snackbar.
     */
    fun onMenuItemClicked(fragment: Fragment, menuItemId: Int, selectedItem: Episode): Boolean {
        val context = fragment.requireContext()
        when (menuItemId) {
            R.id.skip_episode_item -> context.sendBroadcast(MediaButtonReceiver.createIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            R.id.remove_item -> {
                LocalDeleteModal.deleteEpisodesWarnLocal(context, listOf(selectedItem))
            }
            R.id.mark_read_item -> {
//                selectedItem.setPlayed(true)
                setPlayState(Episode.PlayState.PLAYED.code, true, selectedItem)
                if (selectedItem.feed?.isLocalFeed != true && (isProviderConnected || wifiSyncEnabledKey)) {
                    val media: EpisodeMedia? = selectedItem.media
                    // not all items have media, Gpodder only cares about those that do
                    if (needSynch() && media != null) {
                        val actionPlay: EpisodeAction = EpisodeAction.Builder(selectedItem, EpisodeAction.PLAY)
                            .currentTimestamp()
                            .started(media.getDuration() / 1000)
                            .position(media.getDuration() / 1000)
                            .total(media.getDuration() / 1000)
                            .build()
                        SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, actionPlay)
                    }
                }
            }
            R.id.mark_unread_item -> {
//                selectedItem.setPlayed(false)
                setPlayState(Episode.PlayState.UNPLAYED.code, false, selectedItem)
                if (needSynch() && selectedItem.feed?.isLocalFeed != true && selectedItem.media != null) {
                    val actionNew: EpisodeAction = EpisodeAction.Builder(selectedItem, EpisodeAction.NEW)
                        .currentTimestamp()
                        .build()
                    SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, actionNew)
                }
            }
            R.id.add_to_queue_item -> addToQueue(true, selectedItem)
            R.id.remove_from_queue_item -> removeFromQueue(selectedItem)
            R.id.add_to_favorites_item -> setFavorite(selectedItem, true)
            R.id.remove_from_favorites_item -> setFavorite(selectedItem, false)
            R.id.reset_position -> {
                selectedItem.media?.setPosition(0)
                if (curState.curMediaId == (selectedItem.media?.id ?: "")) {
                    writeNoMediaPlaying()
                    IntentUtils.sendLocalBroadcast(context, ACTION_SHUTDOWN_PLAYBACK_SERVICE)
                }
                setPlayState(Episode.PlayState.UNPLAYED.code, true, selectedItem)
            }
            R.id.visit_website_item -> {
                val url = selectedItem.getLinkWithFallback()
                if (url != null) IntentUtils.openInBrowser(context, url)
            }
            R.id.share_item -> {
                val shareDialog: ShareDialog = ShareDialog.newInstance(selectedItem)
                shareDialog.show((fragment.requireActivity().supportFragmentManager), "ShareEpisodeDialog")
            }
            else -> {
                Logd(TAG, "Unknown menuItemId: $menuItemId")
                return false
            }
        }
        // Refresh menu state
        return true
    }
}
