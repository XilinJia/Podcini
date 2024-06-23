package ac.mdiq.podcini.storage.transport

import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import android.content.Context
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import ac.mdiq.podcini.storage.utils.SortOrder
import ac.mdiq.podcini.util.Logd
import org.apache.commons.io.IOUtils

import java.io.IOException
import java.io.Writer
import java.util.*

/** Writes saved favorites to file.  */
class FavoritesWriter : ExportWriter {
    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed?>?, writer: Writer?, context: Context) {
        Logd(TAG, "Starting to write document")

        val templateStream = context!!.assets.open("html-export-template.html")
        var template = IOUtils.toString(templateStream, UTF_8)
        template = template.replace("\\{TITLE\\}".toRegex(), "Favorites")
        val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val favTemplateStream = context.assets.open(FAVORITE_TEMPLATE)
        val favTemplate = IOUtils.toString(favTemplateStream, UTF_8)

        val feedTemplateStream = context.assets.open(FEED_TEMPLATE)
        val feedTemplate = IOUtils.toString(feedTemplateStream, UTF_8)

        val allFavorites = getEpisodes(0, Int.MAX_VALUE,
            EpisodeFilter(EpisodeFilter.IS_FAVORITE), SortOrder.DATE_NEW_OLD)
        val favoriteByFeed = getFeedMap(allFavorites)

        writer!!.append(templateParts[0])

        for (feedId in favoriteByFeed.keys) {
            val favorites: List<Episode> = favoriteByFeed[feedId]!!
            writer.append("<li><div>\n")
            writeFeed(writer, favorites[0].feed, feedTemplate)

            writer.append("<ul>\n")
            for (item in favorites) {
                writeFavoriteItem(writer, item, favTemplate)
            }
            writer.append("</ul></div></li>\n")
        }

        writer.append(templateParts[1])

        Logd(TAG, "Finished writing document")
    }

    /**
     * Group favorite episodes by feed, sorting them by publishing date in descending order.
     *
     * @param favoritesList `List` of all favorite episodes.
     * @return A `Map` favorite episodes, keyed by feed ID.
     */
    private fun getFeedMap(favoritesList: List<Episode>): Map<Long, MutableList<Episode>> {
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
    private fun writeFeed(writer: Writer?, feed: Feed?, feedTemplate: String) {
        val feedInfo = feedTemplate
            .replace("{FEED_IMG}", feed!!.imageUrl!!)
            .replace("{FEED_TITLE}", feed.title!!)
            .replace("{FEED_LINK}", feed.link!!)
            .replace("{FEED_WEBSITE}", feed.downloadUrl!!)

        writer!!.append(feedInfo)
    }

    @Throws(IOException::class)
    private fun writeFavoriteItem(writer: Writer?, item: Episode, favoriteTemplate: String) {
        var favItem = favoriteTemplate.replace("{FAV_TITLE}", item.title!!.trim { it <= ' ' })
        favItem = if (item.link != null) favItem.replace("{FAV_WEBSITE}", item.link!!)
        else favItem.replace("{FAV_WEBSITE}", "")

        favItem = if (item.media != null && item.media!!.downloadUrl != null) favItem.replace("{FAV_MEDIA}", item.media!!.downloadUrl!!)
        else favItem.replace("{FAV_MEDIA}", "")

        writer!!.append(favItem)
    }

    override fun fileExtension(): String {
        return "html"
    }

    companion object {
        private val TAG: String = FavoritesWriter::class.simpleName ?: "Anonymous"
        private const val FAVORITE_TEMPLATE = "html-export-favorites-item-template.html"
        private const val FEED_TEMPLATE = "html-export-feed-template.html"
        private const val UTF_8 = "UTF-8"
    }
}
