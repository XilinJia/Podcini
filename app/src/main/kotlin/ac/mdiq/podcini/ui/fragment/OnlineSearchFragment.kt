package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AddfeedBinding
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.net.feed.discovery.*
import ac.mdiq.podcini.preferences.OpmlBackupAgent.Companion.isOPMLRestared
import ac.mdiq.podcini.preferences.OpmlBackupAgent.Companion.performRestore
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.OpmlImportActivity
import ac.mdiq.podcini.ui.fragment.NavDrawerFragment.Companion.feedCount
import ac.mdiq.podcini.util.Logd
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Provides actions for adding new podcast subscriptions.
 */
@UnstableApi
class OnlineSearchFragment : Fragment() {

    private var _binding: AddfeedBinding? = null
    private val binding get() = _binding!!

    private var activity: MainActivity? = null
    private var displayUpArrow = false

    private val chooseOpmlImportPathLauncher = registerForActivityResult<String, Uri>(ActivityResultContracts.GetContent()) { uri: Uri? ->
        this.chooseOpmlImportPathResult(uri) }

    private val addLocalFolderLauncher = registerForActivityResult<Uri?, Uri>(AddLocalFolder()) { uri: Uri? -> this.addLocalFolderResult(uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = AddfeedBinding.inflate(inflater)
        activity = getActivity() as? MainActivity
        Logd(TAG, "fragment onCreateView")
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        (getActivity() as MainActivity).setupToolbarToggle(binding.toolbar, displayUpArrow)

        binding.searchButton.setOnClickListener { performSearch() }
        binding.searchVistaGuideButton.setOnClickListener { activity?.loadChildFragment(SearchResultsFragment.newInstance(VistaGuidePodcastSearcher::class.java)) }
        binding.searchItunesButton.setOnClickListener { activity?.loadChildFragment(SearchResultsFragment.newInstance(ItunesPodcastSearcher::class.java)) }
        binding.searchFyydButton.setOnClickListener { activity?.loadChildFragment(SearchResultsFragment.newInstance(FyydPodcastSearcher::class.java)) }
        binding.searchGPodderButton.setOnClickListener { activity?.loadChildFragment(SearchResultsFragment.newInstance(GpodnetPodcastSearcher::class.java)) }
        binding.searchPodcastIndexButton.setOnClickListener { activity?.loadChildFragment(SearchResultsFragment.newInstance(PodcastIndexPodcastSearcher::class.java)) }
        binding.combinedFeedSearchEditText.setOnEditorActionListener { _: TextView?, _: Int, _: KeyEvent? ->
            performSearch()
            true
        }
        binding.addViaUrlButton.setOnClickListener { showAddViaUrlDialog() }
        binding.opmlImportButton.setOnClickListener {
            try { chooseOpmlImportPathLauncher.launch("*/*")
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
                activity?.showSnackbarAbovePlayer(R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
            }
        }
        binding.addLocalFolderButton.setOnClickListener {
            try { addLocalFolderLauncher.launch(null)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
                activity?.showSnackbarAbovePlayer(R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
            }
        }

        if (isOPMLRestared && feedCount == 0) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_subscriptions_label)
                .setMessage(R.string.restore_subscriptions_summary)
                .setPositiveButton("Yes") { dialog, _ ->
                    performRestore(requireContext())
                    dialog.dismiss()
                    parentFragmentManager.popBackStack()
                }
                .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                .show()
        }
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun showAddViaUrlDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(R.string.add_podcast_by_url)
        val dialogBinding = EditTextDialogBinding.inflate(layoutInflater)
        dialogBinding.editText.setHint(R.string.add_podcast_by_url_hint)

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData: ClipData? = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0 && clipData.getItemAt(0).text != null) {
            val clipboardContent: String = clipData.getItemAt(0).text.toString()
            if (clipboardContent.trim { it <= ' ' }.startsWith("http")) dialogBinding.editText.setText(clipboardContent.trim { it <= ' ' })
        }
        builder.setView(dialogBinding.root)
        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int -> addUrl(dialogBinding.editText.text.toString()) }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.show()
    }

    private fun addUrl(url: String) {
        val fragment: Fragment = OnlineFeedViewFragment.newInstance(url)
        (activity as MainActivity).loadChildFragment(fragment)
    }

    private fun performSearch() {
        binding.combinedFeedSearchEditText.clearFocus()
        val inVal = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inVal.hideSoftInputFromWindow(binding.combinedFeedSearchEditText.windowToken, 0)
        val query = binding.combinedFeedSearchEditText.text.toString()
        if (query.matches("http[s]?://.*".toRegex())) {
            addUrl(query)
            return
        }
        activity?.loadChildFragment(SearchResultsFragment.newInstance(CombinedSearcher::class.java, query))
        binding.combinedFeedSearchEditText.post { binding.combinedFeedSearchEditText.setText("") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        super.onDestroyView()
    }

    private fun chooseOpmlImportPathResult(uri: Uri?) {
        if (uri == null) return

        val intent = Intent(context, OpmlImportActivity::class.java)
        intent.setData(uri)
        startActivity(intent)
    }

    @UnstableApi private fun addLocalFolderResult(uri: Uri?) {
        if (uri == null) return
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val feed = withContext(Dispatchers.IO) { addLocalFolder(uri) }
                withContext(Dispatchers.Main) {
                    if (feed != null) {
                        val fragment: Fragment = FeedEpisodesFragment.newInstance(feed.id)
                        (getActivity() as MainActivity).loadChildFragment(fragment)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                (getActivity() as MainActivity).showSnackbarAbovePlayer(e.localizedMessage, Snackbar.LENGTH_LONG)
            }
        }
    }

    @UnstableApi private fun addLocalFolder(uri: Uri): Feed? {
        requireActivity().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val documentFile = DocumentFile.fromTreeUri(requireContext(), uri)
        requireNotNull(documentFile) { "Unable to retrieve document tree" }
        var title = documentFile.name
        if (title == null) title = getString(R.string.local_folder)

        val dirFeed = Feed(Feed.PREFIX_LOCAL_FOLDER + uri.toString(), null, title)
        dirFeed.episodes.clear()
        dirFeed.sortOrder = EpisodeSortOrder.EPISODE_TITLE_A_Z
        val fromDatabase: Feed? = updateFeed(requireContext(), dirFeed, false)
        FeedUpdateManager.runOnce(requireContext(), fromDatabase)
        return fromDatabase
    }

    private class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        val TAG = OnlineSearchFragment::class.simpleName ?: "Anonymous"
        private const val KEY_UP_ARROW = "up_arrow"
    }
}
