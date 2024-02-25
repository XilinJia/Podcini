package de.test.podcini.ui

import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.MainActivity
import de.test.podcini.EspressoTestUtils
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import java.io.IOException

/**
 * Test UI for feeds that do not have media files
 */
@RunWith(AndroidJUnit4::class)
class TextOnlyFeedsTest {
    private var uiTestUtils: UITestUtils? = null

    @Rule
    var activityRule: IntentsTestRule<MainActivity> = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    @Throws(IOException::class)
    fun setUp() {
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()

        uiTestUtils = UITestUtils(InstrumentationRegistry.getInstrumentation().targetContext)
        uiTestUtils!!.setHostTextOnlyFeeds(true)
        uiTestUtils!!.setup()

        activityRule.launchActivity(Intent())
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils!!.tearDown()
    }

    @Test
    @Throws(Exception::class)
    fun testMarkAsPlayedList() {
        uiTestUtils!!.addLocalFeedData(false)
        val feed = uiTestUtils!!.hostedFeeds[0]
        EspressoTestUtils.openNavDrawer()
        Espresso.onView(ViewMatchers.withId(R.id.nav_list)).perform(ViewActions.swipeUp())
        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(feed.title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(feed.getItemAtIndex(0).title)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(ViewMatchers.withText(R.string.mark_read_no_media_label), 3000))
        Espresso.onView(ViewMatchers.withText(R.string.mark_read_no_media_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoTestUtils.waitForView(CoreMatchers.allOf(ViewMatchers.withText(R.string.mark_read_no_media_label),
                CoreMatchers.not(ViewMatchers.isDisplayed())), 3000))
    }
}
