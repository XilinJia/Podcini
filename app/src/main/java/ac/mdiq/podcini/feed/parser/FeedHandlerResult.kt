package ac.mdiq.podcini.feed.parser

import ac.mdiq.podcini.storage.model.feed.Feed

/**
 * Container for results returned by the Feed parser
 */
class FeedHandlerResult(@JvmField val feed: Feed, @JvmField val alternateFeedUrls: Map<String, String>, val redirectUrl: String)
