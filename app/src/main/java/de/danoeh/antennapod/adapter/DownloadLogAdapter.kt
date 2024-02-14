package de.danoeh.antennapod.adapter

import de.danoeh.antennapod.activity.MainActivity
import android.app.Activity
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import de.danoeh.antennapod.R
import de.danoeh.antennapod.adapter.actionbutton.DownloadActionButton
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.core.util.DownloadErrorLabel
import de.danoeh.antennapod.core.util.download.FeedUpdateManager
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.view.viewholder.DownloadLogItemViewHolder

/**
 * Displays a list of DownloadStatus entries.
 */
class DownloadLogAdapter(private val context: Activity) : BaseAdapter() {
    private var downloadLog: List<DownloadResult> = ArrayList()

    fun setDownloadLog(downloadLog: List<DownloadResult>) {
        this.downloadLog = downloadLog
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
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

    private fun bind(holder: DownloadLogItemViewHolder, status: DownloadResult, position: Int) {
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

        if (status.title != null) {
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
                    holder.secondaryActionButton.setOnClickListener(View.OnClickListener setOnClickListener@{ v: View? ->
                        holder.secondaryActionButton.visibility = View.INVISIBLE
                        val feed: Feed? = DBReader.getFeed(status.feedfileId)
                        if (feed == null) {
                            Log.e(TAG, "Could not find feed for feed id: " + status.feedfileId)
                            return@setOnClickListener
                        }
                        FeedUpdateManager.runOnce(context, feed)
                    })
                } else if (status.feedfileType == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                    holder.secondaryActionButton.setOnClickListener(View.OnClickListener { v: View? ->
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
        if (position < downloadLog.size) {
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
