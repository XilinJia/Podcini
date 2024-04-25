package ac.mdiq.podcini.util.syndication

import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.util.regex.Pattern

/**
 * This class is based on `HtmlToPlainText` from jsoup's examples package.
 *
 * HTML to plain-text. This example program demonstrates the use of jsoup to convert HTML input to lightly-formatted
 * plain-text. That is divergent from the general goal of jsoup's .text() methods, which is to get clean data from a
 * scrape.
 *
 *
 * Note that this is a fairly simplistic formatter -- for real world use you'll want to embrace and extend.
 *
 *
 *
 * To invoke from the command line, assuming you've downloaded the jsoup jar to your current directory:
 *
 * `java -cp jsoup.jar org.jsoup.examples.HtmlToPlainText url [selector]`
 * where *url* is the URL to fetch, and *selector* is an optional CSS selector.
 *
 * @author Jonathan Hedley, jonathan@hedley.net
 * @author Podcini open source community
 */
class HtmlToPlainText {
    /**
     * Format an Element to plain-text
     * @param element the root element to format
     * @return formatted text
     */
    fun getPlainText(element: Element): String {
        val formatter = FormattingVisitor()
        // walk the DOM, and call .head() and .tail() for each node
        NodeTraversor.traverse(formatter, element)

        return formatter.toString()
    }

    // the formatting rules, implemented in a breadth-first DOM traverse
    private class FormattingVisitor : NodeVisitor {
        private val accum = StringBuilder() // holds the accumulated text

        // hit when the node is first seen
        override fun head(node: Node, depth: Int) {
            val name = node.nodeName()
            when {
                node is TextNode -> append(node.text()) // TextNodes carry all user-readable text in the DOM.
                name == "li" -> append("\n * ")
                name == "dt" -> append("  ")
                StringUtil.`in`(name, "p", "h1", "h2", "h3", "h4", "h5", "tr") -> append("\n")
            }
        }

        // hit when all of the node's children (if any) have been visited
        override fun tail(node: Node, depth: Int) {
            val name = node.nodeName()
            when {
                StringUtil.`in`(name, "br", "dd", "dt", "p", "h1", "h2", "h3", "h4", "h5") -> append("\n")
                name == "a" -> append(String.format(" <%s>", node.absUrl("href")))
            }
        }

        // appends text to the string builder with a simple word wrap method
        private fun append(text: String) {
            if (text == " " && (accum.isEmpty() || StringUtil.`in`(accum.substring(accum.length - 1), " ", "\n")))
                return  // don't accumulate long runs of empty spaces

            accum.append(text)
        }

        override fun toString(): String {
            return accum.toString()
        }
    }

    companion object {
        /**
         * Use this method to strip off HTML encoding from given text.
         * Replaces bullet points with *, ignores colors/bold/...
         *
         * @param str String with any encoding
         * @return Human readable text with minimal HTML formatting
         */
        fun getPlainText(str: String): String {
            var str = str
            when {
                str.isNotEmpty() && isHtml(str) -> {
                    val formatter = HtmlToPlainText()
                    val feedDescription = Jsoup.parse(str)
                    str = StringUtils.trim(formatter.getPlainText(feedDescription))
                }
                str.isEmpty() -> str = ""
            }

            return str
        }

        /**
         * Use this method to determine if a given text has any HTML tag
         *
         * @param str String to be tested for presence of HTML content
         * @return **True** if text contains any HTML tags<br></br>**False** is no HTML tag is found
         */
        private fun isHtml(str: String?): Boolean {
            val htmlTagPattern = "<(\"[^\"]*\"|'[^']*'|[^'\">])*>"
            return Pattern.compile(htmlTagPattern).matcher(str.toString()).find()
        }
    }
}
