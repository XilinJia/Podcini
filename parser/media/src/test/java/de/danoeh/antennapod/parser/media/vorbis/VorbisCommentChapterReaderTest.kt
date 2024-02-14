package de.danoeh.antennapod.parser.media.vorbis

import de.danoeh.antennapod.parser.media.vorbis.VorbisCommentReaderException
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class VorbisCommentChapterReaderTest {
    @Test
    @Throws(IOException::class, VorbisCommentReaderException::class)
    fun testRealFilesAuphonic() {
        testRealFileAuphonic("auphonic.ogg")
        testRealFileAuphonic("auphonic.opus")
    }

    @Throws(IOException::class, VorbisCommentReaderException::class)
    fun testRealFileAuphonic(filename: String?) {
        val inputStream = javaClass.classLoader
            .getResource(filename).openStream()
        val reader = VorbisCommentChapterReader(inputStream)
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
    }
}
