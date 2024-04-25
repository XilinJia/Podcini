package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FilterDialogBinding
import ac.mdiq.podcini.databinding.FilterDialogRowBinding
import ac.mdiq.podcini.feed.FeedItemFilterGroup
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
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

abstract class ItemFilterDialog : BottomSheetDialogFragment() {
    private lateinit var rows: LinearLayout
    private var _binding: FilterDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(R.layout.filter_dialog, null, false)
        _binding = FilterDialogBinding.bind(layout)
        rows = binding.filterRows
        val filter = requireArguments().getSerializable(ARGUMENT_FILTER) as FeedItemFilter?
        Log.d("ItemFilterDialog", "fragment onCreateView")

        //add filter rows
        for (item in FeedItemFilterGroup.entries) {
            val rowBinding = FilterDialogRowBinding.inflate(inflater)
            rowBinding.root.addOnButtonCheckedListener { _: MaterialButtonToggleGroup?, _: Int, _: Boolean ->
                onFilterChanged(newFilterValues)
            }
            rowBinding.filterButton1.setText(item.values[0].displayName)
            rowBinding.filterButton1.tag = item.values[0].filterId
            rowBinding.filterButton2.setText(item.values[1].displayName)
            rowBinding.filterButton2.tag = item.values[1].filterId
            rowBinding.filterButton1.maxLines = 3
            rowBinding.filterButton1.isSingleLine = false
            rowBinding.filterButton2.maxLines = 3
            rowBinding.filterButton2.isSingleLine = false
            rows.addView(rowBinding.root, rows.childCount - 1)
        }

        binding.confirmFiltermenu.setOnClickListener { dismiss() }
        binding.resetFiltermenu.setOnClickListener {
            onFilterChanged(emptySet())
            for (i in 0 until rows.childCount) {
                if (rows.getChildAt(i) is MaterialButtonToggleGroup) (rows.getChildAt(i) as MaterialButtonToggleGroup).clearChecked()
            }
        }

        if (filter != null) {
            for (filterId in filter.values) {
                if (filterId.isNotEmpty()) {
                    val button = layout.findViewWithTag<Button>(filterId)
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
        val bottomSheet = bottomSheetDialog.findViewById<View>(R.id.design_bottom_sheet) as? FrameLayout
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val layoutParams = bottomSheet.layoutParams
            bottomSheet.layoutParams = layoutParams
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    protected val newFilterValues: Set<String>
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

    abstract fun onFilterChanged(newFilterValues: Set<String>)

    companion object {
        @JvmStatic
        protected val ARGUMENT_FILTER: String = "filter"
    }
}
