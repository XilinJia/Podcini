package ac.mdiq.podcini.feed.parser

import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.feed.parser.util.TypeGetter
import android.util.Log
import org.apache.commons.io.input.XmlStreamReader
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.Reader
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

class FeedHandler {
    @Throws(SAXException::class,
        IOException::class,
        ParserConfigurationException::class,
        ac.mdiq.podcini.feed.parser.UnsupportedFeedtypeException::class)
    fun parseFeed(feed: Feed): ac.mdiq.podcini.feed.parser.FeedHandlerResult {
        val tg = TypeGetter()
        val type = tg.getType(feed)
        val handler = ac.mdiq.podcini.feed.parser.SyndHandler(feed, type)

        if (feed.file_url != null) {
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = true
            val saxParser = factory.newSAXParser()
//            saxParser.parse(File(feed.file_url!!), handler)

            val inputStreamReader: Reader = XmlStreamReader(File(feed.file_url!!))
            val inputSource = InputSource(inputStreamReader)
            Log.d("FeedHandler", "starting saxParser.parse")
            saxParser.parse(inputSource, handler)
            inputStreamReader.close()
        }
        return ac.mdiq.podcini.feed.parser.FeedHandlerResult(handler.state.feed,
            handler.state.alternateUrls,
            handler.state.redirectUrl ?: "")
    }
}
