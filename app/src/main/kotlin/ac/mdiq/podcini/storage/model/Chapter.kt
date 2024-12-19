package ac.mdiq.podcini.storage.model

import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.annotations.Index

class Chapter : EmbeddedRealmObject {
    @Index
    var id: Long = 0

    /** Defines starting point in milliseconds.  */
    var start: Long = 0
    var title: String? = null
    var link: String? = null
    var imageUrl: String? = null

    /**
     * ID from the chapter source, not the database ID.
     */
    var chapterId: String? = null

    var episode: Episode? = null

    constructor() {
//        id = newId()
    }

    constructor(start: Long, title: String?, link: String?, imageUrl: String?) {
//        id = newId()
        this.start = start
        this.title = title
        this.link = link
        this.imageUrl = imageUrl
    }

    override fun toString(): String {
        return "ID3Chapter [title=$title, start=$start, url=$link]"
    }
}
