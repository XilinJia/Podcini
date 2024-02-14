package ac.mdiq.podvinci.net.sync.model

class SubscriptionChanges(val added: List<String>,
                          val removed: List<String>,
                          val timestamp: Long
) {
    override fun toString(): String {
        return ("SubscriptionChange [added=" + added.toString()
                + ", removed=" + removed.toString() + ", timestamp="
                + timestamp + "]")
    }
}
