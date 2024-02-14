package ac.mdiq.podvinci.core.storage

import ac.mdiq.podvinci.storage.preferences.UserPreferences
import ac.mdiq.podvinci.storage.preferences.UserPreferences.episodeCleanupValue
import ac.mdiq.podvinci.storage.preferences.UserPreferences.isEnableAutodownload

object EpisodeCleanupAlgorithmFactory {
    @JvmStatic
    fun build(): EpisodeCleanupAlgorithm {
        if (!isEnableAutodownload) {
            return APNullCleanupAlgorithm()
        }
        return when (val cleanupValue = episodeCleanupValue) {
            UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE -> ExceptFavoriteCleanupAlgorithm()
            UserPreferences.EPISODE_CLEANUP_QUEUE -> APQueueCleanupAlgorithm()
            UserPreferences.EPISODE_CLEANUP_NULL -> APNullCleanupAlgorithm()
            else -> APCleanupAlgorithm(cleanupValue)
        }
    }
}
