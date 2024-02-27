package de.test.podcini

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.*
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.service.playback.PlaybackService
import ac.mdiq.podcini.ui.dialog.RatingDialog
import ac.mdiq.podcini.ui.dialog.RatingDialog.saveRated
import ac.mdiq.podcini.ui.fragment.NavDrawerFragment
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.deleteDatabase
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.getInstance
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.init
import ac.mdiq.podcini.preferences.UserPreferences
import junit.framework.AssertionFailedError
import org.awaitility.Awaitility
import org.awaitility.core.ConditionTimeoutException
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object EspressoTestUtils {
    /**
     * Perform action of waiting for a specific view id.
     * https://stackoverflow.com/a/49814995/
     * @param viewMatcher The view to wait for.
     * @param millis The timeout of until when to wait for.
     */
    fun waitForView(viewMatcher: Matcher<View?>, millis: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return ViewMatchers.isRoot()
            }

            override fun getDescription(): String {
                return "wait for a specific view for $millis millis."
            }

            override fun perform(uiController: UiController, view: View) {
                uiController.loopMainThreadUntilIdle()
                val startTime = System.currentTimeMillis()
                val endTime = startTime + millis

                do {
                    for (child in TreeIterables.breadthFirstViewTraversal(view)) {
                        // found view with required ID
                        if (viewMatcher.matches(child)) {
                            return
                        }
                    }

                    uiController.loopMainThreadForAtLeast(50)
                } while (System.currentTimeMillis() < endTime)

                // timeout happens
                throw PerformException.Builder()
                    .withActionDescription(this.description)
                    .withViewDescription(HumanReadables.describe(view))
                    .withCause(TimeoutException())
                    .build()
            }
        }
    }

    /**
     * Wait until a certain view becomes visible, but at the longest until the timeout.
     * Unlike [.waitForView] it doesn't stick to the initial root view.
     *
     * @param viewMatcher The view to wait for.
     * @param timeoutMillis Maximum waiting period in milliseconds.
     * @throws Exception Throws an Exception in case of a timeout.
     */
    @Throws(Exception::class)
    fun waitForViewGlobally(viewMatcher: Matcher<View?>, timeoutMillis: Long) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + timeoutMillis

        do {
            try {
                Espresso.onView(viewMatcher).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
                // no Exception thrown -> check successful
                return
            } catch (ignore: NoMatchingViewException) {
                // check was not successful "not found" -> continue waiting
            } catch (ignore: AssertionFailedError) {
            }
            Thread.sleep(50)
        } while (System.currentTimeMillis() < endTime)

        throw Exception("Timeout after $timeoutMillis ms")
    }

    /**
     * Perform action of waiting for a specific view id.
     * https://stackoverflow.com/a/30338665/
     * @param id The id of the child to click.
     */
    fun clickChildViewWithId(@IdRes id: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return ViewMatchers.isRoot()
            }

            override fun getDescription(): String {
                return "Click on a child view with specified id."
            }

            override fun perform(uiController: UiController, view: View) {
                val v = view.findViewById<View>(id)
                v.performClick()
            }
        }
    }

    /**
     * Clear all app databases.
     */
    fun clearPreferences() {
        val root = InstrumentationRegistry.getInstrumentation().targetContext.filesDir.parentFile
        val sharedPreferencesFileNames = File(root, "shared_prefs").list()
        for (fileName in sharedPreferencesFileNames) {
            println("Cleared database: $fileName")
            InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
                fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().commit()
        }

        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MainActivity.PREF_IS_FIRST_LAUNCH, false)
            .commit()

        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
            .edit()
            .putString(UserPreferences.PREF_UPDATE_INTERVAL, "0")
            .commit()

        RatingDialog.init(InstrumentationRegistry.getInstrumentation().targetContext)
        saveRated()
    }

    fun setLaunchScreen(tag: String?) {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(NavDrawerFragment.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(NavDrawerFragment.PREF_LAST_FRAGMENT_TAG, tag)
            .commit()
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
            .edit()
            .putString(UserPreferences.PREF_DEFAULT_PAGE, UserPreferences.DEFAULT_PAGE_REMEMBER)
            .commit()
    }

    fun clearDatabase() {
        init(InstrumentationRegistry.getInstrumentation().targetContext)
        deleteDatabase()
        val adapter = getInstance()
        adapter.open()
        adapter.close()
    }

    fun clickPreference(@StringRes title: Int) {
        Espresso.onView(ViewMatchers.withId(R.id.recycler_view)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                Matchers.allOf(ViewMatchers.hasDescendant(ViewMatchers.withText(title)),
                    ViewMatchers.hasDescendant(ViewMatchers.withId(android.R.id.widget_frame))),
                ViewActions.click()))
    }

    fun openNavDrawer() {
        Espresso.onView(ViewMatchers.isRoot()).perform(waitForView(ViewMatchers.withId(R.id.main_activity), 1000))
        Espresso.onView(ViewMatchers.withId(R.id.main_activity)).perform(DrawerActions.open())
    }

    fun onDrawerItem(viewMatcher: Matcher<View>?): ViewInteraction {
        return Espresso.onView(Matchers.allOf(viewMatcher, ViewMatchers.withId(R.id.txtvTitle)))
    }

    fun tryKillPlaybackService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.stopService(Intent(context, PlaybackService::class.java))
        try {
            // Android has no reliable way to stop a service instantly.
            // Calling stopSelf marks allows the system to destroy the service but the actual call
            // to onDestroy takes until the next GC of the system, which we can not influence.
            // Try to wait for the service at least a bit.
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until { !PlaybackService.isRunning }
        } catch (e: ConditionTimeoutException) {
            e.printStackTrace()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    fun actionBarOverflow(): Matcher<View> {
        return Matchers.allOf(ViewMatchers.isDisplayed(), ViewMatchers.withContentDescription("More options"))
    }
}
