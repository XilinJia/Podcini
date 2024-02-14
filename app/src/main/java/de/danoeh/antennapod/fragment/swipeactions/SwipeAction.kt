package de.danoeh.antennapod.fragment.swipeactions

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter


interface SwipeAction {
    fun getId(): String?
    fun getTitle(context: Context): String

    @DrawableRes
    fun getActionIcon(): Int

    @AttrRes
    @DrawableRes
    fun getActionColor(): Int

    fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter)

    fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean

    companion object {
        const val ADD_TO_QUEUE: String = "ADD_TO_QUEUE"
        const val REMOVE_FROM_INBOX: String = "REMOVE_FROM_INBOX"
        const val START_DOWNLOAD: String = "START_DOWNLOAD"
        const val MARK_FAV: String = "MARK_FAV"
        const val TOGGLE_PLAYED: String = "MARK_PLAYED"
        const val REMOVE_FROM_QUEUE: String = "REMOVE_FROM_QUEUE"
        const val DELETE: String = "DELETE"
        const val REMOVE_FROM_HISTORY: String = "REMOVE_FROM_HISTORY"
    }
}
