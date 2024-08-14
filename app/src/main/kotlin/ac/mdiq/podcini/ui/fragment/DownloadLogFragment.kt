package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.DownloadLogFragmentBinding
import ac.mdiq.podcini.databinding.DownloadlogItemBinding
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.DownloadResultComparator
import ac.mdiq.podcini.ui.actions.actionbutton.DownloadActionButton
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.dialog.DownloadLogDetailsDialog
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.error.DownloadErrorLabel
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Layout
import android.text.format.DateUtils
import android.util.Log
import android.view.*
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mikepenz.iconics.view.IconicsTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows the download log
 */
class DownloadLogFragment : BottomSheetDialogFragment(), OnItemClickListener, Toolbar.OnMenuItemClickListener {
    private var _binding: DownloadLogFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DownloadLogAdapter

    private var downloadLog: List<DownloadResult> = ArrayList()

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
        downloadLog = listOf()
        super.onDestroyView()
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        val item = adapter.getItem(position)
        if (item is DownloadResult) DownloadLogDetailsDialog(requireContext(), item).show()
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

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        when {
            super.onOptionsItemSelected(item) -> return true
            item.itemId == R.id.clear_logs_item -> clearDownloadLog()
            else -> return false
        }
        return true
    }

    private fun clearDownloadLog() : Job {
        Logd(TAG, "clearDownloadLog called")
        return runOnIOScope {
            realm.write {
                val dlog = query(DownloadResult::class).find()
                delete(dlog)
            }
            EventFlow.postEvent(FlowEvent.DownloadLogEvent())
        }
    }

    private fun getDownloadLog(): List<DownloadResult> {
        Logd(TAG, "getDownloadLog() called")
        val dlog = realm.query(DownloadResult::class).find().toMutableList()
        dlog.sortWith(DownloadResultComparator())
        return realm.copyFromRealm(dlog)
    }

    private fun loadDownloadLog() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    getDownloadLog()
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

    private class DownloadLogAdapter(private val context: Activity) : BaseAdapter() {
        private var downloadLog: List<DownloadResult> = ArrayList()

        fun setDownloadLog(downloadLog: List<DownloadResult>) {
            this.downloadLog = downloadLog
            notifyDataSetChanged()
        }

        @UnstableApi override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder: DownloadLogItemViewHolder
            if (convertView == null) {
                holder = DownloadLogItemViewHolder(context, parent)
                holder.itemView.tag = holder
            } else holder = convertView.tag as DownloadLogItemViewHolder

            val item = getItem(position)
            if (item != null) bind(holder, item, position)
            return holder.itemView
        }

        @UnstableApi private fun bind(holder: DownloadLogItemViewHolder, status: DownloadResult, position: Int) {
            var statusText: String? = ""
            when (status.feedfileType) {
                Feed.FEEDFILETYPE_FEED -> statusText += context.getString(R.string.download_type_feed)
                EpisodeMedia.FEEDFILETYPE_FEEDMEDIA -> statusText += context.getString(R.string.download_type_media)
            }
            statusText += " · "
            statusText += DateUtils.getRelativeTimeSpanString(status.getCompletionDate().time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, 0)
            holder.status.text = statusText

            if (status.title.isNotEmpty()) holder.title.text = status.title
            else holder.title.setText(R.string.download_log_title_unknown)

            if (status.isSuccessful) {
                holder.icon.setTextColor(ThemeUtils.getColorFromAttr(context, R.attr.icon_green))
                holder.icon.text = "{faw_check_circle}"
                holder.icon.setContentDescription(context.getString(R.string.download_successful))
                holder.secondaryActionButton.visibility = View.INVISIBLE
                holder.reason.visibility = View.GONE
                holder.tapForDetails.visibility = View.GONE
            } else {
                if (status.reason == DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE) {
                    holder.icon.setTextColor(ThemeUtils.getColorFromAttr(context, R.attr.icon_yellow))
                    holder.icon.text = "{faw_exclamation_circle}"
                } else {
                    holder.icon.setTextColor(ThemeUtils.getColorFromAttr(context, R.attr.icon_red))
                    holder.icon.text = "{faw_times_circle}"
                }
                holder.icon.setContentDescription(context.getString(R.string.error_label))
                holder.reason.setText(DownloadErrorLabel.from(status.reason))
                holder.reason.visibility = View.VISIBLE
                holder.tapForDetails.visibility = View.VISIBLE

                if (newerWasSuccessful(position, status.feedfileType, status.feedfileId)) {
                    holder.secondaryActionButton.visibility = View.INVISIBLE
                    holder.secondaryActionButton.setOnClickListener(null)
                    holder.secondaryActionButton.tag = null
                } else {
                    holder.secondaryActionIcon.setImageResource(R.drawable.ic_refresh)
                    holder.secondaryActionButton.visibility = View.VISIBLE

                    when (status.feedfileType) {
                        Feed.FEEDFILETYPE_FEED -> {
                            holder.secondaryActionButton.setOnClickListener(View.OnClickListener setOnClickListener@{
                                holder.secondaryActionButton.visibility = View.INVISIBLE
                                val feed: Feed? = getFeed(status.feedfileId)
                                if (feed == null) {
                                    Log.e(TAG, "Could not find feed for feed id: " + status.feedfileId)
                                    return@setOnClickListener
                                }
                                FeedUpdateManager.runOnce(context, feed)
                            })
                        }
                        EpisodeMedia.FEEDFILETYPE_FEEDMEDIA -> {
                            holder.secondaryActionButton.setOnClickListener {
                                holder.secondaryActionButton.visibility = View.INVISIBLE
                                val item_ = realm.query(Episode::class).query("id == $0", status.feedfileId).first().find()
                                if (item_ != null) DownloadActionButton(item_).onClick(context)
                                (context as MainActivity).showSnackbarAbovePlayer(R.string.status_downloading_label, Toast.LENGTH_SHORT)
                            }
                        }
                    }
                }
            }
        }

        private fun newerWasSuccessful(downloadStatusIndex: Int, feedTypeId: Int, id: Long): Boolean {
            for (i in 0 until downloadStatusIndex) {
                val status: DownloadResult = downloadLog[i]
                if (status.feedfileType == feedTypeId && status.feedfileId == id && status.isSuccessful) return true
            }
            return false
        }

        override fun getCount(): Int {
            return downloadLog.size
        }

        override fun getItem(position: Int): DownloadResult? {
            if (position in downloadLog.indices) return downloadLog[position]
            return null
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        private class DownloadLogItemViewHolder(context: Context, parent: ViewGroup?)
            : RecyclerView.ViewHolder(LayoutInflater.from(context).inflate(R.layout.downloadlog_item, parent, false)) {

            val binding = DownloadlogItemBinding.bind(itemView)
            @JvmField
            val secondaryActionButton: View = binding.secondaryActionLayout.secondaryAction
            @JvmField
            val secondaryActionIcon: ImageView = binding.secondaryActionLayout.secondaryActionIcon
            //    val secondaryActionProgress: CircularProgressBar = binding.secondaryAction.secondaryActionProgress
            @JvmField
            val icon: IconicsTextView = binding.txtvIcon
            @JvmField
            val title: TextView = binding.txtvTitle
            @JvmField
            val status: TextView = binding.status
            @JvmField
            val reason: TextView = binding.txtvReason
            @JvmField
            val tapForDetails: TextView = binding.txtvTapForDetails

            init {
                title.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_FULL
                itemView.tag = this
            }
        }

        companion object {
            private val TAG: String = DownloadLogAdapter::class.simpleName ?: "Anonymous"
        }
    }

    companion object {
        private val TAG: String = DownloadLogFragment::class.simpleName ?: "Anonymous"
    }
}
