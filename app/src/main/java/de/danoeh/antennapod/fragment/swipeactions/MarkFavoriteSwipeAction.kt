package de.danoeh.antennapod.fragment.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter

class MarkFavoriteSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.MARK_FAV
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_star
    }

    override fun getActionColor(): Int {
        return R.attr.icon_yellow
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.add_to_favorite_label)
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        DBWriter.toggleFavoriteItem(item)
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return filter.showIsFavorite || filter.showNotFavorite
    }
}
