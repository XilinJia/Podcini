package ac.mdiq.podvinci.core.feed

import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

class FeedEvent(private val action: Action, @JvmField val feedId: Long) {
    enum class Action {
        FILTER_CHANGED,
        SORT_ORDER_CHANGED
    }

    override fun toString(): String {
        return ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("action", action)
            .append("feedId", feedId)
            .toString()
    }
}
