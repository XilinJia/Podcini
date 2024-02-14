package de.danoeh.antennapod.core.storage

import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences.episodeCleanupValue
import de.danoeh.antennapod.storage.preferences.UserPreferences.isEnableAutodownload

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
