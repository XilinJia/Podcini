package ac.mdiq.podcini.core.feed

import ac.mdiq.podcini.model.feed.FeedMedia

internal object FeedMediaMother {
    private const val EPISODE_URL = "http://example.com/episode"
    private const val SIZE: Long = 42
    private const val MIME_TYPE = "audio/mp3"

    @JvmStatic
    fun anyFeedMedia(): FeedMedia {
        return FeedMedia(null, EPISODE_URL, SIZE, MIME_TYPE)
    }
}
