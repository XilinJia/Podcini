package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.R
import androidx.compose.ui.graphics.Color

enum class PlayState(val code: Int, val res: Int, color: Color?, val userSet: Boolean) {
    UNSPECIFIED(-10, R.drawable.ic_questionmark, null, false),
    BUILDING(-2, R.drawable.baseline_build_24, null, false),
    NEW(-1, R.drawable.baseline_fiber_new_24, Color.Green, false),
    UNPLAYED(0, R.drawable.baseline_new_label_24, null, true),
    LATER(1, R.drawable.baseline_watch_later_24, Color.Green, true),
    SOON(2, R.drawable.baseline_local_play_24, Color.Green, true),
    INQUEUE(3, R.drawable.ic_playlist_play_black, Color.Green, false),
    INPROGRESS(5, R.drawable.baseline_play_circle_outline_24, Color.Green, false),
    SKIPPED(6, R.drawable.ic_skip_24dp, null, true),
    PLAYED(10, R.drawable.ic_mark_played, null, true),  // was 1
    IGNORED(20, R.drawable.baseline_visibility_off_24, null, true);

    companion object {
        fun fromCode(code: Int): PlayState {
            return enumValues<PlayState>().firstOrNull { it.code == code } ?: UNSPECIFIED
        }
    }
}
