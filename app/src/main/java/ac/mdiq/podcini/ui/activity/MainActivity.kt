package ac.mdiq.podcini.ui.activity

//import ac.mdiq.podcini.ui.home.HomeFragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.MainActivityBinding
import ac.mdiq.podcini.net.download.FeedUpdateManager
import ac.mdiq.podcini.net.download.FeedUpdateManager.restartUpdateAlarm
import ac.mdiq.podcini.net.download.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.preferences.ThemeSwitcher.getNoTitleTheme
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.backButtonOpensDrawer
import ac.mdiq.podcini.preferences.UserPreferences.defaultPage
import ac.mdiq.podcini.preferences.UserPreferences.hiddenDrawerItems
import ac.mdiq.podcini.receiver.MediaButtonReceiver.Companion.createIntent
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.model.download.DownloadStatus
import ac.mdiq.podcini.ui.appstartintent.MainActivityStarter
import ac.mdiq.podcini.ui.common.ThemeUtils.getDrawableFromAttr
import ac.mdiq.podcini.ui.dialog.RatingDialog
import ac.mdiq.podcini.ui.fragment.*
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.ui.view.LockableBottomSheetBehavior
import ac.mdiq.podcini.util.event.EpisodeDownloadEvent
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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
//    some device doesn't have a drawer
    private var drawerLayout: DrawerLayout? = null

    private lateinit var binding: MainActivityBinding
    private lateinit var mainView: View
    private lateinit var audioPlayerFragmentView: View
    private lateinit var navDrawer: View
    private lateinit var dummyView : View
    lateinit var bottomSheet: LockableBottomSheetBehavior<*>
        private set

    private var drawerToggle: ActionBarDrawerToggle? = null

    @JvmField
    val recycledViewPool: RecyclerView.RecycledViewPool = RecyclerView.RecycledViewPool()
    private var lastTheme = 0
    private var navigationBarInsets = Insets.NONE

    @UnstableApi public override fun onCreate(savedInstanceState: Bundle?) {
        lastTheme = getNoTitleTheme(this)
        setTheme(lastTheme)

        DBReader.updateFeedList()

        if (savedInstanceState != null) {
            ensureGeneratedViewIdGreaterThan(savedInstanceState.getInt(KEY_GENERATED_VIEW_ID, 0))
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(R.layout.main_activity)
        recycledViewPool.setMaxRecycledViews(R.id.view_type_episode_item, 25)

        dummyView = object : View(this) {}

        drawerLayout = findViewById(R.id.main_layout)
        navDrawer = findViewById(R.id.navDrawerFragment)
        setNavDrawerSize()

        mainView = findViewById(R.id.main_view)

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//            Toast.makeText(this, R.string.notification_permission_text, Toast.LENGTH_LONG).show()
            requestPostNotificationPermission()
        }

        // Consume navigation bar insets - we apply them in setPlayerVisible()
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { _: View?, insets: WindowInsetsCompat ->
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
                        loadFragment(SubscriptionFragment.TAG, null)
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
        navDrawer = findViewById(R.id.navDrawerFragment)
        audioPlayerFragmentView = findViewById(R.id.audioplayerFragment)

        checkFirstLaunch()
        this.bottomSheet = BottomSheetBehavior.from(audioPlayerFragmentView) as LockableBottomSheetBehavior<*>
        this.bottomSheet.isHideable = false
        this.bottomSheet.setBottomSheetCallback(bottomSheetCallback)

        restartUpdateAlarm(this, false)
        SynchronizationQueueSink.syncNowIfNotSyncedRecently()

        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData(FeedUpdateManager.WORK_TAG_FEED_UPDATE)
            .observe(this) { workInfos: List<WorkInfo> ->
                var isRefreshingFeeds = false
                for (workInfo in workInfos) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            isRefreshingFeeds = true
                        }
                        WorkInfo.State.ENQUEUED -> {
                            isRefreshingFeeds = true
                        }
                        else -> {
                //                        Log.d(TAG, "workInfo.state ${workInfo.state}")
                        }
                    }
                }
                EventBus.getDefault().postSticky(ac.mdiq.podcini.util.event.FeedUpdateRunningEvent(isRefreshingFeeds))
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
                        WorkInfo.State.SUCCEEDED -> {
                            DownloadStatus.STATE_COMPLETED
                        }
                        WorkInfo.State.FAILED -> {
                            Log.e(TAG, "download failed $downloadUrl")
                            DownloadStatus.STATE_COMPLETED
                        }
                        WorkInfo.State.CANCELLED -> {
                            Log.d(TAG, "download cancelled $downloadUrl")
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

    fun requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                return@registerForActivityResult
            }
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.notification_permission_text)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> {} }
                .setNegativeButton(R.string.cancel_label) { _: DialogInterface?, _: Int -> finish() }
                .show()
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
            val audioPlayer = supportFragmentManager.findFragmentByTag(AudioPlayerFragment.TAG) as? AudioPlayerFragment ?: return

            if (slideOffset == 0.0f) { //STATE_COLLAPSED
                audioPlayer.scrollToPage(AudioPlayerFragment.FIRST_PAGE)
            }
            audioPlayer.fadePlayerToToolbar(slideOffset)
        }
    }

    fun setupToolbarToggle(toolbar: MaterialToolbar, displayUpArrow: Boolean) {
        Log.d(TAG, "setupToolbarToggle ${drawerLayout?.id} $displayUpArrow")
        // Tablet layout does not have a drawer
        if (drawerLayout != null) {
            if (drawerToggle != null) {
                drawerLayout!!.removeDrawerListener(drawerToggle!!)
            }
            drawerToggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close)
            drawerLayout!!.addDrawerListener(drawerToggle!!)
            drawerToggle!!.syncState()
            drawerToggle!!.isDrawerIndicatorEnabled = !displayUpArrow
            drawerToggle!!.toolbarNavigationClickListener = View.OnClickListener { supportFragmentManager.popBackStack() }
        } else if (!displayUpArrow) {
            toolbar.navigationIcon = null
        } else {
            toolbar.setNavigationIcon(getDrawableFromAttr(this, R.attr.homeAsUpIndicator))
            toolbar.setNavigationOnClickListener { supportFragmentManager.popBackStack() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        drawerLayout?.removeDrawerListener(drawerToggle!!)
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
        get() = drawerLayout?.isDrawerOpen(navDrawer)?:false

    private fun updateInsets() {
        setPlayerVisible(audioPlayerFragmentView.visibility == View.VISIBLE)
        val playerHeight = resources.getDimension(R.dimen.external_player_height).toInt()
        bottomSheet.peekHeight = playerHeight + navigationBarInsets.bottom
    }

    fun setPlayerVisible(visible: Boolean) {
        bottomSheet.setLocked(!visible)
        if (visible) {
            bottomSheetCallback.onStateChanged(dummyView, bottomSheet.state) // Update toolbar visibility
        } else {
            bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
//        val mainView = findViewById<FragmentContainerView>(R.id.main_view)
        val params = mainView.layoutParams as MarginLayoutParams
        val externalPlayerHeight = resources.getDimension(R.dimen.external_player_height).toInt()
        params.setMargins(navigationBarInsets.left, 0, navigationBarInsets.right,
            navigationBarInsets.bottom + (if (visible) externalPlayerHeight else 0))
        mainView.layoutParams = params
        val playerView = findViewById<FragmentContainerView>(R.id.playerFragment)
        val playerParams = playerView.layoutParams as MarginLayoutParams
        playerParams.setMargins(navigationBarInsets.left, 0, navigationBarInsets.right, 0)
        playerView.layoutParams = playerParams
        audioPlayerFragmentView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun loadFragment(tag: String?, args: Bundle?) {
        var tag = tag
        var args = args
        Log.d(TAG, "loadFragment(tag: $tag, args: $args)")
        val fragment: Fragment
        when (tag) {
            QueueFragment.TAG -> fragment = QueueFragment()
            AllEpisodesFragment.TAG -> fragment = AllEpisodesFragment()
            CompletedDownloadsFragment.TAG -> fragment = CompletedDownloadsFragment()
            PlaybackHistoryFragment.TAG -> fragment = PlaybackHistoryFragment()
            AddFeedFragment.TAG -> fragment = AddFeedFragment()
            SubscriptionFragment.TAG -> fragment = SubscriptionFragment()
            StatisticsFragment.TAG -> fragment = StatisticsFragment()
            else -> {
                // default to subscriptions screen
                fragment = SubscriptionFragment()
                tag = SubscriptionFragment.TAG
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
        mainView = findViewById(R.id.main_view)

        // Tablet layout does not have a drawer
        drawerLayout?.closeDrawer(navDrawer)
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
        mainView = findViewById(R.id.main_view)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle?.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle?.onConfigurationChanged(newConfig)
        setNavDrawerSize()
    }

    private fun setNavDrawerSize() {
        // Tablet layout does not have a drawer
        if (drawerLayout == null) {
            return
        }
        val screenPercent = resources.getInteger(R.integer.nav_drawer_screen_size_percent) * 0.01f
        val width = (screenWidth * screenPercent).toInt()
        val maxWidth = resources.getDimension(R.dimen.nav_drawer_max_screen_size).toInt()

        navDrawer.layoutParams.width = min(width.toDouble(), maxWidth.toDouble()).toInt()
        Log.d(TAG, "setNavDrawerSize: ${navDrawer.layoutParams.width}")
    }

    private val screenWidth: Int
        get() {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.widthPixels
        }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
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
        if (hiddenDrawerItems.contains(NavDrawerFragment.getLastNavFragment(this))) {
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
        Log.d(TAG, "onOptionsItemSelected ${item.title}")
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
        when {
            isDrawerOpen -> {
                drawerLayout?.closeDrawer(navDrawer)
            }
            bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED -> {
                bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
            supportFragmentManager.backStackEntryCount != 0 -> {
                super.onBackPressed()
            }
            else -> {
                val toPage = defaultPage
                if (NavDrawerFragment.getLastNavFragment(this) == toPage || UserPreferences.DEFAULT_PAGE_REMEMBER == toPage) {
                    if (backButtonOpensDrawer()) {
                        drawerLayout?.openDrawer(navDrawer)
                    } else {
                        super.onBackPressed()
                    }
                } else {
                    loadFragment(toPage, null)
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: ac.mdiq.podcini.util.event.MessageEvent) {
        Log.d(TAG, "onEvent($event)")

        val snackbar = showSnackbarAbovePlayer(event.message, Snackbar.LENGTH_LONG)
        if (event.action != null) {
            snackbar.setAction(event.actionText) { event.action.accept(this) }
        }
    }

    private fun handleNavIntent() {
        Log.d(TAG, "handleNavIntent()")
        val intent = intent
        when {
            intent.hasExtra(EXTRA_FEED_ID) -> {
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
                bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
            intent.hasExtra(MainActivityStarter.EXTRA_FRAGMENT_TAG) -> {
                val tag = intent.getStringExtra(MainActivityStarter.EXTRA_FRAGMENT_TAG)
                val args = intent.getBundleExtra(MainActivityStarter.EXTRA_FRAGMENT_ARGS)
                if (tag != null) loadFragment(tag, args)

                bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
            intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_PLAYER, false) -> {
                bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
                bottomSheetCallback.onSlide(dummyView, 1.0f)
            }
            else -> {
                handleDeeplink(intent.data)
            }
        }

        if (intent.getBooleanExtra(MainActivityStarter.EXTRA_OPEN_DRAWER, false)) {
            drawerLayout?.open()
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
        if (bottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED) {
            s = Snackbar.make(mainView, text!!, duration)
            if (audioPlayerFragmentView.visibility == View.VISIBLE) {
                s.setAnchorView(audioPlayerFragmentView)
            }
        } else {
            s = Snackbar.make(binding.root, text!!, duration)
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
        if (uri?.path == null) {
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
                    "STATISTCS" -> loadFragment(StatisticsFragment.TAG, null)
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
