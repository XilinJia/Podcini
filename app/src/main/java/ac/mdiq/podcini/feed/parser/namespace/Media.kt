package ac.mdiq.podcini.feed.parser.namespace

import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.feed.parser.HandlerState
import ac.mdiq.podcini.feed.parser.element.AtomText
import ac.mdiq.podcini.feed.parser.element.SyndElement
import ac.mdiq.podcini.feed.parser.util.MimeTypeUtils.getMimeType
import ac.mdiq.podcini.feed.parser.util.MimeTypeUtils.isImageFile
import ac.mdiq.podcini.feed.parser.util.MimeTypeUtils.isMediaFile
import android.util.Log
import org.xml.sax.Attributes
import java.util.concurrent.TimeUnit

/** Processes tags from the http://search.yahoo.com/mrss/ namespace.  */
class Media : Namespace() {
    override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
        if (CONTENT == localName) {
            val url = attributes.getValue(DOWNLOAD_URL)
            val defaultStr = attributes.getValue(DEFAULT)
            val medium = attributes.getValue(MEDIUM)
            var validTypeMedia = false
            var validTypeImage = false
            val isDefault = "true" == defaultStr
            var mimeType = getMimeType(attributes.getValue(MIME_TYPE), url)

            if (MEDIUM_AUDIO == medium) {
                validTypeMedia = true
                mimeType = "audio/*"
            } else if (MEDIUM_VIDEO == medium) {
                validTypeMedia = true
                mimeType = "video/*"
            } else if (MEDIUM_IMAGE == medium && (mimeType == null
                            || (!mimeType.startsWith("audio/") && !mimeType.startsWith("video/")))) {
                // Apparently, some publishers explicitly specify the audio file as an image
                validTypeImage = true
                mimeType = "image/*"
            } else {
                if (isMediaFile(mimeType)) {
                    validTypeMedia = true
                } else if (isImageFile(mimeType)) {
                    validTypeImage = true
                }
            }

            if (state.currentItem != null && (state.currentItem!!.media == null || isDefault) && url != null && validTypeMedia) {
                var size: Long = 0
                val sizeStr = attributes.getValue(SIZE)
                if (!sizeStr.isNullOrEmpty()) {
                    try {
                        size = sizeStr.toLong()
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Size \"$sizeStr\" could not be parsed.")
                    }
                }
                var durationMs = 0
                val durationStr = attributes.getValue(DURATION)
                if (!durationStr.isNullOrEmpty()) {
                    try {
                        val duration = durationStr.toLong()
                        durationMs = TimeUnit.MILLISECONDS.convert(duration, TimeUnit.SECONDS).toInt()
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Duration \"$durationStr\" could not be parsed")
                    }
                }
                val media = FeedMedia(state.currentItem, url, size, mimeType)
                if (durationMs > 0) {
                    media.setDuration( durationMs)
                }
                state.currentItem!!.media = media
            } else if (state.currentItem != null && url != null && validTypeImage) {
                state.currentItem!!.imageUrl = url
            }
        } else if (IMAGE == localName) {
            val url = attributes.getValue(IMAGE_URL)
            if (url != null) {
                if (state.currentItem != null) {
                    state.currentItem!!.imageUrl = url
                } else {
                    if (state.feed.imageUrl == null) {
                        state.feed.imageUrl = url
                    }
                }
            }
        } else if (DESCRIPTION == localName) {
            val type = attributes.getValue(DESCRIPTION_TYPE)
            return AtomText(localName, this, type)
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
        if (DESCRIPTION == localName) {
            val content = state.contentBuf.toString()
            if (state.currentItem != null) {
                state.currentItem!!.setDescriptionIfLonger(content)
            }
        }
    }

    companion object {
        private const val TAG = "NSMedia"

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

