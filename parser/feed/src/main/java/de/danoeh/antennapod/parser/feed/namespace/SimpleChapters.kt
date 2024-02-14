package de.danoeh.antennapod.parser.feed.namespace

import android.text.TextUtils
import android.util.Log
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.parser.feed.HandlerState
import de.danoeh.antennapod.parser.feed.element.SyndElement
import de.danoeh.antennapod.parser.feed.util.DateUtils.parseTimeString
import org.xml.sax.Attributes

class SimpleChapters : Namespace() {
    override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
        val currentItem = state.currentItem
        if (currentItem != null) {
            if (localName == CHAPTERS) {
                currentItem.chapters = mutableListOf()
            } else if (localName == CHAPTER && !TextUtils.isEmpty(attributes.getValue(START))) {
                // if the chapter's START is empty, we don't need to do anything
                try {
                    val start = parseTimeString(attributes.getValue(START))
                    val title = attributes.getValue(TITLE)
                    val link = attributes.getValue(HREF)
                    val imageUrl = attributes.getValue(IMAGE)
                    val chapter = Chapter(start, title, link, imageUrl)
                    currentItem.chapters?.add(chapter)
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Unable to read chapter", e)
                }
            }
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
    }

    companion object {
        private const val TAG = "NSSimpleChapters"

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
