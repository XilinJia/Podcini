package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SharedlogFragmentBinding
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.ui.activity.ShareReceiverActivity.Companion.receiveShared
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SharedLogFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private var _binding: SharedlogFragmentBinding? = null
    private val binding get() = _binding!!

    private val logs = mutableStateListOf<ShareLog>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        _binding = SharedlogFragmentBinding.inflate(inflater)
        binding.toolbar.inflateMenu(R.menu.download_log)
        binding.toolbar.setOnMenuItemClickListener(this)

        binding.lazyColumn.setContent {
            CustomTheme(requireContext()) {
                MainView()
            }
        }
        loadLog()
        return binding.root
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        logs.clear()
        super.onDestroyView()
    }

    @Composable
    fun MainView() {
        val lazyListState = rememberLazyListState()
        val showDialog = remember { mutableStateOf(false) }
        val dialogParam = remember { mutableStateOf(ShareLog()) }
        if (showDialog.value) {
            DetailDialog(
                status = dialogParam.value,
                showDialog = showDialog.value,
                onDismissRequest = { showDialog.value = false },
            )
        }
        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(logs) { position, log ->
                val textColor = MaterialTheme.colorScheme.onSurface
                Row (modifier = Modifier.clickable {
                    if (log.status == 1) {
                        showDialog.value = true
                        dialogParam.value = log
                    } else receiveShared(log.url!!, activity as AppCompatActivity, false)
                }) {
                    Column {
                        Row {
                            val icon = remember { if (log.status == 1) Icons.Filled.Info else Icons.Filled.Warning }
                            val iconColor = remember { if (log.status == 1) Color.Green else Color.Yellow }
                            Icon(icon, "Info", tint = iconColor, modifier = Modifier.padding(end = 2.dp))
                            Text(formatDateTimeFlex(Date(log.id)), color = textColor)
                        }
                        Text(log.url?:"unknown", color = textColor)
                        val statusText = remember {"" }
                        Text(statusText, color = textColor)
                        if (log.status != 1) {
                            Text(log.details, color = Color.Red)
                            Text(stringResource(R.string.download_error_tap_for_details), color = textColor)
                        }
                    }
                    var showAction by remember { mutableStateOf(log.status != 1) }
                    if (showAction) {
                        Icon(painter = painterResource(R.drawable.ic_refresh),
                            tint = textColor,
                            contentDescription = null,
                            modifier = Modifier.width(28.dp).height(32.dp).clickable {

                            })
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.clear_logs_item).setVisible(logs.isNotEmpty())
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when {
            super.onOptionsItemSelected(item) -> return true
            item.itemId == R.id.clear_logs_item -> {
                runOnIOScope {
                    realm.write {
                        val dlog = query(ShareLog::class).find()
                        delete(dlog)
                    }
                    loadLog()
                }
            }
            else -> return false
        }
        return true
    }

    private fun loadLog() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Logd(TAG, "getDownloadLog() called")
                    val dlog = realm.query(ShareLog::class).find().toMutableList()
                    realm.copyFromRealm(dlog)
                }
                withContext(Dispatchers.Main) {
                    logs.clear()
                    logs.addAll(result)
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }
    }

    @Composable
    fun DetailDialog(status: ShareLog, showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            var message = requireContext().getString(R.string.download_successful)
            if (status.status == 0) message = status.details

            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(10.dp), shape = RoundedCornerShape(16.dp)) {
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
    
    companion object {
        val TAG: String = SharedLogFragment::class.simpleName ?: "Anonymous"
    }
}
