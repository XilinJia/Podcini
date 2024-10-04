package ac.mdiq.podcini.net.feed.parser

import ac.mdiq.podcini.net.feed.parser.namespace.*
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
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

    companion object {
        private val TAG: String = FeedHandler::class.simpleName ?: "Anonymous"
        private const val ATOM_ROOT = "feed"
        private const val RSS_ROOT = "rss"
    }
}
