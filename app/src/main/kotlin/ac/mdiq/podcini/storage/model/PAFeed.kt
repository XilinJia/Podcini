package ac.mdiq.podcini.storage.model

import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.PrimaryKey

class PAFeed : RealmObject {
    @PrimaryKey
    var id: Int = 0

    var name : String = ""

    var teamName : String = ""

//    var teamhomepage: String? = null
//
//    var teamImageUrl: String? = null

    var category: RealmSet<String> = realmSetOf()

    var type: String = ""   // Audio or Video

    var homepage: String? = null

    var lastPubDate: Long = -1

    var feedUrl: String = ""

    var imageUrl: String? = null

    var rating: Float = 0f

    var language: String = ""

    var author: String = ""

    var description: String = ""

//    var customName: String = ""

//    var iTunesId: String? = null

    var subscribers: Int = -1

    var aveDuration: Int = -1   // appears in minutes

    var frequency: Int = -1

    var episodesNb: Int = -1

    var reviews: Int = -1

    var hubUrl: String? = null

    var topicUrl: String? = null

    var related: RealmSet<Int> = realmSetOf()

    constructor() {}
}