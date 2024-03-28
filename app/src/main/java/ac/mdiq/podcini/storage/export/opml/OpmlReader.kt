package ac.mdiq.podcini.storage.export.opml

import android.util.Log
import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.export.CommonSymbols
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.Reader

/** Reads OPML documents.  */
class OpmlReader {
    // ATTRIBUTES
    private var isInOpml = false
    private var elementList: ArrayList<OpmlElement>? = null

    /**
     * Reads an Opml document and returns a list of all OPML elements it can
     * find
     *
     * @throws IOException
     * @throws XmlPullParserException
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun readDocument(reader: Reader?): ArrayList<OpmlElement> {
        elementList = ArrayList()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val xpp = factory.newPullParser()
        xpp.setInput(reader)
        var eventType = xpp.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_DOCUMENT -> if (BuildConfig.DEBUG) Log.d(TAG, "Reached beginning of document")
                XmlPullParser.START_TAG -> when {
                    xpp.name == OpmlSymbols.OPML -> {
                        isInOpml = true
                        if (BuildConfig.DEBUG) Log.d(TAG, "Reached beginning of OPML tree.")
                    }
                    isInOpml && xpp.name == OpmlSymbols.OUTLINE -> {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Found new Opml element")
                        val element = OpmlElement()

                        val title = xpp.getAttributeValue(null, CommonSymbols.TITLE)
                        if (title != null) {
                            Log.i(TAG, "Using title: $title")
                            element.text = title
                        } else {
                            Log.i(TAG, "Title not found, using text")
                            element.text = xpp.getAttributeValue(null, OpmlSymbols.TEXT)
                        }
                        element.xmlUrl = xpp.getAttributeValue(null, OpmlSymbols.XMLURL)
                        element.htmlUrl = xpp.getAttributeValue(null, OpmlSymbols.HTMLURL)
                        element.type = xpp.getAttributeValue(null, OpmlSymbols.TYPE)
                        if (element.xmlUrl != null) {
                            if (element.text == null) {
                                Log.i(TAG, "Opml element has no text attribute.")
                                element.text = element.xmlUrl
                            }
                            elementList!!.add(element)
                        } else {
                            if (BuildConfig.DEBUG) Log.d(TAG,
                                "Skipping element because of missing xml url")
                        }
                    }
                }
            }
            eventType = xpp.next()
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Parsing finished.")

        return elementList!!
    }

    companion object {
        private const val TAG = "OpmlReader"
    }
}
