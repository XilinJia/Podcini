package ac.mdiq.podcini.storage.export.html

import android.content.Context
import android.util.Log
import ac.mdiq.podcini.storage.export.ExportWriter
import ac.mdiq.podcini.storage.model.feed.Feed
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.io.Writer

/** Writes HTML documents.  */
class HtmlWriter : ExportWriter {
    /**
     * Takes a list of feeds and a writer and writes those into an HTML
     * document.
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed?>?, writer: Writer?, context: Context) {
        Log.d(TAG, "Starting to write document")

        val templateStream = context!!.assets.open("html-export-template.html")
        var template = IOUtils.toString(templateStream, "UTF-8")
        template = template.replace("\\{TITLE\\}".toRegex(), "Subscriptions")
        val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        writer!!.append(templateParts[0])
        for (feed in feeds!!) {
            writer.append("<li><div><img src=\"")
            writer.append(feed!!.imageUrl)
            writer.append("\" /><p>")
            writer.append(feed.title)
            writer.append(" <span><a href=\"")
            writer.append(feed.link)
            writer.append("\">Website</a> • <a href=\"")
            writer.append(feed.download_url)
            writer.append("\">Feed</a></span></p></div></li>\n")
        }
        writer.append(templateParts[1])
        Log.d(TAG, "Finished writing document")
    }

    override fun fileExtension(): String {
        return "html"
    }

    companion object {
        private const val TAG = "HtmlWriter"
    }
}
