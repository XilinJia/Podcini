package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.model.FeedPreferences.Companion.SPEED_USE_GLOBAL
import java.io.Serializable

class FeedFilter(vararg properties: String) : Serializable {

    private val properties: Array<String> = arrayOf(*properties.filter { it.isNotEmpty() }.map {it.trim()}.toTypedArray())

    val showKeepUpdated: Boolean = hasProperty(States.keepUpdated.name)
    val showNotKeepUpdated: Boolean = hasProperty(States.not_keepUpdated.name)
    val showGlobalPlaySpeed: Boolean = hasProperty(States.global_playSpeed.name)
    val showCustomPlaySpeed: Boolean = hasProperty(States.custom_playSpeed.name)
    val showHasComments: Boolean = hasProperty(States.has_comments.name)
    val showNoComments: Boolean = hasProperty(States.no_comments.name)
    val showHasSkips: Boolean = hasProperty(States.has_skips.name)
    val showNoSkips: Boolean = hasProperty(States.no_skips.name)
    val showAlwaysAutoDelete: Boolean = hasProperty(States.always_auto_delete.name)
    val showNeverAutoDelete: Boolean = hasProperty(States.never_auto_delete.name)
    val showAutoDownload: Boolean = hasProperty(States.autoDownload.name)
    val showNotAutoDownload: Boolean = hasProperty(States.not_autoDownload.name)

    constructor(properties: String) : this(*(properties.split(",").toTypedArray()))

    private fun hasProperty(property: String): Boolean {
        return listOf(*properties).contains(property)
    }

    val values: Array<String>
        get() = properties.clone()

    val valuesList: List<String>
        get() = listOf(*properties)

    fun matches(feed: Feed): Boolean {
        when {
            showKeepUpdated && feed.preferences?.keepUpdated != true  -> return false
            showNotKeepUpdated && feed.preferences?.keepUpdated != false  -> return false
            showGlobalPlaySpeed && feed.preferences?.playSpeed != SPEED_USE_GLOBAL -> return false
            showCustomPlaySpeed && feed.preferences?.playSpeed == SPEED_USE_GLOBAL -> return false
            showHasComments && feed.comment.isEmpty() -> return false
            showNoComments && feed.comment.isEmpty()  -> return false
            showHasSkips && feed.preferences?.introSkip == 0 && feed.preferences?.endingSkip == 0  -> return false
            showNoSkips && (feed.preferences?.introSkip != 0 || feed.preferences?.endingSkip != 0)  -> return false
            showAlwaysAutoDelete && feed.preferences?.autoDeleteAction != FeedPreferences.AutoDeleteAction.ALWAYS  -> return false
            showNeverAutoDelete && feed.preferences?.autoDeleteAction != FeedPreferences.AutoDeleteAction.NEVER  -> return false
            showAutoDownload && feed.preferences?.autoDownload != true  -> return false
            showNotAutoDownload && feed.preferences?.autoDownload != false  -> return false
            else -> return true
        }
    }

    fun queryString(): String {
        val statements: MutableList<String> = ArrayList()
        when {
            showKeepUpdated -> statements.add("preferences.keepUpdated == true ")
            showNotKeepUpdated -> statements.add(" preferences.keepUpdated == false ")
        }
        when {
            showGlobalPlaySpeed -> statements.add(" preferences.playSpeed == ${SPEED_USE_GLOBAL} ")
            showCustomPlaySpeed -> statements.add(" preferences.playSpeed != $SPEED_USE_GLOBAL ")
        }
        when {
            showHasSkips -> statements.add(" preferences.introSkip != 0 OR preferences.endingSkip != 0 ")
            showNoSkips -> statements.add(" preferences.introSkip == 0 AND preferences.endingSkip == 0 ")
        }
        when {
            showHasComments -> statements.add(" comment != '' ")
            showNoComments -> statements.add(" comment == '' ")
        }
        when {
            showAlwaysAutoDelete -> statements.add(" preferences.autoDelete == ${FeedPreferences.AutoDeleteAction.ALWAYS.code} ")
            showNeverAutoDelete -> statements.add(" preferences.playSpeed == ${FeedPreferences.AutoDeleteAction.NEVER.code} ")
        }
        when {
            showAutoDownload -> statements.add(" preferences.autoDownload == true ")
            showNotAutoDownload -> statements.add(" preferences.autoDownload == false ")
        }

        if (statements.isEmpty()) return "id > 0"

        val query = StringBuilder(" (" + statements[0])
        for (r in statements.subList(1, statements.size)) {
            query.append(" AND ")
            query.append(r)
        }
        query.append(") ")

        return query.toString()
    }

    @Suppress("EnumEntryName")
    enum class States {
        keepUpdated,
        not_keepUpdated,
        global_playSpeed,
        custom_playSpeed,
        has_skips,
        no_skips,
        has_comments,
        no_comments,
//        global_auto_delete,
        always_auto_delete,
        never_auto_delete,
        autoDownload,
        not_autoDownload,

    }
    companion object {
        @JvmStatic
        fun unfiltered(): FeedFilter {
            return FeedFilter("")
        }
    }
}
