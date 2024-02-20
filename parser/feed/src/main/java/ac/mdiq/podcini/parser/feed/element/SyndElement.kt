package ac.mdiq.podcini.parser.feed.element

import ac.mdiq.podcini.parser.feed.namespace.Namespace

/** Defines a XML Element that is pushed on the tagstack  */
open class SyndElement(@JvmField val name: String, val namespace: Namespace)
