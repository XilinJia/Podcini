package de.danoeh.antennapod.parser.media.id3

import de.danoeh.antennapod.parser.media.id3.model.FrameHeader
import org.apache.commons.io.input.CountingInputStream
import java.io.IOException

/**
 * Reads general ID3 metadata like comment, which Android's MediaMetadataReceiver does not support.
 */
class Id3MetadataReader(input: CountingInputStream?) : ID3Reader(input!!) {
    var comment: String? = null
        private set

    @Throws(IOException::class, ID3ReaderException::class)
    override fun readFrame(frameHeader: FrameHeader) {
        if (FRAME_ID_COMMENT == frameHeader.id) {
            val frameStart = position.toLong()
            val encoding = readByte().toInt()
            skipBytes(3) // Language
            val shortDescription = readEncodedString(encoding, frameHeader.size - 4)
            val longDescription = readEncodedString(encoding,
                (frameHeader.size - (position - frameStart)).toInt())
            comment = if (shortDescription.length > longDescription.length) shortDescription else longDescription
        } else {
            super.readFrame(frameHeader)
        }
    }

    companion object {
        const val FRAME_ID_COMMENT: String = "COMM"
    }
}
