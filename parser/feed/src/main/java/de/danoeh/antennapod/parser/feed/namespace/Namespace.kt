package de.danoeh.antennapod.parser.feed.namespace

import de.danoeh.antennapod.parser.feed.HandlerState
import de.danoeh.antennapod.parser.feed.element.SyndElement
import org.xml.sax.Attributes

abstract class Namespace {
    /** Called by a Feedhandler when in startElement and it detects a namespace element
     * @return The SyndElement to push onto the stack
     */
    abstract fun handleElementStart(localName: String, state: HandlerState, attributes: Attributes): SyndElement

    /** Called by a Feedhandler when in endElement and it detects a namespace element
     */
    abstract fun handleElementEnd(localName: String, state: HandlerState)
}
