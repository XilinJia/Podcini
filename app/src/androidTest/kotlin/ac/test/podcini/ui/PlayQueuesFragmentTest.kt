package de.test.podcini.ui

import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.QueuesFragment
import de.test.podcini.EspressoTestUtils
import de.test.podcini.NthMatcher
import org.hamcrest.CoreMatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * User interface tests for queue fragment.
 */
@RunWith(AndroidJUnit4::class)
class PlayQueuesFragmentTest {
    @Rule
    var activityRule: IntentsTestRule<MainActivity> = IntentsTestRule(MainActivity::class.java, false, false)

    @Before
    fun setUp() {
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()
        EspressoTestUtils.setLaunchScreen(QueuesFragment.TAG)
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
