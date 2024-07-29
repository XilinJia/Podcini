package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FilterDialogBinding
import ac.mdiq.podcini.databinding.FilterDialogRowBinding
import ac.mdiq.podcini.storage.model.FeedFilter
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.Companion.TAG
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.Companion.feedsFilter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
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
import org.apache.commons.lang3.StringUtils

class FeedFilterDialog : BottomSheetDialogFragment() {
    private lateinit var rows: LinearLayout
    private var _binding: FilterDialogBinding? = null
    private val binding get() = _binding!!

    var filter: FeedFilter? = null
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
        val layout = inflater.inflate(R.layout.filter_dialog, container, false)
        _binding = FilterDialogBinding.bind(layout)
        rows = binding.filterRows
        Logd("FeedFilterDialog", "fragment onCreateView")

        //add filter rows
        for (item in FeedFilterGroup.entries) {
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
        Logd(TAG, "onDestroyView")
        _binding = null
        super.onDestroyView()
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

    fun onFilterChanged(newFilterValues: Set<String>) {
        feedsFilter = StringUtils.join(newFilterValues, ",")
        Logd(TAG, "onFilterChanged: $feedsFilter")
        EventFlow.postEvent(FlowEvent.FeedsFilterEvent(newFilterValues))
    }

    enum class FeedFilterGroup(vararg values: ItemProperties) {
        KEEP_UPDATED(ItemProperties(R.string.keep_updated, FeedFilter.States.keepUpdated.name), ItemProperties(R.string.not_keep_updated, FeedFilter.States.not_keepUpdated.name)),
        PLAY_SPEED(ItemProperties(R.string.global_speed, FeedFilter.States.global_playSpeed.name), ItemProperties(R.string.custom_speed, FeedFilter.States.custom_playSpeed.name)),
        SKIPS(ItemProperties(R.string.has_skips, FeedFilter.States.has_skips.name), ItemProperties(R.string.no_skips, FeedFilter.States.no_skips.name)),
        AUTO_DELETE(ItemProperties(R.string.always_auto_delete, FeedFilter.States.always_auto_delete.name), ItemProperties(R.string.never_auto_delete, FeedFilter.States.never_auto_delete.name)),
        AUTO_DOWNLOAD(ItemProperties(R.string.auto_download, FeedFilter.States.autoDownload.name), ItemProperties(R.string.not_auto_download, FeedFilter.States.not_autoDownload.name));

        @JvmField
        val values: Array<ItemProperties> = arrayOf(*values)

        class ItemProperties(@JvmField val displayName: Int, @JvmField val filterId: String)
    }

    companion object {
        fun newInstance(filter: FeedFilter?): FeedFilterDialog {
            val dialog = FeedFilterDialog()
            dialog.filter = filter
            return dialog
        }
    }
}
