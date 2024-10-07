package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.DownloadlogFragmentBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.DownloadResultComparator
import ac.mdiq.podcini.ui.actions.DownloadActionButton
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.error.DownloadErrorLabel.from
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadLogFragment : BottomSheetDialogFragment(), Toolbar.OnMenuItemClickListener {
    private var _binding: DownloadlogFragmentBinding? = null
    private val binding get() = _binding!!

    private val downloadLog = mutableStateListOf<DownloadResult>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        _binding = DownloadlogFragmentBinding.inflate(inflater)
        binding.toolbar.inflateMenu(R.menu.download_log)
        binding.toolbar.setOnMenuItemClickListener(this)

        binding.lazyColumn.setContent {
            CustomTheme(requireContext()) {
                MainView()
            }
        }
        loadDownloadLog()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        downloadLog.clear()
        super.onDestroyView()
    }

    @Composable
    fun MainView() {
        val lazyListState = rememberLazyListState()
        val showDialog = remember { mutableStateOf(false) }
        val dialogParam = remember { mutableStateOf(DownloadResult()) }
        if (showDialog.value) {
            DetailDialog(
                status = dialogParam.value,
                showDialog = showDialog.value,
                onDismissRequest = { showDialog.value = false },
            )
        }
        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(downloadLog) { position, status ->
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
                            val status_: DownloadResult = downloadLog[i]
                            if (status_.feedfileType == feedTypeId && status_.feedfileId == id && status_.isSuccessful) return true
                        }
                        return false
                    }
                    var showAction by remember { mutableStateOf(!status.isSuccessful && !newerWasSuccessful(position, status.feedfileType, status.feedfileId)) }
                    if (showAction) {
                        Icon(painter = painterResource(R.drawable.ic_refresh),
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

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.DownloadLogEvent -> loadDownloadLog()
                    else -> {}
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.clear_logs_item).setVisible(downloadLog.isNotEmpty())
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when {
            super.onOptionsItemSelected(item) -> return true
            item.itemId == R.id.clear_logs_item -> {
                runOnIOScope {
                    realm.write {
                        val dlog = query(DownloadResult::class).find()
                        delete(dlog)
                    }
                    EventFlow.postEvent(FlowEvent.DownloadLogEvent())
                }
            }
            else -> return false
        }
        return true
    }

    private fun loadDownloadLog() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Logd(TAG, "getDownloadLog() called")
                    val dlog = realm.query(DownloadResult::class).find().toMutableList()
                    dlog.sortWith(DownloadResultComparator())
                    realm.copyFromRealm(dlog)
                }
                withContext(Dispatchers.Main) {
                    downloadLog.clear()
                    downloadLog.addAll(result)
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    @Composable
    fun DetailDialog(status: DownloadResult, showDialog: Boolean, onDismissRequest: () -> Unit) {
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
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(10.dp), shape = RoundedCornerShape(16.dp), ) {
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
        private val TAG: String = DownloadLogFragment::class.simpleName ?: "Anonymous"
    }
}
