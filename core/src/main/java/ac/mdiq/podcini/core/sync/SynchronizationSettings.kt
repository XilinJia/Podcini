package ac.mdiq.podcini.core.sync

import android.content.Context
import android.content.SharedPreferences
import ac.mdiq.podcini.core.ClientConfig

object SynchronizationSettings {
    const val LAST_SYNC_ATTEMPT_TIMESTAMP: String = "last_sync_attempt_timestamp"
    private const val NAME = "synchronization"
    private const val SELECTED_SYNC_PROVIDER = "selected_sync_provider"
    private const val LAST_SYNC_ATTEMPT_SUCCESS = "last_sync_attempt_success"
    private const val LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP = "last_episode_actions_sync_timestamp"
    private const val LAST_SUBSCRIPTION_SYNC_TIMESTAMP = "last_sync_timestamp"

    val isProviderConnected: Boolean
        get() = selectedSyncProviderKey != null

    fun resetTimestamps() {
        sharedPreferences.edit()
            .putLong(LAST_SUBSCRIPTION_SYNC_TIMESTAMP, 0)
            .putLong(LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP, 0)
            .putLong(LAST_SYNC_ATTEMPT_TIMESTAMP, 0)
            .apply()
    }

    val isLastSyncSuccessful: Boolean
        get() = sharedPreferences.getBoolean(LAST_SYNC_ATTEMPT_SUCCESS, false)

    val lastSyncAttempt: Long
        get() = sharedPreferences.getLong(LAST_SYNC_ATTEMPT_TIMESTAMP, 0)

    fun setSelectedSyncProvider(provider: SynchronizationProviderViewData?) {
        sharedPreferences
            .edit()
            .putString(SELECTED_SYNC_PROVIDER, provider?.identifier)
            .apply()
    }

    val selectedSyncProviderKey: String?
        get() = sharedPreferences.getString(SELECTED_SYNC_PROVIDER, null)

    fun updateLastSynchronizationAttempt() {
        sharedPreferences.edit()
            .putLong(LAST_SYNC_ATTEMPT_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun setLastSynchronizationAttemptSuccess(isSuccess: Boolean) {
        sharedPreferences.edit()
            .putBoolean(LAST_SYNC_ATTEMPT_SUCCESS, isSuccess)
            .apply()
    }

    val lastSubscriptionSynchronizationTimestamp: Long
        get() = sharedPreferences.getLong(LAST_SUBSCRIPTION_SYNC_TIMESTAMP, 0)

    fun setLastSubscriptionSynchronizationAttemptTimestamp(newTimeStamp: Long) {
        sharedPreferences.edit()
            .putLong(LAST_SUBSCRIPTION_SYNC_TIMESTAMP, newTimeStamp).apply()
    }

    val lastEpisodeActionSynchronizationTimestamp: Long
        get() = sharedPreferences
            .getLong(LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP, 0)

    fun setLastEpisodeActionSynchronizationAttemptTimestamp(timestamp: Long) {
        sharedPreferences.edit()
            .putLong(LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP, timestamp).apply()
    }

    private val sharedPreferences: SharedPreferences
        get() = ClientConfig.applicationCallbacks!!.getApplicationInstance()!!
            .getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
