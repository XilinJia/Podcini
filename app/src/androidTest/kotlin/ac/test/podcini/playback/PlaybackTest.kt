package de.test.podcini.playback

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.ServiceStatusHandler
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.receiver.MediaButtonReceiver.Companion.createIntent
import ac.mdiq.podcini.storage.database.Episodes.getEpisode
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Queues.clearQueue
import ac.mdiq.podcini.storage.database.Queues.getInQueueEpisodeIds
import ac.mdiq.podcini.storage.model.EpisodeFilter.Companion.unfiltered
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.ui.activity.MainActivity
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import de.test.podcini.EspressoTestUtils
import de.test.podcini.IgnoreOnCi
import de.test.podcini.ui.UITestUtils
import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility
import org.hamcrest.Matchers
import org.junit.*
import java.util.concurrent.TimeUnit

/**
 * Test cases for starting and ending playback from the MainActivity and AudioPlayerActivity.
 */
@LargeTest
@IgnoreOnCi
class PlaybackTest {
    @get:Rule
    var activityTestRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java, false, false)

    private var uiTestUtils: UITestUtils? = null
    protected lateinit var context: Context
    private var controller: ServiceStatusHandler? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()

        uiTestUtils = UITestUtils(context)
        uiTestUtils!!.setup()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        activityTestRule.finishActivity()
        EspressoTestUtils.tryKillPlaybackService()
        uiTestUtils!!.tearDown()
        if (controller != null) {
            controller!!.release()
        }
    }

    private fun setupPlaybackController() {
        controller = object : ServiceStatusHandler(activityTestRule.activity) {
            override fun loadMediaInfo() {
                // Do nothing
            }
        }
        controller?.init()
    }

    @Test
    @Throws(Exception::class)
    fun testContinousPlaybackOffMultipleEpisodes() {
        setContinuousPlaybackPreference(false)
        uiTestUtils!!.addLocalFeedData(true)
        activityTestRule.launchActivity(Intent())
        setupPlaybackController()
        playFromQueue(0)
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until { MediaPlayerBase.status == PlayerStatus.INITIALIZED }
    }

    @Test
    @Throws(Exception::class)
    fun testContinuousPlaybackOnMultipleEpisodes() {
        setContinuousPlaybackPreference(true)
        uiTestUtils!!.addLocalFeedData(true)
        activityTestRule.launchActivity(Intent())

        val queue = curQueue.episodes
        val first = queue[0]
        val second = queue[1]

        playFromQueue(0)
//        Awaitility.await().atMost(2, TimeUnit.SECONDS).until { first.media!!.id == currentlyPlayingFeedMediaId }
//        Awaitility.await().atMost(6, TimeUnit.SECONDS).until { second.media!!.id == currentlyPlayingFeedMediaId }
    }


    @Test
    @Throws(Exception::class)
    fun testReplayEpisodeContinuousPlaybackOn() {
        replayEpisodeCheck(true)
    }

    @Test
    @Throws(Exception::class)
    fun testReplayEpisodeContinuousPlaybackOff() {
        replayEpisodeCheck(false)
    }

    @Test
    @Throws(Exception::class)
    fun testSmartMarkAsPlayed_Skip_Average() {
        doTestSmartMarkAsPlayed_Skip_ForEpisode(0)
    }

    @Test
    @Throws(Exception::class)
    fun testSmartMarkAsPlayed_Skip_LastEpisodeInQueue() {
        doTestSmartMarkAsPlayed_Skip_ForEpisode(-1)
    }

    @Test
    @Throws(Exception::class)
    fun testSmartMarkAsPlayed_Pause_WontAffectItem() {
        setSmartMarkAsPlayedPreference(60)

        uiTestUtils!!.addLocalFeedData(true)
        activityTestRule.launchActivity(Intent())
        setupPlaybackController()

        val fiIdx = 0
        val feedItem = curQueue.episodes[fiIdx]

        playFromQueue(fiIdx)

        // let playback run a bit then pause
        Awaitility.await()
            .atMost(1000, TimeUnit.MILLISECONDS)
            .until { PlayerStatus.PLAYING == MediaPlayerBase.status }
        pauseEpisode()
        Awaitility.await()
            .atMost(1000, TimeUnit.MILLISECONDS)
            .until { PlayerStatus.PAUSED == MediaPlayerBase.status }

        Assert.assertThat("Ensure even with smart mark as play, after pause, the item remains in the queue.",
            curQueue.episodes, Matchers.hasItems(feedItem))
        Assert.assertThat("Ensure even with smart mark as play, after pause, the item played status remains false.",
            getEpisode(feedItem.id)!!.isPlayed(), Matchers.`is`(false))
    }

    @Test
    @Throws(Exception::class)
    fun testStartLocal() {
        uiTestUtils!!.addLocalFeedData(true)
        activityTestRule.launchActivity(Intent())
        runBlocking {  clearQueue().join() }
        startLocalPlayback()
    }

    @Test
    @Throws(Exception::class)
    fun testPlayingItemAddsToQueue() {
        uiTestUtils!!.addLocalFeedData(true)
        activityTestRule.launchActivity(Intent())
        runBlocking { clearQueue().join() }
        val queue = curQueue.episodes
        Assert.assertEquals(0, queue.size.toLong())
        startLocalPlayback()
        Awaitility.await().atMost(1, TimeUnit.SECONDS).until { 1 == curQueue.episodes.size }
    }

    @Test
    @Throws(Exception::class)
    fun testContinousPlaybackOffSingleEpisode() {
        setContinuousPlaybackPreference(false)
        uiTestUtils!!.addLocalFeedData(true)
        activityTestRule.launchActivity(Intent())
        runBlocking { clearQueue().join() }
        startLocalPlayback()
    }

    protected fun setContinuousPlaybackPreference(value: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(UserPreferences.Prefs.prefFollowQueue.name, value).commit()
    }

    protected fun setSkipKeepsEpisodePreference(value: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(UserPreferences.Prefs.prefSkipKeepsEpisode.name, value).commit()
    }

    protected fun setSmartMarkAsPlayedPreference(smartMarkAsPlayedSecs: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(UserPreferences.Prefs.prefSmartMarkAsPlayedSecs.name,
            smartMarkAsPlayedSecs.toString(10))
            .commit()
    }

    private fun skipEpisode() {
        context.sendBroadcast(createIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
    }

    protected fun pauseEpisode() {
        context.sendBroadcast(createIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    protected fun startLocalPlayback() {
        EspressoTestUtils.openNavDrawer()
        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(R.string.episodes_label)).perform(ViewActions.click())

        val episodes = getEpisodes(0, 10, unfiltered(), EpisodeSortOrder.DATE_NEW_OLD)
        val allEpisodesMatcher = Matchers.allOf(ViewMatchers.withId(R.id.recyclerView),
            ViewMatchers.isDisplayed(),
            ViewMatchers.hasMinimumChildCount(2))
        Espresso.onView(ViewMatchers.isRoot()).perform(EspressoTestUtils.waitForView(allEpisodesMatcher, 1000))
        Espresso.onView(allEpisodesMatcher).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
            0,
            EspressoTestUtils.clickChildViewWithId(R.id.secondaryActionButton)))

        val media = episodes[0].media
//        Awaitility.await().atMost(1, TimeUnit.SECONDS).until { media!!.id == currentlyPlayingFeedMediaId }
    }

    /**
     *
     * @param itemIdx The 0-based index of the episode to be played in the queue.
     */
    protected fun playFromQueue(itemIdx: Int) {
        val queue = curQueue.episodes

        val queueMatcher = Matchers.allOf(ViewMatchers.withId(R.id.recyclerView),
            ViewMatchers.isDisplayed(),
            ViewMatchers.hasMinimumChildCount(2))
        Espresso.onView(ViewMatchers.isRoot()).perform(EspressoTestUtils.waitForView(queueMatcher, 1000))
        Espresso.onView(queueMatcher).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
            itemIdx,
            EspressoTestUtils.clickChildViewWithId(R.id.secondaryActionButton)))

        val media = queue[itemIdx].media
//        Awaitility.await().atMost(1, TimeUnit.SECONDS).until { media!!.id == currentlyPlayingFeedMediaId }
    }

    /**
     * Check if an episode can be played twice without problems.
     */
    @Throws(Exception::class)
    protected fun replayEpisodeCheck(followQueue: Boolean) {
        setContinuousPlaybackPreference(followQueue)
        uiTestUtils!!.addLocalFeedData(true)
        runBlocking { clearQueue().join() }
        activityTestRule.launchActivity(Intent())
        val episodes = getEpisodes(0, 10, unfiltered(), EpisodeSortOrder.DATE_NEW_OLD)

        startLocalPlayback()
        val media = episodes[0].media
//        Awaitility.await().atMost(1, TimeUnit.SECONDS).until { media!!.id == currentlyPlayingFeedMediaId }
//
//        Awaitility.await().atMost(5, TimeUnit.SECONDS).until { media!!.id != currentlyPlayingFeedMediaId }
//
//        startLocalPlayback()
//
//        Awaitility.await().atMost(1, TimeUnit.SECONDS).until { media!!.id == currentlyPlayingFeedMediaId }
    }

    @Throws(Exception::class)
    protected fun doTestSmartMarkAsPlayed_Skip_ForEpisode(itemIdxNegAllowed: Int) {
        setSmartMarkAsPlayedPreference(60)
        // ensure when an episode is skipped, it is removed due to smart as played
        setSkipKeepsEpisodePreference(false)
        uiTestUtils!!.setMediaFileName("30sec.mp3")
        uiTestUtils!!.addLocalFeedData(true)

        val queue = getInQueueEpisodeIds().toList()
        val fiIdx = if (itemIdxNegAllowed >= 0) {
            itemIdxNegAllowed
        } else { // negative index: count from the end, with -1 being the last one, etc.
            queue.size + itemIdxNegAllowed
        }
        val feedItemId = queue.get(fiIdx)
//        queue.removeIndex(fiIdx)
        Assert.assertFalse(queue.contains(feedItemId)) // Verify that episode is in queue only once

        activityTestRule.launchActivity(Intent())
        playFromQueue(fiIdx)

        skipEpisode()

        //  assert item no longer in queue (needs to wait till skip is asynchronously processed)
        Awaitility.await()
            .atMost(5000, TimeUnit.MILLISECONDS)
            .until { !getInQueueEpisodeIds().contains(feedItemId) }
        Assert.assertTrue(getEpisode(feedItemId)!!.isPlayed())
    }
}
