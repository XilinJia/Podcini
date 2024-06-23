package ac.test.podcini.service.download

/**
 * Represents every possible component of a feed
 *
 * @author daniel
 */
// only used in test
abstract class FeedComponent internal constructor() {
    open var id: Long = 0

    /**
     * Update this FeedComponent's attributes with the attributes from another
     * FeedComponent. This method should only update attributes which where read from
     * the feed.
     */
    fun updateFromOther(other: FeedComponent?) {}

    /**
     * Compare's this FeedComponent's attribute values with another FeedComponent's
     * attribute values. This method will only compare attributes which were
     * read from the feed.
     *
     * @return true if attribute values are different, false otherwise
     */
    fun compareWithOther(other: FeedComponent?): Boolean {
        return false
    }

    /**
     * Should return a non-null, human-readable String so that the item can be
     * identified by the user. Can be title, download-url, etc.
     */
    abstract fun getHumanReadableIdentifier(): String?

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is FeedComponent) return false

        return id == o.id
    }

    override fun hashCode(): Int {
        return (id xor (id ushr 32)).toInt()
    }
}