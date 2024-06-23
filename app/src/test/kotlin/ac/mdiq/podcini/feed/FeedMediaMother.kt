package ac.mdiq.podcini.feed

import ac.mdiq.podcini.storage.model.EpisodeMedia

internal object FeedMediaMother {
    private const val EPISODE_URL = "http://example.com/episode"
    private const val SIZE: Long = 42
    private const val MIME_TYPE = "audio/mp3"

    @JvmStatic
    fun anyFeedMedia(): EpisodeMedia {
        return EpisodeMedia(null, EPISODE_URL, SIZE, MIME_TYPE)
    }
}
