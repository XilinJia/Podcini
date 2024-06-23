package ac.mdiq.podcini.net.feed.parser.namespace

import ac.mdiq.podcini.net.feed.parser.HandlerState
import ac.mdiq.podcini.net.feed.parser.element.AtomText
import ac.mdiq.podcini.net.feed.parser.element.SyndElement
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils.getMimeType
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils.isImageFile
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils.isMediaFile
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.util.Logd
import android.util.Log
import org.xml.sax.Attributes
import java.util.concurrent.TimeUnit

/** Processes tags from the http://search.yahoo.com/mrss/ namespace.  */
class Media : Namespace() {
    override fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement {
//        Log.d(TAG, "handleElementStart $localName")
        when (localName) {
            CONTENT -> {
                val url: String? = attributes.getValue(DOWNLOAD_URL)
                val defaultStr: String? = attributes.getValue(DEFAULT)
                val medium: String? = attributes.getValue(MEDIUM)
                var validTypeMedia = false
                var validTypeImage = false
                val isDefault = "true" == defaultStr
                var mimeType = getMimeType(attributes.getValue(MIME_TYPE), url)

                when {
                    MEDIUM_AUDIO == medium -> {
                        validTypeMedia = true
                        mimeType = "audio/*"
                    }
                    MEDIUM_VIDEO == medium -> {
                        validTypeMedia = true
                        mimeType = "video/*"
                    }
                    MEDIUM_IMAGE == medium && (mimeType == null || (!mimeType.startsWith("audio/") && !mimeType.startsWith("video/"))) -> {
                        // Apparently, some publishers explicitly specify the audio file as an image
                        validTypeImage = true
                        mimeType = "image/*"
                    }
                    else -> {
                        when {
                            isMediaFile(mimeType) -> validTypeMedia = true
                            isImageFile(mimeType) -> validTypeImage = true
                        }
                    }
                }

                when {
                    state.currentItem != null && (state.currentItem!!.media == null || isDefault) && url != null && validTypeMedia -> {
                        var size: Long = 0
                        val sizeStr: String? = attributes.getValue(SIZE)
                        if (!sizeStr.isNullOrEmpty()) {
                            try {
                                size = sizeStr.toLong()
                            } catch (e: NumberFormatException) {
                                Log.e(TAG, "Size \"$sizeStr\" could not be parsed.")
                            }
                        }
                        var durationMs = 0
                        val durationStr: String? = attributes.getValue(DURATION)
                        if (!durationStr.isNullOrEmpty()) {
                            try {
                                val duration = durationStr.toLong()
                                durationMs = TimeUnit.MILLISECONDS.convert(duration, TimeUnit.SECONDS).toInt()
                            } catch (e: NumberFormatException) {
                                Log.e(TAG, "Duration \"$durationStr\" could not be parsed")
                            }
                        }
                        Logd(TAG, "handleElementStart creating media: ${state.currentItem?.title} $url $size $mimeType")
                        val media = EpisodeMedia(state.currentItem, url, size, mimeType)
                        if (durationMs > 0) media.setDuration( durationMs)

                        state.currentItem!!.media = media
                    }
                    state.currentItem != null && url != null && validTypeImage -> {
                        state.currentItem!!.imageUrl = url
                    }
                }
            }
            IMAGE -> {
                val url: String? = attributes.getValue(IMAGE_URL)
                if (url != null) {
                    when {
                        state.currentItem != null -> state.currentItem!!.imageUrl = url
                        else -> if (state.feed.imageUrl == null) state.feed.imageUrl = url
                    }
                }
            }
            DESCRIPTION -> {
                val type: String? = attributes.getValue(DESCRIPTION_TYPE)
                return AtomText(localName, this, type)
            }
        }
        return SyndElement(localName, this)
    }

    override fun handleElementEnd(localName: String, state: HandlerState) {
//        Log.d(TAG, "handleElementEnd $localName")
        if (DESCRIPTION == localName) {
            val content = state.contentBuf.toString()
            state.currentItem?.setDescriptionIfLonger(content)
        }
    }

    companion object {
        private val TAG: String = Media::class.simpleName ?: "Anonymous"

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

