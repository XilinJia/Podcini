package de.test.podcini.service.playback

import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.service.TaskManager
import ac.mdiq.podcini.playback.service.TaskManager.PSTMCallback
import ac.mdiq.podcini.preferences.SleepTimerPreferences.setShakeToReset
import ac.mdiq.podcini.preferences.SleepTimerPreferences.setVibrate
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.ui.widget.WidgetUpdater.WidgetState
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test class for PlaybackServiceTaskManager
 */
@LargeTest
class TaskManagerTest {

    val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun tearDown() {
//        deleteDatabase()
    }

    @Before
    fun setUp() {
        // create new database
        val context = InstrumentationRegistry.getInstrumentation().targetContext
//        init(context)
//        deleteDatabase()
//        val adapter = getInstance()
//        adapter.open()
//        adapter.close()
        setShakeToReset(false)
        setVibrate(false)
    }

    @Test
    fun testInit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pstm = TaskManager(context, defaultPSTM)
        pstm.shutdown()
    }

    private fun writeTestQueue(pref: String): List<Episode>? {
        val NUM_ITEMS = 10
        val f = Feed(0, null, "title", "link", "d", null, null, null, null, "id", null, "null", "url")
        f.episodes.clear()
        for (i in 0 until NUM_ITEMS) {
            f.episodes.add(Episode(0, pref + i, pref + i, "link", Date(), Episode.PLAYED, f))
        }
//        val adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(f)
//        adapter.setQueue(f.items)
//        adapter.close()

        for (item in f.episodes) {
            Assert.assertTrue(item.id != 0L)
        }
        return f.episodes
    }

    @Test
    @Throws(InterruptedException::class)
    fun testStartPositionSaver() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val NUM_COUNTDOWNS = 2
        val TIMEOUT = 3 * TaskManager.POSITION_SAVER_WAITING_INTERVAL
        val countDownLatch = CountDownLatch(NUM_COUNTDOWNS)
        val pstm = TaskManager(c, object : PSTMCallback {
            override fun positionSaverTick() {
                countDownLatch.countDown()
            }

            override fun requestWidgetState(): WidgetState {
                return WidgetState(PlayerStatus.PREPARING)
            }

            override fun onChapterLoaded(media: Playable?) {
            }
        })
        pstm.startPositionSaver()
        countDownLatch.await(TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        pstm.shutdown()
    }

    @Test
    fun testIsPositionSaverActive() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val pstm = TaskManager(c, defaultPSTM)
        pstm.startPositionSaver()
        Assert.assertTrue(pstm.isPositionSaverActive)
        pstm.shutdown()
    }

    @Test
    fun testCancelPositionSaver() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val pstm = TaskManager(c, defaultPSTM)
        pstm.startPositionSaver()
        pstm.cancelPositionSaver()
        Assert.assertFalse(pstm.isPositionSaverActive)
        pstm.shutdown()
    }

    @Test
    @Throws(InterruptedException::class)
    fun testStartWidgetUpdater() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val NUM_COUNTDOWNS = 2
        val TIMEOUT = 3 * TaskManager.WIDGET_UPDATER_NOTIFICATION_INTERVAL
        val countDownLatch = CountDownLatch(NUM_COUNTDOWNS)
        val pstm = TaskManager(c, object : PSTMCallback {
            override fun positionSaverTick() {
            }

            override fun requestWidgetState(): WidgetState {
                countDownLatch.countDown()
                return WidgetState(PlayerStatus.PREPARING)
            }

            override fun onChapterLoaded(media: Playable?) {
            }
        })
        pstm.startWidgetUpdater()
        countDownLatch.await(TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        pstm.shutdown()
    }

    @Test
    fun testStartWidgetUpdaterAfterShutdown() {
        // Should not throw.
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val pstm = TaskManager(c, defaultPSTM)
        pstm.shutdown()
        pstm.startWidgetUpdater()
    }

    @Test
    fun testIsWidgetUpdaterActive() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val pstm = TaskManager(c, defaultPSTM)
        pstm.startWidgetUpdater()
        Assert.assertTrue(pstm.isWidgetUpdaterActive)
        pstm.shutdown()
    }

    @Test
    fun testCancelWidgetUpdater() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val pstm = TaskManager(c, defaultPSTM)
        pstm.startWidgetUpdater()
        pstm.cancelWidgetUpdater()
        Assert.assertFalse(pstm.isWidgetUpdaterActive)
        pstm.shutdown()
    }

    @Test
    fun testCancelAllTasksNoTasksStarted() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val pstm = TaskManager(c, defaultPSTM)
        pstm.cancelAllTasks()
        Assert.assertFalse(pstm.isPositionSaverActive)
        Assert.assertFalse(pstm.isWidgetUpdaterActive)
        Assert.assertFalse(pstm.isSleepTimerActive)
        pstm.shutdown()
    }

    @Test
    @UiThreadTest
    fun testCancelAllTasksAllTasksStarted() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val pstm = TaskManager(c, defaultPSTM)
        pstm.startWidgetUpdater()
        pstm.startPositionSaver()
        pstm.setSleepTimer(100000)
        pstm.cancelAllTasks()
        Assert.assertFalse(pstm.isPositionSaverActive)
        Assert.assertFalse(pstm.isWidgetUpdaterActive)
        Assert.assertFalse(pstm.isSleepTimerActive)
        pstm.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testSetSleepTimer() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val TIME: Long = 2000
        val TIMEOUT = 2 * TIME
        val countDownLatch = CountDownLatch(1)
        val timerReceiver: Any = object : Any() {
            private var eventSink: Job?     = null
            private fun cancelFlowEvents() {
                eventSink?.cancel()
                eventSink = null
            }
            private fun procFlowEvents() {
                if (eventSink != null) return
                eventSink = scope.launch {
                    EventFlow.events.collectLatest { event ->
                        when (event) {
                            is FlowEvent.SleepTimerUpdatedEvent -> sleepTimerUpdate(event)
                            else -> {}
                        }
                    }
                }
            }

            fun sleepTimerUpdate(event: FlowEvent.SleepTimerUpdatedEvent?) {
                if (countDownLatch.count == 0L) {
                    Assert.fail()
                }
                countDownLatch.countDown()
            }
        }
//        EventBus.getDefault().register(timerReceiver)
        val pstm = TaskManager(c, defaultPSTM)
        pstm.setSleepTimer(TIME)
        countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)
//        EventBus.getDefault().unregister(timerReceiver)
        pstm.shutdown()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testDisableSleepTimer() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val TIME: Long = 5000
        val TIMEOUT = 2 * TIME
        val countDownLatch = CountDownLatch(1)
        val timerReceiver: Any = object : Any() {
            private var eventSink: Job?     = null
            private fun cancelFlowEvents() {
                eventSink?.cancel()
                eventSink = null
            }
            private fun procFlowEvents() {
                if (eventSink != null) return
                eventSink = scope.launch {
                    EventFlow.events.collectLatest { event ->
                        when (event) {
                            is FlowEvent.SleepTimerUpdatedEvent -> sleepTimerUpdate(event)
                            else -> {}
                        }
                    }
                }
            }
            fun sleepTimerUpdate(event: FlowEvent.SleepTimerUpdatedEvent) {
                when {
                    event.isOver -> {
                        countDownLatch.countDown()
                    }
                    event.getTimeLeft() == 1L -> {
                        Assert.fail("Arrived at 1 but should have been cancelled")
                    }
                }
            }
        }
        val pstm = TaskManager(c, defaultPSTM)
//        EventBus.getDefault().register(timerReceiver)
        pstm.setSleepTimer(TIME)
        pstm.disableSleepTimer()
        Assert.assertFalse(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS))
        pstm.shutdown()
//        EventBus.getDefault().unregister(timerReceiver)
    }

    @Test
    @UiThreadTest
    fun testIsSleepTimerActivePositive() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val pstm = TaskManager(c, defaultPSTM)
        pstm.setSleepTimer(1000)
        Assert.assertTrue(pstm.isSleepTimerActive)
        pstm.shutdown()
    }

    @Test
    @UiThreadTest
    fun testIsSleepTimerActiveNegative() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val pstm = TaskManager(c, defaultPSTM)
        pstm.setSleepTimer(10000)
        pstm.disableSleepTimer()
        Assert.assertFalse(pstm.isSleepTimerActive)
        pstm.shutdown()
    }

    private val defaultPSTM: PSTMCallback = object : PSTMCallback {
        override fun positionSaverTick() {
        }

        override fun requestWidgetState(): WidgetState {
            return WidgetState(PlayerStatus.PREPARING)
        }

        override fun onChapterLoaded(media: Playable?) {
        }
    }
}
