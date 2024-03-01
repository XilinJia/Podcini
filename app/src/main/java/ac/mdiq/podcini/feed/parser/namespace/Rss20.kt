package ac.mdiq.podcini.feed.parser.namespace

import android.util.Log
import androidx.core.text.HtmlCompat
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.feed.parser.HandlerState
import ac.mdiq.podcini.feed.parser.element.SyndElement
import ac.mdiq.podcini.feed.parser.util.DateUtils.parseOrNullIfFuture
import ac.mdiq.podcini.feed.parser.util.MimeTypeUtils.getMimeType
import ac.mdiq.podcini.feed.parser.util.MimeTypeUtils.isMediaFile
import ac.mdiq.podcini.feed.parser.util.SyndStringUtils.trimAllWhitespace
import org.xml.sax.Attributes

/**
 * SAX-Parser for reading RSS-Feeds.
 */
class Rss20 : Namespace() {
    override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
        if (ITEM == localName && CHANNEL == state.tagstack.lastElement()?.name) {
            state.currentItem = FeedItem()
            state.items.add(state.currentItem!!)
            state.currentItem!!.feed = state.feed
        } else if (ENCLOSURE == localName && ITEM == state.tagstack.peek()?.name) {
            val url = attributes.getValue(ENC_URL)
            val mimeType = getMimeType(attributes.getValue(ENC_TYPE), url)

            val validUrl = url.isNotEmpty()
            if (state.currentItem?.media == null && isMediaFile(mimeType) && validUrl) {
                var size: Long = 0
                try {
                    size = attributes.getValue(ENC_LEN).toLong()
                    if (size < 16384) {
                        // less than 16kb is suspicious, check manually
                        size = 0
                    }
                } catch (e: NumberFormatException) {
                    Log.d(TAG, "Length attribute could not be parsed.")
                }
                val media = FeedMedia(state.currentItem, url, size, mimeType)
                state.currentItem!!.media = media
            }
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
        if (ITEM == localName) {
            if (state.currentItem != null) {
                val currentItem = state.currentItem!!
                // the title tag is optional in RSS 2.0. The description is used
                // as a title if the item has no title-tag.
                if (currentItem.title == null) {
                    currentItem.title = currentItem.description
                }

                if (state.tempObjects.containsKey(Itunes.DURATION)) {
                    if (currentItem.hasMedia()) {
                        val duration = state.tempObjects[Itunes.DURATION] as? Int
                        if (duration != null) currentItem.media!!.setDuration(duration)
                    }
                    state.tempObjects.remove(Itunes.DURATION)
                }
            }
            state.currentItem = null
        } else if (state.tagstack.size >= 2 && state.contentBuf != null) {
            val contentRaw = state.contentBuf.toString()
            val content = trimAllWhitespace(contentRaw)
            val contentFromHtml = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
            val topElement = state.tagstack.peek()
            val top = topElement.name
            val secondElement = state.secondTag
            val second = secondElement.name
            var third: String? = null
            if (state.tagstack.size >= 3) {
                third = state.thirdTag.name
            }
            if (GUID == top && ITEM == second) {
                // some feed creators include an empty or non-standard guid-element in their feed,
                // which should be ignored
                if (contentRaw.isNotEmpty() && state.currentItem != null) {
                    state.currentItem!!.itemIdentifier = contentRaw
                }
            } else if (TITLE == top) {
                if (ITEM == second && state.currentItem != null) {
                    state.currentItem!!.title = contentFromHtml
                } else if (CHANNEL == second) {
                    state.feed.title = contentFromHtml
                }
            } else if (LINK == top) {
                if (CHANNEL == second) {
                    state.feed.link = content
                } else if (ITEM == second && state.currentItem != null) {
                    state.currentItem!!.link = content
                }
            } else if (PUBDATE == top && ITEM == second && state.currentItem != null) {
                state.currentItem!!.pubDate = parseOrNullIfFuture(content)
            } else if (URL == top && IMAGE == second && CHANNEL == third) {
                // prefer itunes:image
                if (state.feed.imageUrl == null) {
                    state.feed.imageUrl = content
                }
            } else if (DESCR == localName) {
                if (CHANNEL == second) {
                    state.feed.description = contentFromHtml
                } else if (ITEM == second && state.currentItem != null) {
                    state.currentItem!!.setDescriptionIfLonger(content) // fromHtml here breaks \n when not html
                }
            } else if (LANGUAGE == localName) {
                state.feed.language = content.lowercase()
            }
        }
    }

    companion object {
        private const val TAG = "NSRSS20"

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
