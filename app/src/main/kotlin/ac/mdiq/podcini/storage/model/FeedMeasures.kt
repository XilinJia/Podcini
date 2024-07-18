package ac.mdiq.podcini.storage.model

import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.annotations.Index

class FeedMeasures : EmbeddedRealmObject {
    @Index
    var feedID: Long = 0L

    var playedCount: Int = 0

    var unplayedCount: Int = 0

    var newCount: Int = 0

    var downloadCount: Int = 0

    constructor() {}
}