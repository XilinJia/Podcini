package ac.mdiq.podcini.ui.actions

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter

interface SwipeAction {
    fun getId(): String?
    fun getTitle(context: Context): String

    @DrawableRes
    fun getActionIcon(): Int

    @AttrRes
    @DrawableRes
    fun getActionColor(): Int

    fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter)

    fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
        return false
    }

    enum class ActionTypes {
        NO_ACTION,
        COMBO,
        RATING,
        SET_PLAY_STATE,
        ADD_TO_QUEUE,
        PUT_TO_QUEUE,
        REMOVE_FROM_QUEUE,
        START_DOWNLOAD,
        DELETE,
        REMOVE_FROM_HISTORY,
        SHELVE,
        ERASE
    }
}
