package ac.mdiq.podcini.net.feed.parser

import ac.mdiq.podcini.net.feed.parser.utils.DateUtils.parseOrNullIfFuture
import ac.mdiq.podcini.net.feed.parser.utils.DateUtils.parseTimeString
import ac.mdiq.podcini.net.feed.parser.utils.DurationParser.inMillis
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils.getMimeType
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils.isImageFile
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils.isMediaFile
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.util.Logd
import android.util.Log
import androidx.core.text.HtmlCompat
import org.apache.commons.io.input.XmlStreamReader
import org.jsoup.Jsoup
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.Reader
import java.util.*
import java.util.concurrent.TimeUnit
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

class FeedHandler {
    @Throws(SAXException::class, IOException::class, ParserConfigurationException::class, UnsupportedFeedtypeException::class)
    fun parseFeed(feed: Feed): FeedHandlerResult {
//        val tg = TypeGetter()
        val type = getType(feed)
        val handler = SyndHandler(feed, type)
        if (feed.fileUrl != null) {
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = true
            val saxParser = factory.newSAXParser()
//            saxParser.parse(File(feed.file_url!!), handler)

            val inputStreamReader: Reader = XmlStreamReader(File(feed.fileUrl!!))
            val inputSource = InputSource(inputStreamReader)
            Logd("FeedHandler", "starting saxParser.parse")
            saxParser.parse(inputSource, handler)
            inputStreamReader.close()
        }
        return FeedHandlerResult(handler.state.feed, handler.state.alternateUrls, handler.state.redirectUrl ?: "")
    }

    @Throws(UnsupportedFeedtypeException::class)
    fun getType(feed: Feed): Type {
        if (feed.type != null) return Type.fromName(feed.type!!)

        val factory: XmlPullParserFactory
        if (feed.fileUrl != null) {
            var reader: Reader? = null
            try {
                factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val xpp = factory.newPullParser()
                reader = createReader(feed)
                xpp.setInput(reader)
                var eventType = xpp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        when (val tag = xpp.name) {
                            ATOM_ROOT -> {
                                feed.type = Feed.FeedType.ATOM1.name
                                Logd(TAG, "Recognized type Atom")
                                val strLang = xpp.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
                                if (strLang != null) feed.language = strLang
                                return Type.ATOM
                            }
                            RSS_ROOT -> {
                                val strVersion = xpp.getAttributeValue(null, "version")
                                when (strVersion) {
                                    null -> {
                                        feed.type = Feed.FeedType.RSS.name
                                        Logd(TAG, "Assuming type RSS 2.0")
                                        return Type.RSS20
                                    }
                                    "2.0" -> {
                                        feed.type = Feed.FeedType.RSS.name
                                        Logd(TAG, "Recognized type RSS 2.0")
                                        return Type.RSS20
                                    }
                                    "0.91", "0.92" -> {
                                        Logd(TAG, "Recognized type RSS 0.91/0.92")
                                        return Type.RSS091
                                    }
                                    else -> throw UnsupportedFeedtypeException("Unsupported rss version")
                                }
                            }
                            else -> {
                                Logd(TAG, "Type is invalid")
                                throw UnsupportedFeedtypeException(Type.INVALID, tag)
                            }
                        }
                    } else {
                        // Apparently exception happens on some devices...
                        try { eventType = xpp.next() } catch (e: RuntimeException) { throw UnsupportedFeedtypeException("Unable to get type") }
                    }
                }
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
                // XML document might actually be a HTML document -> try to parse as HTML
                var rootElement: String? = null
                try {
                    Jsoup.parse(File(feed.fileUrl!!))
                    rootElement = "html"
                } catch (e1: IOException) {
                    Logd(TAG, "IOException: " + feed.fileUrl)
                    e1.printStackTrace()
                }
                throw UnsupportedFeedtypeException(Type.INVALID, rootElement)
            } catch (e: IOException) {
                Logd(TAG, "IOException: " + feed.fileUrl)
                e.printStackTrace()
            } finally {
                if (reader != null) {
                    try { reader.close()
                    } catch (e: IOException) {
                        Logd(TAG, "IOException: $reader")
                        e.printStackTrace()
                    }
                }
            }
        }
        Logd(TAG, "Type is invalid")
        throw UnsupportedFeedtypeException(Type.INVALID)
    }

    private fun createReader(feed: Feed): Reader? {
        if (feed.fileUrl == null) return null

        val reader: Reader
        try { reader = XmlStreamReader(File(feed.fileUrl!!))
        } catch (e: FileNotFoundException) {
            Logd(TAG, "FileNotFoundException: " + feed.fileUrl)
            e.printStackTrace()
            return null
        } catch (e: IOException) {
            Logd(TAG, "IOException: " + feed.fileUrl)
            e.printStackTrace()
            return null
        }
        return reader
    }

    enum class Type {
        RSS20, RSS091, ATOM, YOUTUBE, INVALID;

        companion object {
            fun fromName(name: String): Type {
                for (t in entries) {
                    if (t.name == name) return t
                }
                return INVALID
            }
        }
    }

    /**
     * Contains all relevant information to describe the current state of a SyndHandler.
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

    /** Superclass for all SAX Handlers which process Syndication formats  */
    class SyndHandler(feed: Feed, type: Type) : DefaultHandler() {
        @JvmField
        val state: HandlerState = HandlerState(feed)

        init {
            if (type == Type.RSS20 || type == Type.RSS091) state.defaultNamespaces.push(Rss20())
        }

        @Throws(SAXException::class)
        override fun startElement(uri: String, localName: String, qualifiedName: String, attributes: Attributes) {
            state.contentBuf = StringBuilder()
            val handler = getHandlingNamespace(uri, qualifiedName)
            if (handler != null) {
                val element = handler.handleElementStart(localName, state, attributes)
                state.tagstack.push(element)
            }
        }
        @Throws(SAXException::class)
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (state.tagstack.size >= 2 && state.contentBuf != null) state.contentBuf!!.appendRange(ch, start, start + length)
        }
        @Throws(SAXException::class)
        override fun endElement(uri: String, localName: String, qualifiedName: String) {
            val handler = getHandlingNamespace(uri, qualifiedName)
            if (handler != null) {
                handler.handleElementEnd(localName, state)
                state.tagstack.pop()
            }
            state.contentBuf = null
        }
        @Throws(SAXException::class)
        override fun endPrefixMapping(prefix: String) {
            if (state.defaultNamespaces.size > 1 && prefix == DEFAULT_PREFIX) state.defaultNamespaces.pop()
        }
        @Throws(SAXException::class)
        override fun startPrefixMapping(prefix: String, uri: String) {
            // Find the right namespace
            if (!state.namespaces.containsKey(uri)) {
                when {
                    uri == Atom.NSURI -> {
                        when (prefix) {
                            DEFAULT_PREFIX -> state.defaultNamespaces.push(Atom())
                            Atom.NSTAG -> {
                                state.namespaces[uri] = Atom()
                                Logd(TAG, "Recognized Atom namespace")
                            }
                        }
                    }
                    uri == Content.NSURI && prefix == Content.NSTAG -> {
                        state.namespaces[uri] = Content()
                        Logd(TAG, "Recognized Content namespace")
                    }
                    uri == Itunes.NSURI && prefix == Itunes.NSTAG -> {
                        state.namespaces[uri] = Itunes()
                        Logd(TAG, "Recognized ITunes namespace")
                    }
                    uri == SimpleChapters.NSURI && prefix.matches(SimpleChapters.NSTAG.toRegex()) -> {
                        state.namespaces[uri] = SimpleChapters()
                        Logd(TAG, "Recognized SimpleChapters namespace")
                    }
                    uri == Media.NSURI && prefix == Media.NSTAG -> {
                        state.namespaces[uri] = Media()
                        Logd(TAG, "Recognized media namespace")
                    }
                    uri == DublinCore.NSURI && prefix == DublinCore.NSTAG -> {
                        state.namespaces[uri] = DublinCore()
                        Logd(TAG, "Recognized DublinCore namespace")
                    }
                    uri == PodcastIndex.NSURI || uri == PodcastIndex.NSURI2 && prefix == PodcastIndex.NSTAG -> {
                        state.namespaces[uri] = PodcastIndex()
                        Logd(TAG, "Recognized PodcastIndex namespace")
                    }
                    else -> Logd(TAG, "startPrefixMapping can not handle uri: $uri")
                }
            }
        }
        private fun getHandlingNamespace(uri: String, qualifiedName: String): Namespace? {
            var handler = state.namespaces[uri]
            if (handler == null && !state.defaultNamespaces.empty() && !qualifiedName.contains(":")) handler = state.defaultNamespaces.peek()
            return handler
        }
        @Throws(SAXException::class)
        override fun endDocument() {
            super.endDocument()
            state.feed.episodes.clear()
            state.feed.episodes.addAll(state.items)
        }

        companion object {
            private val TAG: String = SyndHandler::class.simpleName ?: "Anonymous"
            private const val DEFAULT_PREFIX = ""
        }
    }

    class UnsupportedFeedtypeException : Exception {
        val type: Type
        var rootElement: String? = null
            private set
        override var message: String? = null
            get() {
                return when {
                    field != null -> field!!
                    type == Type.INVALID -> "Invalid type"
                    else -> "Type $type not supported"
                }
            }
        constructor(type: Type) : super() {
            this.type = type
        }
        constructor(type: Type, rootElement: String?) {
            this.type = type
            this.rootElement = rootElement
        }
        constructor(message: String?) {
            this.message = message
            type = Type.INVALID
        }

        companion object {
            private const val serialVersionUID = 9105878964928170669L
        }
    }

    class FeedHandlerResult(
            @JvmField val feed: Feed,
            @JvmField val alternateFeedUrls: Map<String, String>,
            val redirectUrl: String)

    abstract class Namespace {
        /** Called by a Feedhandler when in startElement and it detects a namespace element
         * @return The SyndElement to push onto the stack */
        abstract fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement

        /** Called by a Feedhandler when in endElement and it detects a namespace element */
        abstract fun handleElementEnd(localName: String, state: HandlerState)

        /** Trims all whitespace from beginning and ending of a String. {[String.trim]} only trims spaces. */
        fun trimAllWhitespace(string: String): String {
            return string.replace("(^\\s*)|(\\s*$)".toRegex(), "")
        }
    }

    /** Defines a XML Element that is pushed on the tagstack  */
    open class SyndElement(@JvmField val name: String, val namespace: Namespace)

    /** Represents Atom Element which contains text (content, title, summary).  */
    class AtomText(
            name: String,
            namespace: Namespace,
            private val type: String?) : SyndElement(name, namespace) {

        private var content: String? = null

        val processedContent: String?
            /** Processes the content according to the type and returns it.  */
            get() = when (type) {
                null -> content
                TYPE_HTML -> HtmlCompat.fromHtml(content!!, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                TYPE_XHTML -> content
                // Handle as text by default
                else -> content
            }

        fun setContent(content: String?) {
            this.content = content
        }

        companion object {
            const val TYPE_HTML: String = "html"
            private const val TYPE_XHTML = "xhtml"
        }
    }

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
                                    if (isMediaFile(mimeType) && currItem != null) currItem.fillMedia(href, size, mimeType)
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
                                        type == null && state.feed.link == null || LINK_TYPE_HTML == type || LINK_TYPE_XHTML == type -> state.feed.link = href
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
                                        //A Link such as to a directory such as iTunes
                                        LINK_TYPE_HTML, LINK_TYPE_XHTML -> {}
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
                    if (currentItem != null) {
                        val duration = state.tempObjects[Itunes.DURATION] as Int?
                        if (duration != null) currentItem.duration = (duration)
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

    class Content : Namespace() {
        override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
            return SyndElement(localName, this)
        }

        override fun handleElementEnd(localName: String, state: HandlerState) {
            if (ENCODED == localName && state.contentBuf != null)
                state.currentItem?.setDescriptionIfLonger(state.contentBuf.toString())
        }

        companion object {
            const val NSTAG: String = "content"
            const val NSURI: String = "http://purl.org/rss/1.0/modules/content/"

            private const val ENCODED = "encoded"
        }
    }

    class Itunes : Namespace() {
        override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
            if (IMAGE == localName) {
                val url: String? = attributes.getValue(IMAGE_HREF)
                if (state.currentItem != null) state.currentItem!!.imageUrl = url
                // this is the feed image
                // prefer to all other images
                else if (!url.isNullOrEmpty()) state.feed.imageUrl = url
            }
            return SyndElement(localName, this)
        }

        override fun handleElementEnd(localName: String, state: HandlerState) {
            if (state.contentBuf == null) return
            val content = state.contentBuf.toString()
            if (content.isEmpty()) return

            when {
                AUTHOR == localName && state.tagstack.size <= 3 -> {
                    val contentFromHtml = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                    state.feed.author = contentFromHtml
                }
                DURATION == localName -> {
                    try {
                        val durationMs = inMillis(content)
                        state.tempObjects[DURATION] = durationMs.toInt()
                    } catch (e: NumberFormatException) { Log.e(NSTAG, String.format("Duration '%s' could not be parsed", content)) }
                }
                SUBTITLE == localName -> {
                    when {
                        state.currentItem != null && state.currentItem?.description.isNullOrEmpty() -> state.currentItem!!.setDescriptionIfLonger(content)
                        state.feed.description.isNullOrEmpty() -> state.feed.description = content
                    }
                }
                SUMMARY == localName -> {
                    when {
                        state.currentItem != null -> state.currentItem!!.setDescriptionIfLonger(content)
                        Rss20.CHANNEL == state.secondTag.name -> state.feed.description = content
                    }
                }
                NEW_FEED_URL == localName && content.trim { it <= ' ' }.startsWith("http") -> state.redirectUrl = content.trim { it <= ' ' }
            }
        }

        companion object {
            const val NSTAG: String = "itunes"
            const val NSURI: String = "http://www.itunes.com/dtds/podcast-1.0.dtd"

            private const val IMAGE = "image"
            private const val IMAGE_HREF = "href"

            private const val AUTHOR = "author"
            const val DURATION: String = "duration"
            private const val SUBTITLE = "subtitle"
            private const val SUMMARY = "summary"
            private const val NEW_FEED_URL = "new-feed-url"
        }
    }

    /** Processes tags from the http://search.yahoo.com/mrss/ namespace.  */
    class Media : Namespace() {
        override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
//        Log.d(TAG, "handleElementStart $localName")
            when (localName) {
                CONTENT -> {
                    val url: String? = attributes.getValue(DOWNLOAD_URL)
                    val defaultStr: String? = attributes.getValue(DEFAULT)
                    val medium: String? = attributes.getValue(MEDIUM)
                    var validTypeMedia = false
                    var validTypeImage = false
                    val isDefault = "true" == defaultStr
                    var mimeType = getMimeType(attributes.getValue(MIME_TYPE), url)

                    when {
                        MEDIUM_AUDIO == medium -> {
                            validTypeMedia = true
                            mimeType = "audio/*"
                        }
                        MEDIUM_VIDEO == medium -> {
                            validTypeMedia = true
                            mimeType = "video/*"
                        }
                        MEDIUM_IMAGE == medium && (mimeType == null || (!mimeType.startsWith("audio/") && !mimeType.startsWith("video/"))) -> {
                            // Apparently, some publishers explicitly specify the audio file as an image
                            validTypeImage = true
                            mimeType = "image/*"
                        }
                        else -> {
                            when {
                                isMediaFile(mimeType) -> validTypeMedia = true
                                isImageFile(mimeType) -> validTypeImage = true
                            }
                        }
                    }

                    when {
                        state.currentItem != null && (state.currentItem == null || isDefault) && url != null && validTypeMedia -> {
                            var size: Long = 0
                            val sizeStr: String? = attributes.getValue(SIZE)
                            if (!sizeStr.isNullOrEmpty()) {
                                try { size = sizeStr.toLong() } catch (e: NumberFormatException) { Log.e(TAG, "Size \"$sizeStr\" could not be parsed.") }
                            }
                            var durationMs = 0
                            val durationStr: String? = attributes.getValue(DURATION)
                            if (!durationStr.isNullOrEmpty()) {
                                try {
                                    val duration = durationStr.toLong()
                                    durationMs = TimeUnit.MILLISECONDS.convert(duration, TimeUnit.SECONDS).toInt()
                                } catch (e: NumberFormatException) { Log.e(TAG, "Duration \"$durationStr\" could not be parsed") }
                            }
                            Logd(TAG, "handleElementStart creating media: ${state.currentItem?.title} $url $size $mimeType")
                            state.currentItem?.fillMedia(url, size, mimeType)
                            if (durationMs > 0) state.currentItem?.duration = ( durationMs)
                        }
                        state.currentItem != null && url != null && validTypeImage -> state.currentItem!!.imageUrl = url
                    }
                }
                IMAGE -> {
                    val url: String? = attributes.getValue(IMAGE_URL)
                    if (url != null) {
                        when {
                            state.currentItem != null -> state.currentItem!!.imageUrl = url
                            else -> if (state.feed.imageUrl == null) state.feed.imageUrl = url
                        }
                    }
                }
                DESCRIPTION -> {
                    val type: String? = attributes.getValue(DESCRIPTION_TYPE)
                    return AtomText(localName, this, type)
                }
            }
            return SyndElement(localName, this)
        }

        override fun handleElementEnd(localName: String, state: HandlerState) {
//        Log.d(TAG, "handleElementEnd $localName")
            if (DESCRIPTION == localName) {
                val content = state.contentBuf.toString()
                state.currentItem?.setDescriptionIfLonger(content)
            }
        }

        companion object {
            private val TAG: String = Media::class.simpleName ?: "Anonymous"

            const val NSTAG: String = "media"
            const val NSURI: String = "http://search.yahoo.com/mrss/"

            private const val CONTENT = "content"
            private const val DOWNLOAD_URL = "url"
            private const val SIZE = "fileSize"
            private const val MIME_TYPE = "type"
            private const val DURATION = "duration"
            private const val DEFAULT = "isDefault"
            private const val MEDIUM = "medium"

            private const val MEDIUM_IMAGE = "image"
            private const val MEDIUM_AUDIO = "audio"
            private const val MEDIUM_VIDEO = "video"

            private const val IMAGE = "thumbnail"
            private const val IMAGE_URL = "url"

            private const val DESCRIPTION = "description"
            private const val DESCRIPTION_TYPE = "type"
        }
    }

    class PodcastIndex : Namespace() {
        override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
            when (localName) {
                FUNDING -> {
                    val href: String? = attributes.getValue(URL)
                    val funding = FeedFunding(href, "")
                    state.currentFunding = funding
                    state.feed.addPayment(state.currentFunding!!)
                }
                CHAPTERS -> {
                    val href: String? = attributes.getValue(URL)
                    if (state.currentItem != null && !href.isNullOrEmpty()) state.currentItem!!.podcastIndexChapterUrl = href
                }
            }
            return SyndElement(localName, this)
        }

        override fun handleElementEnd(localName: String, state: HandlerState) {
            if (state.contentBuf == null) return
            val content = state.contentBuf.toString()
            if (FUNDING == localName && state.currentFunding != null && content.isNotEmpty()) state.currentFunding!!.setContent(content)
        }

        companion object {
            const val NSTAG: String = "podcast"
            const val NSURI: String = "https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/1.0.md"
            const val NSURI2: String = "https://podcastindex.org/namespace/1.0"
            private const val URL = "url"
            private const val FUNDING = "funding"
            private const val CHAPTERS = "chapters"
        }
    }

    /**
     * SAX-Parser for reading RSS-Feeds.
     */
    class Rss20 : Namespace() {
        override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
//        Log.d(TAG, "handleElementStart $localName")
            when {
                ITEM == localName && CHANNEL == state.tagstack.lastElement()?.name -> {
                    state.currentItem = Episode()
                    state.items.add(state.currentItem!!)
//                state.currentItem!!.feed = state.feed
                }
                ENCLOSURE == localName && ITEM == state.tagstack.peek()?.name -> {
                    val url: String? = attributes.getValue(ENC_URL)
                    val mimeType: String? = getMimeType(attributes.getValue(ENC_TYPE), url)
                    val validUrl = !url.isNullOrBlank()
                    if (state.currentItem == null && isMediaFile(mimeType) && validUrl) {
                        var size: Long = 0
                        try {
                            size = attributes.getValue(ENC_LEN)?.toLong() ?: 0
                            // less than 16kb is suspicious, check manually
                            if (size < 16384) size = 0
                        } catch (e: NumberFormatException) { Logd(TAG, "Length attribute could not be parsed.") }
                        state.currentItem?.fillMedia(url, size, mimeType)
                    }
                }
            }
            return SyndElement(localName, this)
        }

        override fun handleElementEnd(localName: String, state: HandlerState) {
//        Log.d(TAG, "handleElementEnd $localName")
            when {
                ITEM == localName -> {
                    if (state.currentItem != null) {
                        val currentItem = state.currentItem!!
                        // the title tag is optional in RSS 2.0. The description is used
                        // as a title if the item has no title-tag.
                        if (currentItem.title == null) currentItem.title = currentItem.description

                        if (state.tempObjects.containsKey(Itunes.DURATION)) {
                            val duration = state.tempObjects[Itunes.DURATION] as? Int
                            if (duration != null) currentItem.duration = duration
                            state.tempObjects.remove(Itunes.DURATION)
                        }
                    }
                    state.currentItem = null
                }
                state.tagstack.size >= 2 && state.contentBuf != null -> {
                    val contentRaw = state.contentBuf.toString()
                    val content = trimAllWhitespace(contentRaw)
                    val topElement = state.tagstack.peek()
                    val top = topElement.name
                    val secondElement = state.secondTag
                    val second = secondElement.name
                    var third: String? = null
                    if (state.tagstack.size >= 3) third = state.thirdTag.name

                    when {
                        // some feed creators include an empty or non-standard guid-element in their feed,
                        // which should be ignored
                        GUID == top && ITEM == second -> if (contentRaw.isNotEmpty() && state.currentItem != null) state.currentItem!!.identifier = contentRaw
                        TITLE == top -> {
                            val contentFromHtml = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            when {
                                ITEM == second && state.currentItem != null -> state.currentItem!!.title = contentFromHtml
                                CHANNEL == second -> state.feed.title = contentFromHtml
                            }
                        }
                        LINK == top -> {
                            when {
                                CHANNEL == second -> state.feed.link = content
                                ITEM == second && state.currentItem != null -> state.currentItem!!.link = content
                            }
                        }
                        PUBDATE == top && ITEM == second && state.currentItem != null -> state.currentItem!!.pubDate = parseOrNullIfFuture(content)?.time ?: 0
                        // prefer itunes:image
                        URL == top && IMAGE == second && CHANNEL == third -> if (state.feed.imageUrl == null) state.feed.imageUrl = content
                        DESCR == localName -> {
                            when {
                                CHANNEL == second -> state.feed.description = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                                ITEM == second && state.currentItem != null -> state.currentItem!!.setDescriptionIfLonger(content) // fromHtml here breaks \n when not html
                            }
                        }
                        LANGUAGE == localName -> state.feed.language = content.lowercase()
                    }
                }
            }
        }

        companion object {
            private val TAG: String = Rss20::class.simpleName ?: "Anonymous"

            const val CHANNEL: String = "channel"
            const val ITEM: String = "item"
            private const val GUID = "guid"
            private const val TITLE = "title"
            private const val LINK = "link"
            private const val DESCR = "description"
            private const val PUBDATE = "pubDate"
            private const val ENCLOSURE = "enclosure"
            private const val IMAGE = "image"
            private const val URL = "url"
            private const val LANGUAGE = "language"

            private const val ENC_URL = "url"
            private const val ENC_LEN = "length"
            private const val ENC_TYPE = "type"
        }
    }

    class SimpleChapters : Namespace() {
        override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
            val currentItem = state.currentItem
            if (currentItem != null) {
                when {
                    localName == CHAPTERS -> currentItem.chapters.clear()
                    localName == CHAPTER && !attributes.getValue(START).isNullOrEmpty() -> {
                        // if the chapter's START is empty, we don't need to do anything
                        try {
                            val start= parseTimeString(attributes.getValue(START))
                            val title: String? = attributes.getValue(TITLE)
                            val link: String? = attributes.getValue(HREF)
                            val imageUrl: String? = attributes.getValue(IMAGE)
                            val chapter = Chapter(start, title, link, imageUrl)
                            currentItem.chapters?.add(chapter)
                        } catch (e: NumberFormatException) { Log.e(TAG, "Unable to read chapter", e) }
                    }
                }
            }
            return SyndElement(localName, this)
        }

        override fun handleElementEnd(localName: String, state: HandlerState) {}

        companion object {
            private val TAG: String = SimpleChapters::class.simpleName ?: "Anonymous"

            const val NSTAG: String = "psc|sc"
            const val NSURI: String = "http://podlove.org/simple-chapters"

            private const val CHAPTERS = "chapters"
            private const val CHAPTER = "chapter"
            private const val START = "start"
            private const val TITLE = "title"
            private const val HREF = "href"
            private const val IMAGE = "image"
        }
    }

    class DublinCore : Namespace() {
        override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
            return SyndElement(localName, this)
        }

        override fun handleElementEnd(localName: String, state: HandlerState) {
            if (state.currentItem != null && state.contentBuf != null && state.tagstack.size >= 2) {
                val currentItem = state.currentItem
                val top = state.tagstack.peek().name
                val second = state.secondTag.name
                if (DATE == top && ITEM == second) {
                    val content = state.contentBuf.toString()
                    currentItem!!.pubDate = parseOrNullIfFuture(content)?.time ?: 0
                }
            }
        }

        companion object {
            const val NSTAG: String = "dc"
            const val NSURI: String = "http://purl.org/dc/elements/1.1/"

            private const val ITEM = "item"
            private const val DATE = "date"
        }
    }

    companion object {
        private val TAG: String = FeedHandler::class.simpleName ?: "Anonymous"
        private const val ATOM_ROOT = "feed"
        private const val RSS_ROOT = "rss"
    }
}
