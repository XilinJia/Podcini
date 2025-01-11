package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.net.feed.FeedUpdateManager.restartUpdateAlarm
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.defaultPage
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.ThemeSwitcher.getNoTitleTheme
import ac.mdiq.podcini.preferences.autoBackup
import ac.mdiq.podcini.receiver.MediaButtonReceiver.Companion.createIntent
import ac.mdiq.podcini.storage.database.Feeds.buildTags
import ac.mdiq.podcini.storage.database.Feeds.cancelMonitorFeeds
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.monitorFeeds
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.dialog.RatingDialog
import ac.mdiq.podcini.ui.screens.*
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.setOnlineFeedUrl
import ac.mdiq.podcini.ui.utils.setOnlineSearchTerms
import ac.mdiq.podcini.ui.utils.setSearchTerms
import ac.mdiq.podcini.ui.utils.starter.MainActivityStarter
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.StrictMode
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class MainActivity : CastEnabledActivity() {
    private var lastTheme = 0
    private var navigationBarInsets = Insets.NONE

    val prefs: SharedPreferences by lazy { getSharedPreferences("MainActivityPrefs", MODE_PRIVATE) }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        Toast.makeText(this, R.string.notification_permission_text, Toast.LENGTH_LONG).show()
        if (isGranted) {
            checkAndRequestUnrestrictedBackgroundActivity(this)
            return@registerForActivityResult
        }
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.notification_permission_text)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> checkAndRequestUnrestrictedBackgroundActivity(this) }
            .setNegativeButton(R.string.cancel_label) { _: DialogInterface?, _: Int -> checkAndRequestUnrestrictedBackgroundActivity(this) }
            .show()
    }

    var showUnrestrictedBackgroundPermissionDialog by mutableStateOf(false)

    enum class Screens {
        Subscriptions,
        FeedEpisodes,
        FeedInfo,
        FeedSettings,
        Episodes,
        EpisodeInfo,
        EpisodeHome,
        Queues,
        Search,
        OnlineSearch,
        OnlineFeed,
        OnlineEpisodes,
        Discovery,
        SearchResults,
        Logs,
        Statistics
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

        lifecycleScope.launch((Dispatchers.IO)) { buildTags() }

//        if (savedInstanceState != null) ensureGeneratedViewIdGreaterThan(savedInstanceState.getInt(Extras.generated_view_id.name, 0))

//        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Logd(TAG, "handleOnBackPressed called")
                when {
                    isDrawerOpen -> closeDrawer()
                    isBSExpanded -> isBSExpanded = false
                    mainNavController.previousBackStackEntry != null -> mainNavController.popBackStack()
                    else -> {
                        val toPage = defaultPage
                        if (getLastNavScreen() == toPage || AppPreferences.DefaultPages.Remember.name == toPage) {
                            if (getPref(AppPrefs.prefBackButtonOpensDrawer, false)) openDrawer()
                            else {
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                            }
                        } else loadScreen(toPage, null)
                    }
                }
            }
        })

        setContent {
            CustomTheme(this) {
//                if (showToast) CustomToast(message = toastMassege, onDismiss = { showToast = false })
                MainActivityUI()
            }
        }

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.notification_permission_text)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                .setNegativeButton(R.string.cancel_label) { _: DialogInterface?, _: Int -> checkAndRequestUnrestrictedBackgroundActivity(this@MainActivity) }
                .show()
        } else checkAndRequestUnrestrictedBackgroundActivity(this)

        runOnIOScope {  checkFirstLaunch() }

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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainActivityUI() {
        lcScope = rememberCoroutineScope()
        val navController = rememberNavController()
        mainNavController = navController
        val sheetState = rememberBottomSheetScaffoldState(bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded))

        if (showUnrestrictedBackgroundPermissionDialog) UnrestrictedBackgroundPermissionDialog { showUnrestrictedBackgroundPermissionDialog = false }

        LaunchedEffect(isBSExpanded) {
            if (isBSExpanded) lcScope?.launch { sheetState.bottomSheetState.expand() }
            else lcScope?.launch { sheetState.bottomSheetState.partialExpand() }
        }

        ModalNavigationDrawer(drawerState = drawerState, modifier = Modifier.fillMaxHeight(), drawerContent = { NavDrawerScreen() }) {
            val insets = WindowInsets.systemBars.asPaddingValues()
            var dynamicBottomPadding by remember {  mutableStateOf<Dp>(0.dp) }
            dynamicBottomPadding = insets.calculateBottomPadding()
            Logd(TAG, "effectiveBottomPadding: $dynamicBottomPadding")
            BottomSheetScaffold(scaffoldState = sheetState, sheetPeekHeight = dynamicBottomPadding + 110.dp, sheetDragHandle = {}, topBar = {},
                sheetSwipeEnabled = false, sheetShape = RectangleShape,
                sheetContent = { AudioPlayerScreen() }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(
                    top = paddingValues.calculateTopPadding(),
                    start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                    end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = paddingValues.calculateBottomPadding() - 110.dp
                )) {
                    CompositionLocalProvider(LocalNavController provides navController) {
                        NavHost(navController = navController, startDestination = Screens.Subscriptions.name) {
                            composable(Screens.Subscriptions.name) { SubscriptionsScreen() }
                            composable(Screens.FeedEpisodes.name) { FeedEpisodesScreen() }
                            composable(Screens.FeedInfo.name) { FeedInfoScreen() }
                            composable(Screens.FeedSettings.name) { FeedSettingsScreen() }
                            composable(Screens.EpisodeInfo.name) { EpisodeInfoScreen() }
                            composable(Screens.EpisodeHome.name) { EpisodeHomeScreen() }
                            composable(Screens.Episodes.name) { EpisodesScreen() }
                            composable(Screens.Queues.name) { QueuesScreen() }
                            composable(Screens.Search.name) { SearchScreen() }
                            composable(Screens.OnlineSearch.name) { OnlineSearchScreen() }
                            composable(Screens.Discovery.name) { DiscoveryScreen() }
                            composable(Screens.OnlineFeed.name) { OnlineFeedScreen() }
                            composable(Screens.OnlineEpisodes.name) { OnlineEpisodesScreen() }
                            composable(Screens.SearchResults.name) { SearchResultsScreen() }
                            composable(Screens.Logs.name) { LogsScreen() }
                            composable(Screens.Statistics.name) { StatisticsScreen() }
                            composable("DefaultPage") { SubscriptionsScreen() }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun UnrestrictedBackgroundPermissionDialog(onDismiss: () -> Unit) {
        var dontAskAgain by remember { mutableStateOf(false) }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text("Permission Required") },
            text = {
                Column {
                    Text(stringResource(R.string.unrestricted_background_permission_text))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = dontAskAgain, onCheckedChange = { dontAskAgain = it })
                        Text(stringResource(R.string.checkbox_do_not_show_again))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontAskAgain) prefs.edit().putBoolean("dont_ask_again_unrestricted_background", true).apply()
                    val intent = Intent()
                    intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    this@MainActivity.startActivity(intent)
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    fun checkAndRequestUnrestrictedBackgroundActivity(context: Context) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        val dontAskAgain = prefs.getBoolean("dont_ask_again_unrestricted_background", false)
        if (!isIgnoringBatteryOptimizations && !dontAskAgain) {
            showUnrestrictedBackgroundPermissionDialog = true
//            val composeView = ComposeView(this).apply {
//                setContent { UnrestrictedBackgroundPermissionDialog(onDismiss = { (parent as? ViewGroup)?.removeView(this) }) }
//            }
//            (window.decorView as? ViewGroup)?.addView(composeView)
        }
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
        //        setPlayerVisible(audioPlayerView.visibility == View.VISIBLE)
        val playerHeight = resources.getDimension(R.dimen.external_player_height).toInt()
        Logd(TAG, "playerHeight: $playerHeight ${navigationBarInsets.bottom}")
//        bottomSheet.peekHeight = playerHeight + navigationBarInsets.bottom
    }

    /**
     * View.generateViewId stores the current ID in a static variable.
     * When the process is killed, the variable gets reset.
     * This makes sure that we do not get ID collisions
     * and therefore errors when trying to restore state from another view.
     */
//    private fun ensureGeneratedViewIdGreaterThan(minimum: Int) {
//        while (View.generateViewId() <= minimum) {
//            // Generate new IDs
//        }
//    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(Extras.generated_view_id.name, View.generateViewId())
    }

//    fun setupToolbarToggle(toolbar: MaterialToolbar, displayUpArrow: Boolean) {
//        Logd(TAG, "setupToolbarToggle ${drawerLayout?.id} $displayUpArrow")
//        // Tablet layout does not have a drawer
//        when {
//            drawerLayout != null -> {
//                if (drawerToggle != null) drawerLayout!!.removeDrawerListener(drawerToggle!!)
//                drawerToggle = object : ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
//                    override fun onDrawerOpened(drawerView: View) {
//                        super.onDrawerOpened(drawerView)
//                        Logd(TAG, "Drawer opened")
////                        navDrawerFragment.loadData()
//                    }
//                }
//                drawerLayout!!.addDrawerListener(drawerToggle!!)
//                drawerToggle!!.syncState()
//                drawerToggle!!.isDrawerIndicatorEnabled = !displayUpArrow
//                drawerToggle!!.toolbarNavigationClickListener = View.OnClickListener { supportFragmentManager.popBackStack() }
//            }
//            !displayUpArrow -> toolbar.navigationIcon = null
//            else -> {
//                toolbar.setNavigationIcon(getDrawableFromAttr(this, androidx.appcompat.R.attr.homeAsUpIndicator))
//                toolbar.setNavigationOnClickListener { supportFragmentManager.popBackStack() }
//            }
//        }
//    }

    override fun onDestroy() {
        Logd(TAG, "onDestroy")
//        WorkManager.getInstance(this).pruneWork()
//        realm.close()
//        bottomSheet.removeBottomSheetCallback(bottomSheetCallback)
//        if (drawerToggle != null) drawerLayout?.removeDrawerListener(drawerToggle!!)
//        MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }

    private fun checkFirstLaunch() {
        if (prefs.getBoolean(Extras.prefMainActivityIsFirstLaunch.name, true)) {
            restartUpdateAlarm(this, true)
            val edit = prefs.edit()
            edit.putBoolean(Extras.prefMainActivityIsFirstLaunch.name, false)
            edit.apply()
        }
    }

    fun setPlayerVisible(visible_: Boolean?) {
        Logd(TAG, "setPlayerVisible $visible_")
//        val visible = visible_ ?: (bottomSheet.state != BottomSheetBehavior.STATE_COLLAPSED)

//        bottomSheet.setLocked(!visible)
//        if (visible) bottomSheetCallback.onStateChanged(dummyView, bottomSheet.state)    // Update toolbar visibility
//        else bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)

//        val params = mainView.layoutParams as MarginLayoutParams
//        val externalPlayerHeight = resources.getDimension(R.dimen.external_player_height).toInt()
//        Logd(TAG, "externalPlayerHeight: $externalPlayerHeight ${navigationBarInsets.bottom}")
//        params.setMargins(navigationBarInsets.left, 0, navigationBarInsets.right, navigationBarInsets.bottom + (if (visible) externalPlayerHeight else 0))
//        mainView.layoutParams = params
//        audioPlayerView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun isPlayerVisible(): Boolean = true
//        audioPlayerView.visibility == View.VISIBLE

    fun loadScreen(tag: String?, args: Bundle?) {
        var tag = tag
        var args = args
        Logd(TAG, "loadFragment(tag: $tag, args: $args)")
        when (tag) {
            Screens.Subscriptions.name, Screens.Queues.name, Screens.Logs.name, Screens.OnlineSearch.name, Screens.Episodes.name, Screens.Statistics.name ->
                mainNavController.navigate(tag)
            Screens.FeedEpisodes.name -> {
                if (args == null) {
                    val feedId = getLastNavScreenArg().toLongOrNull()
                    if (feedId != null) {
                        val feed = getFeed(feedId)
                        if (feed != null) {
                            feedOnDisplay = feed
                            mainNavController.navigate(tag)
                        }
                    } else mainNavController.navigate(Screens.Subscriptions.name)
                } else mainNavController.navigate(Screens.Subscriptions.name)
            }
            else -> {
                tag = Screens.Subscriptions.name
                mainNavController.navigate(tag)
            }
        }
        runOnIOScope { saveLastNavScreen(tag) }
    }

//    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
//        super.onRestoreInstanceState(savedInstanceState)
////        if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) bottomSheetCallback.onSlide(dummyView, 1.0f)
//    }

    public override fun onStart() {
        super.onStart()
        procFlowEvents()
        RatingDialog.init(this)
        monitorFeeds(lifecycleScope)
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
        cancelMonitorFeeds()
    }

    override fun onResume() {
        super.onResume()
        autoBackup(this)
        handleNavIntent()
        RatingDialog.check()
        if (lastTheme != getNoTitleTheme(this)) {
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        lastTheme = getNoTitleTheme(this) // Don't recreate activity when a result is pending
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Logd(TAG, "onOptionsItemSelected ${item.title}")
        when {
//            drawerToggle != null && drawerToggle!!.onOptionsItemSelected(item) -> return true // Tablet layout does not have a drawer
//            item.itemId == android.R.id.home -> {
//                if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
//                return true
//            }
            else -> return super.onOptionsItemSelected(item)
        }
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
//                        val snackbar = showSnackbarAbovePlayer(event.message, Snackbar.LENGTH_LONG)
//                        if (event.action != null) snackbar.setAction(event.actionText) { event.action.accept(this@MainActivity) }
                    }
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event -> Logd(TAG, "Received sticky event: ${event.TAG}") }
        }
    }

    private fun handleNavIntent() {
        Logd(TAG, "handleNavIntent()")
        val intent = intent
        when {
            intent.hasExtra(Extras.fragment_feed_id.name) -> {
                val feedId = intent.getLongExtra(Extras.fragment_feed_id.name, 0)
//                val args = intent.getBundleExtra(MainActivityStarter.Extras.fragment_args.name)
                Logd(TAG, "handleNavIntent: feedId: $feedId")
                if (feedId > 0) {
                    val startedFromShare = intent.getBooleanExtra(Extras.started_from_share.name, false)
                    val addToBackStack = intent.getBooleanExtra(Extras.add_to_back_stack.name, false)
                    Logd(TAG, "handleNavIntent: startedFromShare: $startedFromShare addToBackStack: $addToBackStack")
                    if (startedFromShare || addToBackStack) {
                        feedOnDisplay = getFeed(feedId) ?: Feed()
                        mainNavController.navigate(Screens.FeedEpisodes.name)
                    }
                    else {
                        feedOnDisplay = getFeed(feedId) ?: Feed()
                        mainNavController.navigate(Screens.FeedEpisodes.name)
                    }
                }
                isBSExpanded = false
            }
            intent.hasExtra(Extras.fragment_feed_url.name) -> {
                val feedurl = intent.getStringExtra(Extras.fragment_feed_url.name)
                val isShared = intent.getBooleanExtra(Extras.isShared.name, false)
                if (feedurl != null) {
                    setOnlineFeedUrl(feedurl, shared = isShared)
                    mainNavController.navigate(Screens.OnlineFeed.name)
                }
            }
            intent.hasExtra(Extras.search_string.name) -> {
                val query = intent.getStringExtra(Extras.search_string.name)
                setOnlineSearchTerms(CombinedSearcher::class.java, query)
                mainNavController.navigate(Screens.SearchResults.name)
            }
//            intent.hasExtra(MainActivityStarter.Extras.fragment_tag.name) -> {
//                val tag = intent.getStringExtra(MainActivityStarter.Extras.fragment_tag.name)
//                val args = intent.getBundleExtra(MainActivityStarter.Extras.fragment_args.name)
//                if (tag != null) loadScreen(tag, args)
//                collapseBottomSheet()
////                bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
//            }
            intent.getBooleanExtra(MainActivityStarter.Extras.open_player.name, false) -> {
                isBSExpanded = true
//                bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
//                bottomSheetCallback.onSlide(dummyView, 1.0f)
            }
            else -> handleDeeplink(intent.data)
        }
//        if (intent.getBooleanExtra(MainActivityStarter.Extras.open_drawer.name, false)) drawerLayout?.open()
//        if (intent.getBooleanExtra(MainActivityStarter.Extras.open_logs.name, false)) mainNavController.navigate(Screens.Logs.name)
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

    // TODO
//    fun showSnackbarAbovePlayer(text: CharSequence, duration: Int): Snackbar {
//        val s: Snackbar
//        if (bottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED) {
//            s = Snackbar.make(mainView, text, duration)
//            if (audioPlayerView.visibility == View.VISIBLE) s.anchorView = audioPlayerView
//        } else s = Snackbar.make(mainView, text, duration)
//        s.show()
//        return s
//    }

//    fun showSnackbarAbovePlayer(text: Int, duration: Int): Snackbar {
//        return showSnackbarAbovePlayer(resources.getText(text), duration)
//    }

    /**
     * Handles the deep link incoming via App Actions.
     * Performs an in-app search or opens the relevant feature of the app depending on the query
     * @param uri incoming deep link
     */
    private fun handleDeeplink(uri: Uri?) {
        if (uri?.path == null) return
        Logd(TAG, "Handling deeplink: $uri")
        when (uri.path) {
            "/deeplink/search" -> {
                val query = uri.getQueryParameter("query") ?: return
                setSearchTerms(query)
                mainNavController.navigate(Screens.Search.name)
            }
            "/deeplink/main" -> {
                val feature = uri.getQueryParameter("page") ?: return
                when (feature) {
                    "EPISODES" -> mainNavController.navigate(Screens.Episodes.name)
                    "QUEUE" -> mainNavController.navigate(Screens.Queues.name)
                    "SUBSCRIPTIONS" -> mainNavController.navigate(Screens.Subscriptions.name)
                    "STATISTCS" -> mainNavController.navigate(Screens.Statistics.name)
                    else -> {
//                        showSnackbarAbovePlayer(getString(R.string.app_action_not_found)+feature, Snackbar.LENGTH_LONG)
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

        lateinit var mainNavController: NavHostController
        val LocalNavController = staticCompositionLocalOf<NavController> { error("NavController not provided") }

        private val drawerState = DrawerState(initialValue = DrawerValue.Closed)
        var lcScope: CoroutineScope? = null

        fun openDrawer() {
            lcScope?.launch { drawerState.open() }
        }

        fun closeDrawer() {
            lcScope?.launch { drawerState.close() }
        }

        val isDrawerOpen: Boolean
            get() = drawerState.isOpen

        var isBSExpanded by mutableStateOf(false)

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
