package ac.mdiq.podcini.model.feed

enum class FeedCounter(val id: Int) {
    SHOW_NEW(1),
    SHOW_UNPLAYED(2),
    SHOW_NONE(3),
    SHOW_DOWNLOADED(4),
    SHOW_DOWNLOADED_UNPLAYED(5);

    companion object {
        fun fromOrdinal(id: Int): FeedCounter {
            for (counter in entries) {
                if (counter.id == id) {
                    return counter
                }
            }
            return SHOW_NONE
        }
    }
}
