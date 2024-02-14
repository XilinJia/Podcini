package de.danoeh.antennapod.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.EditText
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.preferences.ThemeSwitcher.getNoTitleTheme
import de.danoeh.antennapod.core.receiver.MediaButtonReceiver.Companion.createIntent
import de.danoeh.antennapod.core.sync.queue.SynchronizationQueueSink
import de.danoeh.antennapod.core.util.download.FeedUpdateManager
import de.danoeh.antennapod.core.util.download.FeedUpdateManager.restartUpdateAlarm
import de.danoeh.antennapod.core.util.download.FeedUpdateManager.runOnceOrAsk
import de.danoeh.antennapod.dialog.RatingDialog
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.fragment.*
import de.danoeh.antennapod.model.download.DownloadStatus
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.playback.cast.CastEnabledActivity
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences.backButtonOpensDrawer
import de.danoeh.antennapod.storage.preferences.UserPreferences.defaultPage
import de.danoeh.antennapod.storage.preferences.UserPreferences.hiddenDrawerItems
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.common.ThemeUtils.getDrawableFromAttr
import de.danoeh.antennapod.ui.home.HomeFragment
import de.danoeh.antennapod.view.LockableBottomSheetBehavior
import org.apache.commons.lang3.ArrayUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.min

/**
 * The activity that is shown when the user launches the app.
 */
@UnstableApi
class MainActivity : CastEnabledActivity() {
    private var drawerLayout: DrawerLayout? = null
    private var drawerToggle: ActionBarDrawerToggle? = null
    private var navDrawer: View? = null
    private lateinit var dummyView : View
    var bottomSheet: LockableBottomSheetBehavior<*>? = null
        private set
    @JvmField
    val recycledViewPool: RecyclerView.RecycledViewPool = RecyclerView.RecycledViewPool()
    private var lastTheme = 0
    private var navigationBarInsets = Insets.NONE

    @UnstableApi public override fun onCreate(savedInstanceState: Bundle?) {
        lastTheme = getNoTitleTheme(this)
        setTheme(lastTheme)
        if (savedInstanceState != null) {
            ensureGeneratedViewIdGreaterThan(savedInstanceState.getInt(KEY_GENERATED_VIEW_ID, 0))
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        recycledViewPool.setMaxRecycledViews(R.id.view_type_episode_item, 25)

        dummyView = object : View(this) {}

        drawerLayout = findViewById(R.id.drawer_layout)
        navDrawer = findViewById(R.id.navDrawerFragment)
        setNavDrawerSize()

        // Consume navigation bar insets - we apply them in setPlayerVisible()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_view)) { v: View?, insets: WindowInsetsCompat ->
            navigationBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            updateInsets()
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
                .build()
        }

        val fm = supportFragmentManager
        if (fm.findFragmentByTag(MAIN_FRAGMENT_TAG) == null) {
            if (UserPreferences.DEFAULT_PAGE_REMEMBER != defaultPage) {
                loadFragment(defaultPage, null)
            } else {
                val lastFragment = NavDrawerFragment.getLastNavFragment(this)
                if (ArrayUtils.contains(NavDrawerFragment.NAV_DRAWER_TAGS, lastFragment)) {
                    loadFragment(lastFragment, null)
                } else {
                    try {
                        loadFeedFragmentById(lastFragment.toInt().toLong(), null)
                    } catch (e: NumberFormatException) {
                        // it's not a number, this happens if we removed
                        // a label from the NAV_DRAWER_TAGS
                        // give them a nice default...
                        loadFragment(HomeFragment.TAG, null)
                    }
                }
            }
        }

        val transaction = fm.beginTransaction()
        val navDrawerFragment = NavDrawerFragment()
        transaction.replace(R.id.navDrawerFragment, navDrawerFragment, NavDrawerFragment.TAG)
        val audioPlayerFragment = AudioPlayerFragment()
        transaction.replace(R.id.audioplayerFragment, audioPlayerFragment, AudioPlayerFragment.TAG)
        transaction.commit()

        checkFirstLaunch()
        val bottomSheet = findViewById<View>(R.id.audioplayerFragment)
        this.bottomSheet = BottomSheetBehavior.from(bottomSheet) as LockableBottomSheetBehavior<*>
        this.bottomSheet?.isHideable = false
        this.bottomSheet?.setBottomSheetCallback(bottomSheetCallback)

        restartUpdateAlarm(this, false)
        SynchronizationQueueSink.syncNowIfNotSyncedRecently()

        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData(FeedUpdateManager.WORK_TAG_FEED_UPDATE)
            .observe(this) { workInfos: List<WorkInfo> ->
                var isRefreshingFeeds = false
                for (workInfo in workInfos) {
                    if (workInfo.state == WorkInfo.State.RUNNING) {
                        isRefreshingFeeds = true
                    } else if (workInfo.state == WorkInfo.State.ENQUEUED) {
                        isRefreshingFeeds = true
                    }
                }
                EventBus.getDefault().postSticky(FeedUpdateRunningEvent(isRefreshingFeeds))
            }
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData(DownloadServiceInterface.WORK_TAG)
            .observe(this) { workInfos: List<WorkInfo> ->
                val updatedEpisodes: MutableMap<String, DownloadStatus> = HashMap()
                for (workInfo in workInfos) {
                    var downloadUrl: String? = null
                    for (tag in workInfo.tags) {
                        if (tag.startsWith(DownloadServiceInterface.WORK_TAG_EPISODE_URL)) {
                            downloadUrl = tag.substring(DownloadServiceInterface.WORK_TAG_EPISODE_URL.length)
                        }
                    }
                    if (downloadUrl == null) {
                        continue
                    }
                    var status: Int
                    status = when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            DownloadStatus.STATE_RUNNING
                        }
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                            DownloadStatus.STATE_QUEUED
                        }
                        else -> {
                            DownloadStatus.STATE_COMPLETED
                        }
                    }
                    var progress = workInfo.progress.getInt(DownloadServiceInterface.WORK_DATA_PROGRESS, -1)
                    if (progress == -1 && status != DownloadStatus.STATE_COMPLETED) {
                        status = DownloadStatus.STATE_QUEUED
                        progress = 0
                    }
                    updatedEpisodes[downloadUrl] = DownloadStatus(status, progress)
                }
                DownloadServiceInterface.get()?.setCurrentDownloads(updatedEpisodes)
                EventBus.getDefault().postSticky(EpisodeDownloadEvent(updatedEpisodes))
            }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateInsets()
    }

    /**
     * View.generateViewId stores the current ID in a static variable.
     * When the process is killed, the variable gets reset.
     * This makes sure that we do not get ID collisions
     * and therefore errors when trying to restore state from another view.
     */
    private fun ensureGeneratedViewIdGreaterThan(minimum: Int) {
        while (View.generateViewId() <= minimum) {
            // Generate new IDs
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_GENERATED_VIEW_ID, View.generateViewId())
    }

    private val bottomSheetCallback: BottomSheetCallback = @UnstableApi object : BottomSheetCallback() {
         override fun onStateChanged(view: View, state: Int) {
             if (state == BottomSheetBehavior.STATE_COLLAPSED) {
                 onSlide(view,0.0f)
             } else if (state == BottomSheetBehavior.STATE_EXPANDED) {
                 onSlide(view, 1.0f)
             }
         }
        override fun onSlide(view: View, slideOffset: Float) {
            val audioPlayer = supportFragmentManager
                .findFragmentByTag(AudioPlayerFragment.TAG) as AudioPlayerFragment?
            if (audioPlayer == null) {
                return
            }

            if (slideOffset == 0.0f) { //STATE_COLLAPSED
                audioPlayer.scrollToPage(AudioPlayerFragment.POS_COVER)
            }

            audioPlayer.fadePlayerToToolbar(slideOffset)
        }
    }

    fun setupToolbarToggle(toolbar: MaterialToolbar, displayUpArrow: Boolean) {
        if (drawerLayout != null) { // Tablet layout does not have a drawer
            if (drawerToggle != null) {
                drawerLayout!!.removeDrawerListener(drawerToggle!!)
            }
            drawerToggle = ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close)
            drawerLayout!!.addDrawerListener(drawerToggle!!)
            drawerToggle!!.syncState()
            drawerToggle!!.isDrawerIndicatorEnabled = !displayUpArrow
            drawerToggle!!.toolbarNavigationClickListener =
                View.OnClickListener { v: View? -> supportFragmentManager.popBackStack() }
        } else if (!displayUpArrow) {
            toolbar.navigationIcon = null
        } else {
            toolbar.setNavigationIcon(getDrawableFromAttr(this, R.attr.homeAsUpIndicator))
            toolbar.setNavigationOnClickListener { v: View? -> supportFragmentManager.popBackStack() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (drawerLayout != null) {
            drawerLayout!!.removeDrawerListener(drawerToggle!!)
        }
    }

    private fun checkFirstLaunch() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(PREF_IS_FIRST_LAUNCH, true)) {
            restartUpdateAlarm(this, true)

            val edit = prefs.edit()
            edit.putBoolean(PREF_IS_FIRST_LAUNCH, false)
            edit.apply()
        }
    }

    val isDrawerOpen: Boolean
        get() = drawerLayout != null && navDrawer != null && drawerLayout!!.isDrawerOpen(navDrawer!!)

    private fun updateInsets() {
        setPlayerVisible(findViewById<View>(R.id.audioplayerFragment).visibility == View.VISIBLE)
        val playerHeight = resources.getDimension(R.dimen.external_player_height).toInt()
        bottomSheet!!.peekHeight = playerHeight + navigationBarInsets.bottom
    }

    fun setPlayerVisible(visible: Boolean) {
        bottomSheet!!.setLocked(!visible)
        if (visible) {
            bottomSheetCallback.onStateChanged(dummyView, bottomSheet!!.state) // Update toolbar visibility
        } else {
            bottomSheet!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
        val mainView = findViewById<FragmentContainerView>(R.id.main_view)
        val params = mainView.layoutParams as MarginLayoutParams
        val externalPlayerHeight = resources.getDimension(R.dimen.external_player_height).toInt()
        params.setMargins(navigationBarInsets.left, 0, navigationBarInsets.right,
            navigationBarInsets.bottom + (if (visible) externalPlayerHeight else 0))
        mainView.layoutParams = params
        val playerView = findViewById<FragmentContainerView>(R.id.playerFragment)
        val playerParams = playerView.layoutParams as MarginLayoutParams
        playerParams.setMargins(navigationBarInsets.left, 0, navigationBarInsets.right, 0)
        playerView.layoutParams = playerParams
        findViewById<View>(R.id.audioplayerFragment).visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun loadFragment(tag: String?, args: Bundle?) {
        var tag = tag
        var args = args
        Log.d(TAG, "loadFragment(tag: $tag, args: $args)")
        val fragment: Fragment
        when (tag) {
            HomeFragment.TAG -> fragment = HomeFragment()
            QueueFragment.TAG -> fragment = QueueFragment()
            InboxFragment.TAG -> fragment = InboxFragment()
            AllEpisodesFragment.TAG -> fragment = AllEpisodesFragment()
            CompletedDownloadsFragment.TAG -> fragment = CompletedDownloadsFragment()
            PlaybackHistoryFragment.TAG -> fragment = PlaybackHistoryFragment()
            AddFeedFragment.TAG -> fragment = AddFeedFragment()
            SubscriptionFragment.TAG -> fragment = SubscriptionFragment()
            else -> {
                // default to home screen
                fragment = HomeFragment()
                tag = HomeFragment.TAG
                args = null
            }
        }
        if (args != null) {
            fragment.arguments = args
        }
        NavDrawerFragment.saveLastNavFragment(this, tag)
        loadFragment(fragment)
    }

    fun loadFeedFragmentById(feedId: Long, args: Bundle?) {
        val fragment: Fragment = FeedItemlistFragment.newInstance(feedId)
        if (args != null) {
            fragment.arguments = args
        }
        NavDrawerFragment.saveLastNavFragment(this, feedId.toString())
        loadFragment(fragment)
    }

    private fun loadFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        // clear back stack
        for (i in 0 until fragmentManager.backStackEntryCount) {
            fragmentManager.popBackStack()
        }
        val t = fragmentManager.beginTransaction()
        t.replace(R.id.main_view, fragment, MAIN_FRAGMENT_TAG)
        fragmentManager.popBackStack()
        // TODO: we have to allow state loss here
        // since this function can get called from an AsyncTask which
        // could be finishing after our app has already committed state
        // and is about to get shutdown.  What we *should* do is
        // not commit anything in an AsyncTask, but that's a bigger
        // change than we want now.
        t.commitAllowingStateLoss()

        if (drawerLayout != null) { // Tablet layout does not have a drawer
            drawerLayout!!.closeDrawer(navDrawer!!)
        }
    }

    @JvmOverloads
    fun loadChildFragment(fragment: Fragment, transition: TransitionEffect? = TransitionEffect.NONE) {
        val transaction = supportFragmentManager.beginTransaction()

        when (transition) {
            TransitionEffect.FADE -> transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            TransitionEffect.SLIDE -> transaction.setCustomAnimations(
                R.anim.slide_right_in,
                R.anim.slide_left_out,
                R.anim.slide_left_in,
                R.anim.slide_right_out)
            TransitionEffect.NONE -> {}
            null -> {}
        }
        transaction
            .hide(supportFragmentManager.findFragmentByTag(MAIN_FRAGMENT_TAG)!!)
            .add(R.id.main_view, fragment, MAIN_FRAGMENT_TAG)
            .addToBackStack(null)
            .commit()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (drawerToggle != null) { // Tablet layout does not have a drawer
            drawerToggle!!.syncState()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (drawerToggle != null) { // Tablet layout does not have a drawer
            drawerToggle!!.onConfigurationChanged(newConfig)
        }
        setNavDrawerSize()
    }

    private fun setNavDrawerSize() {
        if (drawerToggle == null) { // Tablet layout does not have a drawer
            return
        }
        val screenPercent = resources.getInteger(R.integer.nav_drawer_screen_size_percent) * 0.01f
        val width = (screenWidth * screenPercent).toInt()
        val maxWidth = resources.getDimension(R.dimen.nav_drawer_max_screen_size).toInt()

        navDrawer!!.layoutParams.width = min(width.toDouble(), maxWidth.toDouble()).toInt()
    }

    private val screenWidth: Int
        get() {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.widthPixels
        }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        if (bottomSheet!!.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetCallback.onSlide(dummyView, 1.0f)
        }
    }

    public override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        RatingDialog.init(this)
    }

    override fun onResume() {
        super.onResume()
        handleNavIntent()
        RatingDialog.check()

        if (lastTheme != getNoTitleTheme(this)) {
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }
        if (hiddenDrawerItems!!.contains(NavDrawerFragment.getLastNavFragment(this))) {
            loadFragment(defaultPage, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        lastTheme = getNoTitleTheme(this) // Don't recreate activity when a result is pending
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Glide.get(this).trimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle != null && drawerToggle!!.onOptionsItemSelected(item)) { // Tablet layout does not have a drawer
            return true
        } else if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            }
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isDrawerOpen) {
            drawerLayout!!.closeDrawer(navDrawer!!)
        } else if (bottomSheet!!.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheet!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else if (supportFragmentManager.backStackEntryCount != 0) {
            super.onBackPressed()
        } else {
            val toPage = defaultPage
            if (NavDrawerFragment.getLastNavFragment(this) == toPage || UserPreferences.DEFAULT_PAGE_REMEMBER == toPage) {
                if (backButtonOpensDrawer() && drawerLayout != null) {
                    drawerLayout!!.openDrawer(navDrawer!!)
                } else {
                    super.onBackPressed()
                }
            } else {
                loadFragment(toPage, null)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: MessageEvent) {
        Log.d(TAG, "onEvent($event)")

        val snackbar = showSnackbarAbovePlayer(event.message, Snackbar.LENGTH_LONG)
        if (event.action != null) {
            snackbar.setAction(event.actionText) { v: View? -> event.action!!.accept(this) }
        }
    }

    private fun handleNavIntent() {
        Log.d(TAG, "handleNavIntent()")
        val intent = intent
        if (intent.hasExtra(EXTRA_FEED_ID)) {
            val feedId = intent.getLongExtra(EXTRA_FEED_ID, 0)
            val args = intent.getBundleExtra(MainActivityStarter.EXTRA_FRAGMENT_ARGS)
            if (feedId > 0) {
                val startedFromSearch = intent.getBooleanExtra(EXTRA_STARTED_FROM_SEARCH, false)
                val addToBackStack = intent.getBooleanExtra(EXTRA_ADD_TO_BACK_STACK, false)
                if (startedFromSearch || addToBackStack) {
                    loadChildFragment(FeedItemlistFragment.newInstance(feedId))
                } else {
                    loadFeedFragmentById(feedId, args)
                }
            }
            bottomSheet!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else if (intent.hasExtra(MainActivityStarter.EXTRA_FRAGMENT_TAG)) {
            val tag = intent.getStringExtra(MainActivityStarter.EXTRA_FRAGMENT_TAG)
            val args = intent.getBundleExtra(MainActivityStarter.EXTRA_FRAGMENT_ARGS)
            if (tag != null) {
                loadFragment(tag, args)
            }
            bottomSheet!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else if (intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_PLAYER, false)) {
            bottomSheet!!.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetCallback.onSlide(dummyView, 1.0f)
        } else {
            handleDeeplink(intent.data)
        }

        if (intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_DRAWER, false) && drawerLayout != null) {
            drawerLayout!!.open()
        }
        if (intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_DOWNLOAD_LOGS, false)) {
            DownloadLogFragment().show(supportFragmentManager, null)
        }
        if (intent.getBooleanExtra(EXTRA_REFRESH_ON_START, false)) {
            runOnceOrAsk(this)
        }
        // to avoid handling the intent twice when the configuration changes
        setIntent(Intent(this@MainActivity, MainActivity::class.java))
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent()
    }

    fun showSnackbarAbovePlayer(text: CharSequence?, duration: Int): Snackbar {
        val s: Snackbar
        if (bottomSheet!!.state == BottomSheetBehavior.STATE_COLLAPSED) {
            s = Snackbar.make(findViewById(R.id.main_view), text!!, duration)
            if (findViewById<View>(R.id.audioplayerFragment).visibility == View.VISIBLE) {
                s.setAnchorView(findViewById(R.id.audioplayerFragment))
            }
        } else {
            s = Snackbar.make(findViewById(android.R.id.content), text!!, duration)
        }
        s.show()
        return s
    }

    fun showSnackbarAbovePlayer(text: Int, duration: Int): Snackbar {
        return showSnackbarAbovePlayer(resources.getText(text), duration)
    }

    /**
     * Handles the deep link incoming via App Actions.
     * Performs an in-app search or opens the relevant feature of the app
     * depending on the query.
     *
     * @param uri incoming deep link
     */
    private fun handleDeeplink(uri: Uri?) {
        if (uri == null || uri.path == null) {
            return
        }
        Log.d(TAG, "Handling deeplink: $uri")
        when (uri.path) {
            "/deeplink/search" -> {
                val query = uri.getQueryParameter("query") ?: return

                this.loadChildFragment(SearchFragment.newInstance(query))
            }
            "/deeplink/main" -> {
                val feature = uri.getQueryParameter("page") ?: return
                when (feature) {
                    "DOWNLOADS" -> loadFragment(CompletedDownloadsFragment.TAG, null)
                    "HISTORY" -> loadFragment(PlaybackHistoryFragment.TAG, null)
                    "EPISODES" -> loadFragment(AllEpisodesFragment.TAG, null)
                    "QUEUE" -> loadFragment(QueueFragment.TAG, null)
                    "SUBSCRIPTIONS" -> loadFragment(SubscriptionFragment.TAG, null)
                    else -> {
                        showSnackbarAbovePlayer(getString(R.string.app_action_not_found)+feature, Snackbar.LENGTH_LONG)
                        return
                    }
                }
            }
            else -> {}
        }
    }

    //Hardware keyboard support
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val currentFocus = currentFocus
        if (currentFocus is EditText) {
            return super.onKeyUp(keyCode, event)
        }

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        var customKeyCode: Int? = null
        EventBus.getDefault().post(event)

        when (keyCode) {
            KeyEvent.KEYCODE_P -> customKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> customKeyCode =
                KeyEvent.KEYCODE_MEDIA_REWIND
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> customKeyCode =
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_W -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_M -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                return true
            }
        }
        if (customKeyCode != null) {
            sendBroadcast(createIntent(this, customKeyCode))
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        private const val TAG = "MainActivity"
        const val MAIN_FRAGMENT_TAG: String = "main"

        const val PREF_NAME: String = "MainActivityPrefs"
        const val PREF_IS_FIRST_LAUNCH: String = "prefMainActivityIsFirstLaunch"

        const val EXTRA_FEED_ID: String = "fragment_feed_id"
        const val EXTRA_REFRESH_ON_START: String = "refresh_on_start"
        const val EXTRA_STARTED_FROM_SEARCH: String = "started_from_search"
        const val EXTRA_ADD_TO_BACK_STACK: String = "add_to_back_stack"
        const val KEY_GENERATED_VIEW_ID: String = "generated_view_id"

        @JvmStatic
        fun getIntentToOpenFeed(context: Context, feedId: Long): Intent {
            val intent = Intent(context.applicationContext, MainActivity::class.java)
            intent.putExtra(EXTRA_FEED_ID, feedId)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }
    }
}
