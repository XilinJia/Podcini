package ac.mdiq.podcini.storage.model.playback

enum class MediaType {
    AUDIO, VIDEO, UNKNOWN;

    companion object {
        private val AUDIO_APPLICATION_MIME_STRINGS: Set<String> = HashSet(mutableListOf(
            "application/ogg",
            "application/opus",
            "application/x-flac"
        ))

        fun fromMimeType(mimeType: String?): MediaType {
            return when {
                mimeType.isNullOrEmpty() -> {
                    UNKNOWN
                }
                mimeType.startsWith("audio") -> {
                    AUDIO
                }
                mimeType.startsWith("video") -> {
                    VIDEO
                }
                AUDIO_APPLICATION_MIME_STRINGS.contains(mimeType) -> {
                    AUDIO
                }
                else -> UNKNOWN
            }
        }
    }
}
