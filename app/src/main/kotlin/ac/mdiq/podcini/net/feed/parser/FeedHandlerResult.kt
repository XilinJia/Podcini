package ac.mdiq.podcini.net.feed.parser

import ac.mdiq.podcini.storage.model.Feed

/**
 * Container for results returned by the Feed parser
 */
class FeedHandlerResult(@JvmField val feed: Feed, @JvmField val alternateFeedUrls: Map<String, String>, val redirectUrl: String)
