package ac.mdiq.podcini.net.sync.model

class SubscriptionChanges(val added: List<String>,
                          val removed: List<String>,
                          val timestamp: Long
) {
    override fun toString(): String {
        return ("SubscriptionChange [added=$added, removed=$removed, timestamp=$timestamp]")
    }
}
