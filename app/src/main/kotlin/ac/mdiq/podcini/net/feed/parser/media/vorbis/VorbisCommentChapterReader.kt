package ac.mdiq.podcini.net.feed.parser.media.vorbis

import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.util.Logd
import java.io.InputStream
import java.util.concurrent.TimeUnit

class VorbisCommentChapterReader(input: InputStream?) : VorbisCommentReader(input!!) {
    private val chapters: MutableList<Chapter> = ArrayList()

    public override fun handles(key: String?): Boolean {
        return key!!.matches(CHAPTER_KEY.toRegex())
    }

    @Throws(VorbisCommentReaderException::class)
    public override fun onContentVectorValue(key: String?, value: String?) {
        Logd(TAG, "Key: $key, value: $value")

        val attribute = getAttributeTypeFromKey(key)
        val id = getIdFromKey(key)
        var chapter = getChapterById(id.toLong())
        when (attribute) {
            null -> {
                if (getChapterById(id.toLong()) == null) {
                    // new chapter
                    val start = getStartTimeFromValue(value)
                    chapter = Chapter()
                    chapter.chapterId = "" + id
                    chapter.start = start
                    chapters.add(chapter)
                } else throw VorbisCommentReaderException("Found chapter with duplicate ID ($key, $value)")
            }
            CHAPTER_ATTRIBUTE_TITLE -> if (chapter != null) chapter.title = value
            CHAPTER_ATTRIBUTE_LINK -> if (chapter != null) chapter.link = value
        }
    }

    private fun getChapterById(id: Long): Chapter? {
        for (c in chapters) {
            if (("" + id) == c.chapterId) return c
        }
        return null
    }

    fun getChapters(): List<Chapter> {
        return chapters
    }

    companion object {
        private val TAG: String = VorbisCommentChapterReader::class.simpleName ?: "Anonymous"

        private const val CHAPTER_KEY = "chapter\\d\\d\\d.*"
        private const val CHAPTER_ATTRIBUTE_TITLE = "name"
        private const val CHAPTER_ATTRIBUTE_LINK = "url"
        private const val CHAPTERXXX_LENGTH = "chapterxxx".length

        @Throws(VorbisCommentReaderException::class)
        fun getStartTimeFromValue(value: String?): Long {
            val parts = value!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size >= 3) {
                try {
                    val hours = TimeUnit.MILLISECONDS.convert(
                        parts[0].toLong(), TimeUnit.HOURS)
                    val minutes = TimeUnit.MILLISECONDS.convert(
                        parts[1].toLong(), TimeUnit.MINUTES)
                    if (parts[2].contains("-->")) parts[2] = parts[2].substring(0, parts[2].indexOf("-->"))
                    val seconds = TimeUnit.MILLISECONDS.convert((parts[2].toFloat().toLong()), TimeUnit.SECONDS)
                    return hours + minutes + seconds
                } catch (e: NumberFormatException) {
                    throw VorbisCommentReaderException(e)
                }
            } else {
                throw VorbisCommentReaderException("Invalid time string")
            }
        }

        /**
         * Return the id of a vorbiscomment chapter from a string like CHAPTERxxx*
         *
         * @return the id of the chapter key or -1 if the id couldn't be read.
         * @throws VorbisCommentReaderException
         */
        @Throws(VorbisCommentReaderException::class)
        private fun getIdFromKey(key: String?): Int {
            if (key!!.length >= CHAPTERXXX_LENGTH) { // >= CHAPTERxxx
                try {
                    val strId = key.substring(8, 10)
                    return strId.toInt()
                } catch (e: NumberFormatException) {
                    throw VorbisCommentReaderException(e)
                }
            }
            throw VorbisCommentReaderException("key is too short ($key)")
        }

        /**
         * Get the string that comes after 'CHAPTERxxx', for example 'name' or
         * 'url'.
         */
        private fun getAttributeTypeFromKey(key: String?): String? {
            if (key!!.length > CHAPTERXXX_LENGTH) return key.substring(CHAPTERXXX_LENGTH)

            return null
        }
    }
}
