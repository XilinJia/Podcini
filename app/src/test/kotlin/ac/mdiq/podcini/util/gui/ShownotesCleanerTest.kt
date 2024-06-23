package ac.mdiq.podcini.util.gui

import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podcini.ui.utils.ShownotesCleaner.Companion.cleanStyleTag
import ac.mdiq.podcini.ui.utils.ShownotesCleaner.Companion.getTimecodeLinkTime
import ac.mdiq.podcini.ui.utils.ShownotesCleaner.Companion.isTimecodeLink
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
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun testProcessShownotesAddTimecodeHhmmssNoChapters() {
        val timeStr = "10:11:12"
        val time = (3600 * 1000 * 10 + 60 * 1000 * 11 + 12 * 1000).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, Int.MAX_VALUE)
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeHhmmssMoreThen24HoursNoChapters() {
        val timeStr = "25:00:00"
        val time = (25 * 60 * 60 * 1000).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, Int.MAX_VALUE)
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeHhmmNoChapters() {
        val timeStr = "10:11"
        val time = (3600 * 1000 * 10 + 60 * 1000 * 11).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, Int.MAX_VALUE)
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeMmssNoChapters() {
        val timeStr = "10:11"
        val time = (10 * 60 * 1000 + 11 * 1000).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, 11 * 60 * 1000)
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeHmmssNoChapters() {
        val timeStr = "2:11:12"
        val time = (2 * 60 * 60 * 1000 + 11 * 60 * 1000 + 12 * 1000).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, Int.MAX_VALUE)
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeMssNoChapters() {
        val timeStr = "1:12"
        val time = (60 * 1000 + 12 * 1000).toLong()

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, 2 * 60 * 1000)
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddNoTimecodeDuration() {
        val timeStr = "2:11:12"
        val time = 2 * 60 * 60 * 1000 + 11 * 60 * 1000 + 12 * 1000

        val shownotes = "<p> Some test text with a timecode $timeStr here.</p>"
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, time)
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
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, 2 * 60 * 60 * 1000)
        checkLinkCorrect(res, longArrayOf((10 * 60 * 1000 + 12 * 1000).toLong(),
            (60 * 60 * 1000 + 10 * 60 * 1000 + 12 * 1000).toLong()), timeStrings)
    }

    @Test
    fun testProcessShownotesAddTimecodeMultipleShortFormatNoChapters() {
        // One of these timecodes fits as HH:MM and one does not so both should be parsed as MM:SS.

        val timeStrings = arrayOf<String?>("10:12", "2:12")

        val shownotes = ("<p> Some test text with a timecode " + timeStrings[0]
                + " here. Hey look another one " + timeStrings[1] + " here!</p>")
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, 3 * 60 * 60 * 1000)
        checkLinkCorrect(res,
            longArrayOf((10 * 60 * 1000 + 12 * 1000).toLong(), (2 * 60 * 1000 + 12 * 1000).toLong()),
            timeStrings)
    }

    @Test
    fun testProcessShownotesAddTimecodeParentheses() {
        val timeStr = "10:11"
        val time = (3600 * 1000 * 10 + 60 * 1000 * 11).toLong()

        val shownotes = "<p> Some test text with a timecode ($timeStr) here.</p>"
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, Int.MAX_VALUE)
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeBrackets() {
        val timeStr = "10:11"
        val time = (3600 * 1000 * 10 + 60 * 1000 * 11).toLong()

        val shownotes = "<p> Some test text with a timecode [$timeStr] here.</p>"
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, Int.MAX_VALUE)
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeAngleBrackets() {
        val timeStr = "10:11"
        val time = (3600 * 1000 * 10 + 60 * 1000 * 11).toLong()

        val shownotes = "<p> Some test text with a timecode <$timeStr> here.</p>"
        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes, Int.MAX_VALUE)
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

        val t = ShownotesCleaner(context)
        val res = t.processShownotes(shownotes.toString(), Int.MAX_VALUE)
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
            if (href.startsWith("podcini://")) {
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
        Assert.assertFalse(isTimecodeLink("http://podcini/timecode/123123"))
        Assert.assertFalse(isTimecodeLink("podcini://timecode/"))
        Assert.assertFalse(isTimecodeLink("podcini://123123"))
        Assert.assertFalse(isTimecodeLink("podcini://timecode/123123a"))
        Assert.assertTrue(isTimecodeLink("podcini://timecode/123"))
        Assert.assertTrue(isTimecodeLink("podcini://timecode/1"))
    }

    @Test
    fun testGetTimecodeLinkTime() {
        Assert.assertEquals(-1, getTimecodeLinkTime(null).toLong())
        Assert.assertEquals(-1, getTimecodeLinkTime("http://timecode/123").toLong())
        Assert.assertEquals(123, getTimecodeLinkTime("podcini://timecode/123").toLong())
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
