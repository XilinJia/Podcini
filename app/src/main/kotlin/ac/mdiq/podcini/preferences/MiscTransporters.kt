package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.net.sync.SyncService.Companion.isValidGuid
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.EpisodeAction.Companion.readFromJsonObject
import ac.mdiq.podcini.net.sync.model.SyncServiceException
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.hasAlmostEnded
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.util.Logd
import android.content.Context
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import java.io.IOException
import java.io.Reader
import java.io.Writer
import java.util.Date
import java.util.TreeMap
import kotlin.collections.get

class EpisodeProgressReader {
    val TAG = "EpisodeProgressReader"

    fun readDocument(reader: Reader) {
        val jsonString = reader.readText()
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val jsonAction = jsonArray.getJSONObject(i)
            Logd(TAG, "Loaded EpisodeActions message: $i $jsonAction")
            val action = readFromJsonObject(jsonAction) ?: continue
            Logd(TAG, "processing action: $action")
            val result = processEpisodeAction(action) ?: continue
//                upsertBlk(result.second) {}
        }
    }
    private fun processEpisodeAction(action: EpisodeAction): Pair<Long, Episode>? {
        val guid = if (isValidGuid(action.guid)) action.guid else null
        var feedItem = getEpisodeByGuidOrUrl(guid, action.episode?:"", false) ?: return null
        if (feedItem.media == null) {
            Logd(TAG, "Feed item has no media: $action")
            return null
        }
        var idRemove = 0L
        feedItem = upsertBlk(feedItem) {
            it.media!!.startPosition = action.started * 1000
            it.media!!.setPosition(action.position * 1000)
            it.media!!.playedDuration = action.playedDuration * 1000
            it.media!!.lastPlayedTime = (action.timestamp!!.time)
            it.rating = if (action.isFavorite) Rating.SUPER.code else Rating.UNRATED.code
            it.playState = action.playState
            if (hasAlmostEnded(it.media!!)) {
                Logd(TAG, "Marking as played: $action")
                it.setPlayed(true)
                it.media!!.setPosition(0)
                idRemove = it.id
            } else Logd(TAG, "Setting position: $action")
        }
        return Pair(idRemove, feedItem)
    }
}

class EpisodesProgressWriter : ExportWriter {
    val TAG = "EpisodesProgressWriter"

    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed>, writer: Writer, context: Context) {
        Logd(TAG, "Starting to write document")
        val queuedEpisodeActions: MutableList<EpisodeAction> = mutableListOf()
        val pausedItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.paused.name), EpisodeSortOrder.DATE_NEW_OLD)
        val readItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.played.name), EpisodeSortOrder.DATE_NEW_OLD)
        val favoriteItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_NEW_OLD)
        val comItems = mutableSetOf<Episode>()
        comItems.addAll(pausedItems)
        comItems.addAll(readItems)
        comItems.addAll(favoriteItems)
        Logd(TAG, "Save state for all " + comItems.size + " played episodes")
        for (item in comItems) {
            val media = item.media ?: continue
            val played = EpisodeAction.Builder(item, EpisodeAction.PLAY)
                .timestamp(Date(media.lastPlayedTime))
                .started(media.startPosition / 1000)
                .position(media.position / 1000)
                .playedDuration(media.playedDuration / 1000)
                .total(media.duration / 1000)
                .isFavorite(item.isSUPER)
                .playState(item.playState)
                .build()
            queuedEpisodeActions.add(played)
        }
        if (queuedEpisodeActions.isNotEmpty()) {
            try {
                Logd(TAG, "Saving ${queuedEpisodeActions.size} actions: ${StringUtils.join(queuedEpisodeActions, ", ")}")
                val list = JSONArray()
                for (episodeAction in queuedEpisodeActions) {
                    val obj = episodeAction.writeToJsonObject()
                    if (obj != null) {
                        Logd(TAG, "saving EpisodeAction: $obj")
                        list.put(obj)
                    }
                }
                writer.write(list.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                throw SyncServiceException(e)
            }
        }
        Logd(TAG, "Finished writing document")
    }
    override fun fileExtension(): String {
        return "json"
    }
}
class FavoritesWriter : ExportWriter {
    val TAG = "FavoritesWriter"

    private val FAVORITE_TEMPLATE = "html-export-favorites-item-template.html"
    private val FEED_TEMPLATE = "html-export-feed-template.html"
    private val UTF_8 = "UTF-8"
    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed>, writer: Writer, context: Context) {
        Logd(TAG, "Starting to write document")
        val templateStream = context.assets.open("html-export-template.html")
        var template = IOUtils.toString(templateStream, UTF_8)
        template = template.replace("\\{TITLE\\}".toRegex(), "Favorites")
        val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val favTemplateStream = context.assets.open(FAVORITE_TEMPLATE)
        val favTemplate = IOUtils.toString(favTemplateStream, UTF_8)
        val feedTemplateStream = context.assets.open(FEED_TEMPLATE)
        val feedTemplate = IOUtils.toString(feedTemplateStream, UTF_8)
        val allFavorites = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_NEW_OLD)
        val favoritesByFeed = buildFeedMap(allFavorites)
        writer.append(templateParts[0])
        for (feedId in favoritesByFeed.keys) {
            val favorites: List<Episode> = favoritesByFeed[feedId]!!
            if (favorites[0].feed == null) continue
            writer.append("<li><div>\n")
            writeFeed(writer, favorites[0].feed!!, feedTemplate)
            writer.append("<ul>\n")
            for (item in favorites) writeFavoriteItem(writer, item, favTemplate)
            writer.append("</ul></div></li>\n")
        }
        writer.append(templateParts[1])
        Logd(TAG, "Finished writing document")
    }
    /**
     * Group favorite episodes by feed, sorting them by publishing date in descending order.
     * @param favoritesList `List` of all favorite episodes.
     * @return A `Map` favorite episodes, keyed by feed ID.
     */
    private fun buildFeedMap(favoritesList: List<Episode>): Map<Long, MutableList<Episode>> {
        val feedMap: MutableMap<Long, MutableList<Episode>> = TreeMap()
        for (item in favoritesList) {
            var feedEpisodes = feedMap[item.feedId]
            if (feedEpisodes == null) {
                feedEpisodes = ArrayList()
                if (item.feedId != null) feedMap[item.feedId!!] = feedEpisodes
            }
            feedEpisodes.add(item)
        }
        return feedMap
    }
    @Throws(IOException::class)
    private fun writeFeed(writer: Writer, feed: Feed, feedTemplate: String) {
        val feedInfo = feedTemplate
            .replace("{FEED_IMG}", feed.imageUrl?:"")
            .replace("{FEED_TITLE}", feed.title?:" No title")
            .replace("{FEED_LINK}", feed.link?: "")
            .replace("{FEED_WEBSITE}", feed.downloadUrl?:"")
        writer.append(feedInfo)
    }
    @Throws(IOException::class)
    private fun writeFavoriteItem(writer: Writer, item: Episode, favoriteTemplate: String) {
        var favItem = favoriteTemplate.replace("{FAV_TITLE}", item.title!!.trim { it <= ' ' })
        favItem = if (item.link != null) favItem.replace("{FAV_WEBSITE}", item.link!!)
        else favItem.replace("{FAV_WEBSITE}", "")
        favItem =
            if (item.media != null && item.media!!.downloadUrl != null) favItem.replace("{FAV_MEDIA}", item.media!!.downloadUrl!!)
            else favItem.replace("{FAV_MEDIA}", "")
        writer.append(favItem)
    }
    override fun fileExtension(): String {
        return "html"
    }
}
class HtmlWriter : ExportWriter {
    val TAG = "HtmlWriter"

    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed>, writer: Writer, context: Context) {
        Logd(TAG, "Starting to write document")
        val templateStream = context.assets.open("html-export-template.html")
        var template = IOUtils.toString(templateStream, "UTF-8")
        template = template.replace("\\{TITLE\\}".toRegex(), "Subscriptions")
        val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        writer.append(templateParts[0])
        for (feed in feeds) {
            writer.append("<li><div><img src=\"")
            writer.append(feed.imageUrl)
            writer.append("\" /><p>")
            writer.append(feed.title)
            writer.append(" <span><a href=\"")
            writer.append(feed.link)
            writer.append("\">Website</a> • <a href=\"")
            writer.append(feed.downloadUrl)
            writer.append("\">Feed</a></span></p></div></li>\n")
        }
        writer.append(templateParts[1])
        Logd(TAG, "Finished writing document")
    }
    override fun fileExtension(): String {
        return "html"
    }
}
