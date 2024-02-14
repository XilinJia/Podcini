package ac.mdiq.podvinci.core.util.gui

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podvinci.core.util.gui.ShownotesCleaner.Companion.cleanStyleTag
import ac.mdiq.podvinci.core.util.gui.ShownotesCleaner.Companion.getTimecodeLinkTime
import ac.mdiq.podvinci.core.util.gui.ShownotesCleaner.Companion.isTimecodeLink
import org.jsoup.Jsoup
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test class for [ShownotesCleaner].
 */
@RunWith(RobolectricTestRunner::class)
class ShownotesCleanerTest {
    private var context: Context? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testProcessShownotesAddTimecodeHhmmssNoChapters() {
        val timeStr = "10:11:12"
        val time = (3600 * 1000 * 10 + 60 * 1000 * 11 + 12 * 1000).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context!!, shownotes, Int.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeHhmmssMoreThen24HoursNoChapters() {
        val timeStr = "25:00:00"
        val time = (25 * 60 * 60 * 1000).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context!!, shownotes, Int.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeHhmmNoChapters() {
        val timeStr = "10:11"
        val time = (3600 * 1000 * 10 + 60 * 1000 * 11).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context!!, shownotes, Int.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeMmssNoChapters() {
        val timeStr = "10:11"
        val time = (10 * 60 * 1000 + 11 * 1000).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context!!, shownotes, 11 * 60 * 1000)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeHmmssNoChapters() {
        val timeStr = "2:11:12"
        val time = (2 * 60 * 60 * 1000 + 11 * 60 * 1000 + 12 * 1000).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context!!, shownotes, Int.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeMssNoChapters() {
        val timeStr = "1:12"
        val time = (60 * 1000 + 12 * 1000).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context!!, shownotes, 2 * 60 * 1000)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddNoTimecodeDuration() {
        val timeStr = "2:11:12"
        val time = 2 * 60 * 60 * 1000 + 11 * 60 * 1000 + 12 * 1000

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context!!, shownotes, time)
        val res = t.processShownotes()
        val d = Jsoup.parse(res)
        Assert.assertEquals("Should not parse time codes that equal duration",
            0,
            d.body().getElementsByTag("a").size.toLong())
    }

    @Test
    fun testProcessShownotesAddTimecodeMultipleFormatsNoChapters() {
        val timeStrings = arrayOf<String?>("10:12", "1:10:12")

        val shownotes = ("<p> Some test text with a timecode " + timeStrings[0]
                + " here. Hey look another one " + timeStrings[1] + " here!</p>")
        val t = ShownotesCleaner(context!!, shownotes, 2 * 60 * 60 * 1000)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf((10 * 60 * 1000 + 12 * 1000).toLong(),
            (60 * 60 * 1000 + 10 * 60 * 1000 + 12 * 1000).toLong()), timeStrings)
    }

    @Test
    fun testProcessShownotesAddTimecodeMultipleShortFormatNoChapters() {
        // One of these timecodes fits as HH:MM and one does not so both should be parsed as MM:SS.

        val timeStrings = arrayOf<String?>("10:12", "2:12")

        val shownotes = ("<p> Some test text with a timecode " + timeStrings[0]
                + " here. Hey look another one " + timeStrings[1] + " here!</p>")
        val t = ShownotesCleaner(context!!, shownotes, 3 * 60 * 60 * 1000)
        val res = t.processShownotes()
        checkLinkCorrect(res,
            longArrayOf((10 * 60 * 1000 + 12 * 1000).toLong(), (2 * 60 * 1000 + 12 * 1000).toLong()),
            timeStrings)
    }

    @Test
    fun testProcessShownotesAddTimecodeParentheses() {
        val timeStr = "10:11"
        val time = (3600 * 1000 * 10 + 60 * 1000 * 11).toLong()

        val shownotes = "<p> Some test text with a timecode ($timeStr) here.</p>"
        val t = ShownotesCleaner(context!!, shownotes, Int.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeBrackets() {
        val timeStr = "10:11"
        val time = (3600 * 1000 * 10 + 60 * 1000 * 11).toLong()

        val shownotes = "<p> Some test text with a timecode [$timeStr] here.</p>"
        val t = ShownotesCleaner(context!!, shownotes, Int.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeAngleBrackets() {
        val timeStr = "10:11"
        val time = (3600 * 1000 * 10 + 60 * 1000 * 11).toLong()

        val shownotes = "<p> Some test text with a timecode <$timeStr> here.</p>"
        val t = ShownotesCleaner(context!!, shownotes, Int.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAndInvalidTimecode() {
        val timeStrs = arrayOf("2:1", "0:0", "000", "00", "00:000")

        val shownotes = StringBuilder("<p> Some test text with timecodes ")
        for (timeStr in timeStrs) {
            shownotes.append(timeStr).append(" ")
        }
        shownotes.append("here.</p>")

        val t = ShownotesCleaner(context!!, shownotes.toString(), Int.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, LongArray(0), arrayOfNulls(0))
    }

    private fun checkLinkCorrect(res: String, timecodes: LongArray, timecodeStr: Array<String?>) {
        Assert.assertNotNull(res)
        val d = Jsoup.parse(res)
        val links = d.body().getElementsByTag("a")
        var countedLinks = 0
        for (link in links) {
            val href = link.attributes()["href"]
            val text = link.text()
            if (href.startsWith("podvinci://")) {
                Assert.assertTrue(href.endsWith(timecodes[countedLinks].toString()))
                Assert.assertEquals(timecodeStr[countedLinks], text)
                countedLinks++
                Assert.assertTrue("Contains too many links: " + countedLinks + " > "
                        + timecodes.size, countedLinks <= timecodes.size)
            }
        }
        Assert.assertEquals(timecodes.size.toLong(), countedLinks.toLong())
    }

    @Test
    fun testIsTimecodeLink() {
        Assert.assertFalse(isTimecodeLink(null))
        Assert.assertFalse(isTimecodeLink("http://podvinci/timecode/123123"))
        Assert.assertFalse(isTimecodeLink("podvinci://timecode/"))
        Assert.assertFalse(isTimecodeLink("podvinci://123123"))
        Assert.assertFalse(isTimecodeLink("podvinci://timecode/123123a"))
        Assert.assertTrue(isTimecodeLink("podvinci://timecode/123"))
        Assert.assertTrue(isTimecodeLink("podvinci://timecode/1"))
    }

    @Test
    fun testGetTimecodeLinkTime() {
        Assert.assertEquals(-1, getTimecodeLinkTime(null).toLong())
        Assert.assertEquals(-1, getTimecodeLinkTime("http://timecode/123").toLong())
        Assert.assertEquals(123, getTimecodeLinkTime("podvinci://timecode/123").toLong())
    }

    @Test
    fun testCleanupColors() {
        val input = ("/* /* */ .foo { text-decoration: underline;color:#f00;font-weight:bold;}"
                + "#bar { text-decoration: underline;color:#f00;font-weight:bold; }"
                + "div {text-decoration: underline; color /* */ : /* */ #f00 /* */; font-weight:bold; }"
                + "#foobar { /* color: */ text-decoration: underline; /* color: */font-weight:bold /* ; */; }"
                + "baz { background-color:#f00;border: solid 2px;border-color:#0f0;text-decoration: underline; }")
        val expected = (" .foo { text-decoration: underline;font-weight:bold;}"
                + "#bar { text-decoration: underline;font-weight:bold; }"
                + "div {text-decoration: underline;  font-weight:bold; }"
                + "#foobar {  text-decoration: underline; font-weight:bold ; }"
                + "baz { background-color:#f00;border: solid 2px;border-color:#0f0;text-decoration: underline; }")
        Assert.assertEquals(expected, cleanStyleTag(input))
    }
}
