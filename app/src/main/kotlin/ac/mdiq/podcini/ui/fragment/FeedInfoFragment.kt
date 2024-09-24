package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.databinding.FeedinfoBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.net.feed.discovery.CombinedSearcher
import ac.mdiq.podcini.net.utils.HtmlToPlainText
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.database.Feeds.updateFeedDownloadURL
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.FeedFunding
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.dialog.RemoveFeedDialog
import ac.mdiq.podcini.ui.statistics.FeedStatisticsFragment
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.ui.utils.ToolbarIconTintManager
import ac.mdiq.podcini.ui.utils.TransitionEffect
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.ShareUtils
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.R.string
import android.app.Activity
import android.content.*
import android.content.res.Configuration
import android.graphics.LightingColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.load
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.apache.commons.lang3.StringUtils
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Displays information about a feed.
 */
@UnstableApi
class FeedInfoFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private var _binding: FeedinfoBinding? = null
    private val binding get() = _binding!!

    private lateinit var feed: Feed
    private lateinit var toolbar: MaterialToolbar

    private val addLocalFolderLauncher = registerForActivityResult<Uri?, Uri>(AddLocalFolder()) {
        uri: Uri? -> this.addLocalFolderResult(uri)
    }

    private val copyUrlToClipboard = View.OnClickListener {
        if (feed.downloadUrl != null) {
            val url: String = feed.downloadUrl!!
            val clipData: ClipData = ClipData.newPlainText(url, url)
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clipData)
            if (Build.VERSION.SDK_INT <= 32) (activity as MainActivity).showSnackbarAbovePlayer(R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FeedinfoBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.feedinfo)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.setOnMenuItemClickListener(this)
        refreshToolbarState()

        val appBar: AppBarLayout = binding.appBar
        val collapsingToolbar: CollapsingToolbarLayout = binding.collapsingToolbar
        val iconTintManager: ToolbarIconTintManager = object : ToolbarIconTintManager(requireContext(), toolbar, collapsingToolbar) {
            override fun doTint(themedContext: Context) {
                toolbar.menu.findItem(R.id.visit_website_item).setIcon(AppCompatResources.getDrawable(themedContext, R.drawable.ic_web))
                toolbar.menu.findItem(R.id.share_item).setIcon(AppCompatResources.getDrawable(themedContext, R.drawable.ic_share))
            }
        }
        iconTintManager.updateTint()
        appBar.addOnOffsetChangedListener(iconTintManager)

//        imgvCover = binding.header.imgvCover
//        imgvBackground = binding.imgvBackground
        // https://github.com/bumptech/glide/issues/529
        binding.imgvBackground.colorFilter = LightingColorFilter(-0x7d7d7e, 0x000000)

        binding.header.episodes.text = feed.episodes.size.toString() + " episodes"
        binding.header.episodes.setOnClickListener {
            (activity as MainActivity).loadChildFragment(FeedEpisodesFragment.newInstance(feed.id))
        }
        binding.header.butShowSettings.setOnClickListener {
            (activity as MainActivity).loadChildFragment(FeedSettingsFragment.newInstance(feed), TransitionEffect.SLIDE)
        }

        binding.btnvRelatedFeeds.setOnClickListener {
            val fragment = SearchResultsFragment.newInstance(CombinedSearcher::class.java, "${binding.header.txtvAuthor.text} podcasts")
            (activity as MainActivity).loadChildFragment(fragment, TransitionEffect.SLIDE)
        }
        binding.txtvUrl.setOnClickListener(copyUrlToClipboard)
        parentFragmentManager.beginTransaction().replace(R.id.statisticsFragmentContainer,
            FeedStatisticsFragment.newInstance(feed.id, false), "feed_statistics_fragment").commitAllowingStateLoss()
        binding.btnvOpenStatistics.setOnClickListener {
            (activity as MainActivity).loadChildFragment(StatisticsFragment(), TransitionEffect.SLIDE)
        }
        showFeed()
        return binding.root
    }

    override fun onStart() {
        Logd(TAG, "onStart() called")
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        Logd(TAG, "onStop() called")
        super.onStop()
        cancelFlowEvents()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val horizontalSpacing = resources.getDimension(R.dimen.additional_horizontal_spacing).toInt()
        binding.header.root.setPadding(horizontalSpacing, binding.header.root.paddingTop, horizontalSpacing, binding.header.root.paddingBottom)
        binding.infoContainer.setPadding(horizontalSpacing, binding.infoContainer.paddingTop, horizontalSpacing, binding.infoContainer.paddingBottom)
    }

    private fun showFeed() {
        Logd(TAG, "Language: ${feed.language} Author: ${feed.author}")
        Logd(TAG, "URL: ${feed.downloadUrl}")

//        TODO: need to generate blurred image for background
        binding.header.imgvCover.load(feed.imageUrl) {
            placeholder(R.color.light_gray)
            error(R.mipmap.ic_launcher)
        }
        binding.header.txtvTitle.text = feed.title
        binding.header.txtvTitle.setMaxLines(3)

        binding.txtvDescription.text = HtmlToPlainText.getPlainText(feed.description?:"")

        binding.feedTitle.text = feed.title
        binding.feedAuthor.text = feed.author

        if (!feed.author.isNullOrEmpty()) binding.header.txtvAuthor.text = feed.author

        binding.txtvUrl.text = feed.downloadUrl
        binding.txtvUrl.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_paperclip, 0)

        if (feed.paymentLinks.isEmpty()) {
            binding.lblSupport.visibility = View.GONE
            binding.txtvFundingUrl.visibility = View.GONE
        } else {
            binding.lblSupport.visibility = View.VISIBLE
            val fundingList: ArrayList<FeedFunding> = feed.paymentLinks

            // Filter for duplicates, but keep items in the order that they have in the feed.
            val i: MutableIterator<FeedFunding> = fundingList.iterator()
            while (i.hasNext()) {
                val funding: FeedFunding = i.next()
                for (other in fundingList) {
                    if (other.url == funding.url) {
                        if (other.content != null && funding.content != null && other.content!!.length > funding.content!!.length) {
                            i.remove()
                            break
                        }
                    }
                }
            }

            var str = StringBuilder()
            for (funding in fundingList) {
                str.append(if (funding.content == null || funding.content!!.isEmpty()) requireContext().resources.getString(R.string.support_podcast)
                else funding.content).append(" ").append(funding.url)
                str.append("\n")
            }
            str = StringBuilder(StringUtils.trim(str.toString()))
            binding.txtvFundingUrl.text = str.toString()
        }
        refreshToolbarState()
    }

    fun setFeed(feed_: Feed) {
        feed = feed_
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        feed = Feed()
        super.onDestroyView()
    }

    private fun refreshToolbarState() {
        toolbar.menu?.findItem(R.id.reconnect_local_folder)?.setVisible(feed.isLocalFeed)
        toolbar.menu?.findItem(R.id.share_item)?.setVisible(!feed.isLocalFeed)
        toolbar.menu?.findItem(R.id.visit_website_item)
            ?.setVisible(feed.link != null && IntentUtils.isCallable(requireContext(), Intent(Intent.ACTION_VIEW, Uri.parse(feed.link))))
        toolbar.menu?.findItem(R.id.edit_feed_url_item)?.setVisible(!feed.isLocalFeed)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.visit_website_item -> if (feed.link != null) IntentUtils.openInBrowser(requireContext(), feed.link!!)
            R.id.share_item -> ShareUtils.shareFeedLink(requireContext(), feed)
            R.id.reconnect_local_folder -> {
                val alert = MaterialAlertDialogBuilder(requireContext())
                alert.setMessage(R.string.reconnect_local_folder_warning)
                alert.setPositiveButton(string.ok) { _: DialogInterface?, _: Int ->
                    try {
                        addLocalFolderLauncher.launch(null)
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "No activity found. Should never happen...")
                    }
                }
                alert.setNegativeButton(string.cancel, null)
                alert.show()
            }
            R.id.edit_feed_url_item -> {
                object : EditUrlSettingsDialog(activity as Activity, feed) {
                    override fun setUrl(url: String?) {
                        feed.downloadUrl = url
                        binding.txtvUrl.text = feed.downloadUrl
                        binding.txtvUrl.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_paperclip, 0)
                    }
                }.show()
            }
            R.id.remove_feed -> {
                RemoveFeedDialog.show(requireContext(), feed) {
                    (activity as MainActivity).loadFragment(UserPreferences.defaultPage, null)
                    // Make sure fragment is hidden before actually starting to delete
                    requireActivity().supportFragmentManager.executePendingTransactions()
                }
            }
            else -> return false
        }
        return true
    }

    @UnstableApi private fun addLocalFolderResult(uri: Uri?) {
        if (uri == null) return
//        reconnectLocalFolder(uri)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireActivity().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val documentFile = DocumentFile.fromTreeUri(requireContext(), uri)
                    requireNotNull(documentFile) { "Unable to retrieve document tree" }
                    feed.downloadUrl = Feed.PREFIX_LOCAL_FOLDER + uri.toString()
                    updateFeed(requireContext(), feed, true)
                }
                withContext(Dispatchers.Main) {
                    (activity as MainActivity).showSnackbarAbovePlayer(string.ok, Snackbar.LENGTH_SHORT)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { (activity as MainActivity).showSnackbarAbovePlayer(e.localizedMessage?:"No message", Snackbar.LENGTH_LONG) }
            }
        }
    }

//    @UnstableApi private fun reconnectLocalFolder(uri: Uri) {
//        lifecycleScope.launch {
//            try {
//                withContext(Dispatchers.IO) {
//                    requireActivity().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                    val documentFile = DocumentFile.fromTreeUri(requireContext(), uri)
//                    requireNotNull(documentFile) { "Unable to retrieve document tree" }
//                    feed.downloadUrl = Feed.PREFIX_LOCAL_FOLDER + uri.toString()
//                    updateFeed(requireContext(), feed, true)
//                }
//                withContext(Dispatchers.Main) {
//                    (activity as MainActivity).showSnackbarAbovePlayer(string.ok, Snackbar.LENGTH_SHORT)
//                }
//            } catch (e: Throwable) {
//                withContext(Dispatchers.Main) {
//                    (activity as MainActivity).showSnackbarAbovePlayer(e.localizedMessage, Snackbar.LENGTH_LONG)
//                }
//            }
//        }
//    }

    private var eventSink: Job? = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink == null) eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedPrefsChangeEvent -> feed = event.feed
                    else -> {}
                }
            }
        }
    }

    private class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @UnstableApi
    abstract class EditUrlSettingsDialog(activity: Activity, private val feed: Feed) {
        val TAG = this::class.simpleName ?: "Anonymous"
        private val activityRef = WeakReference(activity)

        fun show() {
            val activity = activityRef.get() ?: return
            val binding = EditTextDialogBinding.inflate(LayoutInflater.from(activity))
            binding.editText.setText(feed.downloadUrl)
            MaterialAlertDialogBuilder(activity)
                .setView(binding.root)
                .setTitle(R.string.edit_url_menu)
                .setPositiveButton(string.ok) { _: DialogInterface?, _: Int -> showConfirmAlertDialog(binding.editText.text.toString()) }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }
        @UnstableApi private fun onConfirmed(original: String, updated: String) {
            try {
                runBlocking { updateFeedDownloadURL(original, updated).join() }
                feed.downloadUrl = updated
                runOnce(activityRef.get()!!, feed)
            } catch (e: ExecutionException) { throw RuntimeException(e)
            } catch (e: InterruptedException) { throw RuntimeException(e) }
        }
        @UnstableApi private fun showConfirmAlertDialog(url: String) {
            val activity = activityRef.get()
            val alertDialog = MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.edit_url_menu)
                .setMessage(R.string.edit_url_confirmation_msg)
                .setPositiveButton(string.ok) { _: DialogInterface?, _: Int ->
                    onConfirmed(feed.downloadUrl?:"", url)
                    setUrl(url)
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            object : CountDownTimer(15000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).text = String.format(Locale.getDefault(), "%s (%d)",
                        activity.getString(string.ok), millisUntilFinished / 1000 + 1)
                }
                override fun onFinish() {
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(string.ok)
                }
            }.start()
        }
        protected abstract fun setUrl(url: String?)
    }

    companion object {
        private val TAG: String = FeedInfoFragment::class.simpleName ?: "Anonymous"

        fun newInstance(feed: Feed): FeedInfoFragment {
            val fragment = FeedInfoFragment()
            fragment.setFeed(feed)
            return fragment
        }
    }
}
