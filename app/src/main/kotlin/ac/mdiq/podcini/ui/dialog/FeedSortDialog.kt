package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SortDialogBinding
import ac.mdiq.podcini.databinding.SortDialogItemActiveBinding
import ac.mdiq.podcini.databinding.SortDialogItemBinding
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.model.FeedSortOrder
import ac.mdiq.podcini.storage.model.FeedSortOrder.Companion.getSortOrder
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.Companion.feedOrderBy
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.Companion.feedOrderDir
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CompoundButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

open class FeedSortDialog : BottomSheetDialogFragment() {
    private val TAG: String = FeedSortDialog::class.simpleName ?: "Anonymous"

    protected var _binding: SortDialogBinding? = null
    protected val binding get() = _binding!!

    protected var sortOrder: FeedSortOrder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sortOrder = getSortOrder(feedOrderDir, feedOrderBy)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = SortDialogBinding.inflate(inflater)
        binding.gridLayout.columnCount = 1
        populateList()
        binding.keepSortedCheckbox.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> this@FeedSortDialog.onSelectionChanged() }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
    
    private fun populateList() {
        binding.gridLayout.removeAllViews()
        onAddItem(R.string.feed_order_alphabetical, FeedSortOrder.ALPHABETIC_A_Z, FeedSortOrder.ALPHABETIC_Z_A, true)
        onAddItem(R.string.feed_order_last_update, FeedSortOrder.LAST_UPDATED_OLD_NEW, FeedSortOrder.LAST_UPDATED_NEW_OLD, false)
        onAddItem(R.string.feed_order_last_download, FeedSortOrder.LAST_DOWNLOAD_OLD_NEW, FeedSortOrder.LAST_DOWNLOAD_NEW_OLD, false)
        onAddItem(R.string.feed_order_last_unread_update, FeedSortOrder.LAST_UPDATED_UNPLAYED_OLD_NEW, FeedSortOrder.LAST_UPDATED_UNPLAYED_NEW_OLD, false)
        onAddItem(R.string.feed_order_new_episodes, FeedSortOrder.NEW_EPISODES_LEAST, FeedSortOrder.NEW_EPISODES_MOST, false)
        onAddItem(R.string.feed_order_unplayed_count, FeedSortOrder.UNPLAYED_OLD_NEW, FeedSortOrder.UNPLAYED_NEW_OLD, false)
        onAddItem(R.string.feed_order_most_played, FeedSortOrder.LEAST_PLAYED, FeedSortOrder.MOST_PLAYED, false)
        onAddItem(R.string.feed_counter_downloaded_unplayed, FeedSortOrder.LEAST_DOWNLAODED_UNPLAYED, FeedSortOrder.MOST_DOWNLOADED_UNPLAYED, false)
        onAddItem(R.string.feed_counter_downloaded, FeedSortOrder.LEAST_DOWNLAODED, FeedSortOrder.MOST_DOWNLOADED, false)
    }

    protected open fun onAddItem(title: Int, ascending: FeedSortOrder, descending: FeedSortOrder, ascendingIsDefault: Boolean) {
        Logd(TAG, "onAddItem $title")
        if (sortOrder == ascending || sortOrder == descending) {
            val item = SortDialogItemActiveBinding.inflate(layoutInflater, binding.gridLayout, false)
            val other: FeedSortOrder
            when {
                ascending == descending -> {
                    item.button.setText(title)
                    other = ascending
                }
                sortOrder == ascending -> {
                    item.button.text = getString(title) + "\u00A0▲"
                    other = descending
                }
                else -> {
                    item.button.text = getString(title) + "\u00A0▼"
                    other = ascending
                }
            }
            item.button.setOnClickListener {
                Logd(TAG, "button clicked: $title ${other.name} ${other.code}")
                sortOrder = other
                populateList()
                setFeedOrder(sortOrder!!.index.toString(), if (sortOrder == ascending) 0 else 1)
                onSelectionChanged()
                EventFlow.postEvent(FlowEvent.FeedsSortedEvent())
            }
            binding.gridLayout.addView(item.root)
        } else {
            val item = SortDialogItemBinding.inflate(layoutInflater, binding.gridLayout, false)
            item.button.setText(title)
            item.button.setOnClickListener {
                sortOrder = if (ascendingIsDefault) ascending else descending
                Logd(TAG, "button clicked 1: ${getString(title)} ${sortOrder!!.name} ${sortOrder!!.code}")
                populateList()
                setFeedOrder(sortOrder!!.index.toString(), if (sortOrder == ascending) 0 else 1)
                onSelectionChanged()
                EventFlow.postEvent(FlowEvent.FeedsSortedEvent())
            }
            binding.gridLayout.addView(item.root)
        }
    }

    protected open fun onSelectionChanged() {}

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        super.onDestroyView()
    }

    private fun setFeedOrder(selected: String, dir: Int) {
        appPrefs.edit().putString(UserPreferences.Prefs.prefDrawerFeedOrder.name, selected).apply()
        appPrefs.edit().putInt(UserPreferences.Prefs.prefDrawerFeedOrderDir.name, dir).apply()
    }
}
