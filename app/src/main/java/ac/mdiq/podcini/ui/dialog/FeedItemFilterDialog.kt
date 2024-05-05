package ac.mdiq.podcini.ui.dialog

import android.os.Bundle
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.Feed
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

class FeedItemFilterDialog : ItemFilterDialog() {
    @OptIn(UnstableApi::class) override fun onFilterChanged(newFilterValues: Set<String>) {
        val feedId = requireArguments().getLong(ARGUMENT_FEED_ID)
        DBWriter.persistFeedItemsFilter(feedId, newFilterValues)
    }

    companion object {
        private const val ARGUMENT_FEED_ID = "feedId"

        fun newInstance(feed: Feed): FeedItemFilterDialog {
            val dialog = FeedItemFilterDialog()
            val arguments = Bundle()
            arguments.putSerializable(ARGUMENT_FILTER, feed.itemFilter)
            arguments.putLong(ARGUMENT_FEED_ID, feed.id)
            dialog.arguments = arguments
            return dialog
        }
    }
}
