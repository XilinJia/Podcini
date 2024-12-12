package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.getFeedByTitleAndAuthor
import ac.mdiq.podcini.storage.database.LogsAndStats.DownloadResultComparator
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.Rating.Companion.fromCode
import ac.mdiq.podcini.ui.actions.DownloadActionButton
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.ShareReceiverActivity.Companion.receiveShared
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.ConfirmAddYoutubeEpisode
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import ac.mdiq.podcini.util.error.DownloadErrorLabel.from
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class LogsFragment : Fragment() {
    private val shareLogs = mutableStateListOf<ShareLog>()
    private val subscriptionLogs = mutableStateListOf<SubscriptionLog>()
    private val downloadLogs = mutableStateListOf<DownloadResult>()
    private var title by mutableStateOf("")

    private var displayUpArrow = false

    private var showDeleteConfirmDialog = mutableStateOf(false)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            ComfirmDialog(R.string.confirm_delete_logs_label, stringResource(R.string.confirm_delete_logs_message), showDeleteConfirmDialog) {
                                runOnIOScope {
                                    when {
                                        shareLogs.isNotEmpty() -> {
                                            realm.write {
                                                val items = query(ShareLog::class).find()
                                                delete(items)
                                            }
                                            shareLogs.clear()
                                            loadShareLog()
                                        }
                                        subscriptionLogs.isNotEmpty() -> {
                                            realm.write {
                                                val items = query(SubscriptionLog::class).find()
                                                delete(items)
                                            }
                                            subscriptionLogs.clear()
                                            loadSubscriptionLog()
                                        }
                                        downloadLogs.isNotEmpty() -> {
                                            realm.write {
                                                val items = query(DownloadResult::class).find()
                                                delete(items)
                                            }
                                            downloadLogs.clear()
                                            loadDownloadLog()
                                        }
                                    }
                                }
                            }
                            when {
                                downloadLogs.isNotEmpty() -> DownloadLogView()
                                shareLogs.isNotEmpty() -> SharedLogView()
                                subscriptionLogs.isNotEmpty() -> SubscriptionLogView()
                            }
                        }
                    }
                }
            }
        }
        loadDownloadLog()
        return composeView
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        clearAllLogs()
        super.onDestroyView()
    }

    @Composable
    fun SharedLogView() {
        val lazyListState = rememberLazyListState()
        val showSharedDialog = remember { mutableStateOf(false) }
        val sharedlogState = remember { mutableStateOf(ShareLog()) }
        if (showSharedDialog.value) {
            SharedDetailDialog(status = sharedlogState.value, showDialog = showSharedDialog.value, onDismissRequest = { showSharedDialog.value = false })
        }
        var showYTMediaConfirmDialog by remember { mutableStateOf(false) }
        var sharedUrl by remember { mutableStateOf("") }
        ConfirmAddYoutubeEpisode(listOf(sharedUrl), showYTMediaConfirmDialog, onDismissRequest = { showYTMediaConfirmDialog = false })

        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(shareLogs) { position, log ->
                val textColor = MaterialTheme.colorScheme.onSurface
                Row (modifier = Modifier.clickable {
                    if (log.status < ShareLog.Status.SUCCESS.ordinal) {
                        receiveShared(log.url!!, activity as AppCompatActivity, false) {
                            sharedUrl = log.url!!
                            showYTMediaConfirmDialog = true
                        }
                    } else {
                        Logd(TAG, "shared log url: ${log.url}")
                        var hasError = false
                        when(log.type) {
                             ShareLog.Type.YTMedia.name, "youtube media" -> {
                                val episode = realm.query(Episode::class).query("title == $0", log.title).first().find()
                                if (episode != null) (activity as MainActivity).loadChildFragment(EpisodeInfoFragment.newInstance(episode))
                                 else hasError = true
                            }
                            ShareLog.Type.Podcast.name, "podcast" -> {
                                val feed = getFeedByTitleAndAuthor(log.title?:"", log.author?:"")
                                if (feed != null ) (activity as MainActivity).loadChildFragment(FeedInfoFragment.newInstance(feed))
                                else hasError = true
                            }
                            else -> {
                                showSharedDialog.value = true
                                sharedlogState.value = log
                            }
                        }
                        if (hasError) {
                            showSharedDialog.value = true
                            sharedlogState.value = log
                        }
                    }
                }) {
                    Column {
                        Row {
                            val icon = remember { if (log.status == ShareLog.Status.SUCCESS.ordinal) Icons.Filled.Info else Icons.Filled.Warning }
                            val iconColor = remember { if (log.status == ShareLog.Status.SUCCESS.ordinal) Color.Green else Color.Yellow }
                            Icon(icon, "Info", tint = iconColor, modifier = Modifier.padding(end = 2.dp))
                            Text(formatDateTimeFlex(Date(log.id)), color = textColor)
                            Spacer(Modifier.weight(1f))
                            var showAction by remember { mutableStateOf(log.status < ShareLog.Status.SUCCESS.ordinal) }
                            if (showAction) {
                                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_delete), tint = textColor, contentDescription = null,
                                    modifier = Modifier.width(25.dp).height(25.dp).clickable {
                                    })
                            }
                        }
                        Text(log.title?:"unknown title", color = textColor)
                        Text(log.url?:"unknown url", color = textColor)
                        val statusText = when (log.status) {
                            ShareLog.Status.ERROR.ordinal -> ShareLog.Status.ERROR.name
                            ShareLog.Status.SUCCESS.ordinal -> ShareLog.Status.SUCCESS.name
                            ShareLog.Status.EXISTING.ordinal -> ShareLog.Status.EXISTING.name
                            else -> ""
                        }
                        Row {
                            Text(statusText, color = textColor)
                            Spacer(Modifier.weight(1f))
                            Text(log.type?:"unknow type", color = textColor)
                        }
                        if (log.status < ShareLog.Status.SUCCESS.ordinal) {
                            Text(log.details, color = Color.Red)
                            Text(stringResource(R.string.download_error_tap_for_details), color = textColor)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SubscriptionLogView() {
        val lazyListState = rememberLazyListState()
        val showDialog = remember { mutableStateOf(false) }
        val dialogParam = remember { mutableStateOf(SubscriptionLog()) }
        if (showDialog.value)
            SubscriptionDetailDialog(log = dialogParam.value, showDialog = showDialog.value, onDismissRequest = { showDialog.value = false })

        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(subscriptionLogs) { position, log ->
                val textColor = MaterialTheme.colorScheme.onSurface
                Row (verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp).clickable {
                    dialogParam.value = log
                    showDialog.value = true
                }) {
                    val iconRes = remember { fromCode(log.rating).res  }
                    Icon(imageVector = ImageVector.vectorResource(iconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                        modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(40.dp).height(40.dp).padding(end = 15.dp))
                    Column {
                        Text(log.type + ": " + formatDateTimeFlex(Date(log.id)) + " -- " + formatDateTimeFlex(Date(log.cancelDate)), color = textColor)
                        Text(log.title, color = textColor)
                    }
                }
            }
        }
    }

    @Composable
    fun DownloadLogView() {
        val lazyListState = rememberLazyListState()
        val showDialog = remember { mutableStateOf(false) }
        val dialogParam = remember { mutableStateOf(DownloadResult()) }
        DownlaodDetailDialog(status = dialogParam.value, showDialog = showDialog.value, onDismissRequest = { showDialog.value = false })

        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(downloadLogs) { position, status ->
                val textColor = MaterialTheme.colorScheme.onSurface
                Row (modifier = Modifier.clickable {
                    showDialog.value = true
                    dialogParam.value = status
                }) {
                    Column {
                        Row {
                            val icon = remember { if (status.isSuccessful) Icons.Filled.Info else Icons.Filled.Warning }
                            val iconColor = remember { if (status.isSuccessful) Color.Green else Color.Yellow }
                            Icon(icon, "Info", tint = iconColor, modifier = Modifier.padding(end = 2.dp))
                            Text(status.title.ifEmpty { stringResource(R.string.download_log_title_unknown) }, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val statusText = remember {"" +
                                when (status.feedfileType) {
                                    Feed.FEEDFILETYPE_FEED ->  requireContext().getString(R.string.download_type_feed)
                                    EpisodeMedia.FEEDFILETYPE_FEEDMEDIA -> requireContext().getString(R.string.download_type_media)
                                    else -> "" } + " · " +
                                DateUtils.getRelativeTimeSpanString(status.getCompletionDate().time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, 0)
                        }
                        Text(statusText, color = textColor)
                        if (!status.isSuccessful) {
                            Text(stringResource(from(status.reason)), color = Color.Red)
                            Text(stringResource(R.string.download_error_tap_for_details), color = textColor)
                        }
                    }
                    fun newerWasSuccessful(downloadStatusIndex: Int, feedTypeId: Int, id: Long): Boolean {
                        for (i in 0 until downloadStatusIndex) {
                            val status_: DownloadResult = downloadLogs[i]
                            if (status_.feedfileType == feedTypeId && status_.feedfileId == id && status_.isSuccessful) return true
                        }
                        return false
                    }
                    var showAction by remember { mutableStateOf(!status.isSuccessful && !newerWasSuccessful(position, status.feedfileType, status.feedfileId)) }
                    if (showAction) {
                        Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_refresh),
                            tint = textColor,
                            contentDescription = null,
                            modifier = Modifier.width(28.dp).height(32.dp).clickable {
                                when (status.feedfileType) {
                                    Feed.FEEDFILETYPE_FEED -> {
                                        showAction = false
                                        val feed: Feed? = getFeed(status.feedfileId)
                                        if (feed == null) {
                                            Log.e(TAG, "Could not find feed for feed id: " + status.feedfileId)
                                            return@clickable
                                        }
                                        FeedUpdateManager.runOnce(requireContext(), feed)
                                    }
                                    EpisodeMedia.FEEDFILETYPE_FEEDMEDIA -> {
                                        showAction = false
                                        val item_ = realm.query(Episode::class).query("id == $0", status.feedfileId).first().find()
                                        if (item_ != null) DownloadActionButton(item_).onClick(requireContext())
                                        (context as MainActivity).showSnackbarAbovePlayer(R.string.status_downloading_label, Toast.LENGTH_SHORT)
                                    }
                                }
                            })
                    }
                }
            }
        }
    }

    private fun clearAllLogs() {
        subscriptionLogs.clear()
        shareLogs.clear()
        downloadLogs.clear()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar(title = { Text(title) },
            navigationIcon = if (displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_history), contentDescription = "Open Drawer") } }
            },
            actions = {
                if (title != "Downloads log") IconButton(onClick = {
                    clearAllLogs()
                    loadDownloadLog()
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_download), contentDescription = "download") }
                if (title != "Shares log") IconButton(onClick = {
                    clearAllLogs()
                    loadShareLog()
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_share), contentDescription = "share") }
                if (title != "Subscriptions log") IconButton(onClick = {
                    clearAllLogs()
                    loadSubscriptionLog()
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_subscriptions), contentDescription = "subscriptions") }
                IconButton(onClick = { showDeleteConfirmDialog.value = true
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_delete), contentDescription = "clear history") }
            }
        )
    }

    private fun loadShareLog() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Logd(TAG, "getDownloadLog() called")
                    realm.query(ShareLog::class).sort("id", Sort.DESCENDING).find().toMutableList()
                }
                withContext(Dispatchers.Main) {
                    shareLogs.addAll(result)
                    title = "Shares log"
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }
    }

    private fun loadSubscriptionLog() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Logd(TAG, "getDownloadLog() called")
                    realm.query(SubscriptionLog::class).sort("id", Sort.DESCENDING).find().toMutableList()
                }
                withContext(Dispatchers.Main) {
                    subscriptionLogs.addAll(result)
                    title = "Subscriptions log"
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }
    }

    private fun loadDownloadLog() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Logd(TAG, "getDownloadLog() called")
                    val dlog = realm.query(DownloadResult::class).find().toMutableList()
                    dlog.sortWith(DownloadResultComparator())
//                    realm.copyFromRealm(dlog)
                    dlog
                }
                withContext(Dispatchers.Main) {
                    downloadLogs.addAll(result)
                    title = "Downloads log"
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }
    }

    @Composable
    fun SharedDetailDialog(status: ShareLog, showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            val message = when (status.status) {
                ShareLog.Status.ERROR.ordinal -> status.details
                ShareLog.Status.SUCCESS.ordinal -> stringResource(R.string.download_successful)
                ShareLog.Status.EXISTING.ordinal -> stringResource(R.string.share_existing)
                else -> ""
            }
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(10.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        val textColor = MaterialTheme.colorScheme.onSurface
                        Text(stringResource(R.string.download_error_details), color = textColor, modifier = Modifier.padding(bottom = 3.dp))
                        Text(message, color = textColor)
                        Row(Modifier.padding(top = 10.dp)) {
                            Spacer(Modifier.weight(0.5f))
                            Text(stringResource(R.string.copy_to_clipboard), color = textColor,
                                modifier = Modifier.clickable {
                                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(requireContext().getString(R.string.download_error_details), message)
                                    clipboard.setPrimaryClip(clip)
                                    if (Build.VERSION.SDK_INT < 32) EventFlow.postEvent(FlowEvent.MessageEvent(requireContext().getString(R.string.copied_to_clipboard)))
                                })
                            Spacer(Modifier.weight(0.3f))
                            Text("OK", color = textColor, modifier = Modifier.clickable { onDismissRequest() })
                            Spacer(Modifier.weight(0.2f))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SubscriptionDetailDialog(log: SubscriptionLog, showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(10.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        val textColor = MaterialTheme.colorScheme.onSurface
                        Text(stringResource(R.string.download_error_details), color = textColor, modifier = Modifier.padding(bottom = 3.dp))
                        Text(log.title, color = textColor)
                        Text(log.comment, color = textColor)
                        Text("URL: " + log.url, color = textColor)
                        Text("Link: " + log.link, color = textColor)
                        Row(Modifier.padding(top = 10.dp)) {
                            Spacer(Modifier.weight(0.3f))
                            Text("OK", color = textColor, modifier = Modifier.clickable { onDismissRequest() })
                            Spacer(Modifier.weight(0.2f))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun DownlaodDetailDialog(status: DownloadResult, showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            var url = "unknown"
            when (status.feedfileType) {
                EpisodeMedia.FEEDFILETYPE_FEEDMEDIA -> {
                    val media = realm.query(EpisodeMedia::class).query("id == $0", status.feedfileId).first().find()
                    if (media != null) url = media.downloadUrl?:""
                }
                Feed.FEEDFILETYPE_FEED -> {
                    val feed = getFeed(status.feedfileId, false)
                    if (feed != null) url = feed.downloadUrl?:""
                }
            }
            var message = requireContext().getString(R.string.download_successful)
            if (!status.isSuccessful) message = status.reasonDetailed
            val messageFull = requireContext().getString(R.string.download_log_details_message, requireContext().getString(from(status.reason)), message, url)

            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(10.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        val textColor = MaterialTheme.colorScheme.onSurface
                        Text(stringResource(R.string.download_error_details), color = textColor, modifier = Modifier.padding(bottom = 3.dp))
                        Text(messageFull, color = textColor)
                        Row(Modifier.padding(top = 10.dp)) {
                            Spacer(Modifier.weight(0.5f))
                            Text(stringResource(R.string.copy_to_clipboard), color = textColor,
                                modifier = Modifier.clickable {
                                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(requireContext().getString(R.string.download_error_details), messageFull)
                                    clipboard.setPrimaryClip(clip)
                                    if (Build.VERSION.SDK_INT < 32)
                                        EventFlow.postEvent(FlowEvent.MessageEvent(requireContext().getString(R.string.copied_to_clipboard)))
                                })
                            Spacer(Modifier.weight(0.3f))
                            Text("OK", color = textColor, modifier = Modifier.clickable { onDismissRequest() })
                            Spacer(Modifier.weight(0.2f))
                        }
                    }
                }
            }
        }
    }

    companion object {
        val TAG: String = LogsFragment::class.simpleName ?: "Anonymous"
        private const val KEY_UP_ARROW = "up_arrow"
    }
}
