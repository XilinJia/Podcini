package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.DownloadLogFragmentBinding
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.ui.adapter.DownloadLogAdapter
import ac.mdiq.podcini.ui.dialog.DownloadLogDetailsDialog
import ac.mdiq.podcini.ui.view.EmptyViewHandler
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.DownloadLogEvent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.widget.Toolbar
import androidx.media3.common.util.UnstableApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Shows the download log
 */
class DownloadLogFragment : BottomSheetDialogFragment(), OnItemClickListener, Toolbar.OnMenuItemClickListener {
    private var _binding: DownloadLogFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DownloadLogAdapter

    private var downloadLog: List<DownloadResult> = ArrayList()
//    private var disposable: Disposable? = null
    val scope = CoroutineScope(Dispatchers.Main)

    override fun onStop() {
        super.onStop()
        scope.cancel()
//        disposable?.dispose()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        _binding = DownloadLogFragmentBinding.inflate(inflater)
        binding.toolbar.inflateMenu(R.menu.download_log)
        binding.toolbar.setOnMenuItemClickListener(this)

        val emptyView = EmptyViewHandler(requireContext())
        emptyView.setIcon(R.drawable.ic_download)
        emptyView.setTitle(R.string.no_log_downloads_head_label)
        emptyView.setMessage(R.string.no_log_downloads_label)
        emptyView.attachToListView(binding.list)

        adapter = DownloadLogAdapter(requireActivity())
        binding.list.adapter = adapter
        binding.list.onItemClickListener = this
        binding.list.isNestedScrollingEnabled = true
        EventBus.getDefault().register(this)
        loadDownloadLog()

        return binding.root
    }

    override fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
        _binding = null
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        val item = adapter.getItem(position)
        if (item is DownloadResult) DownloadLogDetailsDialog(requireContext(), item).show()
    }

    @Subscribe
    fun onDownloadLogChanged(event: DownloadLogEvent?) {
        loadDownloadLog()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.clear_logs_item).setVisible(downloadLog.isNotEmpty())
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        when {
            super.onOptionsItemSelected(item) -> return true
            item.itemId == R.id.clear_logs_item -> {
                DBWriter.clearDownloadLog()
                return true
            }
            else -> return false
        }
    }

    private fun loadDownloadLog() {
//        disposable?.dispose()

//        disposable = Observable.fromCallable { DBReader.getDownloadLog() }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe({ result: List<DownloadResult>? ->
//                if (result != null) {
//                    downloadLog = result
//                    adapter.setDownloadLog(downloadLog)
//                }
//            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    DBReader.getDownloadLog()
                }
                withContext(Dispatchers.Main) {
                    downloadLog = result
                    adapter.setDownloadLog(downloadLog)
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    companion object {
        private const val TAG = "DownloadLogFragment"
    }
}
