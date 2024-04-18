package ac.mdiq.podcini.ui.utils

import ac.mdiq.podcini.R
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import androidx.annotation.ColorInt
import ac.mdiq.podcini.util.Converter.durationStringLongToMs
import ac.mdiq.podcini.util.Converter.durationStringShortToMs
import org.apache.commons.io.IOUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

/**
 * Cleans up and prepares shownotes:
 * - Guesses time stamps to make them clickable
 * - Removes some formatting
 */
class ShownotesCleaner(context: Context, private val rawShownotes: String, private val playableDuration: Int) {
    private val noShownotesLabel = context.getString(R.string.no_shownotes_label)
    private val webviewStyle: String

    init {
        val colorPrimary = colorToHtml(context, android.R.attr.textColorPrimary)
        val colorAccent = colorToHtml(context, R.attr.colorAccent)
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics).toInt()
        var styleString: String? = ""
        try {
            val templateStream = context.assets.open("shownotes-style.css")
            styleString = IOUtils.toString(templateStream, "UTF-8")
        } catch (e: IOException) {
            e.printStackTrace()
        }
        webviewStyle = String.format(Locale.US, styleString!!, colorPrimary, colorAccent, margin, margin, margin, margin)
    }

    private fun colorToHtml(context: Context, colorAttr: Int): String {
        val res = context.theme.obtainStyledAttributes(intArrayOf(colorAttr))
        @ColorInt val col = res.getColor(0, 0)
        val color = ("rgba(" + Color.red(col) + "," + Color.green(col) + "," + Color.blue(col) + "," + (Color.alpha(col) / 255.0) + ")")
        res.recycle()
        return color
    }

    /**
     * Applies an app-specific CSS stylesheet and adds timecode links (optional).
     *
     *
     * This method does NOT change the original shownotes string of the shownotesProvider object and it should
     * also not be changed by the caller.
     *
     * @return The processed HTML string.
     */
    fun processShownotes(): String {
        var shownotes = rawShownotes

        if (shownotes.isEmpty()) {
            Log.d(TAG, "shownotesProvider contained no shownotes. Returning 'no shownotes' message")
            shownotes = "<html><head></head><body><p id='apNoShownotes'>$noShownotesLabel</p></body></html>"
        }

        // replace ASCII line breaks with HTML ones if shownotes don't contain HTML line breaks already
        if (!LINE_BREAK_REGEX.matcher(shownotes).find() && !shownotes.contains("<p>")) {
            shownotes = shownotes.replace("\n", "<br />")
        }

        val document = Jsoup.parse(shownotes)
        cleanCss(document)
        document.head().appendElement("style").attr("type", "text/css").text(webviewStyle)
        addTimecodes(document)
        return document.toString()
    }

    private fun addTimecodes(document: Document) {
        val elementsWithTimeCodes = document.body().getElementsMatchingOwnText(TIMECODE_REGEX)
        Log.d(TAG, "Recognized " + elementsWithTimeCodes.size + " timecodes")

        if (elementsWithTimeCodes.size == 0) {
            // No elements with timecodes
            return
        }
        var useHourFormat = true

        if (playableDuration != Int.MAX_VALUE) {
            // We need to decide if we are going to treat short timecodes as HH:MM or MM:SS. To do
            // so we will parse all the short timecodes and see if they fit in the duration. If one
            // does not we will use MM:SS, otherwise all will be parsed as HH:MM.

            for (element in elementsWithTimeCodes) {
                val matcherForElement = TIMECODE_REGEX.matcher(element.html())
                while (matcherForElement.find()) {
                    // We only want short timecodes right now.

                    if (matcherForElement.group(1) == null) {
                        val time = durationStringShortToMs(matcherForElement.group(0)!!, true)

                        // If the parsed timecode is greater then the duration then we know we need to
                        // use the minute format so we are done.
                        if (time > playableDuration) {
                            useHourFormat = false
                            break
                        }
                    }
                }

                if (!useHourFormat) {
                    break
                }
            }
        }

        for (element in elementsWithTimeCodes) {
            val matcherForElement = TIMECODE_REGEX.matcher(element.html())
            val buffer = StringBuffer()

            while (matcherForElement.find()) {
                val group = matcherForElement.group(0) ?: continue

                val time = if (matcherForElement.group(1) != null) durationStringLongToMs(group)
                else durationStringShortToMs(group, useHourFormat)

                var replacementText = group
                if (time < playableDuration) {
                    replacementText = String.format(Locale.US, TIMECODE_LINK, time, group)
                }

                matcherForElement.appendReplacement(buffer, replacementText)
            }

            matcherForElement.appendTail(buffer)
            element.html(buffer.toString())
        }
    }

    private fun cleanCss(document: Document) {
        for (element in document.allElements) {
            when {
                element.hasAttr("style") -> {
                    element.attr("style", element.attr("style").replace(CSS_COLOR.toRegex(), ""))
                }
                element.tagName() == "style" -> {
                    element.html(cleanStyleTag(element.html()))
                }
            }
        }
    }

    companion object {
        private const val TAG = "Timeline"

        private val TIMECODE_LINK_REGEX: Pattern = Pattern.compile("podcini://timecode/(\\d+)")
        private const val TIMECODE_LINK = "<a class=\"timecode\" href=\"podcini://timecode/%d\">%s</a>"
        private val TIMECODE_REGEX: Pattern = Pattern.compile("\\b((\\d+):)?(\\d+):(\\d{2})\\b")
        private val LINE_BREAK_REGEX: Pattern = Pattern.compile("<br */?>")
        private const val CSS_COLOR = "(?<=(\\s|;|^))color\\s*:([^;])*;"
        private const val CSS_COMMENT = "/\\*.*?\\*/"

        /**
         * Returns true if the given link is a timecode link.
         */
        @JvmStatic
        fun isTimecodeLink(link: String?): Boolean {
            return link != null && link.matches(TIMECODE_LINK_REGEX.pattern().toRegex())
        }

        /**
         * Returns the time in milliseconds that is attached to this link or -1
         * if the link is no valid timecode link.
         */
        @JvmStatic
        fun getTimecodeLinkTime(link: String?): Int {
            if (isTimecodeLink(link)) {
                val m = TIMECODE_LINK_REGEX.matcher(link!!)

                try {
                    if (m.find()) {
                        return m.group(1)?.toInt()?:0
                    }
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                }
            }
            return -1
        }

        @JvmStatic
        fun cleanStyleTag(oldCss: String): String {
            return oldCss.replace(CSS_COMMENT.toRegex(), "").replace(CSS_COLOR.toRegex(), "")
        }
    }
}
