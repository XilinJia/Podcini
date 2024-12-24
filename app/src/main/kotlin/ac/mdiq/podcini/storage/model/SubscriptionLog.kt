package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.util.Logd
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class SubscriptionLog: RealmObject {
    @PrimaryKey
    var id: Long = 0L   // this is the Date().time

    // this can be that of a feed or a synthetic episode
//    var itemId: Long = 0L

    var url: String? = null

    var link: String? = null

    var title: String = ""

    var type: String? = null

    var cancelDate: Long = 0

    var rating: Int = Rating.UNRATED.code

    var comment: String = ""

    constructor() {}

    constructor(itemId: Long, title: String, url: String, link: String, type: String) {
//        this.itemId = itemId
        this.title = title
        this.url = url
        this.link = link
        this.type = type

        // itemId being either feed.id or episode.id is 100 times the creation time
        id = itemId / 100
    }

    enum class Type {
        Feed,
        Media,
    }

    companion object {
        val TAG: String = SubscriptionLog::class.simpleName ?: "Anonymous"

        var feedLogsMap: Map<String, SubscriptionLog>? = null
            get() {
                if (field == null) field = getFeedLogMap()
                return field
            }

        fun getFeedLogMap(): Map<String, SubscriptionLog> {
            val logs = realm.query(SubscriptionLog::class).query("type == $0", "Feed").find()
            val map = mutableMapOf<String, SubscriptionLog>()
            for (l in logs) {
                Logd(TAG, "getFeedLogMap ${l.title} ${l.url}")
                if (!l.url.isNullOrEmpty()) map[l.url!!] = l
                if (!l.link.isNullOrEmpty()) map[l.link!!] = l
            }
            return map.toMap()
        }
    }
}