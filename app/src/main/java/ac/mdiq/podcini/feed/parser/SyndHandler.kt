package ac.mdiq.podcini.feed.parser

import android.util.Log
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.feed.parser.namespace.*
import ac.mdiq.podcini.feed.parser.util.TypeGetter
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** Superclass for all SAX Handlers which process Syndication formats  */
class SyndHandler(feed: Feed, type: TypeGetter.Type) : DefaultHandler() {
    @JvmField
    val state: HandlerState = HandlerState(feed)

    init {
        if (type == TypeGetter.Type.RSS20 || type == TypeGetter.Type.RSS091) {
            state.defaultNamespaces.push(Rss20())
        }
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
        if (state.tagstack.size >= 2 && state.contentBuf != null) {
            state.contentBuf!!.appendRange(ch, start, start + length)
        }
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
        if (state.defaultNamespaces.size > 1 && prefix == DEFAULT_PREFIX) {
            state.defaultNamespaces.pop()
        }
    }

    @Throws(SAXException::class)
    override fun startPrefixMapping(prefix: String, uri: String) {
        // Find the right namespace
        if (!state.namespaces.containsKey(uri)) {
            when {
                uri == Atom.NSURI -> {
                    when (prefix) {
                        DEFAULT_PREFIX -> {
                            state.defaultNamespaces.push(Atom())
                        }
                        Atom.NSTAG -> {
                            state.namespaces[uri] = Atom()
                            Log.d(TAG, "Recognized Atom namespace")
                        }
                    }
                }
                uri == Content.NSURI && prefix == Content.NSTAG -> {
                    state.namespaces[uri] = Content()
                    Log.d(TAG, "Recognized Content namespace")
                }
                uri == Itunes.NSURI && prefix == Itunes.NSTAG -> {
                    state.namespaces[uri] = Itunes()
                    Log.d(TAG, "Recognized ITunes namespace")
                }
                uri == YouTube.NSURI && prefix == YouTube.NSTAG -> {
                    state.namespaces[uri] = YouTube()
                    Log.d(TAG, "Recognized YouTube namespace")
                }
                uri == SimpleChapters.NSURI && prefix.matches(SimpleChapters.NSTAG.toRegex()) -> {
                    state.namespaces[uri] = SimpleChapters()
                    Log.d(TAG, "Recognized SimpleChapters namespace")
                }
                uri == Media.NSURI && prefix == Media.NSTAG -> {
                    state.namespaces[uri] = Media()
                    Log.d(TAG, "Recognized media namespace")
                }
                uri == DublinCore.NSURI && prefix == DublinCore.NSTAG -> {
                    state.namespaces[uri] = DublinCore()
                    Log.d(TAG, "Recognized DublinCore namespace")
                }
                uri == PodcastIndex.NSURI || uri == PodcastIndex.NSURI2 && prefix == PodcastIndex.NSTAG -> {
                    state.namespaces[uri] = PodcastIndex()
                    Log.d(TAG, "Recognized PodcastIndex namespace")
                }
            }
        }
    }

    private fun getHandlingNamespace(uri: String, qualifiedName: String): Namespace? {
        var handler = state.namespaces[uri]
        if (handler == null && !state.defaultNamespaces.empty() && !qualifiedName.contains(":")) {
            handler = state.defaultNamespaces.peek()
        }
        return handler
    }

    @Throws(SAXException::class)
    override fun endDocument() {
        super.endDocument()
        state.feed.items = state.items
    }

    companion object {
        private const val TAG = "SyndHandler"
        private const val DEFAULT_PREFIX = ""
    }
}
