package ac.mdiq.podcini.storage.model.feed

import android.text.TextUtils

class SubscriptionsFilter(private val properties: Array<String>) {
    private var showIfCounterGreaterZero = false

    private var showAutoDownloadEnabled = false
    private var showAutoDownloadDisabled = false

    private var showUpdatedEnabled = false
    private var showUpdatedDisabled = false

    private var showEpisodeNotificationEnabled = false
    private var showEpisodeNotificationDisabled = false

    constructor(properties: String?) : this(TextUtils.split(properties, divider))


    init {
        for (property in properties) {
            // see R.arrays.feed_filter_values
            when (property) {
                "counter_greater_zero" -> showIfCounterGreaterZero = true
                "enabled_auto_download" -> showAutoDownloadEnabled = true
                "disabled_auto_download" -> showAutoDownloadDisabled = true
                "enabled_updates" -> showUpdatedEnabled = true
                "disabled_updates" -> showUpdatedDisabled = true
                "episode_notification_enabled" -> showEpisodeNotificationEnabled = true
                "episode_notification_disabled" -> showEpisodeNotificationDisabled = true
                else -> {}
            }
        }
    }

    val isEnabled: Boolean
        get() = properties.isNotEmpty()

    /**
     * Run a list of feed items through the filter.
     */
    fun filter(items: List<Feed>, feedCounters: Map<Long?, Int>): List<Feed> {
        if (properties.isEmpty()) {
            return items
        }

        val result: MutableList<Feed> = ArrayList()

        for (item in items) {
            val itemPreferences = item.preferences

            // If the item does not meet a requirement, skip it.
            if (showAutoDownloadEnabled && itemPreferences?.autoDownload != true) {
                continue
            } else if (showAutoDownloadDisabled && itemPreferences?.autoDownload == true) {
                continue
            }

            if (showUpdatedEnabled && itemPreferences?.keepUpdated != true) {
                continue
            } else if (showUpdatedDisabled && itemPreferences?.keepUpdated == true) {
                continue
            }

            if (showEpisodeNotificationEnabled && itemPreferences?.showEpisodeNotification != true) {
                continue
            } else if (showEpisodeNotificationDisabled && itemPreferences?.showEpisodeNotification == true) {
                continue
            }

            // If the item reaches here, it meets all criteria (except counter > 0)
            result.add(item)
        }

        if (showIfCounterGreaterZero) {
            for (i in result.indices.reversed()) {
                if (!feedCounters.containsKey(result[i].id) || feedCounters[result[i].id]!! <= 0) {
                    result.removeAt(i)
                }
            }
        }

        return result
    }

    val values: Array<String>
        get() = properties.clone()

    fun serialize(): String {
        return TextUtils.join(divider, values)
    }

    companion object {
        private const val divider = ","
    }
}
