package ac.mdiq.podcini.feed.parser.element

import ac.mdiq.podcini.feed.parser.namespace.Namespace

/** Defines a XML Element that is pushed on the tagstack  */
open class SyndElement(@JvmField val name: String, val namespace: Namespace)
