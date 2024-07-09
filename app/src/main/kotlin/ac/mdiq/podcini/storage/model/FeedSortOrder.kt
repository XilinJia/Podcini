package ac.mdiq.podcini.storage.model

enum class FeedSortOrder(val code: Int, val index: Int) {
    UNPLAYED_NEW_OLD(1, 0),
    UNPLAYED_OLD_NEW(2, 0),
    ALPHABETIC_A_Z(3, 1),
    ALPHABETIC_Z_A(4,1 ),
    LAST_UPDATED_NEW_OLD(5, 2),
    LAST_UPDATED_OLD_NEW(6, 2),
    LAST_UPDATED_UNPLAYED_NEW_OLD(7, 3),
    LAST_UPDATED_UNPLAYED_OLD_NEW(8, 3),
    MOST_PLAYED(9, 4),
    LEAST_PLAYED(10, 4),
    MOST_DOWNLOADED(11, 5),
    LEAST_DOWNLAODED(12, 5),
    MOST_DOWNLOADED_UNPLAYED(13, 6),
    LEAST_DOWNLAODED_UNPLAYED(14, 6),
    NEW_EPISODES_MOST(15, 7),
    NEW_EPISODES_LEAST(16, 7);

    companion object {
        fun getSortOrder(dir: Int, index: Int): FeedSortOrder? {
            return enumValues<FeedSortOrder>().firstOrNull { it.code == 2*index+dir+1 && it.index == index }
        }
    }
}
