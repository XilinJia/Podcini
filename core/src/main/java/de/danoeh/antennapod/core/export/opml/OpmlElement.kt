package de.danoeh.antennapod.core.export.opml

/** Represents a single feed in an OPML file.  */
class OpmlElement {
    @kotlin.jvm.JvmField
    var text: String? = null
    var xmlUrl: String? = null
    var htmlUrl: String? = null
    var type: String? = null
}
