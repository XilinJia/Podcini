package ac.mdiq.podcini.parser.feed.namespace

import android.text.TextUtils
import ac.mdiq.podcini.model.feed.FeedFunding
import ac.mdiq.podcini.parser.feed.HandlerState
import ac.mdiq.podcini.parser.feed.element.SyndElement
import org.xml.sax.Attributes

class PodcastIndex : Namespace() {
    override fun handleElementStart(localName: String, state: HandlerState,
                                    attributes: Attributes): SyndElement {
        if (FUNDING == localName) {
            val href = attributes.getValue(URL)
            val funding = FeedFunding(href, "")
            state.currentFunding = funding
            state.feed.addPayment(state.currentFunding!!)
        } else if (CHAPTERS == localName) {
            val href = attributes.getValue(URL)
            if (!TextUtils.isEmpty(href)) {
                state.currentItem!!.podcastIndexChapterUrl = href
            }
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
        if (state.contentBuf == null) {
            return
        }
        val content = state.contentBuf.toString()
        if (FUNDING == localName && state.currentFunding != null && !TextUtils.isEmpty(content)) {
            state.currentFunding!!.setContent(content)
        }
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
