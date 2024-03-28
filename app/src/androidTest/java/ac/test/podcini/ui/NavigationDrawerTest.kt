package de.test.podcini.ui

import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.fragment.*
import ac.mdiq.podcini.preferences.UserPreferences.hiddenDrawerItems
import de.test.podcini.EspressoTestUtils
import de.test.podcini.NthMatcher
import org.hamcrest.Matchers
import org.junit.*
import org.junit.runner.RunWith

import java.io.IOException
import java.util.*

/**
 * User interface tests for MainActivity drawer.
 */
@RunWith(AndroidJUnit4::class)
class NavigationDrawerTest {
    private var uiTestUtils: UITestUtils? = null

    @Rule
    var activityRule: IntentsTestRule<MainActivity> = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    @Throws(IOException::class)
    fun setUp() {
        uiTestUtils = UITestUtils(InstrumentationRegistry.getInstrumentation().targetContext)
        uiTestUtils!!.setup()

        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils!!.tearDown()
    }

    private fun openNavDrawer() {
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(ViewMatchers.withId(R.id.main_layout), 1000))
        Espresso.onView(ViewMatchers.withId(R.id.main_layout)).perform(DrawerActions.open())
    }

    @Test
    @Throws(Exception::class)
    fun testClickNavDrawer() {
        uiTestUtils!!.addLocalFeedData(false)
        hiddenDrawerItems = ArrayList()
        activityRule.launchActivity(Intent())

        // queue
        openNavDrawer()
        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(R.string.queue_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(Matchers.allOf(ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.toolbar)),
                ViewMatchers.withText(R.string.queue_label)), 1000))

        // Inbox
//        openNavDrawer()
//        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(R.string.inbox_label)).perform(ViewActions.click())
//        Espresso.onView(ViewMatchers.isRoot())
//            .perform(EspressoTestUtils.waitForView(Matchers.allOf(ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.toolbar)),
//                ViewMatchers.withText(R.string.inbox_label)), 1000))

        // episodes
        openNavDrawer()
        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(R.string.episodes_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(Matchers.allOf(ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.toolbar)),
                ViewMatchers.withText(R.string.episodes_label), ViewMatchers.isDisplayed()), 1000))

        // Subscriptions
        openNavDrawer()
        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(R.string.subscriptions_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(Matchers.allOf(ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.toolbar)),
                ViewMatchers.withText(R.string.subscriptions_label), ViewMatchers.isDisplayed()), 1000))

        // downloads
        openNavDrawer()
        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(R.string.downloads_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(Matchers.allOf(ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.toolbar)),
                ViewMatchers.withText(R.string.downloads_label), ViewMatchers.isDisplayed()), 1000))

        // playback history
        openNavDrawer()
        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(R.string.playback_history_label))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(Matchers.allOf(ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.toolbar)),
                ViewMatchers.withText(R.string.playback_history_label), ViewMatchers.isDisplayed()), 1000))

        // add podcast
        openNavDrawer()
        Espresso.onView(ViewMatchers.withId(R.id.nav_list)).perform(ViewActions.swipeUp())
        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(R.string.add_feed_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(Matchers.allOf(ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.toolbar)),
                ViewMatchers.withText(R.string.add_feed_label), ViewMatchers.isDisplayed()), 1000))

        // podcasts
        for (i in uiTestUtils!!.hostedFeeds.indices) {
            val f = uiTestUtils!!.hostedFeeds[i]
            openNavDrawer()
            EspressoTestUtils.onDrawerItem(ViewMatchers.withText(f.title)).perform(ViewActions.click())
            Espresso.onView(ViewMatchers.isRoot())
                .perform(EspressoTestUtils.waitForView(Matchers.allOf(ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.appBar)),
                    ViewMatchers.withText(f.title), ViewMatchers.isDisplayed()), 1000))
        }
    }

    @Test
    fun testGoToPreferences() {
        activityRule.launchActivity(Intent())
        openNavDrawer()
        Espresso.onView(ViewMatchers.withText(R.string.settings_label)).perform(ViewActions.click())
        Intents.intended(IntentMatchers.hasComponent(PreferenceActivity::class.java.name))
    }

    @Test
    fun testDrawerPreferencesHideSomeElements() {
        hiddenDrawerItems = ArrayList()
        activityRule.launchActivity(Intent())
        openNavDrawer()
        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(R.string.queue_label)).perform(ViewActions.longClick())
        Espresso.onView(ViewMatchers.withText(R.string.episodes_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.contentPanel)).perform(ViewActions.swipeUp())
        Espresso.onView(ViewMatchers.withText(R.string.playback_history_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.confirm_label)).perform(ViewActions.click())

        val hidden = hiddenDrawerItems
        Assert.assertEquals(2, hidden!!.size.toLong())
        Assert.assertTrue(hidden.contains(AllEpisodesFragment.TAG))
        Assert.assertTrue(hidden.contains(PlaybackHistoryFragment.TAG))
    }

    @Test
    fun testDrawerPreferencesUnhideSomeElements() {
        var hidden = listOf(PlaybackHistoryFragment.TAG, DownloadsFragment.TAG)
        hiddenDrawerItems = hidden
        activityRule.launchActivity(Intent())
        openNavDrawer()
        Espresso.onView(NthMatcher.first(ViewMatchers.withText(R.string.queue_label))).perform(ViewActions.longClick())

        Espresso.onView(ViewMatchers.withText(R.string.queue_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.contentPanel)).perform(ViewActions.swipeUp())
        Espresso.onView(ViewMatchers.withText(R.string.downloads_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.confirm_label)).perform(ViewActions.click())

        hidden = hiddenDrawerItems?.filterNotNull()?: listOf()
        Assert.assertEquals(2, hidden.size.toLong())
        Assert.assertTrue(hidden.contains(QueueFragment.TAG))
        Assert.assertTrue(hidden.contains(PlaybackHistoryFragment.TAG))
    }


    @Test
    fun testDrawerPreferencesHideAllElements() {
        hiddenDrawerItems = ArrayList()
        activityRule.launchActivity(Intent())
        val titles = activityRule.activity.resources.getStringArray(R.array.nav_drawer_titles)

        openNavDrawer()
        Espresso.onView(NthMatcher.first(ViewMatchers.withText(R.string.queue_label))).perform(ViewActions.longClick())
        for (i in titles.indices) {
            val title = titles[i]
            Espresso.onView(Matchers.allOf(ViewMatchers.withText(title), ViewMatchers.isDisplayed()))
                .perform(ViewActions.click())

            if (i == 3) {
                Espresso.onView(ViewMatchers.withId(R.id.contentPanel)).perform(ViewActions.swipeUp())
            }
        }

        Espresso.onView(ViewMatchers.withText(R.string.confirm_label)).perform(ViewActions.click())

        val hidden = hiddenDrawerItems
        Assert.assertEquals(titles.size.toLong(), hidden!!.size.toLong())
        for (tag in NavDrawerFragment.NAV_DRAWER_TAGS) {
            Assert.assertTrue(hidden.contains(tag))
        }
    }

    @Test
    fun testDrawerPreferencesHideCurrentElement() {
        hiddenDrawerItems = ArrayList()
        activityRule.launchActivity(Intent())
        openNavDrawer()
        Espresso.onView(ViewMatchers.withText(R.string.downloads_label)).perform(ViewActions.click())
        openNavDrawer()

        Espresso.onView(NthMatcher.first(ViewMatchers.withText(R.string.queue_label))).perform(ViewActions.longClick())
        Espresso.onView(ViewMatchers.withId(R.id.contentPanel)).perform(ViewActions.swipeUp())
        Espresso.onView(NthMatcher.first(ViewMatchers.withText(R.string.downloads_label))).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.confirm_label)).perform(ViewActions.click())

        val hidden = hiddenDrawerItems
        Assert.assertEquals(1, hidden!!.size.toLong())
        Assert.assertTrue(hidden.contains(DownloadsFragment.TAG))
    }
}
