package de.danoeh.antennapod.parser.feed.element.element

import de.danoeh.antennapod.parser.feed.element.AtomText
import de.danoeh.antennapod.parser.feed.namespace.Atom
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner

/**
 * Unit test for [AtomText].
 */
@RunWith(RobolectricTestRunner::class)
class AtomTextTest {
    @Test
    fun testProcessingHtml() {
        for (pair in TEST_DATA) {
            val atomText = AtomText("", Atom(), AtomText.TYPE_HTML)
            atomText.setContent(pair[0])
            Assert.assertEquals(pair[1], atomText.processedContent)
        }
    }

    companion object {
        private val TEST_DATA = arrayOf(arrayOf<String?>("&gt;", ">"),
            arrayOf<String?>(">", ">"),
            arrayOf<String?>("&lt;Fran&ccedil;ais&gt;", "<Français>"),
            arrayOf<String?>("ßÄÖÜ", "ßÄÖÜ"),
            arrayOf<String?>("&quot;", "\""),
            arrayOf<String?>("&szlig;", "ß"),
            arrayOf<String?>("&#8217;", "’"),
            arrayOf<String?>("&#x2030;", "‰"),
            arrayOf<String?>("&euro;", "€")
        )
    }
}
