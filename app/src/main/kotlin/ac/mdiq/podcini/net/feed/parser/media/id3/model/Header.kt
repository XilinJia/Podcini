package ac.mdiq.podcini.net.feed.parser.media.id3.model

abstract class Header internal constructor(
        @JvmField val id: String,
        @JvmField val size: Int) {
    
    override fun toString(): String {
        return "Header [id=$id, size=$size]"
    }
}
