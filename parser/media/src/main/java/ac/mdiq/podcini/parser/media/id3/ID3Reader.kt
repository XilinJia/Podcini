package ac.mdiq.podcini.parser.media.id3

import android.util.Log
import ac.mdiq.podcini.parser.media.id3.model.FrameHeader
import ac.mdiq.podcini.parser.media.id3.model.TagHeader
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.CountingInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.MalformedInputException

/**
 * Reads the ID3 Tag of a given file.
 * See https://id3.org/id3v2.3.0
 */
open class ID3Reader(private val inputStream: CountingInputStream) {
    private var tagHeader: TagHeader? = null

    @Throws(IOException::class, ID3ReaderException::class)
    fun readInputStream() {
        tagHeader = readTagHeader()
        val tagContentStartPosition = position
        while (position < tagContentStartPosition + tagHeader!!.size) {
            val frameHeader = readFrameHeader()
            if (frameHeader.id[0] < '0' || frameHeader.id[0] > 'z') {
                Log.d(TAG, "Stopping because of invalid frame: $frameHeader")
                return
            }
            readFrame(frameHeader)
        }
    }

    @Throws(IOException::class, ID3ReaderException::class)
    protected open fun readFrame(frameHeader: FrameHeader) {
        Log.d(TAG, "Skipping frame: " + frameHeader.id + ", size: " + frameHeader.size)
        skipBytes(frameHeader.size)
    }

    val position: Int
        get() = inputStream.count

    /**
     * Skip a certain number of bytes on the given input stream.
     */
    @Throws(IOException::class, ID3ReaderException::class)
    fun skipBytes(number: Int) {
        if (number < 0) {
            throw ID3ReaderException("Trying to read a negative number of bytes")
        }
        IOUtils.skipFully(inputStream, number.toLong())
    }

    @Throws(IOException::class)
    fun readByte(): Byte {
        return inputStream.read().toByte()
    }

    @Throws(IOException::class)
    fun readShort(): Short {
        val firstByte = inputStream.read().toChar()
        val secondByte = inputStream.read().toChar()
        return ((firstByte.code shl 8) or secondByte.code).toShort()
    }

    @Throws(IOException::class)
    fun readInt(): Int {
        val firstByte = inputStream.read().toChar()
        val secondByte = inputStream.read().toChar()
        val thirdByte = inputStream.read().toChar()
        val fourthByte = inputStream.read().toChar()
        return (firstByte.code shl 24) or (secondByte.code shl 16) or (thirdByte.code shl 8) or fourthByte.code
    }

    @Throws(ID3ReaderException::class, IOException::class)
    fun expectChar(expected: Char) {
        val read = inputStream.read().toChar()
        if (read != expected) {
            throw ID3ReaderException("Expected $expected and got $read")
        }
    }

    @Throws(ID3ReaderException::class, IOException::class)
    fun readTagHeader(): TagHeader {
        expectChar('I')
        expectChar('D')
        expectChar('3')
        val version = readShort()
        val flags = readByte()
        val size = unsynchsafe(readInt())
        if ((flags.toInt() and 64) != 0) {
            val extendedHeaderSize = readInt()
            skipBytes(extendedHeaderSize - 4)
        }
        return TagHeader("ID3", size, version, flags)
    }

    @Throws(IOException::class)
    fun readFrameHeader(): FrameHeader {
        val id = readPlainBytesToString(FRAME_ID_LENGTH)
        var size = readInt()
        if (tagHeader != null && tagHeader!!.version >= 0x0400) {
            size = unsynchsafe(size)
        }
        val flags = readShort()
        return FrameHeader(id, size, flags)
    }

    private fun unsynchsafe(inVal: Int): Int {
        var out = 0
        var mask = 0x7F000000

        while (mask != 0) {
            out = out shr 1
            out = out or (inVal and mask)
            mask = mask shr 8
        }

        return out
    }

    /**
     * Reads a null-terminated string with encoding.
     */
    @Throws(IOException::class)
    fun readEncodingAndString(max: Int): String {
        val encoding = readByte()
        return readEncodedString(encoding.toInt(), max - 1)
    }

    @Throws(IOException::class)
    protected fun readPlainBytesToString(length: Int): String {
        val stringBuilder = StringBuilder()
        var bytesRead = 0
        while (bytesRead < length) {
            stringBuilder.append(Char(readByte().toUShort()))
            bytesRead++
        }
        return stringBuilder.toString()
    }

    @Throws(IOException::class)
    protected fun readIsoStringNullTerminated(max: Int): String {
        return readEncodedString(ENCODING_ISO.toInt(), max)
    }

    @Throws(IOException::class)
    fun readEncodedString(encoding: Int, max: Int): String {
        return when (encoding) {
            ENCODING_UTF16_WITH_BOM.toInt(), ENCODING_UTF16_WITHOUT_BOM.toInt() -> {
                readEncodedString2(Charset.forName("UTF-16"), max)
            }
            ENCODING_UTF8.toInt() -> {
                readEncodedString2(Charset.forName("UTF-8"), max)
            }
            else -> {
                readEncodedString1(Charset.forName("ISO-8859-1"), max)
            }
        }
    }

    /**
     * Reads chars where the encoding uses 1 char per symbol.
     */
    @Throws(IOException::class)
    private fun readEncodedString1(charset: Charset, max: Int): String {
        val bytes = ByteArrayOutputStream()
        var bytesRead = 0
        while (bytesRead < max) {
            val c = readByte()
            bytesRead++
            if (c.toInt() == 0) {
                break
            }
            bytes.write(c.toInt())
        }
        return charset.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
    }

    /**
     * Reads chars where the encoding uses 2 chars per symbol.
     */
    @Throws(IOException::class)
    private fun readEncodedString2(charset: Charset, max: Int): String {
        val bytes = ByteArrayOutputStream()
        var bytesRead = 0
        var foundEnd = false
        while (bytesRead + 1 < max) {
            val c1 = readByte()
            val c2 = readByte()
            if (c1.toInt() == 0 && c2.toInt() == 0) {
                foundEnd = true
                break
            }
            bytesRead += 2
            bytes.write(c1.toInt())
            bytes.write(c2.toInt())
        }
        if (!foundEnd && bytesRead < max) {
            // Last character
            val c = readByte()
            if (c.toInt() != 0) {
                bytes.write(c.toInt())
            }
        }
        return try {
            charset.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (e: MalformedInputException) {
            ""
        }
    }

    companion object {
        private const val TAG = "ID3Reader"
        private const val FRAME_ID_LENGTH = 4
        const val ENCODING_ISO: Byte = 0
        const val ENCODING_UTF16_WITH_BOM: Byte = 1
        const val ENCODING_UTF16_WITHOUT_BOM: Byte = 2
        const val ENCODING_UTF8: Byte = 3
    }
}
