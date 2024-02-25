package ac.mdiq.podcini.feed

import java.io.IOException

class FeedUrlNotFoundException(@JvmField val artistName: String, @JvmField val trackName: String) : IOException() {
    override val message: String
        get() = "Result does not specify a feed url"
}