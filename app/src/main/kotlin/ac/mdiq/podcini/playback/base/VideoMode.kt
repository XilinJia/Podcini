package ac.mdiq.podcini.playback.base

enum class VideoMode(val code: Int, val tag: String) {
    NONE(0, "none"),
    WINDOW_VIEW(1, "window mode"),
    FULL_SCREEN_VIEW(2, "full screen"),
    AUDIO_ONLY(3, "audio only");

    companion object {
        val videoModeTags = VideoMode.entries.map { it.tag }

        fun fromCode(code: Int): VideoMode {
            return enumValues<VideoMode>().firstOrNull { it.code == code } ?: NONE
        }
        fun fromTag(tag: String): VideoMode {
            return enumValues<VideoMode>().firstOrNull { it.tag == tag } ?: NONE
        }
    }
}
