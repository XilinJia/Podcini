package ac.mdiq.podvinci.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
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
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.feed.FeedItemFilterGroup
import ac.mdiq.podvinci.databinding.FilterDialogBinding
import ac.mdiq.podvinci.databinding.FilterDialogRowBinding
import ac.mdiq.podvinci.model.feed.FeedItemFilter

abstract class ItemFilterDialog : BottomSheetDialogFragment() {
    private var rows: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? {
        val layout = inflater.inflate(R.layout.filter_dialog, null, false)
        val binding = FilterDialogBinding.bind(layout)
        rows = binding.filterRows
        val filter = requireArguments().getSerializable(ARGUMENT_FILTER) as FeedItemFilter?

        //add filter rows
        for (item in FeedItemFilterGroup.entries) {
            val rowBinding = FilterDialogRowBinding.inflate(inflater)
            rowBinding.root.addOnButtonCheckedListener { group: MaterialButtonToggleGroup?, checkedId: Int, isChecked: Boolean ->
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
            rows!!.addView(rowBinding.root, rows!!.childCount - 1)
        }

        binding.confirmFiltermenu.setOnClickListener { view1: View? -> dismiss() }
        binding.resetFiltermenu.setOnClickListener { view1: View? ->
            onFilterChanged(emptySet())
            for (i in 0 until rows!!.childCount) {
                if (rows!!.getChildAt(i) is MaterialButtonToggleGroup) {
                    (rows!!.getChildAt(i) as MaterialButtonToggleGroup).clearChecked()
                }
            }
        }

        for (filterId in filter!!.values) {
            if (!TextUtils.isEmpty(filterId)) {
                val button = layout.findViewWithTag<Button>(filterId)
                if (button != null) {
                    (button.parent as MaterialButtonToggleGroup).check(button.id)
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

    private fun setupFullHeight(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet = bottomSheetDialog.findViewById<View>(R.id.design_bottom_sheet) as FrameLayout?
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
            for (i in 0 until rows!!.childCount) {
                if (rows!!.getChildAt(i) !is MaterialButtonToggleGroup) {
                    continue
                }
                val group = rows!!.getChildAt(i) as MaterialButtonToggleGroup
                if (group.checkedButtonId == View.NO_ID) {
                    continue
                }
                val tag = group.findViewById<View>(group.checkedButtonId).tag as String?
                    ?: // Clear buttons use no tag
                    continue
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