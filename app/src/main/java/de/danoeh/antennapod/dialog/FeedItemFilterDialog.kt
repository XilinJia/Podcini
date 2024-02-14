package de.danoeh.antennapod.dialog

import android.os.Bundle
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.model.feed.Feed

class FeedItemFilterDialog : ItemFilterDialog() {
    override fun onFilterChanged(newFilterValues: Set<String>) {
        val feedId = requireArguments().getLong(ARGUMENT_FEED_ID)
        DBWriter.setFeedItemsFilter(feedId, newFilterValues)
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
