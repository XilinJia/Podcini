package ac.mdiq.podcini.feed.parser.util

import android.util.Log
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.feed.parser.UnsupportedFeedtypeException
import org.apache.commons.io.input.XmlStreamReader
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.Reader

/** Gets the type of a specific feed by reading the root element.  */
class TypeGetter {
    enum class Type {
        RSS20, RSS091, ATOM, INVALID
    }

    @Throws(UnsupportedFeedtypeException::class)
    fun getType(feed: Feed): Type {
        val factory: XmlPullParserFactory
        if (feed.file_url != null) {
            var reader: Reader? = null
            try {
                factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val xpp = factory.newPullParser()
                reader = createReader(feed)
                xpp.setInput(reader)
                var eventType = xpp.eventType
//                TODO: need to check about handling webpage
//                return return Type.ATOM

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        when (val tag = xpp.name) {
                            ATOM_ROOT -> {
                                feed.type = Feed.TYPE_ATOM1
                                Log.d(TAG, "Recognized type Atom")

                                val strLang = xpp.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
                                if (strLang != null) {
                                    feed.language = strLang
                                }

                                return Type.ATOM
                            }
                            RSS_ROOT -> {
                                val strVersion = xpp.getAttributeValue(null, "version")
                                when (strVersion) {
                                    null -> {
                                        feed.type = Feed.TYPE_RSS2
                                        Log.d(TAG, "Assuming type RSS 2.0")
                                        return Type.RSS20
                                    }
                                    "2.0" -> {
                                        feed.type = Feed.TYPE_RSS2
                                        Log.d(TAG, "Recognized type RSS 2.0")
                                        return Type.RSS20
                                    }
                                    "0.91", "0.92" -> {
                                        Log.d(TAG, "Recognized type RSS 0.91/0.92")
                                        return Type.RSS091
                                    }
                                    else -> {
                                        throw UnsupportedFeedtypeException("Unsupported rss version")
                                    }
                                }
                            }
                            else -> {
                                Log.d(TAG, "Type is invalid")
                                throw UnsupportedFeedtypeException(Type.INVALID, tag)
                            }
                        }
                    } else {
                        try {
                            eventType = xpp.next()
                        } catch (e: RuntimeException) {
                            // Apparently this happens on some devices...
                            throw UnsupportedFeedtypeException("Unable to get type")
                        }
                    }
                }
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
                // XML document might actually be a HTML document -> try to parse as HTML
                var rootElement: String? = null
                try {
                    Jsoup.parse(File(feed.file_url!!))
                    rootElement = "html"
                } catch (e1: IOException) {
                    Log.d(TAG, "IOException: " + feed.file_url)
                    e1.printStackTrace()
                }
                throw UnsupportedFeedtypeException(Type.INVALID, rootElement)
            } catch (e: IOException) {
                Log.d(TAG, "IOException: " + feed.file_url)
                e.printStackTrace()
            } finally {
                if (reader != null) {
                    try {
                        reader.close()
                    } catch (e: IOException) {
                        Log.d(TAG, "IOException: $reader")
                        e.printStackTrace()
                    }
                }
            }
        }
        Log.d(TAG, "Type is invalid")
        throw UnsupportedFeedtypeException(Type.INVALID)
    }

    private fun createReader(feed: Feed): Reader? {
        if (feed.file_url == null) return null

        val reader: Reader
        try {
            reader = XmlStreamReader(File(feed.file_url!!))
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "FileNotFoundException: " + feed.file_url)
            e.printStackTrace()
            return null
        } catch (e: IOException) {
            Log.d(TAG, "IOException: " + feed.file_url)
            e.printStackTrace()
            return null
        }
        return reader
    }

    companion object {
        private const val TAG = "TypeGetter"

        private const val ATOM_ROOT = "feed"
        private const val RSS_ROOT = "rss"
    }
}
