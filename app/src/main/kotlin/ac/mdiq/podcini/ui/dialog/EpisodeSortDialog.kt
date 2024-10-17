package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SortDialogBinding
import ac.mdiq.podcini.databinding.SortDialogItemActiveBinding
import ac.mdiq.podcini.databinding.SortDialogItemBinding
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.Companion.TAG
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CompoundButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

open class EpisodeSortDialog : BottomSheetDialogFragment() {
    protected var _binding: SortDialogBinding? = null
    protected val binding get() = _binding!!

    protected var sortOrder: EpisodeSortOrder? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = SortDialogBinding.inflate(inflater)
        populateList()
        binding.keepSortedCheckbox.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> this@EpisodeSortDialog.onSelectionChanged() }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
    
    private fun populateList() {
        binding.gridLayout.removeAllViews()
        onAddItem(R.string.episode_title, EpisodeSortOrder.EPISODE_TITLE_A_Z, EpisodeSortOrder.EPISODE_TITLE_Z_A, true)
        onAddItem(R.string.feed_title, EpisodeSortOrder.FEED_TITLE_A_Z, EpisodeSortOrder.FEED_TITLE_Z_A, true)
        onAddItem(R.string.duration, EpisodeSortOrder.DURATION_SHORT_LONG, EpisodeSortOrder.DURATION_LONG_SHORT, true)
        onAddItem(R.string.publish_date, EpisodeSortOrder.DATE_OLD_NEW, EpisodeSortOrder.DATE_NEW_OLD, false)
        onAddItem(R.string.download_date, EpisodeSortOrder.DOWNLOAD_DATE_OLD_NEW, EpisodeSortOrder.DOWNLOAD_DATE_NEW_OLD, false)
        onAddItem(R.string.last_played_date, EpisodeSortOrder.PLAYED_DATE_OLD_NEW, EpisodeSortOrder.PLAYED_DATE_NEW_OLD, false)
        onAddItem(R.string.completed_date, EpisodeSortOrder.COMPLETED_DATE_OLD_NEW, EpisodeSortOrder.COMPLETED_DATE_NEW_OLD, false)
        onAddItem(R.string.size, EpisodeSortOrder.SIZE_SMALL_LARGE, EpisodeSortOrder.SIZE_LARGE_SMALL, false)
        onAddItem(R.string.filename, EpisodeSortOrder.EPISODE_FILENAME_A_Z, EpisodeSortOrder.EPISODE_FILENAME_Z_A, true)
        onAddItem(R.string.random, EpisodeSortOrder.RANDOM, EpisodeSortOrder.RANDOM, true)
        onAddItem(R.string.smart_shuffle, EpisodeSortOrder.SMART_SHUFFLE_OLD_NEW, EpisodeSortOrder.SMART_SHUFFLE_NEW_OLD, false)
    }

    protected open fun onAddItem(title: Int, ascending: EpisodeSortOrder, descending: EpisodeSortOrder, ascendingIsDefault: Boolean) {
        if (sortOrder == ascending || sortOrder == descending) {
            val item = SortDialogItemActiveBinding.inflate(layoutInflater, binding.gridLayout, false)
            val other: EpisodeSortOrder
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
                sortOrder = other
                populateList()
                onSelectionChanged()
            }
            binding.gridLayout.addView(item.root)
        } else {
            val item = SortDialogItemBinding.inflate(layoutInflater, binding.gridLayout, false)
            item.button.setText(title)
            item.button.setOnClickListener {
                sortOrder = if (ascendingIsDefault) ascending else descending
                populateList()
                onSelectionChanged()
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
}
