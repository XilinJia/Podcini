package de.test.podcini.ui

import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.MainActivity
import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.test.podcini.EspressoTestUtils
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * User interface tests for MainActivity.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private var uiTestUtils: UITestUtils? = null

    @Rule
    var activityRule: IntentsTestRule<MainActivity> = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    @Throws(IOException::class)
    fun setUp() {
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()

        activityRule.launchActivity(Intent())

        uiTestUtils = UITestUtils(InstrumentationRegistry.getInstrumentation().targetContext)
        uiTestUtils!!.setup()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils!!.tearDown()
//        deleteDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun testAddFeed() {
        // connect to podcast feed
        uiTestUtils!!.addHostedFeedData()
        val feed = uiTestUtils!!.hostedFeeds[0]
        EspressoTestUtils.openNavDrawer()
        Espresso.onView(ViewMatchers.withText(R.string.add_feed_label)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.addViaUrlButton)).perform(ViewActions.scrollTo(), ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.editText)).perform(ViewActions.replaceText(feed.downloadUrl))
        Espresso.onView(ViewMatchers.withText(R.string.confirm_label))
            .perform(ViewActions.scrollTo(), ViewActions.click())

        // subscribe podcast
        Espresso.closeSoftKeyboard()
        EspressoTestUtils.waitForViewGlobally(ViewMatchers.withText(R.string.subscribe_label), 15000)
        Espresso.onView(ViewMatchers.withText(R.string.subscribe_label)).perform(ViewActions.click())

        // wait for podcast feed item list
        EspressoTestUtils.waitForViewGlobally(ViewMatchers.withId(R.id.butShowSettings), 15000)
    }
}
