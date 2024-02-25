package ac.mdiq.podcini.feed.parser.media.vorbis

internal class VorbisCommentHeader(val vendorString: String, val userCommentLength: Long) {
    override fun toString(): String {
        return ("VorbisCommentHeader [vendorString=" + vendorString + ", userCommentLength=" + userCommentLength + "]")
    }
}
