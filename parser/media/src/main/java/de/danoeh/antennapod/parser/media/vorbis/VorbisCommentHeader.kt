package de.danoeh.antennapod.parser.media.vorbis

internal class VorbisCommentHeader(val vendorString: String, val userCommentLength: Long) {
    override fun toString(): String {
        return ("VorbisCommentHeader [vendorString=" + vendorString
                + ", userCommentLength=" + userCommentLength + "]")
    }
}
