package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.Prefs.prefEpisodeCover
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.util.Logd

/**
 * Utility class to use the appropriate image resource based on [UserPreferences].
 */
object ImageResourceUtils {
    /**
     * @return `true` if episodes should use their own cover, `false`  otherwise
     */
    val useEpisodeCoverSetting: Boolean
        get() = appPrefs.getBoolean(UserPreferences.Prefs.prefEpisodeCover.name, true)

    /**
     * returns the image location, does prefer the episode cover if available and enabled in settings.
     */
    @JvmStatic
    fun getEpisodeListImageLocation(playable: Playable): String? {
        return if (useEpisodeCoverSetting) playable.getImageLocation()
        else getFallbackImageLocation(playable)
    }

    /**
     * returns the image location, does prefer the episode cover if available and enabled in settings.
     */
    @JvmStatic
    fun getEpisodeListImageLocation(episode: Episode): String? {
        Logd("ImageResourceUtils", "getEpisodeListImageLocation called")
        return if (useEpisodeCoverSetting) episode.imageLocation
        else getFallbackImageLocation(episode)
    }

    @JvmStatic
    fun getFallbackImageLocation(playable: Playable): String? {
        if (playable is EpisodeMedia) {
            val item = playable.episodeOrFetch()
            return item?.feed?.imageUrl
        } else return playable.getImageLocation()
    }

    @JvmStatic
    fun getFallbackImageLocation(episode: Episode): String? {
        return episode.feed?.imageUrl
    }
}
