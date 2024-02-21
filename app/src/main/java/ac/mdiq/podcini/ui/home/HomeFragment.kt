package ac.mdiq.podcini.ui.home

import ac.mdiq.podcini.activity.MainActivity
import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.storage.DBReader
import ac.mdiq.podcini.core.util.download.FeedUpdateManager
import ac.mdiq.podcini.databinding.HomeFragmentBinding
import ac.mdiq.podcini.event.FeedListUpdateEvent
import ac.mdiq.podcini.event.FeedUpdateRunningEvent
import ac.mdiq.podcini.fragment.SearchFragment
import ac.mdiq.podcini.storage.preferences.UserPreferences
import ac.mdiq.podcini.ui.echo.EchoActivity
import ac.mdiq.podcini.ui.home.sections.*
import ac.mdiq.podcini.view.LiftOnScrollListener
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

/**
 * Shows unread or recently published episodes
 */
class HomeFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private var displayUpArrow = false
    private var disposable: Disposable? = null

    private lateinit var viewBinding: HomeFragmentBinding

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        viewBinding = HomeFragmentBinding.inflate(inflater)
        viewBinding.toolbar.inflateMenu(R.menu.home)
        viewBinding.toolbar.setOnMenuItemClickListener(this)
        displayUpArrow = savedInstanceState?.getBoolean(KEY_UP_ARROW)?:false

        viewBinding.homeScrollView.setOnScrollChangeListener(LiftOnScrollListener(viewBinding.appbar))
        (requireActivity() as MainActivity).setupToolbarToggle(viewBinding.toolbar, displayUpArrow)
        populateSectionList()
        updateWelcomeScreenVisibility()

        viewBinding.swipeRefresh.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        viewBinding.swipeRefresh.setOnRefreshListener {
            FeedUpdateManager.runOnceOrAsk(requireContext())
        }

        return viewBinding.root
    }

    @OptIn(UnstableApi::class) private fun populateSectionList() {
        viewBinding.homeContainer.removeAllViews()

        val prefs: SharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (!prefs.getBoolean(PREF_DISABLE_NOTIFICATION_PERMISSION_NAG, false)) {
                addSection(AllowNotificationsSection())
            }
        }
        if (Calendar.getInstance()[Calendar.YEAR] == EchoActivity.RELEASE_YEAR &&
                Calendar.getInstance()[Calendar.MONTH] == Calendar.DECEMBER &&
                Calendar.getInstance()[Calendar.DAY_OF_MONTH] >= 10 &&
                prefs.getInt(PREF_HIDE_ECHO, 0) != EchoActivity.RELEASE_YEAR) {
            addSection(EchoSection())
        }

        val hiddenSections = getHiddenSections(requireContext())
        val sectionTags = resources.getStringArray(R.array.home_section_tags)
        for (sectionTag in sectionTags) {
            if (hiddenSections.contains(sectionTag)) {
                continue
            }
            addSection(getSection(sectionTag))
        }
    }

    private fun addSection(section: Fragment?) {
        if (section == null) return
        val containerView = FragmentContainerView(requireContext())
        containerView.id = View.generateViewId()
        viewBinding.homeContainer.addView(containerView)
        childFragmentManager.beginTransaction().add(containerView.id, section).commit()
    }

    private fun getSection(tag: String): Fragment? {
        return when (tag) {
            QueueSection.TAG -> QueueSection()
            InboxSection.TAG -> InboxSection()
            EpisodesSurpriseSection.TAG -> EpisodesSurpriseSection()
            SubscriptionsSection.TAG -> SubscriptionsSection()
            DownloadsSection.TAG -> DownloadsSection()
            else -> null
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        viewBinding.swipeRefresh.isRefreshing = event.isFeedUpdateRunning
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.homesettings_items -> {
                HomeSectionsSettingsDialog.open(requireContext()
                ) { dialogInterface: DialogInterface?, i: Int -> populateSectionList() }
                return true
            }
            R.id.refresh_item -> {
                FeedUpdateManager.runOnceOrAsk(requireContext())
                return true
            }
            R.id.action_search -> {
                (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent?) {
        updateWelcomeScreenVisibility()
    }

    private fun updateWelcomeScreenVisibility() {
        disposable?.dispose()

        disposable = Observable.fromCallable { DBReader.getNavDrawerData(UserPreferences.subscriptionsFilter).items.size }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ numSubscriptions: Int ->
                    viewBinding.welcomeContainer.visibility = if (numSubscriptions == 0) View.VISIBLE else View.GONE
                    viewBinding.homeContainer.visibility = if (numSubscriptions == 0) View.GONE else View.VISIBLE
                }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG: String = "HomeFragment"
        const val PREF_NAME: String = "PrefHomeFragment"
        const val PREF_HIDDEN_SECTIONS: String = "PrefHomeSectionsString"
        const val PREF_DISABLE_NOTIFICATION_PERMISSION_NAG: String = "DisableNotificationPermissionNag"
        const val PREF_HIDE_ECHO: String = "HideEcho"

        private const val KEY_UP_ARROW = "up_arrow"
        @JvmStatic
        fun getHiddenSections(context: Context): List<String> {
            val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val hiddenSectionsString: String = prefs.getString(PREF_HIDDEN_SECTIONS, "") ?: ""
            return ArrayList(listOf(*TextUtils.split(hiddenSectionsString, ",")))
        }
    }
}
