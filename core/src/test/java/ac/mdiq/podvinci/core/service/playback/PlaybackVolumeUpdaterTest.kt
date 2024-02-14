package ac.mdiq.podvinci.core.service.playback

import ac.mdiq.podvinci.model.feed.*
import ac.mdiq.podvinci.model.playback.Playable
import ac.mdiq.podvinci.playback.base.PlaybackServiceMediaPlayer
import ac.mdiq.podvinci.playback.base.PlayerStatus
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

class PlaybackVolumeUpdaterTest {
    private var mediaPlayer: PlaybackServiceMediaPlayer? = null

    @Before
    fun setUp() {
        mediaPlayer = Mockito.mock(PlaybackServiceMediaPlayer::class.java)
    }

    @Test
    fun noChangeIfNoFeedMediaPlaying() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.PAUSED)

        val noFeedMedia = Mockito.mock(Playable::class.java)
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(noFeedMedia)

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.OFF)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun noChangeIfPlayerStatusIsError() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.ERROR)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(feedMedia)

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.OFF)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun noChangeIfPlayerStatusIsIndeterminate() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.INDETERMINATE)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(feedMedia)

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.OFF)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun noChangeIfPlayerStatusIsStopped() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.STOPPED)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(feedMedia)

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.OFF)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun noChangeIfPlayableIsNoItemOfAffectedFeed() {
        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.PLAYING)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(feedMedia)
        Mockito.`when`(feedMedia.getItem()?.feed?.id).thenReturn(FEED_ID + 1)

        val playbackVolumeUpdater = PlaybackVolumeUpdater()
        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.OFF)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsPaused() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.PAUSED)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.getItem()!!.feed!!.preferences!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsPrepared() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.PREPARED)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.getItem()!!.feed!!.preferences!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsInitializing() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.INITIALIZING)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.getItem()!!.feed!!.preferences!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsPreparing() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.PREPARING)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.getItem()!!.feed!!.preferences!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsSeeking() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.SEEKING)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.getItem()!!.feed!!.preferences!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.LIGHT_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.never())?.pause(ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean())
        Mockito.verify(mediaPlayer, Mockito.never())?.resume()
    }

    @Test
    fun updatesPreferencesAndForcesVolumeChangeForLoadedFeedMediaIfPlayerStatusIsPlaying() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        Mockito.`when`(mediaPlayer!!.playerStatus).thenReturn(PlayerStatus.PLAYING)

        val feedMedia = mockFeedMedia()
        Mockito.`when`(mediaPlayer!!.getPlayable()).thenReturn(feedMedia)
        val feedPreferences: FeedPreferences = feedMedia.getItem()!!.feed!!.preferences!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, FEED_ID, VolumeAdaptionSetting.HEAVY_REDUCTION)

        Mockito.verify(feedPreferences, Mockito.times(1))
            .volumeAdaptionSetting = (VolumeAdaptionSetting.HEAVY_REDUCTION)

        Mockito.verify(mediaPlayer, Mockito.times(1))?.pause(false, false)
        Mockito.verify(mediaPlayer, Mockito.times(1))?.resume()
    }

    private fun mockFeedMedia(): FeedMedia {
        val feedMedia = Mockito.mock(FeedMedia::class.java)
        val feedItem = Mockito.mock(FeedItem::class.java)
        val feed = Mockito.mock(Feed::class.java)
        val feedPreferences = Mockito.mock(FeedPreferences::class.java)

        Mockito.`when`(feedMedia.getItem()).thenReturn(feedItem)
        Mockito.`when`(feedItem.feed).thenReturn(feed)
        Mockito.`when`(feed.id).thenReturn(FEED_ID)
        Mockito.`when`(feed.preferences).thenReturn(feedPreferences)
        return feedMedia
    }

    companion object {
        private const val FEED_ID: Long = 42
    }
}
