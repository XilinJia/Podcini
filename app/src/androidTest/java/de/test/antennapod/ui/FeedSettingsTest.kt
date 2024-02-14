package de.test.antennapod.ui

import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.model.feed.Feed
import de.test.antennapod.EspressoTestUtils
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class FeedSettingsTest {
    private var uiTestUtils: UITestUtils? = null
    private var feed: Feed? = null

    @Rule
    var activityRule: IntentsTestRule<MainActivity> = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    @Throws(Exception::class)
    fun setUp() {
        uiTestUtils = UITestUtils(InstrumentationRegistry.getInstrumentation().targetContext)
        uiTestUtils!!.setup()

        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()

        uiTestUtils!!.addLocalFeedData(false)
        feed = uiTestUtils!!.hostedFeeds[0]
        val intent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_FEED_ID, feed!!.id)
        activityRule.launchActivity(intent)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils!!.tearDown()
    }

    @Test
    fun testClickFeedSettings() {
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(Matchers.allOf(ViewMatchers.isDescendantOfA(ViewMatchers.withId(R.id.appBar)),
                ViewMatchers.withText(feed!!.title), ViewMatchers.isDisplayed()), 1000))
        Espresso.onView(ViewMatchers.withId(R.id.butShowSettings)).perform(ViewActions.click())

        EspressoTestUtils.clickPreference(R.string.keep_updated)

        EspressoTestUtils.clickPreference(R.string.authentication_label)
        Espresso.onView(ViewMatchers.withText(R.string.cancel_label)).perform(ViewActions.click())

        EspressoTestUtils.clickPreference(R.string.playback_speed)
        Espresso.onView(ViewMatchers.withText(R.string.cancel_label)).perform(ViewActions.click())

        EspressoTestUtils.clickPreference(R.string.pref_feed_skip)
        Espresso.onView(ViewMatchers.withText(R.string.cancel_label)).perform(ViewActions.click())

        EspressoTestUtils.clickPreference(R.string.auto_delete_label)
        Espresso.onView(ViewMatchers.withText(R.string.cancel_label)).perform(ViewActions.click())

        EspressoTestUtils.clickPreference(R.string.feed_volume_adapdation)
        Espresso.onView(ViewMatchers.withText(R.string.cancel_label)).perform(ViewActions.click())
    }
}
