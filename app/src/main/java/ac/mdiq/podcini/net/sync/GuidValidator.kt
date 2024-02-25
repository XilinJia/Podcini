package ac.mdiq.podcini.net.sync

object GuidValidator {
    @JvmStatic
    fun isValidGuid(guid: String?): Boolean {
        return (guid != null && !guid.trim { it <= ' ' }.isEmpty()
                && guid != "null")
    }
}

