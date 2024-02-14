package de.danoeh.antennapod.fragment

import de.danoeh.antennapod.activity.MainActivity
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.dialog.ItemSortDialog
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.greenrobot.eventbus.EventBus

/**
 * Like 'EpisodesFragment' except that it only shows new episodes and
 * supports swiping to mark as read.
 */
class InboxFragment : EpisodesListFragment() {
    private var prefs: SharedPreferences? = null
    override fun getFragmentTag(): String {
        return "NewEpisodesFragment"
    }
    override fun getPrefName(): String {
        return "PrefNewEpisodesFragment"
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        toolbar?.inflateMenu(R.menu.inbox)
        toolbar?.setTitle(R.string.inbox_label)
        prefs = requireActivity().getSharedPreferences(getPrefName(), Context.MODE_PRIVATE)
        updateToolbar()
        emptyView?.setIcon(R.drawable.ic_inbox)
        emptyView?.setTitle(R.string.no_inbox_head_label)
        emptyView?.setMessage(R.string.no_inbox_label)
        speedDialView?.removeActionItemById(R.id.mark_unread_batch)
        speedDialView?.removeActionItemById(R.id.remove_from_queue_batch)
        speedDialView?.removeActionItemById(R.id.delete_batch)
        return root
    }

    override fun getFilter(): FeedItemFilter {
        return FeedItemFilter(FeedItemFilter.NEW)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }
        if (item.itemId == R.id.remove_all_inbox_item) {
            if (prefs != null && prefs!!.getBoolean(PREF_DO_NOT_PROMPT_REMOVE_ALL_FROM_INBOX, false)) {
                removeAllFromInbox()
            } else {
                showRemoveAllDialog()
            }
            return true
        } else if (item.itemId == R.id.inbox_sort) {
            InboxSortDialog().show(childFragmentManager, "SortDialog")
            return true
        }
        return false
    }

    override fun loadData(): List<FeedItem> {
        return DBReader.getEpisodes(0, page * EPISODES_PER_PAGE,
            FeedItemFilter(FeedItemFilter.NEW), UserPreferences.inboxSortedOrder)
    }

    override fun loadMoreData(page: Int): List<FeedItem> {
        return DBReader.getEpisodes((page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE,
            FeedItemFilter(FeedItemFilter.NEW), UserPreferences.inboxSortedOrder)
    }

    override fun loadTotalItemCount(): Int {
        return DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.NEW))
    }

    private fun removeAllFromInbox() {
        DBWriter.removeAllNewFlags()
        (activity as MainActivity).showSnackbarAbovePlayer(R.string.removed_all_inbox_msg, Toast.LENGTH_SHORT)
    }

    private fun showRemoveAllDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(R.string.remove_all_inbox_label)
        builder.setMessage(R.string.remove_all_inbox_confirmation_msg)

        val view = View.inflate(context, R.layout.checkbox_do_not_show_again, null)
        val checkNeverAskAgain: CheckBox = view.findViewById(R.id.checkbox_do_not_show_again)
        builder.setView(view)

        builder.setPositiveButton(R.string.confirm_label
        ) { dialog: DialogInterface, which: Int ->
            dialog.dismiss()
            removeAllFromInbox()
            prefs?.edit()?.putBoolean(PREF_DO_NOT_PROMPT_REMOVE_ALL_FROM_INBOX, checkNeverAskAgain.isChecked)
                ?.apply()
        }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.show()
    }

    class InboxSortDialog : ItemSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = UserPreferences.inboxSortedOrder
        }

        override fun onAddItem(title: Int,
                                         ascending: SortOrder,
                                         descending: SortOrder,
                                         ascendingIsDefault: Boolean
        ) {
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.DURATION_SHORT_LONG) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }

        override fun onSelectionChanged() {
            super.onSelectionChanged()
            UserPreferences.inboxSortedOrder = sortOrder
            EventBus.getDefault().post(FeedListUpdateEvent(0))
        }
    }

    companion object {
        const val TAG: String = "NewEpisodesFragment"
        private const val PREF_DO_NOT_PROMPT_REMOVE_ALL_FROM_INBOX = "prefDoNotPromptRemovalAllFromInbox"
    }
}
