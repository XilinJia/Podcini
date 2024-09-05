package ac.mdiq.podcini.net.feed.parser.media.id3.model

class TagHeader(
        id: String,
        size: Int,
        @JvmField val version: Short,
        private val flags: Byte) : Header(id, size) {

    override fun toString(): String {
        return ("TagHeader [version=" + version + ", flags=" + flags + ", id=" + id + ", size=" + size + "]")
    }
}
