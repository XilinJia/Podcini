package de.danoeh.antennapod.core.export.opml

import de.danoeh.antennapod.core.export.CommonSymbols

/** Contains symbols for reading and writing OPML documents.  */
internal object OpmlSymbols : CommonSymbols() {
    const val OPML: String = "opml"
    const val OUTLINE: String = "outline"
    const val TEXT: String = "text"
    const val XMLURL: String = "xmlUrl"
    const val HTMLURL: String = "htmlUrl"
    const val TYPE: String = "type"
    const val VERSION: String = "version"
    const val DATE_CREATED: String = "dateCreated"
}
