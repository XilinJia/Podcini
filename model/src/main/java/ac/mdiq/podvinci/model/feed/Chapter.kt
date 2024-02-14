package ac.mdiq.podvinci.model.feed

class Chapter : FeedComponent {
    /** Defines starting point in milliseconds.  */
    @JvmField
    var start: Long = 0
    @JvmField
    var title: String? = null
    @JvmField
    var link: String? = null
    @JvmField
    var imageUrl: String? = null

    /**
     * ID from the chapter source, not the database ID.
     */
    @JvmField
    var chapterId: String? = null

    constructor()

    constructor(start: Long, title: String?, link: String?, imageUrl: String?) {
        this.start = start
        this.title = title
        this.link = link
        this.imageUrl = imageUrl
    }

    override fun getHumanReadableIdentifier(): String? {
        return title
    }

    override fun toString(): String {
        return "ID3Chapter [title=" + title + ", start=" + start + ", url=" + link + "]"
    }
}
