package de.test.podcini.service.playback

import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerBase.MediaPlayerInfo
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.service.LocalMediaPlayer
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import de.test.podcini.EspressoTestUtils
import de.test.podcini.util.service.download.HTTPBin
import junit.framework.AssertionFailedError
import org.apache.commons.io.IOUtils
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

/**
 * Test class for LocalPSMP
 */
@MediumTest
class MediaPlayerBaseTest {
    private var PLAYABLE_LOCAL_URL: String? = null
    private var httpServer: HTTPBin? = null
    private var playableFileUrl: String? = null

    @Volatile
    private var assertionError: AssertionFailedError? = null

    @After
    @UiThreadTest
    @Throws(Exception::class)
    fun tearDown() {
//        deleteDatabase()
        httpServer!!.stop()
    }

    @Before
    @UiThreadTest
    @Throws(Exception::class)
    fun setUp() {
        assertionError = null
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        httpServer = HTTPBin()
        httpServer!!.start()
        playableFileUrl = httpServer!!.baseUrl + "/files/0"

        var cacheDir = context.getExternalFilesDir("testFiles")
        if (cacheDir == null) cacheDir = context.getExternalFilesDir("testFiles")
        val dest = File(cacheDir, PLAYABLE_DEST_URL)

        Assert.assertNotNull(cacheDir)
        Assert.assertTrue(cacheDir!!.canWrite())
        Assert.assertTrue(cacheDir.canRead())
        if (!dest.exists()) {
            val i = InstrumentationRegistry.getInstrumentation().context.assets.open("3sec.mp3")
            val o: OutputStream = FileOutputStream(File(cacheDir, PLAYABLE_DEST_URL))
            IOUtils.copy(i, o)
            o.flush()
            o.close()
            i.close()
        }
        PLAYABLE_LOCAL_URL = dest.absolutePath
        Assert.assertEquals(0, httpServer!!.serveFile(dest).toLong())
    }

    private fun checkPSMPInfo(info: MediaPlayerInfo?) {
        try {
            when (info!!.playerStatus) {
                PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.PREPARED, PlayerStatus.PREPARING, PlayerStatus.INITIALIZED, PlayerStatus.INITIALIZING, PlayerStatus.SEEKING -> Assert.assertNotNull(
                    info.playable)
                PlayerStatus.STOPPED, PlayerStatus.ERROR -> Assert.assertNull(info.playable)
                else -> {}
            }
        } catch (e: AssertionFailedError) {
            if (assertionError == null) assertionError = e
        }
    }

    @Test
    @UiThreadTest
    fun testInit() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, DefaultMediaPlayerCallback())
        psmp.shutdown()
    }

    private fun writeTestPlayable(downloadUrl: String?, fileUrl: String?): Playable {
        val f = Feed(0, null, "f", "l", "d", null, null, null, null, "i", null, null, "l")
        val prefs = FeedPreferences(f.id, false, FeedPreferences.AutoDeleteAction.NEVER,
            VolumeAdaptionSetting.OFF, null, null)
        f.preferences = prefs
        f.episodes.clear()
        val i = Episode(0, "t", "i", "l", Date(), Episode.PlayState.UNPLAYED.code, f)
        f.episodes.add(i)
        val media = EpisodeMedia(0, i, 0, 0, 0, "audio/wav", fileUrl, downloadUrl, fileUrl != null, null, 0, 0)
        i.setMedia(media)
//        val adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(f)
//        Assert.assertTrue(media.id != 0L)
//        adapter.close()
        return media
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectStreamNoStartNoPrepare() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val countDownLatch = CountDownLatch(2)
        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                try {
                    checkPSMPInfo(newInfo)
                    check(newInfo!!.playerStatus != PlayerStatus.ERROR) { "MediaPlayer error" }
                    when (countDownLatch.count) {
                        0L -> {
                            Assert.fail()
                        }
                        2L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZING, newInfo.playerStatus)
                            countDownLatch.countDown()
                        }
                        else -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZED, newInfo.playerStatus)
                            countDownLatch.countDown()
                        }
                    }
                } catch (e: AssertionFailedError) {
                    if (assertionError == null) assertionError = e
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, null)
        psmp.playMediaObject(p, true, false, false, false)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res)

        Assert.assertSame(PlayerStatus.INITIALIZED, psmp.playerInfo.playerStatus)
        Assert.assertFalse(psmp.startWhenPrepared.get())
        callback.cancel()
        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectStreamStartNoPrepare() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val countDownLatch = CountDownLatch(2)
        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                try {
                    checkPSMPInfo(newInfo)
                    check(newInfo!!.playerStatus != PlayerStatus.ERROR) { "MediaPlayer error" }
                    when (countDownLatch.count) {
                        0L -> {
                            Assert.fail()
                        }
                        2L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZING, newInfo.playerStatus)
                            countDownLatch.countDown()
                        }
                        else -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZED, newInfo.playerStatus)
                            countDownLatch.countDown()
                        }
                    }
                } catch (e: AssertionFailedError) {
                    if (assertionError == null) assertionError = e
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, null)
        psmp.playMediaObject(p, true, true, false)

        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res)

        Assert.assertSame(PlayerStatus.INITIALIZED, psmp.playerInfo.playerStatus)
        Assert.assertTrue(psmp.startWhenPrepared.get())
        callback.cancel()
        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectStreamNoStartPrepare() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val countDownLatch = CountDownLatch(4)
        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                try {
                    checkPSMPInfo(newInfo)
                    check(newInfo!!.playerStatus != PlayerStatus.ERROR) { "MediaPlayer error" }
                    when (countDownLatch.count) {
                        0L -> {
                            Assert.fail()
                        }
                        4L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZING, newInfo.playerStatus)
                        }
                        3L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZED, newInfo.playerStatus)
                        }
                        2L -> {
                            Assert.assertEquals(PlayerStatus.PREPARING, newInfo.playerStatus)
                        }
                        1L -> {
                            Assert.assertEquals(PlayerStatus.PREPARED, newInfo.playerStatus)
                        }
                    }
                    countDownLatch.countDown()
                } catch (e: AssertionFailedError) {
                    if (assertionError == null) assertionError = e
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, null)
        psmp.playMediaObject(p, true, false, true)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res)
        Assert.assertSame(PlayerStatus.PREPARED, psmp.playerInfo.playerStatus)
        callback.cancel()

        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectStreamStartPrepare() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val countDownLatch = CountDownLatch(5)
        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                try {
                    checkPSMPInfo(newInfo)
                    check(newInfo!!.playerStatus != PlayerStatus.ERROR) { "MediaPlayer error" }
                    when (countDownLatch.count) {
                        0L -> {
                            Assert.fail()
                        }
                        5L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZING, newInfo.playerStatus)
                        }
                        4L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZED, newInfo.playerStatus)
                        }
                        3L -> {
                            Assert.assertEquals(PlayerStatus.PREPARING, newInfo.playerStatus)
                        }
                        2L -> {
                            Assert.assertEquals(PlayerStatus.PREPARED, newInfo.playerStatus)
                        }
                        1L -> {
                            Assert.assertEquals(PlayerStatus.PLAYING, newInfo.playerStatus)
                        }
                    }
                    countDownLatch.countDown()
                } catch (e: AssertionFailedError) {
                    if (assertionError == null) assertionError = e
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, null)
        psmp.playMediaObject(p, true, true, true)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res)
        Assert.assertSame(PlayerStatus.PLAYING, psmp.playerInfo.playerStatus)
        callback.cancel()
        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectLocalNoStartNoPrepare() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val countDownLatch = CountDownLatch(2)
        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                try {
                    checkPSMPInfo(newInfo)
                    check(newInfo!!.playerStatus != PlayerStatus.ERROR) { "MediaPlayer error" }
                    when (countDownLatch.count) {
                        0L -> {
                            Assert.fail()
                        }
                        2L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZING, newInfo.playerStatus)
                            countDownLatch.countDown()
                        }
                        else -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZED, newInfo.playerStatus)
                            countDownLatch.countDown()
                        }
                    }
                } catch (e: AssertionFailedError) {
                    if (assertionError == null) assertionError = e
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        psmp.playMediaObject(p, false, false, false)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res)
        Assert.assertSame(PlayerStatus.INITIALIZED, psmp.playerInfo.playerStatus)
        Assert.assertFalse(psmp.startWhenPrepared.get())
        callback.cancel()
        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectLocalStartNoPrepare() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val countDownLatch = CountDownLatch(2)
        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                try {
                    checkPSMPInfo(newInfo)
                    check(newInfo!!.playerStatus != PlayerStatus.ERROR) { "MediaPlayer error" }
                    when (countDownLatch.count) {
                        0L -> {
                            Assert.fail()
                        }
                        2L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZING, newInfo.playerStatus)
                            countDownLatch.countDown()
                        }
                        else -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZED, newInfo.playerStatus)
                            countDownLatch.countDown()
                        }
                    }
                } catch (e: AssertionFailedError) {
                    if (assertionError == null) assertionError = e
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        psmp.playMediaObject(p, false, true, false)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res)
        Assert.assertSame(PlayerStatus.INITIALIZED, psmp.playerInfo.playerStatus)
        Assert.assertTrue(psmp.startWhenPrepared.get())
        callback.cancel()
        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectLocalNoStartPrepare() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val countDownLatch = CountDownLatch(4)
        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                try {
                    checkPSMPInfo(newInfo)
                    check(newInfo!!.playerStatus != PlayerStatus.ERROR) { "MediaPlayer error" }
                    when (countDownLatch.count) {
                        0L -> {
                            Assert.fail()
                        }
                        4L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZING, newInfo.playerStatus)
                        }
                        3L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZED, newInfo.playerStatus)
                        }
                        2L -> {
                            Assert.assertEquals(PlayerStatus.PREPARING, newInfo.playerStatus)
                        }
                        1L -> {
                            Assert.assertEquals(PlayerStatus.PREPARED, newInfo.playerStatus)
                        }
                    }
                    countDownLatch.countDown()
                } catch (e: AssertionFailedError) {
                    if (assertionError == null) assertionError = e
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        psmp.playMediaObject(p, false, false, true)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res)
        Assert.assertSame(PlayerStatus.PREPARED, psmp.playerInfo.playerStatus)
        callback.cancel()
        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectLocalStartPrepare() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val countDownLatch = CountDownLatch(5)
        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                try {
                    checkPSMPInfo(newInfo)
                    check(newInfo!!.playerStatus != PlayerStatus.ERROR) { "MediaPlayer error" }
                    when (countDownLatch.count) {
                        0L -> {
                            Assert.fail()
                        }
                        5L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZING, newInfo.playerStatus)
                        }
                        4L -> {
                            Assert.assertEquals(PlayerStatus.INITIALIZED, newInfo.playerStatus)
                        }
                        3L -> {
                            Assert.assertEquals(PlayerStatus.PREPARING, newInfo.playerStatus)
                        }
                        2L -> {
                            Assert.assertEquals(PlayerStatus.PREPARED, newInfo.playerStatus)
                        }
                        1L -> {
                            Assert.assertEquals(PlayerStatus.PLAYING, newInfo.playerStatus)
                        }
                    }
                } catch (e: AssertionFailedError) {
                    if (assertionError == null) assertionError = e
                } finally {
                    countDownLatch.countDown()
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        psmp.playMediaObject(p, false, true, true)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res)
        Assert.assertSame(PlayerStatus.PLAYING, psmp.playerInfo.playerStatus)
        callback.cancel()
        psmp.shutdown()
    }

    @Throws(InterruptedException::class)
    private fun pauseTestSkeleton(initialState: PlayerStatus,
                                  stream: Boolean,
                                  abandonAudioFocus: Boolean,
                                  reinit: Boolean,
                                  timeoutSeconds: Long
    ) {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val latchCount = if ((stream && reinit)) 2 else 1
        val countDownLatch = CountDownLatch(latchCount)

        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                checkPSMPInfo(newInfo)
                when {
                    newInfo!!.playerStatus == PlayerStatus.ERROR -> {
                        if (assertionError == null) assertionError = UnexpectedStateChange(newInfo.playerStatus)
                    }
                    initialState != PlayerStatus.PLAYING -> {
                        if (assertionError == null) assertionError = UnexpectedStateChange(newInfo.playerStatus)
                    }
                    else -> {
                        when (newInfo.playerStatus) {
                            PlayerStatus.PAUSED -> if (latchCount.toLong() == countDownLatch.count) countDownLatch.countDown()
                            else {
                                if (assertionError == null) assertionError = UnexpectedStateChange(newInfo.playerStatus)
                            }
                            PlayerStatus.INITIALIZED -> when {
                                stream && reinit && countDownLatch.count < latchCount -> {
                                    countDownLatch.countDown()
                                }
                                countDownLatch.count < latchCount -> {
                                    if (assertionError == null) assertionError = UnexpectedStateChange(newInfo.playerStatus)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            override fun shouldStop() {
                if (assertionError == null) assertionError = AssertionFailedError("Unexpected call to shouldStop")
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        if (initialState == PlayerStatus.PLAYING) {
            psmp.playMediaObject(p, stream, true, true)
        }
        psmp.pause(abandonAudioFocus, reinit)
        val res = countDownLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res || initialState != PlayerStatus.PLAYING)
        callback.cancel()
        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPauseDefaultState() {
        pauseTestSkeleton(PlayerStatus.STOPPED, false, false, false, 1)
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateNoAbandonNoReinitNoStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, false, false, false, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateNoAbandonNoReinitStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, true, false, false, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateAbandonNoReinitNoStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, false, true, false, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateAbandonNoReinitStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, true, true, false, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateNoAbandonReinitNoStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, false, false, true, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateNoAbandonReinitStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, true, false, true, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateAbandonReinitNoStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, false, true, true, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateAbandonReinitStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, true, true, true, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Throws(InterruptedException::class)
    private fun resumeTestSkeleton(initialState: PlayerStatus, timeoutSeconds: Long) {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val latchCount =
            when (initialState) {
                PlayerStatus.PAUSED, PlayerStatus.PLAYING -> 2
                PlayerStatus.PREPARED -> 1
                else -> 0
            }
        val countDownLatch = CountDownLatch(latchCount)

        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                checkPSMPInfo(newInfo)
                when {
                    newInfo!!.playerStatus == PlayerStatus.ERROR -> {
                        if (assertionError == null) assertionError = UnexpectedStateChange(newInfo.playerStatus)
                    }
                    newInfo.playerStatus == PlayerStatus.PLAYING -> {
                        if (countDownLatch.count == 0L) {
                            if (assertionError == null) assertionError = UnexpectedStateChange(newInfo.playerStatus)
                        } else {
                            countDownLatch.countDown()
                        }
                    }
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        if (initialState == PlayerStatus.PREPARED || initialState == PlayerStatus.PLAYING || initialState == PlayerStatus.PAUSED) {
            val startWhenPrepared = (initialState != PlayerStatus.PREPARED)
            psmp.playMediaObject(writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL), false, startWhenPrepared, true)
        }
        if (initialState == PlayerStatus.PAUSED) {
            psmp.pause(false, false)
        }
        psmp.resume()
        val res = countDownLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res || (initialState != PlayerStatus.PAUSED && initialState != PlayerStatus.PREPARED))
        callback.cancel()
        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testResumePausedState() {
        resumeTestSkeleton(PlayerStatus.PAUSED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testResumePreparedState() {
        resumeTestSkeleton(PlayerStatus.PREPARED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testResumePlayingState() {
        resumeTestSkeleton(PlayerStatus.PLAYING, 1)
    }

    @Throws(InterruptedException::class)
    private fun prepareTestSkeleton(initialState: PlayerStatus, timeoutSeconds: Long) {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val latchCount = 1
        val countDownLatch = CountDownLatch(latchCount)
        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                checkPSMPInfo(newInfo)
                if (newInfo!!.playerStatus == PlayerStatus.ERROR) {
                    if (assertionError == null) assertionError = UnexpectedStateChange(newInfo.playerStatus)
                } else {
                    when {
                        initialState == PlayerStatus.INITIALIZED && newInfo.playerStatus == PlayerStatus.PREPARED -> {
                            countDownLatch.countDown()
                        }
                        initialState != PlayerStatus.INITIALIZED && initialState == newInfo.playerStatus -> {
                            countDownLatch.countDown()
                        }
                    }
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        if (initialState == PlayerStatus.INITIALIZED || initialState == PlayerStatus.PLAYING || initialState == PlayerStatus.PREPARED || initialState == PlayerStatus.PAUSED) {
            val prepareImmediately = (initialState != PlayerStatus.INITIALIZED)
            val startWhenPrepared = (initialState != PlayerStatus.PREPARED)
            psmp.playMediaObject(p, false, startWhenPrepared, prepareImmediately)
            if (initialState == PlayerStatus.PAUSED) {
                psmp.pause(false, false)
            }
            psmp.prepare()
        }

        val res = countDownLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (initialState != PlayerStatus.INITIALIZED) {
            Assert.assertEquals(initialState, psmp.playerInfo.playerStatus)
        }

        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res)
        callback.cancel()
        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPrepareInitializedState() {
        prepareTestSkeleton(PlayerStatus.INITIALIZED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPreparePlayingState() {
        prepareTestSkeleton(PlayerStatus.PLAYING, 1)
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPreparePausedState() {
        prepareTestSkeleton(PlayerStatus.PAUSED, 1)
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPreparePreparedState() {
        prepareTestSkeleton(PlayerStatus.PREPARED, 1)
    }

    @Throws(InterruptedException::class)
    private fun reinitTestSkeleton(initialState: PlayerStatus, timeoutSeconds: Long) {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val latchCount = 2
        val countDownLatch = CountDownLatch(latchCount)
        val callback = CancelableMediaPlayerCallback(object : DefaultMediaPlayerCallback() {
            override fun statusChanged(newInfo: MediaPlayerInfo?) {
                checkPSMPInfo(newInfo)
                if (newInfo!!.playerStatus == PlayerStatus.ERROR) {
                    if (assertionError == null) assertionError = UnexpectedStateChange(newInfo.playerStatus)
                } else {
                    when {
                        newInfo.playerStatus == initialState -> {
                            countDownLatch.countDown()
                        }
                        countDownLatch.count < latchCount && newInfo.playerStatus == PlayerStatus.INITIALIZED -> {
                            countDownLatch.countDown()
                        }
                    }
                }
            }
        })
        val psmp: MediaPlayerBase = LocalMediaPlayer(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        val prepareImmediately = initialState != PlayerStatus.INITIALIZED
        val startImmediately = initialState != PlayerStatus.PREPARED
        psmp.playMediaObject(p, false, startImmediately, prepareImmediately)
        if (initialState == PlayerStatus.PAUSED) {
            psmp.pause(false, false)
        }
        psmp.reinit()
        val res = countDownLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (assertionError != null) throw assertionError!!
        Assert.assertTrue(res)
        callback.cancel()
        psmp.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testReinitPlayingState() {
        reinitTestSkeleton(PlayerStatus.PLAYING, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testReinitPausedState() {
        reinitTestSkeleton(PlayerStatus.PAUSED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPreparedPlayingState() {
        reinitTestSkeleton(PlayerStatus.PREPARED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testReinitInitializedState() {
        reinitTestSkeleton(PlayerStatus.INITIALIZED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    private class UnexpectedStateChange(status: PlayerStatus) : AssertionFailedError("Unexpected state change: $status")
    companion object {
        private const val PLAYABLE_DEST_URL = "psmptestfile.mp3"
        private const val LATCH_TIMEOUT_SECONDS = 3
    }
}
