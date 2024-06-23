package ac.mdiq.podcini.net.feed.parser.namespace

import ac.mdiq.podcini.net.feed.parser.HandlerState
import ac.mdiq.podcini.net.feed.parser.element.SyndElement
import org.xml.sax.Attributes

abstract class Namespace {
    /** Called by a Feedhandler when in startElement and it detects a namespace element
     * @return The SyndElement to push onto the stack
     */
    abstract fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement

    /** Called by a Feedhandler when in endElement and it detects a namespace element
     */
    abstract fun handleElementEnd(localName: String, state: HandlerState)

    /**
     * Trims all whitespace from beginning and ending of a String. {[String.trim]} only trims spaces.
     */
    fun trimAllWhitespace(string: String): String {
        return string.replace("(^\\s*)|(\\s*$)".toRegex(), "")
    }

}
