package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EditTagsDialogBinding
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedPreferences
import ac.mdiq.podcini.ui.adapter.SimpleChipAdapter
import ac.mdiq.podcini.ui.view.ItemOffsetDecoration
import ac.mdiq.podcini.util.event.FeedTagsChangedEvent
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import androidx.annotation.OptIn
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.io.Serializable

class TagSettingsDialog : DialogFragment() {
    private lateinit var displayedTags: MutableList<String>
    private lateinit var viewBinding: EditTagsDialogBinding
    private lateinit var adapter: SimpleChipAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val serializedData: Serializable? = requireArguments().getSerializable(ARG_FEED_PREFERENCES)
        val feedPreferencesList = if (serializedData is ArrayList<*>) serializedData.filterIsInstance<FeedPreferences>() else listOf()
//        val feedPreferencesList = serializedData as? List<FeedPreferences> ?: listOf()
        val commonTags: MutableSet<String> = if (feedPreferencesList.isEmpty()) mutableSetOf() else HashSet(feedPreferencesList[0].getTags())

        for (preference in feedPreferencesList) {
            commonTags.retainAll(preference.getTags())
        }
        displayedTags = ArrayList(commonTags)
        displayedTags.remove(FeedPreferences.TAG_ROOT)

        viewBinding = EditTagsDialogBinding.inflate(layoutInflater)
        viewBinding.tagsRecycler.layoutManager = GridLayoutManager(context, 2)
        viewBinding.tagsRecycler.addItemDecoration(ItemOffsetDecoration(requireContext(), 4))
        adapter = object : SimpleChipAdapter(requireContext()) {
            override fun getChips(): List<String> {
                return displayedTags
            }

            override fun onRemoveClicked(position: Int) {
                displayedTags.removeAt(position)
                notifyDataSetChanged()
            }
        }
        viewBinding.tagsRecycler.adapter = adapter
//        viewBinding.rootFolderCheckbox.isChecked = commonTags.contains(FeedPreferences.TAG_ROOT)

        viewBinding.newTagTextInput.setEndIconOnClickListener {
            addTag(viewBinding.newTagEditText.text.toString().trim { it <= ' ' })
        }

        loadTags()
        viewBinding.newTagEditText.threshold = 1
        viewBinding.newTagEditText.setOnTouchListener { _, _ ->
            viewBinding.newTagEditText.showDropDown()
            viewBinding.newTagEditText.requestFocus()
            false
        }

        if (feedPreferencesList.size > 1) {
            viewBinding.commonTagsInfo.visibility = View.VISIBLE
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setView(viewBinding.root)
        dialog.setTitle(R.string.feed_tags_label)
        dialog.setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
            addTag(viewBinding.newTagEditText.text.toString().trim { it <= ' ' })
            updatePreferencesTags(feedPreferencesList, commonTags)
            DBReader.buildTags()
            EventBus.getDefault().post(FeedTagsChangedEvent())
        }
        dialog.setNegativeButton(R.string.cancel_label, null)
        return dialog.create()
    }

    private fun loadTags() {
        Observable.fromCallable {
            DBReader.getTags()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: List<String> ->
                    val acAdapter = ArrayAdapter(requireContext(), R.layout.single_tag_text_view, result)
                    viewBinding.newTagEditText.setAdapter(acAdapter)
                }, { error: Throwable? ->
                    Log.e(TAG, Log.getStackTraceString(error))
                })
    }

    private fun addTag(name: String) {
        if (name.isEmpty() || displayedTags.contains(name)) {
            return
        }
        displayedTags.add(name)
        viewBinding.newTagEditText.setText("")
        adapter.notifyDataSetChanged()
    }

    @OptIn(UnstableApi::class) private fun updatePreferencesTags(feedPreferencesList: List<FeedPreferences>, commonTags: Set<String>) {
//        if (viewBinding.rootFolderCheckbox.isChecked) {
//            displayedTags.add(FeedPreferences.TAG_ROOT)
//        }
        for (preferences in feedPreferencesList) {
            preferences.getTags().removeAll(commonTags)
            preferences.getTags().addAll(displayedTags)
            DBWriter.setFeedPreferences(preferences)
        }
    }

    companion object {
        const val TAG: String = "TagSettingsDialog"
        private const val ARG_FEED_PREFERENCES = "feed_preferences"
        fun newInstance(preferencesList: List<FeedPreferences>): TagSettingsDialog {
            val fragment = TagSettingsDialog()
            val args = Bundle()
            args.putSerializable(ARG_FEED_PREFERENCES, ArrayList(preferencesList))
            fragment.arguments = args
            return fragment
        }
    }
}
