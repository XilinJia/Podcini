package ac.mdiq.podcini.feed

import ac.mdiq.podcini.R

enum class SubscriptionsFilterGroup(vararg values: ItemProperties) {
    COUNTER_GREATER_ZERO(ItemProperties(R.string.subscriptions_counter_greater_zero, "counter_greater_zero")),
    AUTO_DOWNLOAD(ItemProperties(R.string.auto_downloaded, "enabled_auto_download"), ItemProperties(R.string.not_auto_downloaded, "disabled_auto_download")),
    UPDATED(ItemProperties(R.string.kept_updated, "enabled_updates"), ItemProperties(R.string.not_kept_updated, "disabled_updates")),
    NEW_EPISODE_NOTIFICATION(ItemProperties(R.string.new_episode_notification_enabled, "episode_notification_enabled"), ItemProperties(R.string.new_episode_notification_disabled, "episode_notification_disabled"));

    @JvmField
    val values: Array<ItemProperties>

    init {
        this.values = values as Array<ItemProperties>
    }

    class ItemProperties(@JvmField val displayName: Int, @JvmField val filterId: String)
}
