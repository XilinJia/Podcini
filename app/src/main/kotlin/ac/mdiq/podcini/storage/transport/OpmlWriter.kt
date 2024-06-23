package ac.mdiq.podcini.storage.transport

import android.content.Context
import android.util.Xml
import ac.mdiq.podcini.util.DateFormatter.formatRfc822Date
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import java.io.IOException
import java.io.Writer
import java.util.*

/** Writes OPML documents.  */
class OpmlWriter : ExportWriter {
    /**
     * Takes a list of feeds and a writer and writes those into an OPML
     * document.
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed?>?, writer: Writer?, context: Context) {
        Logd(TAG, "Starting to write document")
        val xs = Xml.newSerializer()
        xs.setFeature(CommonSymbols.XML_FEATURE_INDENT_OUTPUT, true)
        xs.setOutput(writer)

        xs.startDocument(ENCODING, false)
        xs.startTag(null, OpmlSymbols.OPML)
        xs.attribute(null, OpmlSymbols.VERSION, OPML_VERSION)

        xs.startTag(null, CommonSymbols.HEAD)
        xs.startTag(null, CommonSymbols.TITLE)
        xs.text(OPML_TITLE)
        xs.endTag(null, CommonSymbols.TITLE)
        xs.startTag(null, OpmlSymbols.DATE_CREATED)
        xs.text(formatRfc822Date(Date()))
        xs.endTag(null, OpmlSymbols.DATE_CREATED)
        xs.endTag(null, CommonSymbols.HEAD)

        xs.startTag(null, CommonSymbols.BODY)
        for (feed in feeds!!) {
            xs.startTag(null, OpmlSymbols.OUTLINE)
            xs.attribute(null, OpmlSymbols.TEXT, feed!!.title)
            xs.attribute(null, CommonSymbols.TITLE, feed.title)
            if (feed.type != null) xs.attribute(null, OpmlSymbols.TYPE, feed.type)
            xs.attribute(null, OpmlSymbols.XMLURL, feed.downloadUrl)
            if (feed.link != null) xs.attribute(null, OpmlSymbols.HTMLURL, feed.link)
            xs.endTag(null, OpmlSymbols.OUTLINE)
        }
        xs.endTag(null, CommonSymbols.BODY)
        xs.endTag(null, OpmlSymbols.OPML)
        xs.endDocument()
        Logd(TAG, "Finished writing document")
    }

    override fun fileExtension(): String {
        return "opml"
    }

    companion object {
        private val TAG: String = OpmlWriter::class.simpleName ?: "Anonymous"
        private const val ENCODING = "UTF-8"
        private const val OPML_VERSION = "2.0"
        private const val OPML_TITLE = "Podcini Subscriptions"
    }
}
