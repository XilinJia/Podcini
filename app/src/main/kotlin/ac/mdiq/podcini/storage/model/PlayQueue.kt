package ac.mdiq.podcini.storage.model

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.*

class PlayQueue : RealmObject {

    @PrimaryKey
    var id: Long = 0L

    var name: String = ""

    var updated: Long = Date().time
        private set

    var episodeIds: RealmList<Long> = realmListOf()

    @Ignore
    val episodes: MutableList<Episode> = mutableListOf()

    fun isInQueue(episode: Episode): Boolean {
        return episodeIds.contains(episode.id)
    }

    fun update() {
        updated = Date().time
    }

    constructor() {}
}