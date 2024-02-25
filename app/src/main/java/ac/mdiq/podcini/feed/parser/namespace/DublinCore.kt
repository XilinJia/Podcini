package ac.mdiq.podcini.feed.parser.namespace

import ac.mdiq.podcini.feed.parser.HandlerState
import ac.mdiq.podcini.feed.parser.element.SyndElement
import ac.mdiq.podcini.feed.parser.util.DateUtils.parseOrNullIfFuture
import org.xml.sax.Attributes

class DublinCore : Namespace() {
    override fun handleElementStart(localName: String, state: ac.mdiq.podcini.feed.parser.HandlerState, attributes: Attributes): SyndElement {
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: ac.mdiq.podcini.feed.parser.HandlerState) {
        if (state.currentItem != null && state.contentBuf != null && state.tagstack.size >= 2) {
            val currentItem = state.currentItem
            val top = state.tagstack.peek().name
            val second = state.secondTag.name
            if (DATE == top && ITEM == second) {
                val content = state.contentBuf.toString()
                currentItem!!.pubDate = parseOrNullIfFuture(content)
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
