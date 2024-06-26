package ac.mdiq.podcini.feed.parser.media.vorbis

import ac.mdiq.podcini.net.feed.parser.media.vorbis.VorbisCommentReaderException
import ac.mdiq.podcini.net.feed.parser.media.vorbis.VorbisCommentMetadataReader
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class VorbisCommentMetadataReaderTest {
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
        val reader = VorbisCommentMetadataReader(inputStream)
        reader.readInputStream()
        Assert.assertEquals("Summary", reader.description)
    }
}
