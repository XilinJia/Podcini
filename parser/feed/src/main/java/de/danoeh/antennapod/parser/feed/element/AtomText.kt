package de.danoeh.antennapod.parser.feed.element

import androidx.core.text.HtmlCompat
import de.danoeh.antennapod.parser.feed.namespace.Namespace

/** Represents Atom Element which contains text (content, title, summary).  */
class AtomText(name: String?, namespace: Namespace?, private val type: String?) : SyndElement(
    name!!, namespace!!) {
    private var content: String? = null

    val processedContent: String?
        /** Processes the content according to the type and returns it.  */
        get() = if (type == null) {
            content
        } else if (type == TYPE_HTML) {
            HtmlCompat.fromHtml(content!!, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        } else if (type == TYPE_XHTML) {
            content
        } else { // Handle as text by default
            content
        }

    fun setContent(content: String?) {
        this.content = content
    }

    companion object {
        const val TYPE_HTML: String = "html"
        private const val TYPE_XHTML = "xhtml"
    }
}
