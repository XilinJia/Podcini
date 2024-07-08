package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FilterDialogBinding
import ac.mdiq.podcini.databinding.FilterDialogRowBinding
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.util.Logd
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup

abstract class EpisodeFilterDialog : BottomSheetDialogFragment() {
    private lateinit var rows: LinearLayout
    private var _binding: FilterDialogBinding? = null
    private val binding get() = _binding!!

    var filter: EpisodeFilter? = null
    private val buttonMap: MutableMap<String, Button> = mutableMapOf()

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
        val layout = inflater.inflate(R.layout.filter_dialog, null, false)
        _binding = FilterDialogBinding.bind(layout)
        rows = binding.filterRows
        Logd("ItemFilterDialog", "fragment onCreateView")

        //add filter rows
        for (item in FeedItemFilterGroup.entries) {
//            Logd("EpisodeFilterDialog", "FeedItemFilterGroup: ${item.values[0].filterId} ${item.values[1].filterId}")
            val rBinding = FilterDialogRowBinding.inflate(inflater)
//            rowBinding.root.addOnButtonCheckedListener { _: MaterialButtonToggleGroup?, _: Int, _: Boolean ->
//                onFilterChanged(newFilterValues)
//            }
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface: DialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            setupFullHeight(bottomSheetDialog)
        }
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupFullHeight(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet = bottomSheetDialog.findViewById<View>(com.leinardi.android.speeddial.R.id.design_bottom_sheet) as? FrameLayout
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val layoutParams = bottomSheet.layoutParams
            bottomSheet.layoutParams = layoutParams
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    abstract fun onFilterChanged(newFilterValues: Set<String>)

    enum class FeedItemFilterGroup(vararg values: ItemProperties) {
        PLAYED(ItemProperties(R.string.hide_played_episodes_label, EpisodeFilter.PLAYED), ItemProperties(R.string.not_played, EpisodeFilter.UNPLAYED)),
        PAUSED(ItemProperties(R.string.hide_paused_episodes_label, EpisodeFilter.PAUSED), ItemProperties(R.string.not_paused, EpisodeFilter.NOT_PAUSED)),
        FAVORITE(ItemProperties(R.string.hide_is_favorite_label, EpisodeFilter.IS_FAVORITE), ItemProperties(R.string.not_favorite, EpisodeFilter.NOT_FAVORITE)),
        MEDIA(ItemProperties(R.string.has_media, EpisodeFilter.HAS_MEDIA), ItemProperties(R.string.no_media, EpisodeFilter.NO_MEDIA)),
        QUEUED(ItemProperties(R.string.queued_label, EpisodeFilter.QUEUED), ItemProperties(R.string.not_queued_label, EpisodeFilter.NOT_QUEUED)),
        DOWNLOADED(ItemProperties(R.string.hide_downloaded_episodes_label, EpisodeFilter.DOWNLOADED), ItemProperties(R.string.hide_not_downloaded_episodes_label, EpisodeFilter.NOT_DOWNLOADED));

        @JvmField
        val values: Array<ItemProperties> = arrayOf(*values)

        class ItemProperties(@JvmField val displayName: Int, @JvmField val filterId: String)
    }
}
