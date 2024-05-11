package ac.mdiq.podcini.feed.parser.namespace

import ac.mdiq.podcini.feed.parser.HandlerState
import android.util.Log
import androidx.core.text.HtmlCompat
import ac.mdiq.podcini.feed.parser.element.SyndElement
import ac.mdiq.podcini.feed.parser.util.DurationParser.inMillis
import org.xml.sax.Attributes

class YouTube : Namespace() {
    override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
//        Log.d(TAG, "handleElementStart $localName")
        if (IMAGE == localName) {
            val url: String? = attributes.getValue(IMAGE_HREF)

            if (state.currentItem != null) {
                state.currentItem!!.imageUrl = url
            } else {
                // this is the feed image
                // prefer to all other images
                if (!url.isNullOrEmpty()) state.feed.imageUrl = url
            }
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
//        Log.d(TAG, "handleElementEnd $localName")
        if (state.contentBuf == null) return

        val content = state.contentBuf.toString()
        val contentFromHtml = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        if (content.isEmpty()) return

        when {
            AUTHOR == localName && state.tagstack.size <= 3 -> state.feed.author = contentFromHtml
            DURATION == localName -> {
                try {
                    val durationMs = inMillis(content)
                    state.tempObjects[DURATION] = durationMs.toInt()
                } catch (e: NumberFormatException) {
                    Log.e(NSTAG, String.format("Duration '%s' could not be parsed", content))
                }
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
        const val TAG: String = "NSYouTube"
        const val NSTAG: String = "yt"
        const val NSURI: String = "http://www.youtube.com/xml/schemas/2015"

        private const val IMAGE = "thumbnail"
        private const val IMAGE_HREF = "href"

        private const val AUTHOR = "author"
        const val DURATION: String = "duration"
        private const val SUBTITLE = "subtitle"
        private const val SUMMARY = "summary"
        private const val NEW_FEED_URL = "new-feed-url"
    }
}
