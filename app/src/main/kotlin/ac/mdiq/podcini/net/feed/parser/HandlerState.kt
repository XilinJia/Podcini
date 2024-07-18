package ac.mdiq.podcini.net.feed.parser

import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.FeedFunding
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.net.feed.parser.element.SyndElement
import ac.mdiq.podcini.net.feed.parser.namespace.Namespace
import java.util.*

/**
 * Contains all relevant information to describe the current state of a
 * SyndHandler.
 */
/**
 * Feed that the Handler is currently processing.
 */
class HandlerState(@JvmField var feed: Feed) {
    /**
     * Contains links to related feeds, e.g. feeds with enclosures in other formats. The key of the map is the
     * URL of the feed, the value is the title
     */
    @JvmField
    val alternateUrls: MutableMap<String, String> = HashMap()
    @JvmField
    var redirectUrl: String? = null
    @JvmField
    val items: ArrayList<Episode> = ArrayList()
    @JvmField
    var currentItem: Episode? = null
    @JvmField
    var currentFunding: FeedFunding? = null
    @JvmField
    val tagstack: Stack<SyndElement> = Stack()

    /**
     * Namespaces that have been defined so far.
     */
    @JvmField
    val namespaces: MutableMap<String, Namespace> = HashMap()
    @JvmField
    val defaultNamespaces: Stack<Namespace> = Stack()

    /**
     * Buffer for saving characters.
     */
    @JvmField
    var contentBuf: StringBuilder? = null

    /**
     * Temporarily saved objects.
     */
    @JvmField
    val tempObjects: MutableMap<String, Any> = HashMap()

    /**
     * Returns the SyndElement that comes after the top element of the tagstack.
     */
    val secondTag: SyndElement
        get() {
            val top = tagstack.pop()
            val second = tagstack.peek()
            tagstack.push(top)
            return second
        }

    val thirdTag: SyndElement
        get() {
            val top = tagstack.pop()
            val second = tagstack.pop()
            val third = tagstack.peek()
            tagstack.push(second)
            tagstack.push(top)
            return third
        }

    fun addAlternateFeedUrl(title: String, url: String) {
        alternateUrls[url] = title
    }
}
