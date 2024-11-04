package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.MainActivityBinding
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.net.feed.FeedUpdateManager.restartUpdateAlarm
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.net.feed.discovery.CombinedSearcher
import ac.mdiq.podcini.net.feed.discovery.ItunesTopListLoader
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.preferences.ThemeSwitcher.getNoTitleTheme
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.backButtonOpensDrawer
import ac.mdiq.podcini.preferences.UserPreferences.defaultPage
import ac.mdiq.podcini.preferences.UserPreferences.hiddenDrawerItems
import ac.mdiq.podcini.receiver.MediaButtonReceiver.Companion.createIntent
import ac.mdiq.podcini.receiver.PlayerWidget
import ac.mdiq.podcini.storage.database.Feeds.buildTags
import ac.mdiq.podcini.storage.database.Feeds.monitorFeeds
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.dialog.RatingDialog
import ac.mdiq.podcini.ui.fragment.*
import ac.mdiq.podcini.ui.fragment.AudioPlayerFragment.Companion.media3Controller
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.ui.utils.ThemeUtils.getDrawableFromAttr
import ac.mdiq.podcini.ui.utils.TransitionEffect
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.StrictMode
import android.provider.Settings
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope

import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.min

/**
 * The activity that is shown when the user launches the app.
 */

class MainActivity : CastEnabledActivity() {
    private var drawerLayout: DrawerLayout? = null

    private var _binding: MainActivityBinding? = null
    private val binding get() = _binding!!

    private lateinit var mainView: View
    private lateinit var navDrawerFragment: NavDrawerFragment
    private lateinit var audioPlayerFragment: AudioPlayerFragment
    private lateinit var audioPlayerView: View
    private lateinit var navDrawer: View
    private lateinit var dummyView : View
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    lateinit var bottomSheet: BottomSheetBehavior<*>
        private set

    private var drawerToggle: ActionBarDrawerToggle? = null

    @JvmField
    val recycledViewPool: RecyclerView.RecycledViewPool = RecyclerView.RecycledViewPool()
    private var lastTheme = 0
    private var navigationBarInsets = Insets.NONE

//    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
//        if (isGranted) return@registerForActivityResult
//        MaterialAlertDialogBuilder(this)
//            .setMessage(R.string.notification_permission_text)
//            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> }
//            .setNegativeButton(R.string.cancel_label) { _: DialogInterface?, _: Int -> finish() }
//            .show()
//    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        Toast.makeText(this, R.string.notification_permission_text, Toast.LENGTH_LONG).show()
//
//        if (isGranted) return@registerForActivityResult

//        MaterialAlertDialogBuilder(this)
//            .setMessage(R.string.notification_permission_text)
//            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
//            }
//            .setNegativeButton(R.string.cancel_label) { _: DialogInterface?, _: Int ->
//            }
//            .show()

        if (isGranted) {
            checkAndRequestUnrestrictedBackgroundActivity(this)
            return@registerForActivityResult
        }
//        checkAndRequestUnrestrictedBackgroundActivity(this)

        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.notification_permission_text)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                checkAndRequestUnrestrictedBackgroundActivity(this)
            }
            .setNegativeButton(R.string.cancel_label) { _: DialogInterface?, _: Int ->
                checkAndRequestUnrestrictedBackgroundActivity(this)
            }
            .show()
    }

    fun checkAndRequestUnrestrictedBackgroundActivity(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        if (!isIgnoringBatteryOptimizations) {
//            Toast.makeText(context, "Please allow unrestricted background activity for this app", Toast.LENGTH_LONG).show()
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.unrestricted_background_permission_text)
                .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, _: Int ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent()
                        intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
//                        intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).also {
//                            val uri = Uri.parse("package:$packageName")
//                            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                            it.data = uri
//                        }
                        context.startActivity(intent)
                    } else dialog?.dismiss()
                }
                .setNegativeButton(R.string.cancel_label) { dialog, which -> dialog.dismiss() }
                .show()
        }
    }

    private var prevState: Int = 0
    private val bottomSheetCallback: BottomSheetCallback =  object : BottomSheetCallback() {
        override fun onStateChanged(view: View, state: Int) {
            Logd(TAG, "bottomSheet onStateChanged $state ${view.id}")
            when (state) {
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    audioPlayerFragment.onCollaped()
                    onSlide(view,0.0f)
                    prevState = state
                }
                BottomSheetBehavior.STATE_EXPANDED -> {
                    audioPlayerFragment.onExpanded()
                    onSlide(view, 1.0f)
                    prevState = state
                }
                else -> {}
            }
        }
        override fun onSlide(view: View, slideOffset: Float) {
//            val audioPlayer = supportFragmentManager.findFragmentByTag(AudioPlayerFragment.TAG) as? AudioPlayerFragment ?: return
//            if (slideOffset == 0.0f) { //STATE_COLLAPSED
//                audioPlayer.scrollToTop()
//            }
//            audioPlayer.fadePlayerToToolbar(slideOffset)
        }
    }

    private val isDrawerOpen: Boolean
        get() = drawerLayout?.isDrawerOpen(navDrawer)?:false

    private val screenWidth: Int
        get() {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.widthPixels
        }

     public override fun onCreate(savedInstanceState: Bundle?) {
        lastTheme = getNoTitleTheme(this)
        setTheme(lastTheme)

        if (BuildConfig.DEBUG) {
            val builder = StrictMode.ThreadPolicy.Builder()
                .detectAll()  // Enable all detections
                .penaltyLog()  // Log violations to the console
                .penaltyDropBox()
            StrictMode.setThreadPolicy(builder.build())
        }

        val ioScope = CoroutineScope(Dispatchers.IO)
//       init shared preferences
        ioScope.launch {
//            RealmDB.apply { }
//            EpisodesRecyclerView.getSharedPrefs(this@MainActivity)
            NavDrawerFragment.getSharedPrefs(this@MainActivity)
            SwipeActions.getSharedPrefs(this@MainActivity)
            QueuesFragment.getSharedPrefs(this@MainActivity)
//            updateFeedMap()
            buildTags()
            monitorFeeds()
//            InTheatre.apply { }
            AudioPlayerFragment.getSharedPrefs(this@MainActivity)
            PlayerWidget.getSharedPrefs(this@MainActivity)
            StatisticsFragment.getSharedPrefs(this@MainActivity)
            OnlineFeedFragment.getSharedPrefs(this@MainActivity)
            ItunesTopListLoader.getSharedPrefs(this@MainActivity)
        }

        if (savedInstanceState != null) ensureGeneratedViewIdGreaterThan(savedInstanceState.getInt(Extras.generated_view_id.name, 0))

        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        _binding = MainActivityBinding.inflate(layoutInflater)
//        setContentView(R.layout.main_activity)
        setContentView(binding.root)
        recycledViewPool.setMaxRecycledViews(R.id.view_type_episode_item, 25)
        dummyView = object : View(this) {}
        drawerLayout = findViewById(R.id.main_layout)
        navDrawer = findViewById(R.id.navDrawerFragment)
        setNavDrawerSize()
        mainView = findViewById(R.id.main_view)

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.notification_permission_text)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .setNegativeButton(R.string.cancel_label) { _: DialogInterface?, _: Int ->
                    checkAndRequestUnrestrictedBackgroundActivity(this@MainActivity)
                }
                .show()
        } else checkAndRequestUnrestrictedBackgroundActivity(this)

        // Consume navigation bar insets - we apply them in setPlayerVisible()
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { _: View?, insets: WindowInsetsCompat ->
            navigationBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            updateInsets()
            WindowInsetsCompat.Builder(insets).setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE).build()
        }

        val fm = supportFragmentManager
        if (fm.findFragmentByTag(MAIN_FRAGMENT_TAG) == null) {
            if (UserPreferences.DEFAULT_PAGE_REMEMBER != defaultPage) loadFragment(defaultPage, null)
            else {
                val lastFragment = NavDrawerFragment.getLastNavFragment()
                if (NavDrawerFragment.navMap.keys.contains(lastFragment)) loadFragment(lastFragment, null)
                else {
                    // it's not a number, this happens if we removed a label from the NAV_DRAWER_TAGS  give them a nice default...
                    try { loadFeedFragmentById(lastFragment.toInt().toLong(), null) }
                    catch (e: NumberFormatException) { loadFragment(SubscriptionsFragment.TAG, null) }
                }
            }
        }

        val transaction = fm.beginTransaction()
        navDrawerFragment = NavDrawerFragment()
        transaction.replace(R.id.navDrawerFragment, navDrawerFragment, NavDrawerFragment.TAG)
        audioPlayerFragment = AudioPlayerFragment()
        transaction.replace(R.id.audioplayerFragment, audioPlayerFragment, AudioPlayerFragment.TAG)
        transaction.commit()
        navDrawer = findViewById(R.id.navDrawerFragment)
        audioPlayerView = findViewById(R.id.audioplayerFragment)

        runOnIOScope {  checkFirstLaunch() }

        this.bottomSheet = BottomSheetBehavior.from(audioPlayerView)
        this.bottomSheet.isHideable = false
        this.bottomSheet.isDraggable = false
        this.bottomSheet.addBottomSheetCallback(bottomSheetCallback)

        restartUpdateAlarm(this, false)
        runOnIOScope {  SynchronizationQueueSink.syncNowIfNotSyncedRecently() }

        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData(FeedUpdateManager.WORK_TAG_FEED_UPDATE)
            .observe(this) { workInfos: List<WorkInfo> ->
                var isRefreshingFeeds = false
                for (workInfo in workInfos) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> isRefreshingFeeds = true
                        WorkInfo.State.ENQUEUED -> isRefreshingFeeds = true
                        else -> {}
                    }
                }
                EventFlow.postStickyEvent(FlowEvent.FeedUpdatingEvent(isRefreshingFeeds))
            }
        observeDownloads()
    }

    private fun observeDownloads() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { WorkManager.getInstance(this@MainActivity).pruneWork().result.get() }
            WorkManager.getInstance(this@MainActivity)
                .getWorkInfosByTagLiveData(DownloadServiceInterface.WORK_TAG)
                .observe(this@MainActivity) { workInfos: List<WorkInfo> ->
                    val updatedEpisodes: MutableMap<String, DownloadStatus> = HashMap()
                    for (workInfo in workInfos) {
                        var downloadUrl: String? = null
                        for (tag in workInfo.tags) {
                            if (tag.startsWith(DownloadServiceInterface.WORK_TAG_EPISODE_URL))
                                downloadUrl = tag.substring(DownloadServiceInterface.WORK_TAG_EPISODE_URL.length)
                        }
                        if (downloadUrl == null) continue

//                        Logd(TAG, "workInfo.state: ${workInfo.state}")
                        var status: Int
                        status = when (workInfo.state) {
                            WorkInfo.State.RUNNING -> DownloadStatus.State.RUNNING.ordinal
                            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.State.QUEUED.ordinal
                            WorkInfo.State.SUCCEEDED -> DownloadStatus.State.COMPLETED.ordinal
                            WorkInfo.State.FAILED -> {
                                Log.e(TAG, "download failed $downloadUrl")
                                DownloadStatus.State.COMPLETED.ordinal
                            }
                            WorkInfo.State.CANCELLED -> {
                                Logd(TAG, "download cancelled $downloadUrl")
                                DownloadStatus.State.COMPLETED.ordinal
                            }
                        }
                        var progress = workInfo.progress.getInt(DownloadServiceInterface.WORK_DATA_PROGRESS, -1)
                        if (progress == -1 && status != DownloadStatus.State.COMPLETED.ordinal) {
                            status = DownloadStatus.State.QUEUED.ordinal
                            progress = 0
                        }
                        updatedEpisodes[downloadUrl] = DownloadStatus(status, progress)
                    }
                    DownloadServiceInterface.get()?.setCurrentDownloads(updatedEpisodes)
                    EventFlow.postStickyEvent(FlowEvent.EpisodeDownloadEvent(updatedEpisodes))
                }
        }
    }
    //    fun requestPostNotificationPermission() {
//        if (Build.VERSION.SDK_INT >= 33) requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
//    }

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
        outState.putInt(Extras.generated_view_id.name, View.generateViewId())
    }

    fun setupToolbarToggle(toolbar: MaterialToolbar, displayUpArrow: Boolean) {
        Logd(TAG, "setupToolbarToggle ${drawerLayout?.id} $displayUpArrow")
        // Tablet layout does not have a drawer
        when {
            drawerLayout != null -> {
                if (drawerToggle != null) drawerLayout!!.removeDrawerListener(drawerToggle!!)
                drawerToggle = object : ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
                    override fun onDrawerOpened(drawerView: View) {
                        super.onDrawerOpened(drawerView)
                        Logd(TAG, "Drawer opened")
                        navDrawerFragment.loadData()
                    }
                }
                drawerLayout!!.addDrawerListener(drawerToggle!!)
                drawerToggle!!.syncState()
                drawerToggle!!.isDrawerIndicatorEnabled = !displayUpArrow
                drawerToggle!!.toolbarNavigationClickListener = View.OnClickListener { supportFragmentManager.popBackStack() }
            }
            !displayUpArrow -> toolbar.navigationIcon = null
            else -> {
                toolbar.setNavigationIcon(getDrawableFromAttr(this, androidx.appcompat.R.attr.homeAsUpIndicator))
                toolbar.setNavigationOnClickListener { supportFragmentManager.popBackStack() }
            }
        }
    }

    override fun onDestroy() {
        Logd(TAG, "onDestroy")
//        WorkManager.getInstance(this).pruneWork()
        _binding = null
//        realm.close()
        bottomSheet.removeBottomSheetCallback(bottomSheetCallback)
        drawerLayout?.removeDrawerListener(drawerToggle!!)
        MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }

    private fun checkFirstLaunch() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(Extras.prefMainActivityIsFirstLaunch.name, true)) {
            restartUpdateAlarm(this, true)
            val edit = prefs.edit()
            edit.putBoolean(Extras.prefMainActivityIsFirstLaunch.name, false)
            edit.apply()
        }
    }

    private fun updateInsets() {
        setPlayerVisible(audioPlayerView.visibility == View.VISIBLE)
        val playerHeight = resources.getDimension(R.dimen.external_player_height).toInt()
        Logd(TAG, "playerHeight: $playerHeight ${navigationBarInsets.bottom}")
        bottomSheet.peekHeight = playerHeight + navigationBarInsets.bottom
    }

    fun setPlayerVisible(visible_: Boolean?) {
        Logd(TAG, "setPlayerVisible $visible_")
        val visible = visible_ ?: (bottomSheet.state != BottomSheetBehavior.STATE_COLLAPSED)

//        bottomSheet.setLocked(!visible)
        if (visible) bottomSheetCallback.onStateChanged(dummyView, bottomSheet.state)    // Update toolbar visibility
        else bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)

        val params = mainView.layoutParams as MarginLayoutParams
        val externalPlayerHeight = resources.getDimension(R.dimen.external_player_height).toInt()
        Logd(TAG, "externalPlayerHeight: $externalPlayerHeight ${navigationBarInsets.bottom}")
        params.setMargins(navigationBarInsets.left, 0, navigationBarInsets.right,
            navigationBarInsets.bottom + (if (visible) externalPlayerHeight else 0))
        mainView.layoutParams = params
        audioPlayerView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun isPlayerVisible(): Boolean {
        return audioPlayerView.visibility == View.VISIBLE
    }

    fun loadFragment(tag: String?, args: Bundle?) {
        var tag = tag
        var args = args
        Logd(TAG, "loadFragment(tag: $tag, args: $args)")
        val fragment: Fragment
        when (tag) {
            QueuesFragment.TAG -> fragment = QueuesFragment()
            AllEpisodesFragment.TAG -> fragment = AllEpisodesFragment()
            DownloadsFragment.TAG -> fragment = DownloadsFragment()
            LogsFragment.TAG -> fragment = LogsFragment()
//            SubscriptionLogFragment.TAG -> fragment = SubscriptionLogFragment()
            HistoryFragment.TAG -> fragment = HistoryFragment()
            OnlineSearchFragment.TAG -> fragment = OnlineSearchFragment()
            SubscriptionsFragment.TAG -> fragment = SubscriptionsFragment()
            StatisticsFragment.TAG -> fragment = StatisticsFragment()
            FeedEpisodesFragment.TAG -> fragment = FeedEpisodesFragment()
            else -> {
                // default to subscriptions screen
                fragment = SubscriptionsFragment()
                tag = SubscriptionsFragment.TAG
                args = null
            }
        }
        if (args != null) fragment.arguments = args
        runOnIOScope { NavDrawerFragment.saveLastNavFragment(tag) }
        loadFragment(fragment)
    }

    fun loadFeedFragmentById(feedId: Long, args: Bundle?) {
        val fragment: Fragment = FeedEpisodesFragment.newInstance(feedId)
        if (args != null) fragment.arguments = args
        NavDrawerFragment.saveLastNavFragment(feedId.toString())
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
            TransitionEffect.SLIDE -> transaction.setCustomAnimations(R.anim.slide_right_in, R.anim.slide_left_out, R.anim.slide_left_in, R.anim.slide_right_out)
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
        if (drawerLayout == null) return
        val screenPercent = resources.getInteger(R.integer.nav_drawer_screen_size_percent) * 0.01f
        val width = (screenWidth * screenPercent).toInt()
        val maxWidth = resources.getDimension(R.dimen.nav_drawer_max_screen_size).toInt()
        navDrawer.layoutParams.width = min(width.toDouble(), maxWidth.toDouble()).toInt()
        Logd(TAG, "setNavDrawerSize: ${navDrawer.layoutParams.width}")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) bottomSheetCallback.onSlide(dummyView, 1.0f)
    }

    public override fun onStart() {
        super.onStart()
        procFlowEvents()
        RatingDialog.init(this)
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            media3Controller = controllerFuture.get()
//            Logd(TAG, "controllerFuture.addListener: $mediaController")
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    override fun onResume() {
        super.onResume()
        handleNavIntent()
        RatingDialog.check()
        if (lastTheme != getNoTitleTheme(this)) {
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }
        if (hiddenDrawerItems.contains(NavDrawerFragment.getLastNavFragment())) loadFragment(defaultPage, null)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        lastTheme = getNoTitleTheme(this) // Don't recreate activity when a result is pending
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Logd(TAG, "onOptionsItemSelected ${item.title}")
        when {
            drawerToggle != null && drawerToggle!!.onOptionsItemSelected(item) -> return true // Tablet layout does not have a drawer
            item.itemId == android.R.id.home -> {
                if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            isDrawerOpen -> drawerLayout?.closeDrawer(navDrawer)
            bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED -> bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
            supportFragmentManager.backStackEntryCount != 0 -> super.onBackPressed()
            else -> {
                val toPage = defaultPage
                if (NavDrawerFragment.getLastNavFragment() == toPage || UserPreferences.DEFAULT_PAGE_REMEMBER == toPage) {
                    if (backButtonOpensDrawer()) drawerLayout?.openDrawer(navDrawer)
                    else super.onBackPressed()
                } else loadFragment(toPage, null)
            }
        }
    }

    fun openDrawer() {
        drawerLayout?.openDrawer(navDrawer)
    }

    private var eventSink: Job?     = null
    private var eventStickySink: Job? = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
    }
    private fun procFlowEvents() {
        if (eventSink == null) eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.MessageEvent -> {
                        val snackbar = showSnackbarAbovePlayer(event.message, Snackbar.LENGTH_LONG)
                        if (event.action != null) snackbar.setAction(event.actionText) { event.action.accept(this@MainActivity) }
                    }
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
//                when (event) {
//                    else -> {}
//                }
            }
        }
    }

    private fun handleNavIntent() {
        Logd(TAG, "handleNavIntent()")
        val intent = intent
        when {
            intent.hasExtra(Extras.fragment_feed_id.name) -> {
                val feedId = intent.getLongExtra(Extras.fragment_feed_id.name, 0)
                val args = intent.getBundleExtra(MainActivityStarter.Extras.fragment_args.name)
                if (feedId > 0) {
                    val startedFromShare = intent.getBooleanExtra(Extras.started_from_share.name, false)
                    val addToBackStack = intent.getBooleanExtra(Extras.add_to_back_stack.name, false)
                    if (startedFromShare || addToBackStack) loadChildFragment(FeedEpisodesFragment.newInstance(feedId))
                    else loadFeedFragmentById(feedId, args)
                }
                bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
            intent.hasExtra(Extras.fragment_feed_url.name) -> {
                val feedurl = intent.getStringExtra(Extras.fragment_feed_url.name)
                val isShared = intent.getBooleanExtra(Extras.isShared.name, false)
                if (feedurl != null) loadChildFragment(OnlineFeedFragment.newInstance(feedurl, isShared))
            }
            intent.hasExtra(Extras.search_string.name) -> {
                val query = intent.getStringExtra(Extras.search_string.name)
                if (query != null) loadChildFragment(SearchResultsFragment.newInstance(CombinedSearcher::class.java, query))
            }
            intent.hasExtra(MainActivityStarter.Extras.fragment_tag.name) -> {
                val tag = intent.getStringExtra(MainActivityStarter.Extras.fragment_tag.name)
                val args = intent.getBundleExtra(MainActivityStarter.Extras.fragment_args.name)
                if (tag != null) loadFragment(tag, args)
                bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
            intent.getBooleanExtra(MainActivityStarter.Extras.open_player.name, false) -> {
//                bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
//                bottomSheetCallback.onSlide(dummyView, 1.0f)
            }
            else -> handleDeeplink(intent.data)
        }
        if (intent.getBooleanExtra(MainActivityStarter.Extras.open_drawer.name, false)) drawerLayout?.open()
//        if (intent.getBooleanExtra(MainActivityStarter.Extras.open_download_logs.name, false))
//            DownloadLogFragment().show(supportFragmentManager, null)
        if (intent.getBooleanExtra(MainActivityStarter.Extras.open_logs.name, false))
            loadChildFragment(LogsFragment())
        if (intent.getBooleanExtra(Extras.refresh_on_start.name, false)) runOnceOrAsk(this)

        // to avoid handling the intent twice when the configuration changes
        setIntent(Intent(this@MainActivity, MainActivity::class.java))
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent()
    }

    fun showSnackbarAbovePlayer(text: CharSequence, duration: Int): Snackbar {
        val s: Snackbar
        if (bottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED) {
            s = Snackbar.make(mainView, text, duration)
            if (audioPlayerView.visibility == View.VISIBLE) s.setAnchorView(audioPlayerView)
        } else s = Snackbar.make(binding.root, text, duration)

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
        if (uri?.path == null) return

        Logd(TAG, "Handling deeplink: $uri")
        when (uri.path) {
            "/deeplink/search" -> {
                val query = uri.getQueryParameter("query") ?: return
                this.loadChildFragment(SearchFragment.newInstance(query))
            }
            "/deeplink/main" -> {
                val feature = uri.getQueryParameter("page") ?: return
                when (feature) {
                    "DOWNLOADS" -> loadFragment(DownloadsFragment.TAG, null)
                    "HISTORY" -> loadFragment(HistoryFragment.TAG, null)
                    "EPISODES" -> loadFragment(AllEpisodesFragment.TAG, null)
                    "QUEUE" -> loadFragment(QueuesFragment.TAG, null)
                    "SUBSCRIPTIONS" -> loadFragment(SubscriptionsFragment.TAG, null)
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
        if (currentFocus is EditText) return super.onKeyUp(keyCode, event)

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        var customKeyCode: Int? = null
        EventFlow.postEvent(event)

        when (keyCode) {
            KeyEvent.KEYCODE_P -> customKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> customKeyCode = KeyEvent.KEYCODE_MEDIA_REWIND
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> customKeyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_W -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_M -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                return true
            }
        }
        if (customKeyCode != null) {
            sendBroadcast(createIntent(this, customKeyCode))
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    @Suppress("EnumEntryName")
    enum class Extras {
        prefMainActivityIsFirstLaunch,
        fragment_feed_id,
        fragment_feed_url,
        refresh_on_start,
        started_from_share, // TODO: seems not needed
        add_to_back_stack,
        generated_view_id,
        search_string,
        isShared
    }

    companion object {
        private val TAG: String = MainActivity::class.simpleName ?: "Anonymous"
        const val MAIN_FRAGMENT_TAG: String = "main"
        const val PREF_NAME: String = "MainActivityPrefs"

        const val REQUEST_CODE_FIRST_PERMISSION = 1001
        const val REQUEST_CODE_SECOND_PERMISSION = 1002

        @JvmStatic
        fun getIntentToOpenFeed(context: Context, feedId: Long): Intent {
            val intent = Intent(context.applicationContext, MainActivity::class.java)
            intent.putExtra(Extras.fragment_feed_id.name, feedId)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        @JvmStatic
        fun showOnlineFeed(context: Context, feedUrl: String, isShared: Boolean = false): Intent {
            val intent = Intent(context.applicationContext, MainActivity::class.java)
            intent.putExtra(Extras.fragment_feed_url.name, feedUrl)
            intent.putExtra(Extras.isShared.name, isShared)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        @JvmStatic
        fun showOnlineSearch(context: Context, query: String): Intent {
            val intent = Intent(context.applicationContext, MainActivity::class.java)
            intent.putExtra(Extras.search_string.name, query)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }
    }
}
