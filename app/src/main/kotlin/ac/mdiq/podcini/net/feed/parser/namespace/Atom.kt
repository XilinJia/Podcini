package ac.mdiq.podcini.net.feed.parser.namespace

import ac.mdiq.podcini.net.feed.parser.HandlerState
import ac.mdiq.podcini.net.feed.parser.element.AtomText
import ac.mdiq.podcini.net.feed.parser.element.SyndElement
import ac.mdiq.podcini.net.feed.parser.utils.DateUtils.parseOrNullIfFuture
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils.getMimeType
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils.isMediaFile
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.FeedFunding
import ac.mdiq.podcini.util.Logd
import org.xml.sax.Attributes

class Atom : Namespace() {
    override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
//        Log.d(TAG, "handleElementStart $localName")
        when {
            ENTRY == localName -> {
                state.currentItem = Episode()
                state.items.add(state.currentItem!!)
//                state.currentItem!!.feed = state.feed
            }
            localName.matches(isText.toRegex()) -> {
                val type: String? = attributes.getValue(TEXT_TYPE)
                return AtomText(localName, this, type)
            }
            LINK == localName -> {
                val href: String? = attributes.getValue(LINK_HREF)
                val rel: String? = attributes.getValue(LINK_REL)
                val parent = state.tagstack.peek()
                when {
                    parent.name.matches(isFeedItem.toRegex()) -> {
                        when (rel) {
                            null, LINK_REL_ALTERNATE -> if (state.currentItem != null) state.currentItem!!.link = href
                            LINK_REL_ENCLOSURE -> {
                                val strSize: String? = attributes.getValue(LINK_LENGTH)
                                var size: Long = 0
                                try { if (strSize != null) size = strSize.toLong() } catch (e: NumberFormatException) { Logd(TAG, "Length attribute could not be parsed.") }
                                val mimeType: String? = getMimeType(attributes.getValue(LINK_TYPE), href)
                                val currItem = state.currentItem
                                if (isMediaFile(mimeType) && currItem != null && currItem.media == null)
                                    currItem.media = EpisodeMedia(currItem, href, size, mimeType)
                            }
                            LINK_REL_PAYMENT -> if (state.currentItem != null) state.currentItem!!.paymentLink = href
                        }
                    }
                    parent.name.matches(isFeed.toRegex()) -> {
                        when (rel) {
                            null, LINK_REL_ALTERNATE -> {
                                val type: String? = attributes.getValue(LINK_TYPE)
//                                 Use as link if
//                                a) no type-attribute is given and feed-object has no link yet
//                                b) type of link is LINK_TYPE_HTML or LINK_TYPE_XHTML
                                when {
                                    type == null && state.feed.link == null || LINK_TYPE_HTML == type || LINK_TYPE_XHTML == type ->
                                        state.feed.link = href
                                    LINK_TYPE_ATOM == type || LINK_TYPE_RSS == type -> {
                                        // treat as podlove alternate feed
                                        var title: String? = attributes.getValue(LINK_TITLE)
                                        if (title.isNullOrEmpty()) title = href?:""
                                        if (!href.isNullOrEmpty()) state.addAlternateFeedUrl(title, href)
                                    }
                                }
                            }
                            LINK_REL_ARCHIVES -> {
                                val type: String? = attributes.getValue(LINK_TYPE)
                                when (type) {
                                    LINK_TYPE_ATOM, LINK_TYPE_RSS -> {
                                        var title: String? = attributes.getValue(LINK_TITLE)
                                        if (title.isNullOrEmpty()) title = href?:""
                                        if (!href.isNullOrEmpty()) state.addAlternateFeedUrl(title, href)
                                    }
                                    LINK_TYPE_HTML, LINK_TYPE_XHTML -> {
                                        //A Link such as to a directory such as iTunes
                                    }
                                }
                            }
                            LINK_REL_PAYMENT -> state.feed.addPayment(FeedFunding(href, ""))
                            LINK_REL_NEXT -> {
                                state.feed.isPaged = true
                                state.feed.nextPageLink = href
                            }
                        }
                    }
                }
            }
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
//        Log.d(TAG, "handleElementEnd $localName")
        if (ENTRY == localName) {
            if (state.currentItem != null && state.tempObjects.containsKey(Itunes.DURATION)) {
                val currentItem = state.currentItem
                if (currentItem!!.media != null) {
                    val duration = state.tempObjects[Itunes.DURATION] as Int?
                    if (duration != null) currentItem.media!!.setDuration(duration)
                }
                state.tempObjects.remove(Itunes.DURATION)
            }
            state.currentItem = null
        }

        if (state.tagstack.size >= 2) {
            var textElement: AtomText? = null
            val contentRaw = if (state.contentBuf != null) state.contentBuf.toString() else ""
            val content = trimAllWhitespace(contentRaw)
            val topElement = state.tagstack.peek()
            val top = topElement.name
            val secondElement = state.secondTag
            val second = secondElement.name

            if (top.matches(isText.toRegex())) {
                textElement = topElement as AtomText
                textElement.setContent(content)
            }
            when {
                ID == top -> {
                    when {
                        FEED == second -> state.feed.identifier = contentRaw
                        ENTRY == second && state.currentItem != null -> state.currentItem!!.identifier = contentRaw
                    }
                }
                TITLE == top && textElement != null -> {
                    when {
                        FEED == second -> state.feed.title = textElement.processedContent
                        ENTRY == second && state.currentItem != null -> state.currentItem!!.title = textElement.processedContent
                    }
                }
                SUBTITLE == top && FEED == second && textElement != null -> state.feed.description = textElement.processedContent
                CONTENT == top && ENTRY == second && textElement != null && state.currentItem != null ->
                    state.currentItem!!.setDescriptionIfLonger(textElement.processedContent)
                SUMMARY == top && ENTRY == second && textElement != null && state.currentItem != null ->
                    state.currentItem!!.setDescriptionIfLonger(textElement.processedContent)
                UPDATED == top && ENTRY == second && state.currentItem != null && state.currentItem!!.pubDate == 0L ->
                    state.currentItem!!.pubDate = parseOrNullIfFuture(content)?.time ?: 0
                PUBLISHED == top && ENTRY == second && state.currentItem != null ->
                    state.currentItem!!.pubDate = parseOrNullIfFuture(content)?.time ?: 0
                IMAGE_LOGO == top && state.feed.imageUrl == null -> state.feed.imageUrl = content
                IMAGE_ICON == top -> state.feed.imageUrl = content
                AUTHOR_NAME == top && AUTHOR == second && state.currentItem == null -> {
                    val currentName = state.feed.author
                    if (currentName == null) state.feed.author = content
                    else state.feed.author = "$currentName, $content"
                }
            }
        }
    }

    companion object {
        private val TAG: String = Atom::class.simpleName ?: "Anonymous"
        const val NSTAG: String = "atom"
        const val NSURI: String = "http://www.w3.org/2005/Atom"

        private const val FEED = "feed"
        private const val ID = "id"
        private const val TITLE = "title"
        private const val ENTRY = "entry"
        private const val LINK = "link"
        private const val UPDATED = "updated"
        private const val AUTHOR = "author"
        private const val AUTHOR_NAME = "name"
        private const val CONTENT = "content"
        private const val SUMMARY = "summary"
        private const val IMAGE_LOGO = "logo"
        private const val IMAGE_ICON = "icon"
        private const val SUBTITLE = "subtitle"
        private const val PUBLISHED = "published"

        private const val TEXT_TYPE = "type"

        // Link
        private const val LINK_HREF = "href"
        private const val LINK_REL = "rel"
        private const val LINK_TYPE = "type"
        private const val LINK_TITLE = "title"
        private const val LINK_LENGTH = "length"

        // rel-values
        private const val LINK_REL_ALTERNATE = "alternate"
        private const val LINK_REL_ARCHIVES = "archives"
        private const val LINK_REL_ENCLOSURE = "enclosure"
        private const val LINK_REL_PAYMENT = "payment"
        private const val LINK_REL_NEXT = "next"

        // type-values
        private const val LINK_TYPE_ATOM = "application/atom+xml"
        private const val LINK_TYPE_HTML = "text/html"
        private const val LINK_TYPE_XHTML = "application/xml+xhtml"

        private const val LINK_TYPE_RSS = "application/rss+xml"

        /**
         * Regexp to test whether an Element is a Text Element.
         */
        private const val isText = ("$TITLE|$CONTENT|$SUBTITLE|$SUMMARY")

        private const val isFeed = FEED + "|" + Rss20.CHANNEL
        private const val isFeedItem = ENTRY + "|" + Rss20.ITEM
    }
}
