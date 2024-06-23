package ac.mdiq.podcini.feed.parser.media.id3

import ac.mdiq.podcini.net.feed.parser.media.id3.ChapterReader
import ac.mdiq.podcini.net.feed.parser.media.id3.ID3Reader
import ac.mdiq.podcini.net.feed.parser.media.id3.ID3ReaderException
import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.storage.utils.EmbeddedChapterImage.Companion.makeUrl
import ac.mdiq.podcini.net.feed.parser.media.id3.model.FrameHeader
import org.apache.commons.io.input.CountingInputStream
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ChapterRReaderTest {
    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testReadFullTagWithChapter() {
        val chapter = Id3ReaderTest.concat(
            Id3ReaderTest.generateFrameHeader(ChapterReader.FRAME_ID_CHAPTER, CHAPTER_WITHOUT_SUBFRAME.size),
            CHAPTER_WITHOUT_SUBFRAME)
        val data = Id3ReaderTest.concat(
            Id3ReaderTest.generateId3Header(chapter.size),
            chapter)
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        Assert.assertEquals(1, reader.getChapters().size.toLong())
        Assert.assertEquals(CHAPTER_WITHOUT_SUBFRAME_START_TIME.toLong(), reader.getChapters()[0].start)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testReadFullTagWithMultipleChapters() {
        val chapter = Id3ReaderTest.concat(
            Id3ReaderTest.generateFrameHeader(ChapterReader.FRAME_ID_CHAPTER, CHAPTER_WITHOUT_SUBFRAME.size),
            CHAPTER_WITHOUT_SUBFRAME)
        val data = Id3ReaderTest.concat(
            Id3ReaderTest.generateId3Header(2 * chapter.size),
            chapter,
            chapter)
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        Assert.assertEquals(2, reader.getChapters().size.toLong())
        Assert.assertEquals(CHAPTER_WITHOUT_SUBFRAME_START_TIME.toLong(), reader.getChapters()[0].start)
        Assert.assertEquals(CHAPTER_WITHOUT_SUBFRAME_START_TIME.toLong(), reader.getChapters()[1].start)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testReadChapterWithoutSubframes() {
        val header = FrameHeader(ChapterReader.FRAME_ID_CHAPTER,
            CHAPTER_WITHOUT_SUBFRAME.size, 0.toShort())
        val inputStream = CountingInputStream(ByteArrayInputStream(CHAPTER_WITHOUT_SUBFRAME))
        val chapter = ChapterReader(inputStream).readChapter(header)
        Assert.assertEquals(CHAPTER_WITHOUT_SUBFRAME_START_TIME.toLong(), chapter.start)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testReadChapterWithTitle() {
        val title = byteArrayOf(ID3Reader.ENCODING_ISO,
            'H'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte(),  // Title
            0 // Null-terminated
        )
        val chapterData = Id3ReaderTest.concat(
            CHAPTER_WITHOUT_SUBFRAME,
            Id3ReaderTest.generateFrameHeader(ChapterReader.FRAME_ID_TITLE, title.size),
            title)
        val header = FrameHeader(ChapterReader.FRAME_ID_CHAPTER, chapterData.size, 0.toShort())
        val inputStream = CountingInputStream(ByteArrayInputStream(chapterData))
        val reader = ChapterReader(inputStream)
        val chapter = reader.readChapter(header)
        Assert.assertEquals(CHAPTER_WITHOUT_SUBFRAME_START_TIME.toLong(), chapter.start)
        Assert.assertEquals("Hello", chapter.title)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testReadTitleWithGarbage() {
        val titleSubframeContent = byteArrayOf(ID3Reader.ENCODING_ISO,
            'A'.code.toByte(),  // Title
            0,  // Null-terminated
            42, 42, 42, 42 // Garbage, should be ignored
        )
        val header = FrameHeader(ChapterReader.FRAME_ID_TITLE, titleSubframeContent.size, 0.toShort())
        val inputStream = CountingInputStream(ByteArrayInputStream(titleSubframeContent))
        val reader = ChapterReader(inputStream)
        val chapter = Chapter()
        reader.readChapterSubFrame(header, chapter)
        Assert.assertEquals("A", chapter.title)

        // Should skip the garbage and point to the next frame
        Assert.assertEquals(titleSubframeContent.size.toLong(), reader.position.toLong())
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testRealFileUltraschall() {
        val inputStream = CountingInputStream(javaClass.classLoader?.getResource("ultraschall5.mp3")?.openStream())
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        val chapters = reader.getChapters()

        Assert.assertEquals(3, chapters.size.toLong())

        Assert.assertEquals(0, chapters[0].start)
        Assert.assertEquals(4004, chapters[1].start)
        Assert.assertEquals(7999, chapters[2].start)

        Assert.assertEquals("Marke 1", chapters[0].title)
        Assert.assertEquals("Marke 2", chapters[1].title)
        Assert.assertEquals("Marke 3", chapters[2].title)

        Assert.assertEquals("https://example.com", chapters[0].link)
        Assert.assertEquals("https://example.com", chapters[1].link)
        Assert.assertEquals("https://example.com", chapters[2].link)

        Assert.assertEquals(makeUrl(16073, 2750569), chapters[0].imageUrl)
        Assert.assertEquals(makeUrl(2766765, 15740), chapters[1].imageUrl)
        Assert.assertEquals(makeUrl(2782628, 2750569), chapters[2].imageUrl)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testRealFileAuphonic() {
        val inputStream = CountingInputStream(javaClass.classLoader?.getResource("auphonic.mp3")?.openStream())
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        val chapters = reader.getChapters()

        Assert.assertEquals(4, chapters.size.toLong())

        Assert.assertEquals(0, chapters[0].start)
        Assert.assertEquals(3000, chapters[1].start)
        Assert.assertEquals(6000, chapters[2].start)
        Assert.assertEquals(9000, chapters[3].start)

        Assert.assertEquals("Chapter 1 - ❤️😊", chapters[0].title)
        Assert.assertEquals("Chapter 2 - ßöÄ", chapters[1].title)
        Assert.assertEquals("Chapter 3 - 爱", chapters[2].title)
        Assert.assertEquals("Chapter 4", chapters[3].title)

        Assert.assertEquals("https://example.com", chapters[0].link)
        Assert.assertEquals("https://example.com", chapters[1].link)
        Assert.assertEquals("https://example.com", chapters[2].link)
        Assert.assertEquals("https://example.com", chapters[3].link)

        Assert.assertEquals(makeUrl(765, 308), chapters[0].imageUrl)
        Assert.assertEquals(makeUrl(1271, 308), chapters[1].imageUrl)
        Assert.assertEquals(makeUrl(1771, 308), chapters[2].imageUrl)
        Assert.assertEquals(makeUrl(2259, 308), chapters[3].imageUrl)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testRealFileHindenburgJournalistPro() {
        val inputStream = CountingInputStream(javaClass.classLoader?.getResource("hindenburg-journalist-pro.mp3")?.openStream())
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        val chapters = reader.getChapters()

        Assert.assertEquals(2, chapters.size.toLong())

        Assert.assertEquals(0, chapters[0].start)
        Assert.assertEquals(5006, chapters[1].start)

        Assert.assertEquals("Chapter Marker 1", chapters[0].title)
        Assert.assertEquals("Chapter Marker 2", chapters[1].title)

        Assert.assertEquals("https://example.com/chapter1url", chapters[0].link)
        Assert.assertEquals("https://example.com/chapter2url", chapters[1].link)

        Assert.assertEquals(makeUrl(5330, 4015), chapters[0].imageUrl)
        Assert.assertEquals(makeUrl(9498, 4364), chapters[1].imageUrl)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testRealFileMp3chapsPy() {
        val inputStream = CountingInputStream(javaClass.classLoader?.getResource("mp3chaps-py.mp3")?.openStream())
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        val chapters = reader.getChapters()

        Assert.assertEquals(4, chapters.size.toLong())

        Assert.assertEquals(0, chapters[0].start)
        Assert.assertEquals(7000, chapters[1].start)
        Assert.assertEquals(9000, chapters[2].start)
        Assert.assertEquals(11000, chapters[3].start)

        Assert.assertEquals("Start", chapters[0].title)
        Assert.assertEquals("Chapter 1", chapters[1].title)
        Assert.assertEquals("Chapter 2", chapters[2].title)
        Assert.assertEquals("Chapter 3", chapters[3].title)
    }

    companion object {
        private const val CHAPTER_WITHOUT_SUBFRAME_START_TIME: Byte = 23
        private val CHAPTER_WITHOUT_SUBFRAME =
            byteArrayOf('C'.code.toByte(), 'H'.code.toByte(), '1'.code.toByte(), 0,  // String ID for mapping to CTOC
                0, 0, 0, CHAPTER_WITHOUT_SUBFRAME_START_TIME,  // Start time
                0, 0, 0, 0,  // End time
                0, 0, 0, 0,  // Start offset
                0, 0, 0, 0 // End offset
            )
    }
}
