package ac.mdiq.podcini.feed.parser.media.id3

import android.util.Log
import ac.mdiq.podcini.storage.model.feed.Chapter
import ac.mdiq.podcini.storage.model.feed.EmbeddedChapterImage.Companion.makeUrl
import ac.mdiq.podcini.feed.parser.media.id3.model.FrameHeader
import org.apache.commons.io.input.CountingInputStream
import java.io.IOException
import java.net.URLDecoder

/**
 * Reads ID3 chapters.
 * See https://id3.org/id3v2-chapters-1.0
 */
class ChapterReader(input: CountingInputStream?) : ID3Reader(input!!) {
    private val chapters: MutableList<Chapter> = ArrayList()

    @Throws(IOException::class, ID3ReaderException::class)
    override fun readFrame(frameHeader: FrameHeader) {
        if (FRAME_ID_CHAPTER == frameHeader.id) {
            Log.d(TAG, "Handling frame: $frameHeader")
            val chapter = readChapter(frameHeader)
            Log.d(TAG, "Chapter done: $chapter")
            chapters.add(chapter)
        } else {
            super.readFrame(frameHeader)
        }
    }

    @Throws(IOException::class, ID3ReaderException::class)
    fun readChapter(frameHeader: FrameHeader): Chapter {
        val chapterStartedPosition = position
        val elementId = readIsoStringNullTerminated(100)
        val startTime = readInt().toLong()
        skipBytes(12) // Ignore end time, start offset, end offset

        val chapter = Chapter()
        chapter.start = startTime
        chapter.chapterId = elementId

        // Read sub-frames
        while (position < chapterStartedPosition + frameHeader.size) {
            val subFrameHeader = readFrameHeader()
            readChapterSubFrame(subFrameHeader, chapter)
        }
        return chapter
    }

    @Throws(IOException::class, ID3ReaderException::class)
    fun readChapterSubFrame(frameHeader: FrameHeader, chapter: Chapter) {
        Log.d(TAG, "Handling subframe: $frameHeader")
        val frameStartPosition = position
        when (frameHeader.id) {
            FRAME_ID_TITLE -> {
                chapter.title = readEncodingAndString(frameHeader.size)
                Log.d(TAG, "Found title: " + chapter.title)
            }
            FRAME_ID_LINK -> {
                readEncodingAndString(frameHeader.size) // skip description
                val url = readIsoStringNullTerminated(frameStartPosition + frameHeader.size - position)
                try {
                    val decodedLink = URLDecoder.decode(url, "ISO-8859-1")
                    chapter.link = decodedLink
                    Log.d(TAG, "Found link: " + chapter.link)
                } catch (iae: IllegalArgumentException) {
                    Log.w(TAG, "Bad URL found in ID3 data")
                }
            }
            FRAME_ID_PICTURE -> {
                val encoding = readByte()
                val mime = readIsoStringNullTerminated(frameHeader.size)
                val type = readByte()
                val description = readEncodedString(encoding.toInt(), frameHeader.size)
                Log.d(TAG, "Found apic: $mime,$description")
                if (MIME_IMAGE_URL == mime) {
                    val link = readIsoStringNullTerminated(frameHeader.size)
                    Log.d(TAG, "Link: $link")
                    if (chapter.imageUrl.isNullOrEmpty() || type.toInt() == IMAGE_TYPE_COVER) {
                        chapter.imageUrl = link
                    }
                } else {
                    val alreadyConsumed = position - frameStartPosition
                    val rawImageDataLength = frameHeader.size - alreadyConsumed
                    if (chapter.imageUrl.isNullOrEmpty() || type.toInt() == IMAGE_TYPE_COVER) {
                        chapter.imageUrl = makeUrl(position, rawImageDataLength)
                    }
                }
            }
            else -> Log.d(TAG, "Unknown chapter sub-frame.")
        }
        // Skip garbage to fill frame completely
        // This also asserts that we are not reading too many bytes from this frame.
        val alreadyConsumed = position - frameStartPosition
        skipBytes(frameHeader.size - alreadyConsumed)
    }

    fun getChapters(): List<Chapter> {
        return chapters
    }

    companion object {
        private const val TAG = "ID3ChapterReader"

        const val FRAME_ID_CHAPTER: String = "CHAP"
        const val FRAME_ID_TITLE: String = "TIT2"
        const val FRAME_ID_LINK: String = "WXXX"
        const val FRAME_ID_PICTURE: String = "APIC"
        const val MIME_IMAGE_URL: String = "-->"
        const val IMAGE_TYPE_COVER: Int = 3
    }
}
