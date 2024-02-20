package ac.mdiq.podcini.parser.media.id3

import ac.mdiq.podcini.parser.media.id3.ID3ReaderException
import org.apache.commons.io.input.CountingInputStream
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class MetadataReaderTest {
    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testRealFileUltraschall() {
        val inputStream = CountingInputStream(javaClass.classLoader
            .getResource("ultraschall5.mp3").openStream())
        val reader = Id3MetadataReader(inputStream)
        reader.readInputStream()
        Assert.assertEquals("Description", reader.comment)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testRealFileAuphonic() {
        val inputStream = CountingInputStream(javaClass.classLoader
            .getResource("auphonic.mp3").openStream())
        val reader = Id3MetadataReader(inputStream)
        reader.readInputStream()
        Assert.assertEquals("Summary", reader.comment)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testRealFileHindenburgJournalistPro() {
        val inputStream = CountingInputStream(javaClass.classLoader
            .getResource("hindenburg-journalist-pro.mp3").openStream())
        val reader = Id3MetadataReader(inputStream)
        reader.readInputStream()
        Assert.assertEquals("This is the summary of this podcast episode. This file was made with"
                + " Hindenburg Journalist Pro version 1.85, build number 2360.", reader.comment)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testRealFileMp3chapsPy() {
        val inputStream = CountingInputStream(javaClass.classLoader
            .getResource("mp3chaps-py.mp3").openStream())
        val reader = Id3MetadataReader(inputStream)
        reader.readInputStream()
        Assert.assertEquals("2021.08.13", reader.comment)
    }
}
