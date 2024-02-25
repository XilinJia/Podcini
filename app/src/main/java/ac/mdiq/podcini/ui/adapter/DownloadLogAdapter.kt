package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.ui.activity.MainActivity
import android.app.Activity
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.adapter.actionbutton.DownloadActionButton
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.util.DownloadErrorLabel
import ac.mdiq.podcini.net.download.FeedUpdateManager
import ac.mdiq.podcini.storage.model.download.DownloadError
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.ui.common.ThemeUtils
import ac.mdiq.podcini.ui.view.viewholder.DownloadLogItemViewHolder
import androidx.media3.common.util.UnstableApi

/**
 * Displays a list of DownloadStatus entries.
 */
class DownloadLogAdapter(private val context: Activity) : BaseAdapter() {
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
        } else {
            holder = convertView.tag as DownloadLogItemViewHolder
        }
        val item = getItem(position)
        if (item != null) bind(holder, item, position)
        return holder.itemView
    }

    @UnstableApi private fun bind(holder: DownloadLogItemViewHolder, status: DownloadResult, position: Int) {
        var statusText: String? = ""
        if (status.feedfileType == Feed.FEEDFILETYPE_FEED) {
            statusText += context.getString(R.string.download_type_feed)
        } else if (status.feedfileType == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            statusText += context.getString(R.string.download_type_media)
        }
        statusText += " · "
        statusText += DateUtils.getRelativeTimeSpanString(status.getCompletionDate().time,
            System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, 0)
        holder.status.text = statusText

        if (status.title.isNotEmpty()) {
            holder.title.text = status.title
        } else {
            holder.title.setText(R.string.download_log_title_unknown)
        }

        if (status.isSuccessful) {
            holder.icon.setTextColor(ThemeUtils.getColorFromAttr(context, R.attr.icon_green))
            holder.icon.text = "{fa-check-circle}"
            holder.icon.setContentDescription(context.getString(R.string.download_successful))
            holder.secondaryActionButton.visibility = View.INVISIBLE
            holder.reason.visibility = View.GONE
            holder.tapForDetails.visibility = View.GONE
        } else {
            if (status.reason == DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE) {
                holder.icon.setTextColor(ThemeUtils.getColorFromAttr(context, R.attr.icon_yellow))
                holder.icon.text = "{fa-exclamation-circle}"
            } else {
                holder.icon.setTextColor(ThemeUtils.getColorFromAttr(context, R.attr.icon_red))
                holder.icon.text = "{fa-times-circle}"
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

                if (status.feedfileType == Feed.FEEDFILETYPE_FEED) {
                    holder.secondaryActionButton.setOnClickListener(View.OnClickListener setOnClickListener@{
                        holder.secondaryActionButton.visibility = View.INVISIBLE
                        val feed: Feed? = DBReader.getFeed(status.feedfileId)
                        if (feed == null) {
                            Log.e(TAG, "Could not find feed for feed id: " + status.feedfileId)
                            return@setOnClickListener
                        }
                        FeedUpdateManager.runOnce(context, feed)
                    })
                } else if (status.feedfileType == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                    holder.secondaryActionButton.setOnClickListener(View.OnClickListener {
                        holder.secondaryActionButton.visibility = View.INVISIBLE
                        val media: FeedMedia? = DBReader.getFeedMedia(status.feedfileId)
                        if (media == null) {
                            Log.e(TAG, "Could not find feed media for feed id: " + status.feedfileId)
                            return@OnClickListener
                        }
                        if (media.getItem() != null) DownloadActionButton(media.getItem()!!).onClick(context)
                        (context as MainActivity).showSnackbarAbovePlayer(
                            R.string.status_downloading_label, Toast.LENGTH_SHORT)
                    })
                }
            }
        }
    }

    private fun newerWasSuccessful(downloadStatusIndex: Int, feedTypeId: Int, id: Long): Boolean {
        for (i in 0 until downloadStatusIndex) {
            val status: DownloadResult = downloadLog[i]
            if (status.feedfileType == feedTypeId && status.feedfileId == id && status.isSuccessful) {
                return true
            }
        }
        return false
    }

    override fun getCount(): Int {
        return downloadLog.size
    }

    override fun getItem(position: Int): DownloadResult? {
        if (position in downloadLog.indices) {
            return downloadLog[position]
        }
        return null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    companion object {
        private const val TAG = "DownloadLogAdapter"
    }
}
