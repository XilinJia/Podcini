package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EditTagsDialogBinding
import ac.mdiq.podcini.storage.database.Feeds.buildTags
import ac.mdiq.podcini.storage.database.Feeds.getTags
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.FeedPreferences
import ac.mdiq.podcini.ui.adapter.SimpleChipAdapter
import ac.mdiq.podcini.ui.utils.ItemOffsetDecoration
import ac.mdiq.podcini.util.Logd
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.annotation.OptIn
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TagSettingsDialog : DialogFragment() {

    private var _binding: EditTagsDialogBinding? = null
    private val binding get() = _binding!!

    private var feedList:  MutableList<Feed> = mutableListOf()

    private lateinit var displayedTags: MutableList<String>
    private lateinit var adapter: SimpleChipAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val commonTags: MutableSet<String> = if (feedList.isEmpty() || feedList[0].preferences == null) mutableSetOf() else HashSet(feedList[0].preferences!!.tags)
        for (feed in feedList) {
            if (feed.preferences != null) commonTags.retainAll(feed.preferences!!.tags)
        }

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

        if (feedList.size > 1) binding.commonTagsInfo.visibility = View.VISIBLE

        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setView(binding.root)
        dialog.setTitle(R.string.feed_tags_label)
        dialog.setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
            addTag(binding.newTagEditText.text.toString().trim { it <= ' ' })
            updatePreferencesTags(commonTags)
            buildTags()
        }
        dialog.setNegativeButton(R.string.cancel_label, null)
        return dialog.create()
    }

    private fun loadTags() {
        val acAdapter = ArrayAdapter(requireContext(), R.layout.single_tag_text_view, getTags())
        binding.newTagEditText.setAdapter(acAdapter)
    }

    private fun addTag(name: String) {
        if (name.isEmpty() || displayedTags.contains(name)) return

        displayedTags.add(name)
        binding.newTagEditText.setText("")
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        super.onDestroyView()
    }

    @OptIn(UnstableApi::class) private fun updatePreferencesTags(commonTags: Set<String>) {
        for (i in 0..feedList.size-1) {
            val f = feedList[i]
            Logd(TAG, "${f.title} $displayedTags")
            feedList[i] = upsertBlk(f) {
                if (it.preferences != null) {
                    it.preferences!!.tags.removeAll(commonTags)
                    it.preferences!!.tags.addAll(displayedTags)
                }
            }
        }
    }

    private fun setFeedList(feedLst_: List<Feed>) {
        feedList = feedLst_.toMutableList()
    }

    companion object {
        val TAG = TagSettingsDialog::class.simpleName ?: "Anonymous"

        fun newInstance(feedList: List<Feed>): TagSettingsDialog {
            val fragment = TagSettingsDialog()
            fragment.setFeedList(feedList)
            return fragment
        }
    }
}
