package ac.mdiq.podcini.model.playback

import android.text.TextUtils

enum class MediaType {
    AUDIO, VIDEO, UNKNOWN;

    companion object {
        private val AUDIO_APPLICATION_MIME_STRINGS: Set<String> = HashSet(mutableListOf(
            "application/ogg",
            "application/opus",
            "application/x-flac"
        ))

        fun fromMimeType(mimeType: String): MediaType {
            if (TextUtils.isEmpty(mimeType)) {
                return UNKNOWN
            } else if (mimeType.startsWith("audio")) {
                return AUDIO
            } else if (mimeType.startsWith("video")) {
                return VIDEO
            } else if (AUDIO_APPLICATION_MIME_STRINGS.contains(mimeType)) {
                return AUDIO
            }
            return UNKNOWN
        }
    }
}
