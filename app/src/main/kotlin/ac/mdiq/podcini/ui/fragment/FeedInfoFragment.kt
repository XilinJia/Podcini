package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ComposeFragmentBinding
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.utils.HtmlToPlainText
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.database.Feeds.updateFeedDownloadURL
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.FeedFunding
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.LargeTextEditingDialog
import ac.mdiq.podcini.ui.compose.RemoveFeedDialog
import ac.mdiq.podcini.ui.fragment.StatisticsFragment.Companion.FeedStatisticsDialog
import ac.mdiq.podcini.ui.utils.TransitionEffect
import ac.mdiq.podcini.util.*
import android.R.string
import android.app.Activity
import android.content.*
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
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.apache.commons.lang3.StringUtils
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ExecutionException


class FeedInfoFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private var _binding: ComposeFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var feed: Feed
    private lateinit var toolbar: MaterialToolbar

    private var showRemoveFeedDialog by mutableStateOf(false)
    private var txtvAuthor by mutableStateOf("")
    var txtvUrl by mutableStateOf<String?>(null)
    var rating by mutableStateOf(Rating.UNRATED.code)

    private val addLocalFolderLauncher = registerForActivityResult<Uri?, Uri>(AddLocalFolder()) {
        uri: Uri? -> this.addLocalFolderResult(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ComposeFragmentBinding.inflate(inflater)
        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.feedinfo)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.setOnMenuItemClickListener(this)
        refreshToolbarState()

        txtvAuthor = feed.author ?: ""
        txtvUrl = feed.downloadUrl

        binding.mainView.setContent {
            CustomTheme(requireContext()) {
                if (showRemoveFeedDialog) RemoveFeedDialog(listOf(feed), onDismissRequest = {showRemoveFeedDialog = false}) {
                    (activity as MainActivity).loadFragment(UserPreferences.defaultPage, null)
                    // Make sure fragment is hidden before actually starting to delete
                    requireActivity().supportFragmentManager.executePendingTransactions()
                }
                Column {
                    HeaderUI()
                    DetailUI()
                }
            }
        }

//        imgvBackground = binding.imgvBackground
        // https://github.com/bumptech/glide/issues/529
//        binding.imgvBackground.colorFilter = LightingColorFilter(-0x7d7d7e, 0x000000)

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

    @Composable
    fun HeaderUI() {
        val textColor = MaterialTheme.colorScheme.onSurface
        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(feed)) {
            showChooseRatingDialog = false
            setFeed(feed)
        }
        ConstraintLayout(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            val (bgImage, bgColor, controlRow, image1, image2, imgvCover, taColumn) = createRefs()
            AsyncImage(model = feed.imageUrl ?:"", contentDescription = "bgImage", contentScale = ContentScale.FillBounds,
                error = painterResource(R.drawable.teaser),
                modifier = Modifier.fillMaxSize().blur(radiusX = 15.dp, radiusY = 15.dp)
                    .constrainAs(bgImage) {
                        bottom.linkTo(parent.bottom)
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    })
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .constrainAs(bgColor) {
                    bottom.linkTo(parent.bottom)
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                .constrainAs(controlRow) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                }, verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                val ratingIconRes = Rating.fromCode(rating).res
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(30.dp).height(30.dp).clickable(onClick = {
                    showChooseRatingDialog = true
                }))
                Spacer(modifier = Modifier.weight(0.2f))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings_white), tint = textColor, contentDescription = "butShowSettings",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).clickable(onClick = {
                        (activity as MainActivity).loadChildFragment(FeedSettingsFragment.newInstance(feed), TransitionEffect.SLIDE)
                    }))
                Spacer(modifier = Modifier.weight(0.2f))
                Button(onClick = { (activity as MainActivity).loadChildFragment(FeedEpisodesFragment.newInstance(feed.id)) }) {
                    Text(feed.episodes.size.toString() + " " + stringResource(R.string.episodes_label))
                }
                Spacer(modifier = Modifier.width(15.dp))
            }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).constrainAs(imgvCover) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                width = Dimension.fillToConstraints
            }) {
                AsyncImage(model = feed.imageUrl ?: "", contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier.width(100.dp).height(100.dp).padding(start = 16.dp, end = 16.dp).clickable(onClick = {
//                    if (feed != null) {
//                        val fragment = FeedInfoFragment.newInstance(feed)
//                        (activity as MainActivity).loadChildFragment(fragment, TransitionEffect.SLIDE)
//                    }
                    }))
                Column(Modifier.padding(top = 10.dp)) {
                    Text(feed.title ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text = txtvAuthor, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    @Composable
    fun DetailUI() {
        val scrollState = rememberScrollState()
        var showEditComment by remember { mutableStateOf(false) }
        var commentTextState by remember { mutableStateOf(TextFieldValue(feed.comment)) }
        if (showEditComment) LargeTextEditingDialog(textState = commentTextState, onTextChange = { commentTextState = it }, onDismissRequest = {showEditComment = false},
            onSave = {
                runOnIOScope {
                    feed = upsert(feed) { it.comment = commentTextState.text }
                    rating =  feed.rating
                }
            })
        var showFeedStats by remember { mutableStateOf(false) }
        if (showFeedStats) FeedStatisticsDialog(feed.title?: "No title", feed.id) { showFeedStats = false }

        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Text(feed.title ?:"", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
            Text(feed.author ?:"", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Text(HtmlToPlainText.getPlainText(feed.description?:""), color = textColor, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.my_opinion_label) + if (commentTextState.text.isEmpty()) " (Add)" else "",
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showEditComment = true })
            Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))

            if (feed.id > MAX_SYNTHETIC_ID) {
                Text(stringResource(R.string.url_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(text = txtvUrl ?: "", color = textColor, modifier = Modifier.clickable {
                    if (feed.downloadUrl != null) {
                        val url: String = feed.downloadUrl!!
                        val clipData: ClipData = ClipData.newPlainText(url, url)
                        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(clipData)
                        if (Build.VERSION.SDK_INT <= 32) (activity as MainActivity).showSnackbarAbovePlayer(R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT)
                    }
                })
                if (feed.paymentLinks.isNotEmpty()) {
                    Text(stringResource(R.string.support_funding_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    fun fundingText(): String {
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
                        val str = StringBuilder()
                        for (funding in fundingList) {
                            str.append(if (funding.content == null || funding.content!!.isEmpty()) requireContext().resources.getString(
                                R.string.support_podcast)
                            else funding.content).append(" ").append(funding.url)
                            str.append("\n")
                        }
                        return StringBuilder(StringUtils.trim(str.toString())).toString()
                    }
                    val fundText = remember { fundingText() }
                    Text(fundText, color = textColor)
                }
                Button(modifier = Modifier.padding(top = 10.dp), onClick = {
                    val fragment = SearchResultsFragment.newInstance(CombinedSearcher::class.java, "$txtvAuthor podcasts")
                    (activity as MainActivity).loadChildFragment(fragment, TransitionEffect.SLIDE)
                }) { Text(stringResource(R.string.feeds_related_to_author)) }
            }
            Text(stringResource(R.string.statistics_label), color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Row {
                Button({ showFeedStats = true }) { Text(stringResource(R.string.statistics_view_this)) }
                Spacer(Modifier.weight(1f))
                Button({ (activity as MainActivity).loadChildFragment(StatisticsFragment(), TransitionEffect.SLIDE) }) { Text(stringResource(R.string.statistics_view_all)) }
            }
        }
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
////        val horizontalSpacing = resources.getDimension(R.dimen.additional_horizontal_spacing).toInt()
////        binding.header.root.setPadding(horizontalSpacing, binding.header.root.paddingTop, horizontalSpacing, binding.header.root.paddingBottom)
////        binding.infoContainer.setPadding(horizontalSpacing, binding.infoContainer.paddingTop, horizontalSpacing, binding.infoContainer.paddingBottom)
//    }

    private fun showFeed() {
        Logd(TAG, "Language: ${feed.language} Author: ${feed.author}")
        Logd(TAG, "URL: ${feed.downloadUrl}")
//        TODO: need to generate blurred image for background
        refreshToolbarState()
    }

    fun setFeed(feed_: Feed) {
//        feed = feed_
        feed = realm.query(Feed::class).query("id == $0", feed_.id).first().find()!!
        rating = feed.rating
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        feed = Feed()
        super.onDestroyView()
    }

    private fun refreshToolbarState() {
        toolbar.menu?.findItem(R.id.reconnect_local_folder)?.isVisible = feed.isLocalFeed
        toolbar.menu?.findItem(R.id.share_item)?.isVisible = !feed.isLocalFeed
        toolbar.menu?.findItem(R.id.visit_website_item)?.isVisible = feed.link != null && IntentUtils.isCallable(requireContext(), Intent(Intent.ACTION_VIEW, Uri.parse(feed.link)))
        toolbar.menu?.findItem(R.id.edit_feed_url_item)?.isVisible = !feed.isLocalFeed
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.visit_website_item -> if (feed.link != null) IntentUtils.openInBrowser(requireContext(), feed.link!!)
            R.id.share_item -> ShareUtils.shareFeedLinkNew(requireContext(), feed)
            R.id.reconnect_local_folder -> {
                val alert = MaterialAlertDialogBuilder(requireContext())
                alert.setMessage(R.string.reconnect_local_folder_warning)
                alert.setPositiveButton(string.ok) { _: DialogInterface?, _: Int ->
                    try { addLocalFolderLauncher.launch(null) } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") }
                }
                alert.setNegativeButton(string.cancel, null)
                alert.show()
            }
            R.id.edit_feed_url_item -> {
                object : EditUrlSettingsDialog(activity as Activity, feed) {
                    override fun setUrl(url: String?) {
                        feed.downloadUrl = url
                        txtvUrl = feed.downloadUrl
                    }
                }.show()
            }
            R.id.remove_feed -> showRemoveFeedDialog = true
            else -> return false
        }
        return true
    }

     private fun addLocalFolderResult(uri: Uri?) {
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

//     private fun reconnectLocalFolder(uri: Uri) {
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
                    is FlowEvent.FeedPrefsChangeEvent -> {
                        setFeed(feed)
//                        feed = event.feed
                    }
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
         private fun onConfirmed(original: String, updated: String) {
            try {
                runBlocking { updateFeedDownloadURL(original, updated).join() }
                feed.downloadUrl = updated
                runOnce(activityRef.get()!!, feed)
            } catch (e: ExecutionException) { throw RuntimeException(e)
            } catch (e: InterruptedException) { throw RuntimeException(e) }
        }
         private fun showConfirmAlertDialog(url: String) {
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
