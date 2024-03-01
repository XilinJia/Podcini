package ac.mdiq.podcini.feed.parser.namespace

import ac.mdiq.podcini.feed.parser.HandlerState
import ac.mdiq.podcini.feed.parser.element.SyndElement
import org.xml.sax.Attributes

class Content : Namespace() {
    override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
        if (ENCODED == localName && state.currentItem != null && state.contentBuf != null) {
            state.currentItem!!.setDescriptionIfLonger(state.contentBuf.toString())
        }
    }

    companion object {
        const val NSTAG: String = "content"
        const val NSURI: String = "http://purl.org/rss/1.0/modules/content/"

        private const val ENCODED = "encoded"
    }
}
