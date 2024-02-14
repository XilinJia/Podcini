package de.test.antennapod.ui

import android.content.Intent
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.PreferenceActivity
import de.danoeh.antennapod.core.storage.APCleanupAlgorithm
import de.danoeh.antennapod.core.storage.APNullCleanupAlgorithm
import de.danoeh.antennapod.core.storage.APQueueCleanupAlgorithm
import de.danoeh.antennapod.core.storage.EpisodeCleanupAlgorithmFactory.build
import de.danoeh.antennapod.core.storage.ExceptFavoriteCleanupAlgorithm
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation
import de.danoeh.antennapod.storage.preferences.UserPreferences.enqueueLocation
import de.danoeh.antennapod.storage.preferences.UserPreferences.episodeCacheSize
import de.danoeh.antennapod.storage.preferences.UserPreferences.fastForwardSecs
import de.danoeh.antennapod.storage.preferences.UserPreferences.init
import de.danoeh.antennapod.storage.preferences.UserPreferences.isAutoDelete
import de.danoeh.antennapod.storage.preferences.UserPreferences.isAutoDeleteLocal
import de.danoeh.antennapod.storage.preferences.UserPreferences.isEnableAutodownload
import de.danoeh.antennapod.storage.preferences.UserPreferences.isEnableAutodownloadOnBattery
import de.danoeh.antennapod.storage.preferences.UserPreferences.isFollowQueue
import de.danoeh.antennapod.storage.preferences.UserPreferences.isPauseOnHeadsetDisconnect
import de.danoeh.antennapod.storage.preferences.UserPreferences.isPersistNotify
import de.danoeh.antennapod.storage.preferences.UserPreferences.isUnpauseOnBluetoothReconnect
import de.danoeh.antennapod.storage.preferences.UserPreferences.isUnpauseOnHeadsetReconnect
import de.danoeh.antennapod.storage.preferences.UserPreferences.rewindSecs
import de.danoeh.antennapod.storage.preferences.UserPreferences.shouldDeleteRemoveFromQueue
import de.danoeh.antennapod.storage.preferences.UserPreferences.shouldPauseForFocusLoss
import de.danoeh.antennapod.storage.preferences.UserPreferences.showNextChapterOnFullNotification
import de.danoeh.antennapod.storage.preferences.UserPreferences.showPlaybackSpeedOnFullNotification
import de.danoeh.antennapod.storage.preferences.UserPreferences.showSkipOnFullNotification
import de.test.antennapod.EspressoTestUtils
import org.awaitility.Awaitility
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

@LargeTest
class PreferencesTest {
    private var res: Resources? = null

    @get:Rule
    var activityTestRule: ActivityTestRule<PreferenceActivity> = ActivityTestRule(
        PreferenceActivity::class.java,
        false,
        false)


    @Before
    fun setUp() {
        EspressoTestUtils.clearDatabase()
        EspressoTestUtils.clearPreferences()
        activityTestRule.launchActivity(Intent())
        val prefs = PreferenceManager.getDefaultSharedPreferences(activityTestRule.activity)
        prefs.edit().putBoolean(UserPreferences.PREF_ENABLE_AUTODL, true).commit()

        res = activityTestRule.activity.resources
        init(activityTestRule.activity)
    }

    @Test
    fun testEnablePersistentPlaybackControls() {
        val persistNotify = isPersistNotify
        EspressoTestUtils.clickPreference(R.string.user_interface_label)
        EspressoTestUtils.clickPreference(R.string.pref_persistNotify_title)
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { persistNotify != isPersistNotify }
        EspressoTestUtils.clickPreference(R.string.pref_persistNotify_title)
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { persistNotify == isPersistNotify }
    }

    @Test
    fun testSetNotificationButtons() {
        EspressoTestUtils.clickPreference(R.string.user_interface_label)
        val buttons = res!!.getStringArray(R.array.full_notification_buttons_options)
        EspressoTestUtils.clickPreference(R.string.pref_full_notification_buttons_title)
        // First uncheck checkboxes
        Espresso.onView(ViewMatchers.withText(buttons[1])).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(buttons[2])).perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withText(R.string.confirm_label)).perform(ViewActions.click())

        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { showSkipOnFullNotification() }
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { showNextChapterOnFullNotification() }
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { !showPlaybackSpeedOnFullNotification() }
    }

    @Test
    fun testEnqueueLocation() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        doTestEnqueueLocation(R.string.enqueue_location_after_current, EnqueueLocation.AFTER_CURRENTLY_PLAYING)
        doTestEnqueueLocation(R.string.enqueue_location_front, EnqueueLocation.FRONT)
        doTestEnqueueLocation(R.string.enqueue_location_back, EnqueueLocation.BACK)
        doTestEnqueueLocation(R.string.enqueue_location_random, EnqueueLocation.RANDOM)
    }

    private fun doTestEnqueueLocation(@StringRes optionResId: Int, expected: EnqueueLocation) {
        EspressoTestUtils.clickPreference(R.string.pref_enqueue_location_title)
        Espresso.onView(ViewMatchers.withText(optionResId)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { expected == enqueueLocation }
    }

    @Test
    fun testHeadPhonesDisconnect() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        val pauseOnHeadsetDisconnect = isPauseOnHeadsetDisconnect
        Espresso.onView(ViewMatchers.withText(R.string.pref_pauseOnHeadsetDisconnect_title))
            .perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { pauseOnHeadsetDisconnect != isPauseOnHeadsetDisconnect }
        Espresso.onView(ViewMatchers.withText(R.string.pref_pauseOnHeadsetDisconnect_title))
            .perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { pauseOnHeadsetDisconnect == isPauseOnHeadsetDisconnect }
    }

    @Test
    fun testHeadPhonesReconnect() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        if (!isPauseOnHeadsetDisconnect) {
            Espresso.onView(ViewMatchers.withText(R.string.pref_pauseOnHeadsetDisconnect_title))
                .perform(ViewActions.click())
            Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
                .until(UserPreferences::isPauseOnHeadsetDisconnect)
        }
        val unpauseOnHeadsetReconnect = isUnpauseOnHeadsetReconnect
        Espresso.onView(ViewMatchers.withText(R.string.pref_unpauseOnHeadsetReconnect_title))
            .perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { unpauseOnHeadsetReconnect != isUnpauseOnHeadsetReconnect }
        Espresso.onView(ViewMatchers.withText(R.string.pref_unpauseOnHeadsetReconnect_title))
            .perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { unpauseOnHeadsetReconnect == isUnpauseOnHeadsetReconnect }
    }

    @Test
    fun testBluetoothReconnect() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        if (!isPauseOnHeadsetDisconnect) {
            Espresso.onView(ViewMatchers.withText(R.string.pref_pauseOnHeadsetDisconnect_title))
                .perform(ViewActions.click())
            Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
                .until(UserPreferences::isPauseOnHeadsetDisconnect)
        }
        val unpauseOnBluetoothReconnect = isUnpauseOnBluetoothReconnect
        Espresso.onView(ViewMatchers.withText(R.string.pref_unpauseOnBluetoothReconnect_title))
            .perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { unpauseOnBluetoothReconnect != isUnpauseOnBluetoothReconnect }
        Espresso.onView(ViewMatchers.withText(R.string.pref_unpauseOnBluetoothReconnect_title))
            .perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { unpauseOnBluetoothReconnect == isUnpauseOnBluetoothReconnect }
    }

    @Test
    fun testContinuousPlayback() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        val continuousPlayback = isFollowQueue
        EspressoTestUtils.clickPreference(R.string.pref_followQueue_title)
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { continuousPlayback != isFollowQueue }
        EspressoTestUtils.clickPreference(R.string.pref_followQueue_title)
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { continuousPlayback == isFollowQueue }
    }

    @Test
    fun testAutoDelete() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        val autoDelete = isAutoDelete
        Espresso.onView(ViewMatchers.withText(R.string.pref_auto_delete_title)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { autoDelete != isAutoDelete }
        Espresso.onView(ViewMatchers.withText(R.string.pref_auto_delete_title)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { autoDelete == isAutoDelete }
    }

    @Test
    fun testAutoDeleteLocal() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        val initialAutoDelete = isAutoDeleteLocal
        Assert.assertFalse(initialAutoDelete)

        Espresso.onView(ViewMatchers.withText(R.string.pref_auto_local_delete_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.yes)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { isAutoDeleteLocal }

        Espresso.onView(ViewMatchers.withText(R.string.pref_auto_local_delete_title)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { !isAutoDeleteLocal }
    }

    @Test
    fun testPlaybackSpeeds() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        EspressoTestUtils.clickPreference(R.string.playback_speed)
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(ViewMatchers.withText("1.25"), 1000))
        Espresso.onView(ViewMatchers.withText("1.25")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testPauseForInterruptions() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        val pauseForFocusLoss = shouldPauseForFocusLoss()
        EspressoTestUtils.clickPreference(R.string.pref_pausePlaybackForFocusLoss_title)
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { pauseForFocusLoss != shouldPauseForFocusLoss() }
        EspressoTestUtils.clickPreference(R.string.pref_pausePlaybackForFocusLoss_title)
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { pauseForFocusLoss == shouldPauseForFocusLoss() }
    }

    @Test
    fun testSetEpisodeCache() {
        val entries = res!!.getStringArray(R.array.episode_cache_size_entries)
        val values = res!!.getStringArray(R.array.episode_cache_size_values)
        val entry = entries[entries.size / 2]
        val value = values[values.size / 2].toInt()
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        EspressoTestUtils.clickPreference(R.string.pref_episode_cache_title)
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(ViewMatchers.withText(entry), 1000))
        Espresso.onView(ViewMatchers.withText(entry)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { episodeCacheSize == value }
    }

    @Test
    fun testSetEpisodeCacheMin() {
        val entries = res!!.getStringArray(R.array.episode_cache_size_entries)
        val values = res!!.getStringArray(R.array.episode_cache_size_values)
        val minEntry = entries[0]
        val minValue = values[0].toInt()

        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        EspressoTestUtils.clickPreference(R.string.pref_episode_cache_title)
        Espresso.onView(ViewMatchers.withId(R.id.select_dialog_listview)).perform(ViewActions.swipeDown())
        Espresso.onView(ViewMatchers.withText(minEntry)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { episodeCacheSize == minValue }
    }

    @Test
    fun testSetEpisodeCacheMax() {
        val entries = res!!.getStringArray(R.array.episode_cache_size_entries)
        val values = res!!.getStringArray(R.array.episode_cache_size_values)
        val maxEntry = entries[entries.size - 1]
        val maxValue = values[values.size - 1].toInt()
        Espresso.onView(ViewMatchers.withText(R.string.downloads_pref)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.pref_automatic_download_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.pref_episode_cache_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.select_dialog_listview)).perform(ViewActions.swipeUp())
        Espresso.onView(ViewMatchers.withText(maxEntry)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { episodeCacheSize == maxValue }
    }

    @Test
    fun testAutomaticDownload() {
        val automaticDownload = isEnableAutodownload
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { automaticDownload != isEnableAutodownload }
        if (!isEnableAutodownload) {
            EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        }
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until(UserPreferences::isEnableAutodownload)
        val enableAutodownloadOnBattery = isEnableAutodownloadOnBattery
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_on_battery_title)
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { enableAutodownloadOnBattery != isEnableAutodownloadOnBattery }
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_on_battery_title)
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { enableAutodownloadOnBattery == isEnableAutodownloadOnBattery }
    }

    @Test
    fun testEpisodeCleanupFavoriteOnly() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        Espresso.onView(ViewMatchers.withText(R.string.pref_automatic_download_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.pref_episode_cleanup_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.select_dialog_listview)).perform(ViewActions.swipeDown())
        Espresso.onView(ViewMatchers.withText(R.string.episode_cleanup_except_favorite_removal))
            .perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { build() is ExceptFavoriteCleanupAlgorithm }
    }

    @Test
    fun testEpisodeCleanupQueueOnly() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        Espresso.onView(ViewMatchers.withText(R.string.pref_automatic_download_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.pref_episode_cleanup_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.select_dialog_listview)).perform(ViewActions.swipeDown())
        Espresso.onView(ViewMatchers.withText(R.string.episode_cleanup_queue_removal)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { build() is APQueueCleanupAlgorithm }
    }

    @Test
    fun testEpisodeCleanupNeverAlg() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        Espresso.onView(ViewMatchers.withText(R.string.pref_automatic_download_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.pref_episode_cleanup_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.select_dialog_listview)).perform(ViewActions.swipeUp())
        Espresso.onView(ViewMatchers.withText(R.string.episode_cleanup_never)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { build() is APNullCleanupAlgorithm }
    }

    @Test
    fun testEpisodeCleanupClassic() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        Espresso.onView(ViewMatchers.withText(R.string.pref_automatic_download_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.pref_episode_cleanup_title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.episode_cleanup_after_listening)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until {
                val alg = build()
                if (alg is APCleanupAlgorithm) {
                    return@until alg.numberOfHoursAfterPlayback == 0
                }
                false
            }
    }

    @Test
    fun testEpisodeCleanupNumDays() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        EspressoTestUtils.clickPreference(R.string.pref_episode_cleanup_title)
        val search = res!!.getQuantityString(R.plurals.episode_cleanup_days_after_listening, 3, 3)
        Espresso.onView(ViewMatchers.withText(search)).perform(ViewActions.scrollTo())
        Espresso.onView(ViewMatchers.withText(search)).perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until {
                val alg = build()
                if (alg is APCleanupAlgorithm) {
                    return@until alg.numberOfHoursAfterPlayback == 72 // 5 days
                }
                false
            }
    }

    @Test
    fun testRewindChange() {
        val seconds = rewindSecs
        val deltas = res!!.getIntArray(R.array.seek_delta_values)

        EspressoTestUtils.clickPreference(R.string.playback_pref)
        EspressoTestUtils.clickPreference(R.string.pref_rewind)

        val currentIndex = Arrays.binarySearch(deltas, seconds)
        Assert.assertTrue(currentIndex >= 0 && currentIndex < deltas.size) // found?

        // Find next value (wrapping around to next)
        val newIndex = (currentIndex + 1) % deltas.size
        Espresso.onView(ViewMatchers.withText(deltas[newIndex].toString() + " seconds")).perform(ViewActions.click())

        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { rewindSecs == deltas[newIndex] }
    }

    @Test
    fun testFastForwardChange() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        for (i in 2 downTo 1) { // repeat twice to catch any error where fastforward is tracking rewind
            val seconds = fastForwardSecs
            val deltas = res!!.getIntArray(R.array.seek_delta_values)

            EspressoTestUtils.clickPreference(R.string.pref_fast_forward)

            val currentIndex = Arrays.binarySearch(deltas, seconds)
            Assert.assertTrue(currentIndex >= 0 && currentIndex < deltas.size) // found?

            // Find next value (wrapping around to next)
            val newIndex = (currentIndex + 1) % deltas.size

            Espresso.onView(ViewMatchers.withText(deltas[newIndex].toString() + " seconds"))
                .perform(ViewActions.click())

            Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
                .until { fastForwardSecs == deltas[newIndex] }
        }
    }

    @Test
    fun testDeleteRemovesFromQueue() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        if (!shouldDeleteRemoveFromQueue()) {
            EspressoTestUtils.clickPreference(R.string.pref_delete_removes_from_queue_title)
//            TODO: signature not correct, not sure what to do
//            Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
//                .until(Callable { obj: UserPreferences -> obj.shouldDeleteRemoveFromQueue() })
        }
        val deleteRemovesFromQueue = shouldDeleteRemoveFromQueue()
        Espresso.onView(ViewMatchers.withText(R.string.pref_delete_removes_from_queue_title))
            .perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { deleteRemovesFromQueue != shouldDeleteRemoveFromQueue() }
        Espresso.onView(ViewMatchers.withText(R.string.pref_delete_removes_from_queue_title))
            .perform(ViewActions.click())
        Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
            .until { deleteRemovesFromQueue == shouldDeleteRemoveFromQueue() }
    }
}
