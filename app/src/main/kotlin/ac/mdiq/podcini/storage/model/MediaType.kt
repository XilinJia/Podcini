package ac.mdiq.podcini.storage.model

enum class MediaType {
    AUDIO, VIDEO, FLASH, UNKNOWN;

    companion object {
        private val AUDIO_APPLICATION_MIME_STRINGS: Set<String> = HashSet(mutableListOf(
            "application/ogg",
            "application/opus",
            "application/x-flac"
        ))
        private val VIDEO_APPLICATION_MIME_STRINGS: Set<String> = HashSet(mutableListOf("application/x-shockwave-flash"))

        fun fromMimeType(mimeType: String?): MediaType {
            return when {
                mimeType.isNullOrEmpty() -> UNKNOWN
                mimeType.startsWith("audio") -> AUDIO
                mimeType.startsWith("video") -> VIDEO
                AUDIO_APPLICATION_MIME_STRINGS.contains(mimeType) -> AUDIO
                VIDEO_APPLICATION_MIME_STRINGS.contains(mimeType) -> FLASH
                else -> UNKNOWN
            }
        }
    }
}
