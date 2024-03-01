package ac.mdiq.podcini.storage.export.opml

/** Represents a single feed in an OPML file.  */
class OpmlElement {
    @JvmField
    var text: String? = null
    var xmlUrl: String? = null
    var htmlUrl: String? = null
    var type: String? = null
}
