package ac.mdiq.podcini.net.feed.parser.media.vorbis

import ac.mdiq.podcini.util.Logd
import org.apache.commons.io.EndianUtils
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.charset.Charset

abstract class VorbisCommentReader internal constructor(private val input: InputStream) {
    @Throws(VorbisCommentReaderException::class)
    fun readInputStream() {
        try {
            findIdentificationHeader()
            findOggPage()
            findCommentHeader()
            val commentHeader = readCommentHeader()
            Logd(TAG, commentHeader.toString())
            for (i in 0 until commentHeader.userCommentLength) {
                readUserComment()
            }
        } catch (e: IOException) {
            Logd(TAG, "Vorbis parser: " + e.message)
        }
    }

    @Throws(IOException::class)
    private fun findOggPage() {
        // find OggS
        val buffer = ByteArray(4)
        val oggPageHeader = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
        for (bytesRead in 0 until SECOND_PAGE_MAX_LENGTH) {
            val data = input.read()
            if (data == -1) throw IOException("EOF while trying to find vorbis page")

            buffer[bytesRead % buffer.size] = data.toByte()
            if (bufferMatches(buffer, oggPageHeader, bytesRead)) break
        }
        // read segments
        IOUtils.skipFully(input, 22)
        val numSegments = input.read()
        IOUtils.skipFully(input, numSegments.toLong())
    }

    @Throws(VorbisCommentReaderException::class)
    private fun readUserComment() {
        try {
            val vectorLength = EndianUtils.readSwappedUnsignedInteger(input)
            if (vectorLength > 20 * 1024 * 1024) {
                val keyPart = readUtf8String(10)
                throw VorbisCommentReaderException("User comment unrealistically long. key=$keyPart, length=$vectorLength")
            }
            val key = readContentVectorKey(vectorLength)!!.lowercase()
            val shouldReadValue = handles(key)
            Logd(TAG, "key=$key, length=$vectorLength, handles=$shouldReadValue")
            if (shouldReadValue) {
                val value = readUtf8String(vectorLength - key.length - 1)
                onContentVectorValue(key, value)
            } else IOUtils.skipFully(input, vectorLength - key.length - 1)

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun readUtf8String(length: Long): String {
        val buffer = ByteArray(length.toInt())
        IOUtils.readFully(input, buffer)
        val charset = Charset.forName("UTF-8")
        return charset.newDecoder().decode(ByteBuffer.wrap(buffer)).toString()
    }

    /**
     * Looks for an identification header in the first page of the file. If an
     * identification header is found, it will be skipped completely
     */
    @Throws(IOException::class)
    private fun findIdentificationHeader() {
        val buffer = ByteArray(FIRST_OPUS_PAGE_LENGTH)
        IOUtils.readFully(input, buffer)
        val oggIdentificationHeader = byteArrayOf(PACKET_TYPE_IDENTIFICATION.toByte(),
            'v'.code.toByte(),
            'o'.code.toByte(),
            'r'.code.toByte(),
            'b'.code.toByte(),
            'i'.code.toByte(),
            's'.code.toByte())
        for (i in 6 until buffer.size) {
            when {
                bufferMatches(buffer, oggIdentificationHeader, i) -> {
                    IOUtils.skip(input, (FIRST_OGG_PAGE_LENGTH - FIRST_OPUS_PAGE_LENGTH).toLong())
                    return
                }
                bufferMatches(buffer, "OpusHead".toByteArray(), i) -> return
            }
        }
        throw IOException("No vorbis identification header found")
    }

    @Throws(IOException::class)
    private fun findCommentHeader() {
        val buffer = ByteArray(64) // Enough space for some bytes. Used circularly.
        val oggCommentHeader = byteArrayOf(PACKET_TYPE_COMMENT.toByte(),
            'v'.code.toByte(),
            'o'.code.toByte(),
            'r'.code.toByte(),
            'b'.code.toByte(),
            'i'.code.toByte(),
            's'.code.toByte())
        for (bytesRead in 0 until SECOND_PAGE_MAX_LENGTH) {
            buffer[bytesRead % buffer.size] = input.read().toByte()
            when {
                bufferMatches(buffer, oggCommentHeader, bytesRead) -> return
                bufferMatches(buffer, "OpusTags".toByteArray(), bytesRead) -> return
            }
        }
        throw IOException("No comment header found")
    }

    /**
     * Reads backwards in haystack, starting at position. Checks if the bytes match needle.
     * Uses haystack circularly, so when reading at (-1), it reads at (length - 1).
     */
    fun bufferMatches(haystack: ByteArray, needle: ByteArray, position: Int): Boolean {
        for (i in needle.indices) {
            var posInHaystack = position - i
            while (posInHaystack < 0) {
                posInHaystack += haystack.size
            }
            posInHaystack %= haystack.size
            if (haystack[posInHaystack] != needle[needle.size - 1 - i]) return false
        }
        return true
    }

    @Throws(IOException::class, VorbisCommentReaderException::class)
    private fun readCommentHeader(): VorbisCommentHeader {
        try {
            val vendorLength = EndianUtils.readSwappedUnsignedInteger(input)
            val vendorName = readUtf8String(vendorLength)
            val userCommentLength = EndianUtils.readSwappedUnsignedInteger(input)
            return VorbisCommentHeader(vendorName, userCommentLength)
        } catch (e: UnsupportedEncodingException) {
            throw VorbisCommentReaderException(e)
        }
    }

    @Throws(IOException::class)
    private fun readContentVectorKey(vectorLength: Long): String? {
        val builder = StringBuilder()
        for (i in 0 until vectorLength) {
            val c = input.read().toChar()
            if (c == '=') return builder.toString()
            else builder.append(c)
        }
        return null // no key found
    }

    /**
     * Is called every time the Reader finds a content vector. The handler
     * should return true if it wants to handle the content vector.
     */
    protected abstract fun handles(key: String?): Boolean

    /**
     * Is called if onContentVectorKey returned true for the key.
     */
    @Throws(VorbisCommentReaderException::class)
    protected abstract fun onContentVectorValue(key: String?, value: String?)

    internal class VorbisCommentHeader(
            val vendorString: String,
            val userCommentLength: Long) {

        override fun toString(): String {
            return ("VorbisCommentHeader [vendorString=" + vendorString + ", userCommentLength=" + userCommentLength + "]")
        }
    }

    companion object {
        private val TAG: String = VorbisCommentReader::class.simpleName ?: "Anonymous"
        private const val FIRST_OGG_PAGE_LENGTH = 58
        private const val FIRST_OPUS_PAGE_LENGTH = 47
        private const val SECOND_PAGE_MAX_LENGTH = 64 * 1024 * 1024
        private const val PACKET_TYPE_IDENTIFICATION = 1
        private const val PACKET_TYPE_COMMENT = 3
    }
}
