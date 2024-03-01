package ac.mdiq.podcini.storage.export.favorites

import android.content.Context
import android.util.Log
import ac.mdiq.podcini.storage.export.ExportWriter
import ac.mdiq.podcini.storage.DBReader.getEpisodes
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.storage.model.feed.SortOrder
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.io.Writer
import java.util.*

/** Writes saved favorites to file.  */
class FavoritesWriter : ExportWriter {
    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed?>?, writer: Writer?, context: Context) {
        Log.d(TAG, "Starting to write document")

        val templateStream = context!!.assets.open("html-export-template.html")
        var template = IOUtils.toString(templateStream, UTF_8)
        template = template.replace("\\{TITLE\\}".toRegex(), "Favorites")
        val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val favTemplateStream = context.assets.open(FAVORITE_TEMPLATE)
        val favTemplate = IOUtils.toString(favTemplateStream, UTF_8)

        val feedTemplateStream = context.assets.open(FEED_TEMPLATE)
        val feedTemplate = IOUtils.toString(feedTemplateStream, UTF_8)

        val allFavorites = getEpisodes(0, Int.MAX_VALUE,
            FeedItemFilter(FeedItemFilter.IS_FAVORITE), SortOrder.DATE_NEW_OLD)
        val favoriteByFeed = getFeedMap(allFavorites)

        writer!!.append(templateParts[0])

        for (feedId in favoriteByFeed.keys) {
            val favorites: List<FeedItem> = favoriteByFeed[feedId]!!
            writer.append("<li><div>\n")
            writeFeed(writer, favorites[0].feed, feedTemplate)

            writer.append("<ul>\n")
            for (item in favorites) {
                writeFavoriteItem(writer, item, favTemplate)
            }
            writer.append("</ul></div></li>\n")
        }

        writer.append(templateParts[1])

        Log.d(TAG, "Finished writing document")
    }

    /**
     * Group favorite episodes by feed, sorting them by publishing date in descending order.
     *
     * @param favoritesList `List` of all favorite episodes.
     * @return A `Map` favorite episodes, keyed by feed ID.
     */
    private fun getFeedMap(favoritesList: List<FeedItem>): Map<Long, MutableList<FeedItem>> {
        val feedMap: MutableMap<Long, MutableList<FeedItem>> = TreeMap()

        for (item in favoritesList) {
            var feedEpisodes = feedMap[item.feedId]

            if (feedEpisodes == null) {
                feedEpisodes = ArrayList()
                feedMap[item.feedId] = feedEpisodes
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
            .replace("{FEED_WEBSITE}", feed.download_url!!)

        writer!!.append(feedInfo)
    }

    @Throws(IOException::class)
    private fun writeFavoriteItem(writer: Writer?, item: FeedItem, favoriteTemplate: String) {
        var favItem = favoriteTemplate.replace("{FAV_TITLE}", item.title!!.trim { it <= ' ' })
        favItem = if (item.link != null) {
            favItem.replace("{FAV_WEBSITE}", item.link!!)
        } else {
            favItem.replace("{FAV_WEBSITE}", "")
        }
        favItem = if (item.media != null && item.media!!.download_url != null) {
            favItem.replace("{FAV_MEDIA}", item.media!!.download_url!!)
        } else {
            favItem.replace("{FAV_MEDIA}", "")
        }

        writer!!.append(favItem)
    }

    override fun fileExtension(): String {
        return "html"
    }

    companion object {
        private const val TAG = "FavoritesWriter"
        private const val FAVORITE_TEMPLATE = "html-export-favorites-item-template.html"
        private const val FEED_TEMPLATE = "html-export-feed-template.html"
        private const val UTF_8 = "UTF-8"
    }
}
