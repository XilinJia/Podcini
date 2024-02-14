package de.danoeh.antennapod.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.adapter.SimpleChipAdapter
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.core.storage.NavDrawerData.DrawerItem
import de.danoeh.antennapod.databinding.EditTagsDialogBinding
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.view.ItemOffsetDecoration
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class TagSettingsDialog : DialogFragment() {
    private var displayedTags: MutableList<String>? = null
    private var viewBinding: EditTagsDialogBinding? = null
    private var adapter: SimpleChipAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val feedPreferencesList =
            requireArguments().getSerializable(ARG_FEED_PREFERENCES) as ArrayList<FeedPreferences>?
        val commonTags: MutableSet<String> = HashSet(
            feedPreferencesList!![0].getTags())

        for (preference in feedPreferencesList) {
            commonTags.retainAll(preference.getTags())
        }
        displayedTags = ArrayList(commonTags)
        displayedTags?.remove(FeedPreferences.TAG_ROOT)

        viewBinding = EditTagsDialogBinding.inflate(layoutInflater)
        viewBinding!!.tagsRecycler.layoutManager = GridLayoutManager(context, 2)
        viewBinding!!.tagsRecycler.addItemDecoration(ItemOffsetDecoration(requireContext(), 4))
        adapter = object : SimpleChipAdapter(requireContext()) {
            override fun getChips(): List<String?> {
                return displayedTags?: listOf()
            }

            override fun onRemoveClicked(position: Int) {
                displayedTags?.removeAt(position)
                notifyDataSetChanged()
            }
        }
        viewBinding!!.tagsRecycler.adapter = adapter
        viewBinding!!.rootFolderCheckbox.isChecked = commonTags.contains(FeedPreferences.TAG_ROOT)

        viewBinding!!.newTagTextInput.setEndIconOnClickListener { v: View? ->
            addTag(
                viewBinding!!.newTagEditText.text.toString().trim { it <= ' ' })
        }

        loadTags()
        viewBinding!!.newTagEditText.threshold = 1
        viewBinding!!.newTagEditText.setOnTouchListener { v, event ->
            viewBinding!!.newTagEditText.showDropDown()
            viewBinding!!.newTagEditText.requestFocus()
            false
        }

        if (feedPreferencesList.size > 1) {
            viewBinding!!.commonTagsInfo.visibility = View.VISIBLE
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setView(viewBinding!!.root)
        dialog.setTitle(R.string.feed_tags_label)
        dialog.setPositiveButton(android.R.string.ok) { d: DialogInterface?, input: Int ->
            addTag(viewBinding!!.newTagEditText.text.toString().trim { it <= ' ' })
            updatePreferencesTags(feedPreferencesList, commonTags)
        }
        dialog.setNegativeButton(R.string.cancel_label, null)
        return dialog.create()
    }

    private fun loadTags() {
        Observable.fromCallable<List<String>> {
            val data = DBReader.getNavDrawerData(null)
            val items = data.items
            val folders: MutableList<String> = ArrayList()
            for (item in items) {
                if (item.type == DrawerItem.Type.TAG) {
                    if (item.title != null) folders.add(item.title!!)
                }
            }
            folders
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: List<String> ->
                    val acAdapter = ArrayAdapter(
                        requireContext(),
                        R.layout.single_tag_text_view, result)
                    viewBinding!!.newTagEditText.setAdapter(acAdapter)
                }, { error: Throwable? ->
                    Log.e(TAG, Log.getStackTraceString(error))
                })
    }

    private fun addTag(name: String) {
        if (TextUtils.isEmpty(name) || displayedTags!!.contains(name)) {
            return
        }
        displayedTags!!.add(name)
        viewBinding!!.newTagEditText.setText("")
        adapter!!.notifyDataSetChanged()
    }

    private fun updatePreferencesTags(feedPreferencesList: List<FeedPreferences>?, commonTags: Set<String?>) {
        if (viewBinding!!.rootFolderCheckbox.isChecked) {
            displayedTags!!.add(FeedPreferences.TAG_ROOT)
        }
        for (preferences in feedPreferencesList!!) {
            preferences.getTags().removeAll(commonTags)
            preferences.getTags().addAll(displayedTags!!)
            DBWriter.setFeedPreferences(preferences)
        }
    }

    companion object {
        const val TAG: String = "TagSettingsDialog"
        private const val ARG_FEED_PREFERENCES = "feed_preferences"
        fun newInstance(preferencesList: List<FeedPreferences>?): TagSettingsDialog {
            val fragment = TagSettingsDialog()
            val args = Bundle()
            args.putSerializable(ARG_FEED_PREFERENCES, ArrayList(preferencesList))
            fragment.arguments = args
            return fragment
        }
    }
}
