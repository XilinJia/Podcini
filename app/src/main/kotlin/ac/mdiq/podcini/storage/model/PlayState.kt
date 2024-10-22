package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.R

enum class PlayState(val code: Int, val res: Int, val userSet: Boolean) {
    UNSPECIFIED(-10, R.drawable.ic_questionmark, false),
    BUILDING(-2, R.drawable.baseline_build_24, false),
    NEW(-1, R.drawable.baseline_fiber_new_24, false),
    UNPLAYED(0, R.drawable.baseline_new_label_24, true),
    LATER(1, R.drawable.baseline_watch_later_24, true),
    SOON(2, R.drawable.baseline_local_play_24, true),
    INQUEUE(3, R.drawable.ic_playlist_play_black, false),
    INPROGRESS(5, R.drawable.baseline_play_circle_outline_24, false),
    SKIPPED(6, R.drawable.ic_skip_24dp, true),
    PLAYED(10, R.drawable.ic_mark_played, true),  // was 1
    IGNORED(20, R.drawable.baseline_visibility_off_24, true);

    companion object {
        fun fromCode(code: Int): PlayState {
            return enumValues<PlayState>().firstOrNull { it.code == code } ?: UNSPECIFIED
        }
    }
}
