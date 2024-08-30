package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.Feeds.deleteFeedSync
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RemoveFeedDialog {
    private val TAG: String = RemoveFeedDialog::class.simpleName ?: "Anonymous"

    fun show(context: Context, feed: Feed, callback: Runnable?) {
        val feeds = listOf(feed)
        val message = getMessageId(context, feeds)
        showDialog(context, feeds, message, callback)
    }

    fun show(context: Context, feeds: List<Feed>) {
        val message = getMessageId(context, feeds)
        showDialog(context, feeds, message, null)
    }

    private fun showDialog(context: Context, feeds: List<Feed>, message: String, callback: Runnable?) {
        val dialog: ConfirmationDialog = object : ConfirmationDialog(context, R.string.remove_feed_label, message) {
            @OptIn(UnstableApi::class) override fun onConfirmButtonPressed(clickedDialog: DialogInterface) {
                callback?.run()
                clickedDialog.dismiss()

                val progressDialog = ProgressDialog(context)
                progressDialog.setMessage(context.getString(R.string.feed_remover_msg))
                progressDialog.isIndeterminate = true
                progressDialog.setCancelable(false)
                progressDialog.show()

                val scope = CoroutineScope(Dispatchers.Main)
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            for (feed in feeds) {
                                deleteFeedSync(context, feed.id, false)
                            }
                            EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.REMOVED, feeds.map { it.id }))
                        }
                        withContext(Dispatchers.Main) {
                            Logd(TAG, "Feed(s) deleted")
                            progressDialog.dismiss()
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, Log.getStackTraceString(e))
                        withContext(Dispatchers.Main) { progressDialog.dismiss() }
                    }
                }
            }
        }
        dialog.createNewDialog().show()
    }

    private fun getMessageId(context: Context, feeds: List<Feed>): String {
        return if (feeds.size == 1) {
            if (feeds[0].isLocalFeed) context.getString(R.string.feed_delete_confirmation_local_msg) + feeds[0].title
            else context.getString(R.string.feed_delete_confirmation_msg) + feeds[0].title
        } else context.getString(R.string.feed_delete_confirmation_msg_batch)

    }
}
