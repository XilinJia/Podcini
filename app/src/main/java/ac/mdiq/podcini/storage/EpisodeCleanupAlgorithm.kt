package ac.mdiq.podcini.storage

import android.content.Context
import ac.mdiq.podcini.storage.DBReader.getTotalEpisodeCount
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.episodeCacheSize

abstract class EpisodeCleanupAlgorithm {
    /**
     * Deletes downloaded episodes that are no longer needed. What episodes are deleted and how many
     * of them depends on the implementation.
     *
     * @param context     Can be used for accessing the database
     * @param numToRemove An additional parameter. This parameter is either returned by getDefaultCleanupParameter
     * or getPerformCleanupParameter.
     * @return The number of episodes that were deleted.
     */
    protected abstract fun performCleanup(context: Context, numToRemove: Int): Int

    fun performCleanup(context: Context): Int {
        return performCleanup(context, getDefaultCleanupParameter())
    }

    protected abstract fun getDefaultCleanupParameter(): Int
    /**
         * Returns a parameter for performCleanup. The implementation of this interface should decide how much
         * space to free to satisfy the episode cache conditions. If the conditions are already satisfied, this
         * method should not have any effects.
         */


    /**
     * Cleans up just enough episodes to make room for the requested number
     *
     * @param context            Can be used for accessing the database
     * @param amountOfRoomNeeded the number of episodes we need space for
     * @return The number of epiosdes that were deleted
     */
    fun makeRoomForEpisodes(context: Context, amountOfRoomNeeded: Int): Int {
        return performCleanup(context, getNumEpisodesToCleanup(amountOfRoomNeeded))
    }

    /**
     * @return the number of episodes/items that *could* be cleaned up, if needed
     */
    abstract fun getReclaimableItems(): Int

    /**
     * @param amountOfRoomNeeded the number of episodes we want to download
     * @return the number of episodes to delete in order to make room
     */
    fun getNumEpisodesToCleanup(amountOfRoomNeeded: Int): Int {
        if (amountOfRoomNeeded >= 0
                && episodeCacheSize != UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED) {
            val downloadedEpisodes = getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.DOWNLOADED))
            if (downloadedEpisodes + amountOfRoomNeeded >= episodeCacheSize) {
                return (downloadedEpisodes + amountOfRoomNeeded
                        - episodeCacheSize)
            }
        }
        return 0
    }
}
