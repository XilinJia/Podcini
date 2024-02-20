package ac.mdiq.podcini.dialog

import android.content.Context
import android.content.DialogInterface
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.adapter.SimpleChipAdapter
import ac.mdiq.podcini.databinding.EpisodeFilterDialogBinding
import ac.mdiq.podcini.model.feed.FeedFilter
import ac.mdiq.podcini.view.ItemOffsetDecoration

/**
 * Displays a dialog with a text box for filtering episodes and two radio buttons for exclusion/inclusion
 */
abstract class EpisodeFilterDialog(context: Context?, filter: FeedFilter) : MaterialAlertDialogBuilder(
    context!!) {
    private val viewBinding = EpisodeFilterDialogBinding.inflate(LayoutInflater.from(context))
    private val termList: MutableList<String>

    init {
        setTitle(R.string.episode_filters_label)
        setView(viewBinding.root)

        viewBinding.durationCheckBox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            viewBinding.episodeFilterDurationText.isEnabled = isChecked
        }
        if (filter.hasMinimalDurationFilter()) {
            viewBinding.durationCheckBox.isChecked = true
            // Store minimal duration in seconds, show in minutes
            viewBinding.episodeFilterDurationText
                .setText((filter.minimalDurationFilter / 60).toString())
        } else {
            viewBinding.episodeFilterDurationText.isEnabled = false
        }

        if (filter.excludeOnly()) {
            termList = filter.getExcludeFilter().toMutableList()
            viewBinding.excludeRadio.isChecked = true
        } else {
            termList = filter.getIncludeFilter().toMutableList()
            viewBinding.includeRadio.isChecked = true
        }
        setupWordsList()

        setNegativeButton(R.string.cancel_label, null)
        setPositiveButton(R.string.confirm_label) { dialog: DialogInterface, which: Int ->
            this.onConfirmClick(dialog,
                which)
        }
    }

    private fun setupWordsList() {
        viewBinding.termsRecycler.layoutManager = GridLayoutManager(context, 2)
        viewBinding.termsRecycler.addItemDecoration(ItemOffsetDecoration(context, 4))
        val adapter: SimpleChipAdapter = object : SimpleChipAdapter(context) {
            override fun getChips(): List<String> {
                return termList
            }

            override fun onRemoveClicked(position: Int) {
                termList.removeAt(position)
                notifyDataSetChanged()
            }
        }
        viewBinding.termsRecycler.adapter = adapter
        viewBinding.termsTextInput.setEndIconOnClickListener { v: View? ->
            val newWord = viewBinding.termsTextInput.editText!!.text.toString().replace("\"", "").trim { it <= ' ' }
            if (TextUtils.isEmpty(newWord) || termList.contains(newWord)) {
                return@setEndIconOnClickListener
            }
            termList.add(newWord)
            viewBinding.termsTextInput.editText!!.setText("")
            adapter.notifyDataSetChanged()
        }
    }

    protected abstract fun onConfirmed(filter: FeedFilter)

    private fun onConfirmClick(dialog: DialogInterface, which: Int) {
        var minimalDuration = -1
        if (viewBinding.durationCheckBox.isChecked) {
            try {
                // Store minimal duration in seconds
                minimalDuration = viewBinding.episodeFilterDurationText.text.toString().toInt() * 60
            } catch (e: NumberFormatException) {
                // Do not change anything on error
            }
        }
        var excludeFilter = ""
        var includeFilter = ""
        if (viewBinding.includeRadio.isChecked) {
            includeFilter = toFilterString(termList)
        } else {
            excludeFilter = toFilterString(termList)
        }
        onConfirmed(FeedFilter(includeFilter, excludeFilter, minimalDuration))
    }

    private fun toFilterString(words: List<String>?): String {
        val result = StringBuilder()
        for (word in words!!) {
            result.append("\"").append(word).append("\" ")
        }
        return result.toString()
    }
}
