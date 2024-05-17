package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EditTagsDialogBinding
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedPreferences
import ac.mdiq.podcini.ui.adapter.SimpleChipAdapter
import ac.mdiq.podcini.ui.view.ItemOffsetDecoration
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

class TagSettingsDialog : DialogFragment() {
    private var _binding: EditTagsDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var displayedTags: MutableList<String>
    private lateinit var adapter: SimpleChipAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val serializedData: Serializable? = requireArguments().getSerializable(ARG_FEED_PREFERENCES)
        val feedPreferencesList = if (serializedData is ArrayList<*>) serializedData.filterIsInstance<FeedPreferences>() else listOf()
//        val feedPreferencesList = serializedData as? List<FeedPreferences> ?: listOf()
        val commonTags: MutableSet<String> = if (feedPreferencesList.isEmpty()) mutableSetOf() else HashSet(feedPreferencesList[0].getTags())

        for (preference in feedPreferencesList) commonTags.retainAll(preference.getTags())

        displayedTags = ArrayList(commonTags)
        displayedTags.remove(FeedPreferences.TAG_ROOT)

        _binding = EditTagsDialogBinding.inflate(layoutInflater)
        binding.tagsRecycler.layoutManager = GridLayoutManager(context, 2)
        binding.tagsRecycler.addItemDecoration(ItemOffsetDecoration(requireContext(), 4))
        adapter = object : SimpleChipAdapter(requireContext()) {
            override fun getChips(): List<String> {
                return displayedTags
            }

            override fun onRemoveClicked(position: Int) {
                displayedTags.removeAt(position)
                notifyDataSetChanged()
            }
        }
        binding.tagsRecycler.adapter = adapter
//        binding.rootFolderCheckbox.isChecked = commonTags.contains(FeedPreferences.TAG_ROOT)

        binding.newTagTextInput.setEndIconOnClickListener {
            addTag(binding.newTagEditText.text.toString().trim { it <= ' ' })
        }

        loadTags()
        binding.newTagEditText.threshold = 1
        binding.newTagEditText.setOnTouchListener { _, _ ->
            binding.newTagEditText.showDropDown()
            binding.newTagEditText.requestFocus()
            false
        }

        if (feedPreferencesList.size > 1) binding.commonTagsInfo.visibility = View.VISIBLE

        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setView(binding.root)
        dialog.setTitle(R.string.feed_tags_label)
        dialog.setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
            addTag(binding.newTagEditText.text.toString().trim { it <= ' ' })
            updatePreferencesTags(feedPreferencesList, commonTags)
            DBReader.buildTags()
            EventFlow.postEvent(FlowEvent.FeedTagsChangedEvent())
        }
        dialog.setNegativeButton(R.string.cancel_label, null)
        return dialog.create()
    }

    private fun loadTags() {
//        Observable.fromCallable {
//            DBReader.getTags()
//        }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe(
//                { result: List<String> ->
//                    val acAdapter = ArrayAdapter(requireContext(), R.layout.single_tag_text_view, result)
//                    binding.newTagEditText.setAdapter(acAdapter)
//                }, { error: Throwable? ->
//                    Log.e(TAG, Log.getStackTraceString(error))
//                })

        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    DBReader.getTags()
                }
                withContext(Dispatchers.Main) {
                    val acAdapter = ArrayAdapter(requireContext(), R.layout.single_tag_text_view, result)
                    binding.newTagEditText.setAdapter(acAdapter)
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    private fun addTag(name: String) {
        if (name.isEmpty() || displayedTags.contains(name)) return

        displayedTags.add(name)
        binding.newTagEditText.setText("")
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    @OptIn(UnstableApi::class) private fun updatePreferencesTags(feedPreferencesList: List<FeedPreferences>, commonTags: Set<String>) {
//        if (binding.rootFolderCheckbox.isChecked) {
//            displayedTags.add(FeedPreferences.TAG_ROOT)
//        }
        for (preferences in feedPreferencesList) {
            preferences.getTags().removeAll(commonTags)
            preferences.getTags().addAll(displayedTags)
            DBWriter.persistFeedPreferences(preferences)
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
