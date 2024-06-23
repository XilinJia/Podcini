package ac.mdiq.podcini.service.playback

import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.VolumeAdaptionSetting
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

class VolumeUpdaterTest {
    private var mediaPlayer: MediaPlayerBase? = null

    @Before
    fun setUp() {
        mediaPlayer = Mockito.mock(MediaPlayerBase::class.java)
    }

    @Test
    fun noChangeIfNoFeedMediaPlaying() {
        val volumeUpdater = PlaybackService.VolumeUpdater()

        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.PAUSED)

        val noFeedMedia = Mockito.mock(Playable::class.java)
        Mockito.`when`(curMedia).thenReturn(noFeedMedia)

        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.OFF)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun noChangeIfPlayerStatusIsError() {
        val volumeUpdater = PlaybackService.VolumeUpdater()

        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.ERROR)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(curMedia).thenReturn(feedMedia)

        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.OFF)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun noChangeIfPlayerStatusIsIndeterminate() {
        val volumeUpdater = PlaybackService.VolumeUpdater()

        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.INDETERMINATE)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(curMedia).thenReturn(feedMedia)

        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.OFF)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun noChangeIfPlayerStatusIsStopped() {
        val volumeUpdater = PlaybackService.VolumeUpdater()

        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.STOPPED)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(curMedia).thenReturn(feedMedia)

        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.OFF)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun noChangeIfPlayableIsNoItemOfAffectedFeed() {
        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.PLAYING)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(curMedia).thenReturn(feedMedia)
        Mockito.`when`(feedMedia.episode?.feed?.id).thenReturn(FEED_ID + 1)

        val volumeUpdater = PlaybackService.VolumeUpdater()
        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.OFF)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsPaused() {
        val volumeUpdater = PlaybackService.VolumeUpdater()

        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.PAUSED)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(curMedia).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.episode!!.feed!!.preferences!!

        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsPrepared() {
        val volumeUpdater = PlaybackService.VolumeUpdater()

        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.PREPARED)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(curMedia).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.episode!!.feed!!.preferences!!

        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsInitializing() {
        val volumeUpdater = PlaybackService.VolumeUpdater()

        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.INITIALIZING)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(curMedia).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.episode!!.feed!!.preferences!!

        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsPreparing() {
        val volumeUpdater = PlaybackService.VolumeUpdater()

        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.PREPARING)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(curMedia).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.episode!!.feed!!.preferences!!

        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsSeeking() {
        val volumeUpdater = PlaybackService.VolumeUpdater()

        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.SEEKING)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(curMedia).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.episode!!.feed!!.preferences!!

        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesAndForcesVolumeChangeForLoadedFeedMediaIfPlayerStatusIsPlaying() {
        val volumeUpdater = PlaybackService.VolumeUpdater()

        Mockito.`when`(MediaPlayerBase.status).thenReturn(PlayerStatus.PLAYING)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(curMedia).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.episode!!.feed!!.preferences!!

        volumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.HEAVY_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.HEAVY_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.times(1))?.pause(false, false)
        Mockito.verify(mediaPlayer, Mockito.times(1))?.resume()
    }

    private fun mockFeedMedia(): EpisodeMedia {
        val episodeMedia = Mockito.mock(EpisodeMedia::class.java)
        val episode = Mockito.mock(Episode::class.java)
        val feed = Mockito.mock(Feed::class.java)
        val feedPreferences = Mockito.mock(FeedPreferences::class.java)

        Mockito.`when`(episodeMedia.episode).thenReturn(episode)
        Mockito.`when`(episode.feed).thenReturn(feed)
        Mockito.`when`(feed.id).thenReturn(FEED_ID)
        Mockito.`when`(feed.preferences).thenReturn(feedPreferences)
        return episodeMedia
    }

    companion object {
        private const val FEED_ID: Long = 42
    }
}
