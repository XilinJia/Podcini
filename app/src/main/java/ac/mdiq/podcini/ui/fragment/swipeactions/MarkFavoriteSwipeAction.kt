package ac.mdiq.podcini.ui.fragment.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

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

    @OptIn(UnstableApi::class) override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        DBWriter.toggleFavoriteItem(item)
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return filter.showIsFavorite || filter.showNotFavorite
    }
}
