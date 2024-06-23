package ac.mdiq.podcini.net.feed.parser.namespace

import ac.mdiq.podcini.net.feed.parser.HandlerState
import ac.mdiq.podcini.net.feed.parser.element.SyndElement
import ac.mdiq.podcini.net.feed.parser.utils.DateUtils.parseOrNullIfFuture
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils.getMimeType
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils.isMediaFile
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.util.Logd
import androidx.core.text.HtmlCompat
import org.xml.sax.Attributes

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
                if (state.currentItem?.media == null && isMediaFile(mimeType) && validUrl) {
                    var size: Long = 0
                    try {
                        size = attributes.getValue(ENC_LEN)?.toLong() ?: 0
                        // less than 16kb is suspicious, check manually
                        if (size < 16384) size = 0
                    } catch (e: NumberFormatException) {
                        Logd(TAG, "Length attribute could not be parsed.")
                    }
                    val media = EpisodeMedia(state.currentItem, url, size, mimeType)
                    if(state.currentItem != null) state.currentItem!!.media = media
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
                        if (currentItem.media != null) {
                            val duration = state.tempObjects[Itunes.DURATION] as? Int
                            if (duration != null) currentItem.media!!.setDuration(duration)
                        }
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
                    GUID == top && ITEM == second -> {
                        if (contentRaw.isNotEmpty() && state.currentItem != null) state.currentItem!!.identifier = contentRaw
                    }
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
                    PUBDATE == top && ITEM == second && state.currentItem != null ->
                        state.currentItem!!.pubDate = parseOrNullIfFuture(content)?.time ?: 0
                    // prefer itunes:image
                    URL == top && IMAGE == second && CHANNEL == third -> {
                        if (state.feed.imageUrl == null) state.feed.imageUrl = content
                    }
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
