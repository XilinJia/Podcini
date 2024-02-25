package de.test.podcini.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.currentlyPlayingFeedMediaId
import ac.mdiq.podcini.storage.AutomaticDownloadAlgorithm
import ac.mdiq.podcini.storage.DBReader.getQueue
import ac.mdiq.podcini.storage.DBTasks.setDownloadAlgorithm
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.preferences.UserPreferences.isAllowMobileStreaming
import ac.mdiq.podcini.preferences.UserPreferences.isFollowQueue
import de.test.podcini.EspressoTestUtils
import de.test.podcini.ui.UITestUtils
import org.awaitility.Awaitility
import org.awaitility.core.ConditionTimeoutException
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AutoDownloadTest {
    private var context: Context? = null
    private var stubFeedsServer: UITestUtils? = null
    private var stubDownloadAlgorithm: StubDownloadAlgorithm? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        stubFeedsServer = UITestUtils(context!!)
        stubFeedsServer!!.setup()

        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()
        isAllowMobileStreaming = true

        // Setup: enable automatic download
        // it is not needed, as the actual automatic download is stubbed.
        stubDownloadAlgorithm = StubDownloadAlgorithm()
        setDownloadAlgorithm(stubDownloadAlgorithm!!)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        setDownloadAlgorithm(AutomaticDownloadAlgorithm())
        EspressoTestUtils.tryKillPlaybackService()
        stubFeedsServer!!.tearDown()
    }

    /**
     * A cross-functional test, ensuring playback's behavior works with Auto Download in boundary condition.
     *
     * Scenario:
     * - For setting enqueue location AFTER_CURRENTLY_PLAYING
     * - when playback of an episode is complete and the app advances to the next episode (continuous playback on)
     * - when automatic download kicks in,
     * - ensure the next episode is the current playing one, needed for AFTER_CURRENTLY_PLAYING enqueue location.
     */
    @Test
    @Throws(Exception::class)
    fun downloadsEnqueuedToAfterCurrent_CurrentAdvancedToNextOnPlaybackComplete() {
        isFollowQueue = true // continuous playback

        // Setup: feeds and queue
        // downloads 3 of them, leave some in new state (auto-downloadable)
        stubFeedsServer!!.addLocalFeedData(false)
        val queue = getQueue()
        Assert.assertTrue(queue.size > 1)
        val item0 = queue[0]
        val item1 = queue[1]

        // Actual test
        // Play the first one in the queue
        playEpisode(item0)

        try {
            // when playback is complete, advances to the next one, and auto download kicks in,
            // ensure that currently playing has been advanced to the next one by this point.
            Awaitility.await("advanced to the next episode")
                .atMost(6000, TimeUnit.MILLISECONDS) // the test mp3 media is 3-second long. twice should be enough
                .until { item1.media!!.id == stubDownloadAlgorithm?.currentlyPlayingAtDownload }
        } catch (cte: ConditionTimeoutException) {
            val actual: Long = stubDownloadAlgorithm?.currentlyPlayingAtDownload?:0
            Assert.fail("when auto download is triggered, the next episode should be playing: ("
                    + item1.id + ", " + item1.title + ") . "
                    + "Actual playing: (" + actual + ")"
            )
        }
    }

    private fun playEpisode(item: FeedItem) {
        val media = item.media
        PlaybackServiceStarter(context!!, media!!)
            .callEvenIfRunning(true)
            .start()
        Awaitility.await("episode is playing")
            .atMost(2000, TimeUnit.MILLISECONDS)
            .until { item.media!!.id == currentlyPlayingFeedMediaId }
    }

    private class StubDownloadAlgorithm : AutomaticDownloadAlgorithm() {
        var currentlyPlayingAtDownload: Long = -1
            private set

        override fun autoDownloadUndownloadedItems(context: Context): Runnable? {
            return Runnable {
                if (currentlyPlayingAtDownload == -1L) {
                    currentlyPlayingAtDownload = currentlyPlayingFeedMediaId
                } else {
                    throw AssertionError("Stub automatic download should be invoked once and only once")
                }
            }
        }
    }
}
