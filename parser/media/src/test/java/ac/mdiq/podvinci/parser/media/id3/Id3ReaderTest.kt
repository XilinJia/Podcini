package ac.mdiq.podvinci.parser.media.id3

import ac.mdiq.podvinci.parser.media.id3.ID3ReaderException
import org.apache.commons.io.input.CountingInputStream
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
class Id3ReaderTest {
    @Test
    @Throws(IOException::class)
    fun testReadString() {
        val data = byteArrayOf(ID3Reader.ENCODING_ISO,
            'T'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(),
            0 // Null-terminated
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val string = ID3Reader(inputStream).readEncodingAndString(1000)
        Assert.assertEquals("Test", string)
    }

    @Test
    @Throws(IOException::class)
    fun testReadMultipleStrings() {
        val data = byteArrayOf(ID3Reader.ENCODING_ISO,
            'F'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(),
            0,  // Null-terminated
            ID3Reader.ENCODING_ISO,
            'B'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(),
            0 // Null-terminated
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ID3Reader(inputStream)
        Assert.assertEquals("Foo", reader.readEncodingAndString(1000))
        Assert.assertEquals("Bar", reader.readEncodingAndString(1000))
    }

    @Test
    @Throws(IOException::class)
    fun testReadingLimit() {
        val data = byteArrayOf(ID3Reader.ENCODING_ISO,
            'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte(), 'D'.code.toByte()
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ID3Reader(inputStream)
        Assert.assertEquals("ABC", reader.readEncodingAndString(4)) // Includes encoding
        Assert.assertEquals('D'.code.toLong(), reader.readByte().toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testReadUtf16RespectsBom() {
        val data = byteArrayOf(
            ID3Reader.ENCODING_UTF16_WITH_BOM,
            0xff.toByte(), 0xfe.toByte(),  // BOM: Little-endian
            'A'.code.toByte(), 0, 'B'.code.toByte(), 0, 'C'.code.toByte(), 0,
            0, 0,  // Null-terminated
            ID3Reader.ENCODING_UTF16_WITH_BOM,
            0xfe.toByte(), 0xff.toByte(),  // BOM: Big-endian
            0, 'D'.code.toByte(), 0, 'E'.code.toByte(), 0, 'F'.code.toByte(),
            0, 0,  // Null-terminated
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ID3Reader(inputStream)
        Assert.assertEquals("ABC", reader.readEncodingAndString(1000))
        Assert.assertEquals("DEF", reader.readEncodingAndString(1000))
    }

    @Test
    @Throws(IOException::class)
    fun testReadUtf16NullPrefix() {
        val data = byteArrayOf(
            ID3Reader.ENCODING_UTF16_WITH_BOM,
            0xff.toByte(), 0xfe.toByte(),  // BOM
            0x00, 0x01,  // Latin Capital Letter A with macron (Ā)
            0, 0,  // Null-terminated
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val string = ID3Reader(inputStream).readEncodingAndString(1000)
        Assert.assertEquals("Ā", string)
    }

    @Test
    @Throws(IOException::class)
    fun testReadingLimitUtf16() {
        val data = byteArrayOf(ID3Reader.ENCODING_UTF16_WITHOUT_BOM,
            'A'.code.toByte(), 0, 'B'.code.toByte(), 0, 'C'.code.toByte(), 0, 'D'.code.toByte(), 0
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ID3Reader(inputStream)
        reader.readEncodingAndString(6) // Includes encoding, produces broken string
        Assert.assertTrue("Should respect limit even if it breaks a symbol", reader.position <= 6)
    }

    @Test
    @Throws(IOException::class, ID3ReaderException::class)
    fun testReadTagHeader() {
        val data = generateId3Header(23)
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val header = ID3Reader(inputStream).readTagHeader()
        Assert.assertEquals("ID3", header.id)
        Assert.assertEquals(42, header.version.toLong())
        Assert.assertEquals(23, header.size.toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testReadFrameHeader() {
        val data = generateFrameHeader("CHAP", 42)
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val header = ID3Reader(inputStream).readFrameHeader()
        Assert.assertEquals("CHAP", header.id)
        Assert.assertEquals(42, header.size.toLong())
    }

    companion object {
        fun generateFrameHeader(id: String, size: Int): ByteArray {
            return concat(
                id.toByteArray(StandardCharsets.ISO_8859_1),  // Frame ID
                byteArrayOf((size shr 24).toByte(), (size shr 16).toByte(),
                    (size shr 8).toByte(), size.toByte(),  // Size
                    0, 0 // Flags
                ))
        }

        fun generateId3Header(size: Int): ByteArray {
            return byteArrayOf(
                'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),  // Identifier
                0, 42,  // Version
                0,  // Flags
                (size shr 24).toByte(), (size shr 16).toByte(),
                (size shr 8).toByte(), size.toByte(),  // Size
            )
        }

        fun concat(vararg arrays: ByteArray?): ByteArray {
            val outputStream = ByteArrayOutputStream()
            try {
                for (array in arrays) {
                    outputStream.write(array)
                }
            } catch (e: IOException) {
                Assert.fail(e.message)
            }
            return outputStream.toByteArray()
        }
    }
}
