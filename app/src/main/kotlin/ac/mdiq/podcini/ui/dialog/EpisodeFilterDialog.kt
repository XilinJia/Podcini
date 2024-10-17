package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FilterDialogBinding
import ac.mdiq.podcini.databinding.FilterDialogRowBinding
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.Companion.TAG
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup

abstract class EpisodeFilterDialog : BottomSheetDialogFragment() {
    private lateinit var rows: LinearLayout
    private var _binding: FilterDialogBinding? = null
    private val binding get() = _binding!!

    var filter: EpisodeFilter? = null
    private val buttonMap: MutableMap<String, Button> = mutableMapOf()
    val filtersDisabled: MutableSet<FeedItemFilterGroup> = mutableSetOf()

    private val newFilterValues: Set<String>
        get() {
            val newFilterValues: MutableSet<String> = HashSet()
            for (i in 0 until rows.childCount) {
                if (rows.getChildAt(i) !is MaterialButtonToggleGroup) continue
                val group = rows.getChildAt(i) as MaterialButtonToggleGroup
                if (group.checkedButtonId == View.NO_ID) continue
                val tag = group.findViewById<View>(group.checkedButtonId).tag as? String ?: continue
                newFilterValues.add(tag)
            }
            return newFilterValues
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(R.layout.filter_dialog, container, false)
        _binding = FilterDialogBinding.bind(layout)
        rows = binding.filterRows
        Logd("EpisodeFilterDialog", "fragment onCreateView")

        //add filter rows
        for (item in FeedItemFilterGroup.entries) {
//            Logd("EpisodeFilterDialog", "FeedItemFilterGroup: ${item.values[0].filterId} ${item.values[1].filterId}")
            if (item in filtersDisabled) continue

            val rBinding = FilterDialogRowBinding.inflate(inflater)
            rBinding.filterButton1.setOnClickListener { onFilterChanged(newFilterValues) }
            rBinding.filterButton2.setOnClickListener { onFilterChanged(newFilterValues) }

            rBinding.filterButton1.setText(item.values[0].displayName)
            rBinding.filterButton1.tag = item.values[0].filterId
            buttonMap[item.values[0].filterId] = rBinding.filterButton1
            rBinding.filterButton2.setText(item.values[1].displayName)
            rBinding.filterButton2.tag = item.values[1].filterId
            buttonMap[item.values[1].filterId] = rBinding.filterButton2
            rBinding.filterButton1.maxLines = 3
            rBinding.filterButton1.isSingleLine = false
            rBinding.filterButton2.maxLines = 3
            rBinding.filterButton2.isSingleLine = false
            rows.addView(rBinding.root, rows.childCount - 1)
        }

        binding.confirmFiltermenu.setOnClickListener { dismiss() }
        binding.resetFiltermenu.setOnClickListener {
            onFilterChanged(emptySet())
            for (i in 0 until rows.childCount) {
                if (rows.getChildAt(i) is MaterialButtonToggleGroup) (rows.getChildAt(i) as MaterialButtonToggleGroup).clearChecked()
            }
        }

        if (filter != null) {
            for (filterId in filter!!.values) {
                if (filterId.isNotEmpty()) {
                    val button = buttonMap[filterId]
                    if (button != null) (button.parent as MaterialButtonToggleGroup).check(button.id)
                }
            }
        }
        return layout
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        super.onDestroyView()
    }

    abstract fun onFilterChanged(newFilterValues: Set<String>)

    enum class FeedItemFilterGroup(vararg values: ItemProperties) {
        PLAYED(ItemProperties(R.string.hide_played_episodes_label, EpisodeFilter.States.played.name), ItemProperties(R.string.not_played, EpisodeFilter.States.unplayed.name)),
        PAUSED(ItemProperties(R.string.hide_paused_episodes_label, EpisodeFilter.States.paused.name), ItemProperties(R.string.not_paused, EpisodeFilter.States.not_paused.name)),
        FAVORITE(ItemProperties(R.string.hide_is_favorite_label, EpisodeFilter.States.is_favorite.name), ItemProperties(R.string.not_favorite, EpisodeFilter.States.not_favorite.name)),
        MEDIA(ItemProperties(R.string.has_media, EpisodeFilter.States.has_media.name), ItemProperties(R.string.no_media, EpisodeFilter.States.no_media.name)),
        QUEUED(ItemProperties(R.string.queued_label, EpisodeFilter.States.queued.name), ItemProperties(R.string.not_queued_label, EpisodeFilter.States.not_queued.name)),
        DOWNLOADED(ItemProperties(R.string.downloaded_label, EpisodeFilter.States.downloaded.name), ItemProperties(R.string.not_downloaded_label, EpisodeFilter.States.not_downloaded.name)),
        AUTO_DOWNLOADABLE(ItemProperties(R.string.auto_downloadable_label, EpisodeFilter.States.auto_downloadable.name), ItemProperties(R.string.not_auto_downloadable_label, EpisodeFilter.States.not_auto_downloadable.name));

        @JvmField
        val values: Array<ItemProperties> = arrayOf(*values)

        class ItemProperties(@JvmField val displayName: Int, @JvmField val filterId: String)
    }
}
