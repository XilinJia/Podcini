package de.test.antennapod.ui

import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.fragment.QueueFragment
import de.test.antennapod.EspressoTestUtils
import de.test.antennapod.NthMatcher
import org.hamcrest.CoreMatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * User interface tests for queue fragment.
 */
@RunWith(AndroidJUnit4::class)
class QueueFragmentTest {
    @Rule
    var activityRule: IntentsTestRule<MainActivity> = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    fun setUp() {
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()
        EspressoTestUtils.setLaunchScreen(QueueFragment.TAG)
        activityRule.launchActivity(Intent())
    }

    @Test
    fun testLockEmptyQueue() {
        Espresso.onView(NthMatcher.first(EspressoTestUtils.actionBarOverflow())).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.lock_queue)).perform(ViewActions.click())
        Espresso.onView(CoreMatchers.allOf(ViewMatchers.withClassName(CoreMatchers.endsWith("Button")),
            ViewMatchers.withText(R.string.lock_queue))).perform(ViewActions.click())
        Espresso.onView(NthMatcher.first(EspressoTestUtils.actionBarOverflow())).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.lock_queue)).perform(ViewActions.click())
    }

    @Test
    fun testSortEmptyQueue() {
        Espresso.onView(NthMatcher.first(EspressoTestUtils.actionBarOverflow())).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.sort)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.random)).perform(ViewActions.click())
    }

    @Test
    fun testKeepEmptyQueueSorted() {
        Espresso.onView(NthMatcher.first(EspressoTestUtils.actionBarOverflow())).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.sort)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.keep_sorted)).perform(ViewActions.click())
    }
}
