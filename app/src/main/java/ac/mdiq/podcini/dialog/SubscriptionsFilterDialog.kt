package ac.mdiq.podcini.dialog

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
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.feed.SubscriptionsFilterGroup
import ac.mdiq.podcini.databinding.FilterDialogBinding
import ac.mdiq.podcini.databinding.FilterDialogRowBinding
import ac.mdiq.podcini.event.UnreadItemsUpdateEvent
import ac.mdiq.podcini.model.feed.SubscriptionsFilter
import ac.mdiq.podcini.storage.preferences.UserPreferences
import ac.mdiq.podcini.storage.preferences.UserPreferences.subscriptionsFilter
import org.greenrobot.eventbus.EventBus
import java.util.*

class SubscriptionsFilterDialog : BottomSheetDialogFragment() {
    private var rows: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View {
        val subscriptionsFilter = subscriptionsFilter
        val dialogBinding = FilterDialogBinding.inflate(inflater)
        rows = dialogBinding.filterRows

        for (item in SubscriptionsFilterGroup.entries) {
            val binding = FilterDialogRowBinding.inflate(inflater)
            binding.root.addOnButtonCheckedListener { group: MaterialButtonToggleGroup?, checkedId: Int, isChecked: Boolean ->
                updateFilter(
                    filterValues)
            }
            binding.buttonGroup.weightSum = item.values.size.toFloat()
            binding.filterButton1.setText(item.values[0].displayName)
            binding.filterButton1.tag = item.values[0].filterId
            if (item.values.size == 2) {
                binding.filterButton2.setText(item.values[1].displayName)
                binding.filterButton2.tag = item.values[1].filterId
            } else {
                binding.filterButton2.visibility = View.GONE
            }
            binding.filterButton1.maxLines = 3
            binding.filterButton1.isSingleLine = false
            binding.filterButton2.maxLines = 3
            binding.filterButton2.isSingleLine = false
            rows!!.addView(binding.root, rows!!.childCount - 1)
        }

        val filterValues: Set<String> = HashSet(listOf(*subscriptionsFilter.values))
        for (filterId in filterValues) {
            if (!TextUtils.isEmpty(filterId)) {
                val button = dialogBinding.root.findViewWithTag<Button>(filterId)
                if (button != null) {
                    (button.parent as MaterialButtonToggleGroup).check(button.id)
                }
            }
        }

        dialogBinding.confirmFiltermenu.setOnClickListener { view: View? ->
            updateFilter(this.filterValues)
            dismiss()
        }
        dialogBinding.resetFiltermenu.setOnClickListener { view: View? ->
            updateFilter(emptySet())
            for (i in 0 until rows!!.childCount) {
                if (rows!!.getChildAt(i) is MaterialButtonToggleGroup) {
                    (rows!!.getChildAt(i) as MaterialButtonToggleGroup).clearChecked()
                }
            }
        }
        return dialogBinding.root
    }

    private val filterValues: Set<String>
        get() {
            val filterValues: MutableSet<String> = HashSet()
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
                filterValues.add(tag)
            }
            return filterValues
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
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val layoutParams = bottomSheet.layoutParams
            bottomSheet.layoutParams = layoutParams
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    companion object {
        private fun updateFilter(filterValues: Set<String>) {
            val subscriptionsFilter = SubscriptionsFilter(filterValues.toTypedArray<String>())
            UserPreferences.subscriptionsFilter = subscriptionsFilter
            EventBus.getDefault().post(UnreadItemsUpdateEvent())
        }
    }
}
