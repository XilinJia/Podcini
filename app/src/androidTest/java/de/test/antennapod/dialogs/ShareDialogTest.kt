package de.test.antennapod.dialogs

import android.content.Context
import android.content.Intent
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.intent.rule.IntentsTestRule
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.fragment.AllEpisodesFragment
import de.test.antennapod.EspressoTestUtils
import de.test.antennapod.NthMatcher
import de.test.antennapod.ui.UITestUtils
import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * User interface tests for share dialog.
 */
@RunWith(AndroidJUnit4::class)
class ShareDialogTest {
    @Rule
    var activityRule: IntentsTestRule<MainActivity> = IntentsTestRule(MainActivity::class.java, false, false)

    protected var context: Context? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()
        EspressoTestUtils.setLaunchScreen(AllEpisodesFragment.TAG)
        val uiTestUtils = UITestUtils(context!!)
        uiTestUtils.setup()
        uiTestUtils.addLocalFeedData(true)

        activityRule.launchActivity(Intent())

        EspressoTestUtils.openNavDrawer()
        EspressoTestUtils.onDrawerItem(ViewMatchers.withText(R.string.episodes_label)).perform(ViewActions.click())
        val allEpisodesMatcher = Matchers.allOf(ViewMatchers.withId(R.id.recyclerView),
            ViewMatchers.isDisplayed(),
            ViewMatchers.hasMinimumChildCount(2))
        Espresso.onView(ViewMatchers.isRoot()).perform(EspressoTestUtils.waitForView(allEpisodesMatcher, 1000))
        Espresso.onView(allEpisodesMatcher)
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, ViewActions.click()))
        Espresso.onView(NthMatcher.first(EspressoTestUtils.actionBarOverflow())).perform(ViewActions.click())
    }

    @Test
    fun testShareDialogDisplayed() {
        Espresso.onView(ViewMatchers.withText(R.string.share_label)).perform(ViewActions.click())
        Espresso.onView(CoreMatchers.allOf(ViewMatchers.isDisplayed(), ViewMatchers.withText(R.string.share_label)))
    }
}
