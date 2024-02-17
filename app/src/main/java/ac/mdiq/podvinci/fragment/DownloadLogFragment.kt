package ac.mdiq.podvinci.fragment

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.widget.Toolbar
import androidx.media3.common.util.UnstableApi
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.adapter.DownloadLogAdapter
import ac.mdiq.podvinci.core.event.DownloadLogEvent
import ac.mdiq.podvinci.core.storage.DBReader
import ac.mdiq.podvinci.core.storage.DBWriter
import ac.mdiq.podvinci.databinding.DownloadLogFragmentBinding
import ac.mdiq.podvinci.dialog.DownloadLogDetailsDialog
import ac.mdiq.podvinci.model.download.DownloadResult
import ac.mdiq.podvinci.view.EmptyViewHandler
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Shows the download log
 */
class DownloadLogFragment : BottomSheetDialogFragment(), OnItemClickListener, Toolbar.OnMenuItemClickListener {
    private lateinit var viewBinding: DownloadLogFragmentBinding
    private lateinit var adapter: DownloadLogAdapter

    private var downloadLog: List<DownloadResult> = ArrayList()
    private var disposable: Disposable? = null

    override fun onStart() {
        super.onStart()
        loadDownloadLog()
    }

    override fun onStop() {
        super.onStop()
        disposable?.dispose()
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        viewBinding = DownloadLogFragmentBinding.inflate(inflater)
        viewBinding.toolbar.inflateMenu(R.menu.download_log)
        viewBinding.toolbar.setOnMenuItemClickListener(this)

        val emptyView = EmptyViewHandler(activity)
        emptyView.setIcon(R.drawable.ic_download)
        emptyView.setTitle(R.string.no_log_downloads_head_label)
        emptyView.setMessage(R.string.no_log_downloads_label)
        emptyView.attachToListView(viewBinding.list)

        adapter = DownloadLogAdapter(requireActivity())
        viewBinding.list.adapter = adapter
        viewBinding.list.onItemClickListener = this
        viewBinding.list.isNestedScrollingEnabled = true
        EventBus.getDefault().register(this)
        return viewBinding.root
    }

    override fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        val item = adapter.getItem(position)
        if (item is DownloadResult) {
            DownloadLogDetailsDialog(requireContext(), item).show()
        }
    }

    @Subscribe
    fun onDownloadLogChanged(event: DownloadLogEvent?) {
        loadDownloadLog()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.clear_logs_item).setVisible(downloadLog.isNotEmpty())
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        } else if (item.itemId == R.id.clear_logs_item) {
            DBWriter.clearDownloadLog()
            return true
        }
        return false
    }

    private fun loadDownloadLog() {
        disposable?.dispose()

        disposable = Observable.fromCallable { DBReader.getDownloadLog() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: List<DownloadResult>? ->
                if (result != null) {
                    downloadLog = result
                    adapter.setDownloadLog(downloadLog)
                }
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        private const val TAG = "DownloadLogFragment"
    }
}
