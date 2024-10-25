package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.FeedPreferences.Companion.SPEED_USE_GLOBAL
import ac.mdiq.podcini.util.Logd
import java.io.Serializable

class FeedFilter(vararg properties_: String) : Serializable {
    val properties: HashSet<String> = setOf(*properties_).filter { it.isNotEmpty() }.map {it.trim()}.toHashSet()

    constructor(properties: String) : this(*(properties.split(",").toTypedArray()))

    fun queryString(): String {
        val statements: MutableList<String> = mutableListOf()
        when {
            properties.contains(States.keepUpdated.name) -> statements.add("preferences.keepUpdated == true ")
            properties.contains(States.not_keepUpdated.name) -> statements.add(" preferences.keepUpdated == false ")
        }
        when {
            properties.contains(States.pref_streaming.name) -> statements.add("preferences.prefStreamOverDownload == true ")
            properties.contains(States.not_pref_streaming.name) -> statements.add(" preferences.prefStreamOverDownload == false ")
        }

        when {
            properties.contains(States.global_playSpeed.name) -> statements.add(" preferences.playSpeed == $SPEED_USE_GLOBAL ")
            properties.contains(States.custom_playSpeed.name) -> statements.add(" preferences.playSpeed != $SPEED_USE_GLOBAL ")
        }
        when {
            properties.contains(States.has_skips.name) -> statements.add(" preferences.introSkip != 0 OR preferences.endingSkip != 0 ")
            properties.contains(States.no_skips.name) -> statements.add(" preferences.introSkip == 0 AND preferences.endingSkip == 0 ")
        }
        when {
            properties.contains(States.has_comments.name) -> statements.add(" comment != '' ")
            properties.contains(States.no_comments.name) -> statements.add(" comment == '' ")
        }
        when {
            properties.contains(States.synthetic.name) -> statements.add(" id < $MAX_SYNTHETIC_ID ")
            properties.contains(States.normal.name) -> statements.add(" id > $MAX_SYNTHETIC_ID ")
        }
        when {
            properties.contains(States.has_video.name) -> statements.add(" hasVideoMedia == true ")
            properties.contains(States.no_video.name) -> statements.add(" hasVideoMedia == false ")
        }
        when {
            properties.contains(States.youtube.name) -> statements.add(" downloadUrl CONTAINS[c] 'youtube' OR link CONTAINS[c] 'youtube' OR downloadUrl CONTAINS[c] 'youtu.be' OR link CONTAINS[c] 'youtu.be' ")
            properties.contains(States.rss.name) -> statements.add(" !(downloadUrl CONTAINS[c] 'youtube' OR link CONTAINS[c] 'youtube' OR downloadUrl CONTAINS[c] 'youtu.be' OR link CONTAINS[c] 'youtu.be') ")
        }

        val ratingQuerys = mutableListOf<String>()
        if (properties.contains(States.unrated.name)) ratingQuerys.add(" rating == ${Rating.UNRATED.code} ")
        if (properties.contains(States.trash.name)) ratingQuerys.add(" rating == ${Rating.TRASH.code} ")
        if (properties.contains(States.bad.name)) ratingQuerys.add(" rating == ${Rating.BAD.code} ")
        if (properties.contains(States.neutral.name)) ratingQuerys.add(" rating == ${Rating.NEUTRAL.code} ")
        if (properties.contains(States.good.name)) ratingQuerys.add(" rating == ${Rating.GOOD.code} ")
        if (properties.contains(States.favorite.name)) ratingQuerys.add(" rating == ${Rating.FAVORITE.code} ")
        if (ratingQuerys.isNotEmpty()) {
            val query = StringBuilder(" (" + ratingQuerys[0])
            if (ratingQuerys.size > 1) for (r in ratingQuerys.subList(1, ratingQuerys.size)) {
                query.append(" OR ")
                query.append(r)
            }
            query.append(") ")
            statements.add(query.toString())
        }

        val audoDeleteQuerys = mutableListOf<String>()
        if (properties.contains(States.global_auto_delete.name)) audoDeleteQuerys.add(" preferences.autoDelete == ${FeedPreferences.AutoDeleteAction.GLOBAL.code} ")
        if (properties.contains(States.always_auto_delete.name)) audoDeleteQuerys.add(" preferences.autoDelete == ${FeedPreferences.AutoDeleteAction.ALWAYS.code} ")
        if (properties.contains(States.never_auto_delete.name)) audoDeleteQuerys.add(" preferences.playSpeed == ${FeedPreferences.AutoDeleteAction.NEVER.code} ")
        if (audoDeleteQuerys.isNotEmpty()) {
            val query = StringBuilder(" (" + audoDeleteQuerys[0])
            if (audoDeleteQuerys.size > 1) for (r in audoDeleteQuerys.subList(1, audoDeleteQuerys.size)) {
                query.append(" OR ")
                query.append(r)
            }
            query.append(") ")
            Logd("FeedFilter", "audoDeleteQueues: ${query}")
            statements.add(query.toString())
        }
        when {
            properties.contains(States.autoDownload.name) -> statements.add(" preferences.autoDownload == true ")
            properties.contains(States.not_autoDownload.name) -> statements.add(" preferences.autoDownload == false ")
        }
        if (statements.isEmpty()) return "id > 0"

        val query = StringBuilder(" (" + statements[0])
        if (statements.size > 1) for (r in statements.subList(1, statements.size)) {
            query.append(" AND ")
            query.append(r)
        }
        query.append(") ")
        Logd("queryString", "${query}")
        return query.toString()
    }

    @Suppress("EnumEntryName")
    enum class States {
        keepUpdated,
        not_keepUpdated,
        pref_streaming,
        not_pref_streaming,
        global_playSpeed,
        custom_playSpeed,
        has_skips,
        no_skips,
        has_comments,
        no_comments,
        has_video,
        no_video,
        youtube,
        rss,
        synthetic,
        normal,
        global_auto_delete,
        always_auto_delete,
        never_auto_delete,
        autoDownload,
        not_autoDownload,
        unrated,
        trash,
        bad,
        neutral,
        good,
        favorite,
    }

    enum class FeedFilterGroup(val nameRes: Int, vararg values_: ItemProperties) {
        KEEP_UPDATED(R.string.keep_updated, ItemProperties(R.string.yes, States.keepUpdated.name), ItemProperties(R.string.no, States.not_keepUpdated.name)),
        OPINION(R.string.commented, ItemProperties(R.string.yes, States.has_comments.name), ItemProperties(R.string.no, States.no_comments.name)),
        RATING(R.string.rating_label, ItemProperties(R.string.unrated, States.unrated.name),
            ItemProperties(R.string.trash, States.trash.name),
            ItemProperties(R.string.bad, States.bad.name),
            ItemProperties(R.string.neutral, States.neutral.name),
            ItemProperties(R.string.good, States.good.name),
            ItemProperties(R.string.favorite, States.favorite.name),
        ),
        HAS_VIDEO(R.string.has_video, ItemProperties(R.string.yes, States.has_video.name), ItemProperties(R.string.no, States.no_video.name)),
        PLAY_SPEED(R.string.play_speed, ItemProperties(R.string.global_speed, States.global_playSpeed.name), ItemProperties(R.string.custom_speed, States.custom_playSpeed.name)),
        ORIGIN(R.string.feed_origin, ItemProperties(R.string.youtube, States.youtube.name), ItemProperties(R.string.rss, States.rss.name)),
        TYPE(R.string.feed_type, ItemProperties(R.string.synthetic, States.synthetic.name), ItemProperties(R.string.normal, States.normal.name)),
        SKIPS(R.string.has_skips, ItemProperties(R.string.yes, States.has_skips.name), ItemProperties(R.string.no, States.no_skips.name)),
        AUTO_DELETE(R.string.auto_delete, ItemProperties(R.string.always, States.always_auto_delete.name),
            ItemProperties(R.string.never, States.never_auto_delete.name),
            ItemProperties(R.string.global, States.global_auto_delete.name), ),
        PREF_STREAMING(R.string.pref_stream_over_download_title, ItemProperties(R.string.yes, States.pref_streaming.name), ItemProperties(R.string.no, States.not_pref_streaming.name)),
        AUTO_DOWNLOAD(R.string.auto_download, ItemProperties(R.string.yes, States.autoDownload.name), ItemProperties(R.string.no, States.not_autoDownload.name));

        val values: Array<ItemProperties> = arrayOf(*values_)

        class ItemProperties(val displayName: Int, val filterId: String)
    }

    companion object {
        @JvmStatic
        fun unfiltered(): FeedFilter {
            return FeedFilter("")
        }
    }
}
