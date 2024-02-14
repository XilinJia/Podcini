package de.danoeh.antennapod.parser.feed.element

import de.danoeh.antennapod.parser.feed.namespace.Namespace

/** Defines a XML Element that is pushed on the tagstack  */
open class SyndElement(@JvmField val name: String, val namespace: Namespace)
