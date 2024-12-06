package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.PodciniApp.Companion.forceRestart
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.*
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.newBuilder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.reinit
import ac.mdiq.podcini.net.feed.FeedUpdateManager.restartUpdateAlarm
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.SyncService.Companion.isValidGuid
import ac.mdiq.podcini.net.sync.SynchronizationCredentials
import ac.mdiq.podcini.net.sync.SynchronizationProviderViewData
import ac.mdiq.podcini.net.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.setSelectedSyncProvider
import ac.mdiq.podcini.net.sync.SynchronizationSettings.setWifiSyncEnabled
import ac.mdiq.podcini.net.sync.SynchronizationSettings.wifiSyncEnabledKey
import ac.mdiq.podcini.net.sync.gpoddernet.GpodnetService
import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetDevice
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.EpisodeAction.Companion.readFromJsonObject
import ac.mdiq.podcini.net.sync.model.SyncServiceException
import ac.mdiq.podcini.net.sync.nextcloud.NextcloudLoginFlow
import ac.mdiq.podcini.net.sync.wifi.WifiSyncService.Companion.hostPort
import ac.mdiq.podcini.net.sync.wifi.WifiSyncService.Companion.startInstantSync
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.prefPlaybackSpeed
import ac.mdiq.podcini.preferences.*
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlElement
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.preferences.ThemeSwitcher.getTheme
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.fallbackSpeed
import ac.mdiq.podcini.preferences.UserPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.UserPreferences.fullNotificationButtons
import ac.mdiq.podcini.preferences.UserPreferences.proxyConfig
import ac.mdiq.podcini.preferences.UserPreferences.rewindSecs
import ac.mdiq.podcini.preferences.UserPreferences.setVideoMode
import ac.mdiq.podcini.preferences.UserPreferences.speedforwardSpeed
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.hasAlmostEnded
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Queues.EnqueueLocation
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.FileNameGenerator.generateFileName
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsDialog
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.fragment.EpisodesFragment
import ac.mdiq.podcini.ui.fragment.FeedEpisodesFragment
import ac.mdiq.podcini.ui.fragment.QueuesFragment
import ac.mdiq.podcini.ui.utils.ThemeUtils.getColorFromAttr
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.*
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.text.format.Formatter
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.util.Patterns
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import okhttp3.Credentials.basic
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Request.Builder
import okhttp3.Response
import okhttp3.Route
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import java.io.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.round

/**
 * PreferenceActivity for API 11+. In order to change the behavior of the preference UI, see PreferenceController.
 */
class PreferenceActivity : AppCompatActivity() {
    var copyrightNoticeText by mutableStateOf("")

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTheme(this))
        super.onCreate(savedInstanceState)

        val packageHash = packageName.hashCode()
        when {
            packageHash != 1329568231 && packageHash != 1297601420 -> {
                copyrightNoticeText = ("This application is based on Podcini."
                        + " The Podcini team does NOT provide support for this unofficial version."
                        + " If you can read this message, the developers of this modification"
                        + " violate the GNU General Public License (GPL).")
            }
            packageHash == 1297601420 -> copyrightNoticeText = "This is a development version of Podcini and not meant for daily use"
        }

        Logd("PreferenceActivity", "onCreate")
        val ab = supportActionBar
        ab?.setDisplayHomeAsUpEnabled(true)

        setContent {
            val navController = rememberNavController()
            CustomTheme(this) {
                NavHost(navController = navController, startDestination = "Main") {
                    composable("Main") { MainPreferencesScreen(navController) }
                    composable(Screens.preferences_user_interface.tag) { UserInterfacePreferencesScreen(navController) }
                    composable(Screens.preferences_downloads.tag) { DownloadsPreferencesScreen(navController) }
                    composable(Screens.preferences_import_export.tag) { ImportExportPreferencesScreen() }
                    composable(Screens.preferences_autodownload.tag) { AutoDownloadPreferencesScreen() }
                    composable(Screens.preferences_synchronization.tag) { SynchronizationPreferencesScreen() }
                    composable(Screens.preferences_playback.tag) { PlaybackPreferencesScreen() }
                    composable(Screens.preferences_notifications.tag) { NotificationPreferencesScreen() }
                    composable(Screens.preferences_swipe.tag) { SwipePreferencesScreen() }
                    composable(Screens.preferences_swipe.tag) { SwipePreferencesScreen() }
                    composable(Screens.preferences_about.tag) { AboutScreen(navController) }
                    composable(Screens.preferences_license.tag) { LicensesScreen() }
                }
            }
        }

//        val intent = intent
//        if (intent.getBooleanExtra(OPEN_AUTO_DOWNLOAD_SETTINGS, false)) openScreen(Screens.preferences_autodownload)
    }

//    fun openScreen(screen: Screens): PreferenceFragmentCompat {
//        val fragment = when (screen) {
////            Screens.preferences_user_interface -> UserInterfacePreferencesFragment()
//            Screens.preferences_downloads -> DownloadsPreferencesFragment()
//            Screens.preferences_import_export -> ImportExportPreferencesFragment()
//            Screens.preferences_autodownload -> AutoDownloadPreferencesFragment()
//            Screens.preferences_synchronization -> SynchronizationPreferencesFragment()
//            Screens.preferences_playback -> PlaybackPreferencesFragment()
//            Screens.preferences_notifications -> NotificationPreferencesFragment()
//            Screens.preferences_swipe -> SwipePreferencesFragment()
//        }
//        if (screen == Screens.preferences_notifications && Build.VERSION.SDK_INT >= 26) {
//            val intent = Intent()
//            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
//            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
//            startActivity(intent)
//        }
////        else
////            supportFragmentManager.beginTransaction().replace(binding.settingsContainer.id, fragment).addToBackStack(getString(screen.titleRes)).commit()
//        return fragment
//    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount == 0) finish()
            else {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                var view = currentFocus
                //If no view currently has focus, create a new one, just so we can grab a window token from it
                if (view == null) view = View(this)
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                supportFragmentManager.popBackStack()
            }
            return true
        }
        return false
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd("PreferenceActivity", "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.MessageEvent -> onEventMainThread(event)
                    else -> {}
                }
            }
        }
    }

    fun onEventMainThread(event: FlowEvent.MessageEvent) {
//        Logd(FRAGMENT_TAG, "onEvent($event)")
        val s = Snackbar.make(findViewById(android.R.id.content), event.message, Snackbar.LENGTH_LONG)
        if (event.action != null) s.setAction(event.actionText) { event.action.accept(this) }
        s.show()
    }

    @Composable
    fun MainPreferencesScreen(navController: NavController) {
        supportActionBar!!.setTitle(R.string.settings_label)

        @Composable
        fun IconTitleSummaryScreenRow(vecRes: Int, titleRes: Int, summaryRes: Int, screen: String) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                Icon(imageVector = ImageVector.vectorResource(vecRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                Column(modifier = Modifier.weight(1f).clickable(onClick = {
                    navController.navigate(screen)
                })) {
                    Text(stringResource(titleRes), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(summaryRes), color = textColor)
                }
            }
        }

        @Composable
        fun IconTitleActionRow(vecRes: Int, titleRes: Int, callback: ()-> Unit) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                Icon(imageVector = ImageVector.vectorResource(vecRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                Column(modifier = Modifier.weight(1f).clickable(onClick = { callback() })) {
                    Text(stringResource(titleRes), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp).verticalScroll(scrollState)) {
            if (copyrightNoticeText.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = "", tint = Color.Red, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                    Text(copyrightNoticeText, color = textColor)
                }
            }
            IconTitleSummaryScreenRow(R.drawable.ic_appearance, R.string.user_interface_label, R.string.user_interface_sum, Screens.preferences_user_interface.tag)
            IconTitleSummaryScreenRow(R.drawable.ic_play_24dp, R.string.playback_pref, R.string.playback_pref_sum, Screens.preferences_playback.tag)
            IconTitleSummaryScreenRow(R.drawable.ic_download, R.string.downloads_pref, R.string.downloads_pref_sum, Screens.preferences_downloads.tag)
            IconTitleSummaryScreenRow(R.drawable.ic_cloud, R.string.synchronization_pref, R.string.synchronization_sum, Screens.preferences_synchronization.tag)
            IconTitleSummaryScreenRow(R.drawable.ic_storage, R.string.import_export_pref, R.string.import_export_summary, Screens.preferences_import_export.tag)
            IconTitleActionRow(R.drawable.ic_notifications, R.string.notification_pref_fragment) {
                navController.navigate(Screens.preferences_notifications.tag)
//               openScreen(Screens.preferences_notifications)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_backup_on_google_title), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pref_backup_on_google_sum), color = textColor)
                }
                var isChecked by remember { mutableStateOf(appPrefs.getBoolean(UserPreferences.Prefs.prefOPMLBackup.name, true)) }
                Switch(checked = isChecked, onCheckedChange = {
                    isChecked = it
                    appPrefs.edit().putBoolean(UserPreferences.Prefs.prefOPMLBackup.name, it).apply()
                    // Restart the app
                    val intent = packageManager?.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                })
            }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp).padding(top = 10.dp))
            Text(stringResource(R.string.project_pref), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            IconTitleActionRow(R.drawable.ic_questionmark, R.string.documentation_support) { openInBrowser(this@PreferenceActivity, "https://github.com/XilinJia/Podcini") }
            IconTitleActionRow(R.drawable.ic_chat, R.string.visit_user_forum) { openInBrowser(this@PreferenceActivity, "https://github.com/XilinJia/Podcini/discussions") }
            IconTitleActionRow(R.drawable.ic_contribute, R.string.pref_contribute) { openInBrowser(this@PreferenceActivity, "https://github.com/XilinJia/Podcini") }
            IconTitleActionRow(R.drawable.ic_bug, R.string.bug_report_title) { startActivity(Intent(this@PreferenceActivity, BugReportActivity::class.java)) }
            IconTitleActionRow(R.drawable.ic_info, R.string.about_pref) {
                navController.navigate(Screens.preferences_about.tag)
//                supportFragmentManager.beginTransaction().replace(R.id.settingsContainer, AboutFragment()).addToBackStack(getString(R.string.about_pref)).commit()
            }
        }
    }

    @Composable
    fun AboutScreen(navController: NavController) {
        supportActionBar?.setTitle(R.string.about_pref)
//        val snackbarHostState = remember { SnackbarHostState() }
//        val scope = rememberCoroutineScope()
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp)) {
            Image(painter = painterResource(R.drawable.teaser), contentDescription = "")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp)) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_star), contentDescription = "", tint = textColor)
                Column(Modifier.padding(start = 10.dp).clickable(onClick = {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(getString(R.string.bug_report_title), PreferenceManager.getDefaultSharedPreferences(this@PreferenceActivity).getString("about_version", "Default summary"))
                    clipboard.setPrimaryClip(clip)
//                    scope.launch { snackbarHostState.showSnackbar(getString(R.string.copied_to_clipboard), duration = SnackbarDuration.Short) }
                    if (Build.VERSION.SDK_INT <= 32) Snackbar.make(findViewById(android.R.id.content), R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show()
                })) {
                    Text(stringResource(R.string.podcini_version), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(String.format("%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.COMMIT_HASH), color = textColor)
                }
            }
            IconTitleSummaryActionRow(R.drawable.ic_questionmark, R.string.online_help, R.string.online_help_sum) { openInBrowser(this@PreferenceActivity, "https://github.com/XilinJia/Podcini/") }
            IconTitleSummaryActionRow(R.drawable.ic_info, R.string.privacy_policy, R.string.privacy_policy) { openInBrowser(this@PreferenceActivity, "https://github.com/XilinJia/Podcini/blob/main/PrivacyPolicy.md") }
            IconTitleSummaryActionRow(R.drawable.ic_info, R.string.licenses, R.string.licenses_summary) {
                navController.navigate(Screens.preferences_license.tag)
//                supportFragmentManager.beginTransaction().replace(R.id.settingsContainer, LicensesFragment()).addToBackStack(getString(R.string.translators)).commit()
            }
        }
    }

    @Composable
    fun LicensesScreen() {
        class LicenseItem(val title: String, val subtitle: String, val licenseUrl: String, val licenseTextFile: String)
        supportActionBar!!.setTitle(R.string.licenses)
        val licenses = remember { mutableStateListOf<LicenseItem>() }
        LaunchedEffect(Unit) {
            lifecycleScope.launch(Dispatchers.IO) {
                licenses.clear()
                val stream = assets.open("licenses.xml")
                val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                val libraryList = docBuilder.parse(stream).getElementsByTagName("library")
                for (i in 0 until libraryList.length) {
                    val lib = libraryList.item(i).attributes
                    licenses.add(LicenseItem(lib.getNamedItem("name").textContent,
                        String.format("By %s, %s license", lib.getNamedItem("author").textContent, lib.getNamedItem("license").textContent), lib.getNamedItem("website").textContent, lib.getNamedItem("licenseText").textContent))
                }
            }.invokeOnCompletion { throwable -> if (throwable!= null) Toast.makeText(this@PreferenceActivity, throwable.message, Toast.LENGTH_LONG).show() }
        }
//        fun showLicenseText(licenseTextFile: String) {
//            try {
//                val reader = BufferedReader(InputStreamReader(assets.open(licenseTextFile), "UTF-8"))
//                val licenseText = StringBuilder()
//                var line = ""
//                while ((reader.readLine()?.also { line = it }) != null) licenseText.append(line).append("\n")
//                MaterialAlertDialogBuilder(this@PreferenceActivity).setMessage(licenseText).show()
//            } catch (e: IOException) { e.printStackTrace() }
//        }
        val lazyListState = rememberLazyListState()
        val textColor = MaterialTheme.colorScheme.onSurface
        val showLicense = remember { mutableStateOf(false) }
        var licenseText by remember { mutableStateOf("") }
        ComfirmDialog(titleRes = 0, message = licenseText, showLicense) {}
        var showDialog by remember { mutableStateOf(false) }
        var curLicenseIndex by remember { mutableIntStateOf(-1) }
        if (showDialog) Dialog(onDismissRequest = { showDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(licenses[curLicenseIndex].title, color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row {
                        Button(onClick = { openInBrowser(this@PreferenceActivity, licenses[curLicenseIndex].licenseUrl) }) { Text("View website") }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = {
                            try {
                                val reader = BufferedReader(InputStreamReader(assets.open(licenses[curLicenseIndex].licenseTextFile), "UTF-8"))
                                val sb = StringBuilder()
                                var line = ""
                                while ((reader.readLine()?.also { line = it }) != null) sb.append(line).append("\n")
                                licenseText = sb.toString()
                                showLicense.value = true
//                                MaterialAlertDialogBuilder(this@PreferenceActivity).setMessage(licenseText).show()
                            } catch (e: IOException) { e.printStackTrace() }
//                            showLicenseText(licenses[curLicenseIndex].licenseTextFile)
                        }) { Text("View license") }
                    }
                }
            }
        }
        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(licenses) { index, item ->
                Column(Modifier.clickable(onClick = {
                    curLicenseIndex = index
                    showDialog = true
                })) {
                    Text(item.title, color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(item.subtitle, color = textColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    enum class DefaultPages(val res: Int) {
        SubscriptionsFragment(R.string.subscriptions_label),
        QueuesFragment(R.string.queue_label),
        EpisodesFragment(R.string.episodes_label),
        AddFeedFragment(R.string.add_feed_label),
        StatisticsFragment(R.string.statistics_label),
        Remember(R.string.remember_last_page);
    }

    @Composable
    fun UserInterfacePreferencesScreen(navController: NavController) {
        fun showFullNotificationButtonsDialog() {
            val preferredButtons = fullNotificationButtons.toMutableList()
            val allButtonNames = resources.getStringArray(R.array.full_notification_buttons_options)
            val buttonIDs = intArrayOf(2, 3, 4)
            val exactItems = 2
            val completeListener = DialogInterface.OnClickListener { _: DialogInterface?, _: Int -> fullNotificationButtons = preferredButtons }
            val title = resources.getString(R.string.pref_full_notification_buttons_title)

            val checked = BooleanArray(allButtonNames.size) // booleans default to false in java
            // Clear buttons that are not part of the setting anymore
            for (i in preferredButtons.indices.reversed()) {
                var isValid = false
                for (j in checked.indices) {
                    if (buttonIDs[j] == preferredButtons[i]) {
                        isValid = true
                        break
                    }
                }
                if (!isValid) preferredButtons.removeAt(i)
            }
            for (i in checked.indices) if (preferredButtons.contains(buttonIDs[i])) checked[i] = true

            val builder = MaterialAlertDialogBuilder(this@PreferenceActivity)
            builder.setTitle(title)
            builder.setMultiChoiceItems(allButtonNames, checked) { _: DialogInterface?, which: Int, isChecked: Boolean ->
                checked[which] = isChecked
                if (isChecked) preferredButtons.add(buttonIDs[which])
                else preferredButtons.remove(buttonIDs[which])
            }
            builder.setPositiveButton(R.string.confirm_label, null)
            builder.setNegativeButton(R.string.cancel_label, null)
            val dialog = builder.create()
            dialog.show()
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                if (preferredButtons.size != exactItems) {
                    val selectionView = dialog.listView
                    Snackbar.make(selectionView, String.format(resources.getString(R.string.pref_compact_notification_buttons_dialog_error_exact), exactItems), Snackbar.LENGTH_SHORT).show()
                } else {
                    completeListener.onClick(dialog, AlertDialog.BUTTON_POSITIVE)
                    dialog.cancel()
                }
            }
        }

        supportActionBar?.setTitle(R.string.user_interface_label)
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            Text(stringResource(R.string.appearance), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                var checkIndex by remember { mutableIntStateOf(
                    when(UserPreferences.theme) {
                        UserPreferences.ThemePreference.SYSTEM -> 0
                        UserPreferences.ThemePreference.LIGHT -> 1
                        UserPreferences.ThemePreference.DARK -> 2
                        else -> 0
                    }) }
                Spacer(Modifier.weight(1f))
                RadioButton(selected = checkIndex == 0, onClick = {
                    checkIndex = 0
                    UserPreferences.theme = UserPreferences.ThemePreference.SYSTEM
                    ActivityCompat.recreate(this@PreferenceActivity)
                })
                Text(stringResource(R.string.pref_theme_title_automatic), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                RadioButton(selected = checkIndex == 1, onClick = {
                    checkIndex = 1
                    UserPreferences.theme = UserPreferences.ThemePreference.LIGHT
                    ActivityCompat.recreate(this@PreferenceActivity)
                })
                Text(stringResource(R.string.pref_theme_title_light), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                RadioButton(selected = checkIndex == 2, onClick = {
                    checkIndex = 2
                    UserPreferences.theme = UserPreferences.ThemePreference.DARK
                    ActivityCompat.recreate(this@PreferenceActivity)
                })
                Text(stringResource(R.string.pref_theme_title_dark), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_black_theme_title), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pref_black_theme_message), color = textColor)
                }
                var isChecked by remember { mutableStateOf(appPrefs.getBoolean(UserPreferences.Prefs.prefThemeBlack.name, false)) }
                Switch(checked = isChecked, onCheckedChange = {
                    isChecked = it
                    appPrefs.edit().putBoolean(UserPreferences.Prefs.prefThemeBlack.name, it).apply()
                    ActivityCompat.recreate(this@PreferenceActivity)
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_tinted_theme_title), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pref_tinted_theme_message), color = textColor)
                }
                var isChecked by remember { mutableStateOf(appPrefs.getBoolean(UserPreferences.Prefs.prefTintedColors.name, false)) }
                Switch(checked = isChecked, onCheckedChange = {
                    isChecked = it
                    appPrefs.edit().putBoolean(UserPreferences.Prefs.prefTintedColors.name, it).apply()
                    ActivityCompat.recreate(this@PreferenceActivity)
                })
            }
            TitleSummarySwitchPrefRow(R.string.pref_episode_cover_title, R.string.pref_episode_cover_summary, UserPreferences.Prefs.prefEpisodeCover.name)
            TitleSummarySwitchPrefRow(R.string.pref_show_remain_time_title, R.string.pref_show_remain_time_summary, UserPreferences.Prefs.showTimeLeft.name)
            Text(stringResource(R.string.subscriptions_label), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            TitleSummarySwitchPrefRow(R.string.pref_swipe_refresh_title, R.string.pref_swipe_refresh_sum, UserPreferences.Prefs.prefSwipeToRefreshAll.name)
            TitleSummarySwitchPrefRow(R.string.pref_feedGridLayout_title, R.string.pref_feedGridLayout_sum, UserPreferences.Prefs.prefFeedGridLayout.name)
            Text(stringResource(R.string.external_elements), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            if (Build.VERSION.SDK_INT < 26) {
                TitleSummarySwitchPrefRow(R.string.pref_expandNotify_title, R.string.pref_expandNotify_sum, UserPreferences.Prefs.prefExpandNotify.name)
            }
            TitleSummarySwitchPrefRow(R.string.pref_persistNotify_title, R.string.pref_persistNotify_sum, UserPreferences.Prefs.prefPersistNotify.name)
            TitleSummaryActionColumn(R.string.pref_full_notification_buttons_title, R.string.pref_full_notification_buttons_sum) { showFullNotificationButtonsDialog() }
            Text(stringResource(R.string.behavior), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            var showDefaultPageOptions by remember { mutableStateOf(false) }
            var tempSelectedOption by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefDefaultPage.name, DefaultPages.SubscriptionsFragment.name)!!) }
            TitleSummaryActionColumn(R.string.pref_default_page, R.string.pref_default_page_sum) { showDefaultPageOptions = true }
            if (showDefaultPageOptions) {
                AlertDialog(onDismissRequest = { showDefaultPageOptions = false },
                    title = { Text(stringResource(R.string.pref_default_page), style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Column {
                            DefaultPages.entries.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { tempSelectedOption = option.name }) {
                                    Checkbox(checked = tempSelectedOption == option.name, onCheckedChange = { tempSelectedOption = option.name })
                                    Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            appPrefs.edit().putString(UserPreferences.Prefs.prefDefaultPage.name, tempSelectedOption).apply()
                            showDefaultPageOptions = false
                        }) { Text(text = "OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDefaultPageOptions = false }) { Text(text = "Cancel") } }
                )
            }
            TitleSummarySwitchPrefRow(R.string.pref_back_button_opens_drawer, R.string.pref_back_button_opens_drawer_summary, UserPreferences.Prefs.prefBackButtonOpensDrawer.name)
            TitleSummaryActionColumn(R.string.swipeactions_label, R.string.swipeactions_summary) {
                navController.navigate(Screens.preferences_swipe.tag)
//                openScreen(Screens.preferences_swipe)
            }
        }
    }

    @Suppress("EnumEntryName")
    private enum class SwipePrefs(val res: Int, val tag: String) {
        prefSwipeQueue(R.string.queue_label, QueuesFragment.TAG),
        prefSwipeEpisodes(R.string.episodes_label, EpisodesFragment.TAG),
        prefSwipeFeed(R.string.individual_subscription, FeedEpisodesFragment.TAG),
    }

    @Composable
    fun SwipePreferencesScreen() {
        supportActionBar?.setTitle(R.string.swipeactions_label)
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            for (e in SwipePrefs.entries) {
                val showDialog = remember { mutableStateOf(false) }
                if (showDialog.value) SwipeActionsDialog(e.tag, onDismissRequest = { showDialog.value = false }) {}
                Text(stringResource(e.res), color = textColor, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 10.dp).clickable(onClick = { showDialog.value = true }))
            }
        }
    }

    enum class PrefHardwareForwardButton(val res: Int, val res1: Int) {
        FF(R.string.button_action_fast_forward, R.string.keycode_media_fast_forward),
        RW(R.string.button_action_rewind, R.string.keycode_media_rewind),
        SKIP(R.string.button_action_skip_episode, R.string.keycode_media_next),
        START(R.string.button_action_restart_episode, R.string.keycode_media_previous);
    }

    @Composable
    fun PlaybackPreferencesScreen() {
        supportActionBar!!.setTitle(R.string.playback_pref)
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            Text(stringResource(R.string.interruptions), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            var prefUnpauseOnHeadsetReconnect by remember { mutableStateOf(appPrefs.getBoolean(UserPreferences.Prefs.prefPauseOnHeadsetDisconnect.name, true)) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_pauseOnHeadsetDisconnect_title), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pref_pauseOnDisconnect_sum), color = textColor)
                }
                Switch(checked = prefUnpauseOnHeadsetReconnect, onCheckedChange = {
                    prefUnpauseOnHeadsetReconnect = it
                    appPrefs.edit().putBoolean(UserPreferences.Prefs.prefPauseOnHeadsetDisconnect.name, it).apply()
                })
            }
            if (prefUnpauseOnHeadsetReconnect) {
                TitleSummarySwitchPrefRow(R.string.pref_unpauseOnHeadsetReconnect_title, R.string.pref_unpauseOnHeadsetReconnect_sum, UserPreferences.Prefs.prefUnpauseOnHeadsetReconnect.name)
                TitleSummarySwitchPrefRow(R.string.pref_unpauseOnBluetoothReconnect_title, R.string.pref_unpauseOnBluetoothReconnect_sum, UserPreferences.Prefs.prefUnpauseOnBluetoothReconnect.name)
            }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp).padding(top = 10.dp))
            Text(stringResource(R.string.playback_control), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pref_fast_forward), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    var interval by remember { mutableStateOf(fastForwardSecs.toString()) }
                    var showIcon by remember { mutableStateOf(false) }
                    TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("seconds") },
                        singleLine = true, modifier = Modifier.weight(0.5f),
                        onValueChange = {
                            if (it.isEmpty() || it.toIntOrNull() != null) interval = it
                            if (it.toIntOrNull() != null) showIcon = true
                        },
                        trailingIcon = {
                            if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                                modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                    if (interval.isNotBlank()) {
                                        fastForwardSecs = interval.toInt()
                                        showIcon = false
                                    }
                                }))
                        })
                }
                Text(stringResource(R.string.pref_fast_forward_sum), color = textColor)
            }
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pref_rewind), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    var interval by remember { mutableStateOf(rewindSecs.toString()) }
                    var showIcon by remember { mutableStateOf(false) }
                    TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("seconds") },
                        singleLine = true, modifier = Modifier.weight(0.5f),
                        onValueChange = {
                            if (it.isEmpty() || it.toIntOrNull() != null) interval = it
                            if (it.toIntOrNull() != null) showIcon = true
                        },
                        trailingIcon = {
                            if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                                modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                    if (interval.isNotBlank()) {
                                        rewindSecs = interval.toInt()
                                        showIcon = false
                                    }
                                }))
                        })
                }
                Text(stringResource(R.string.pref_rewind_sum), color = textColor)
            }
            var showSpeedDialog by remember { mutableStateOf(false) }
            if (showSpeedDialog) PlaybackSpeedDialog(listOf(), initSpeed = prefPlaybackSpeed, maxSpeed = 3f, isGlobal = true,
                onDismiss = { showSpeedDialog = false }) { speed -> UserPreferences.setPlaybackSpeed(speed) }
            TitleSummaryActionColumn(R.string.playback_speed, R.string.pref_playback_speed_sum) { showSpeedDialog = true }
            var showFBSpeedDialog by remember { mutableStateOf(false) }
            if (showFBSpeedDialog) PlaybackSpeedDialog(listOf(), initSpeed = fallbackSpeed, maxSpeed = 3f, isGlobal = true,
                onDismiss = { showFBSpeedDialog = false }) { speed ->
                val speed_ = when {
                    speed < 0.0f -> 0.0f
                    speed > 3.0f -> 3.0f
                    else -> 0f
                }
                fallbackSpeed = round(100 * speed_) / 100f
            }
            TitleSummaryActionColumn(R.string.pref_fallback_speed, R.string.pref_fallback_speed_sum) { showFBSpeedDialog = true }
            TitleSummarySwitchPrefRow(R.string.pref_playback_time_respects_speed_title, R.string.pref_playback_time_respects_speed_sum, UserPreferences.Prefs.prefPlaybackTimeRespectsSpeed.name)
            var showFFSpeedDialog by remember { mutableStateOf(false) }
            if (showFFSpeedDialog) PlaybackSpeedDialog(listOf(), initSpeed = speedforwardSpeed, maxSpeed = 10f, isGlobal = true,
                onDismiss = { showFFSpeedDialog = false }) { speed ->
                val speed_ = when {
                    speed < 0.0f -> 0.0f
                    speed > 10.0f -> 10.0f
                    else -> 0f
                }
                speedforwardSpeed = round(10 * speed_) / 10
            }
            TitleSummaryActionColumn(R.string.pref_speed_forward, R.string.pref_speed_forward_sum) { showFFSpeedDialog = true }
            TitleSummarySwitchPrefRow(R.string.pref_stream_over_download_title, R.string.pref_stream_over_download_sum, UserPreferences.Prefs.prefStreamOverDownload.name)
            TitleSummarySwitchPrefRow(R.string.pref_low_quality_on_mobile_title, R.string.pref_low_quality_on_mobile_sum, UserPreferences.Prefs.prefLowQualityOnMobile.name)
            TitleSummarySwitchPrefRow(R.string.pref_use_adaptive_progress_title, R.string.pref_use_adaptive_progress_sum, UserPreferences.Prefs.prefUseAdaptiveProgressUpdate.name)
            var showVideoModeDialog by remember { mutableStateOf(false) }
            if (showVideoModeDialog) VideoModeDialog(onDismissRequest = { showVideoModeDialog = false }) { mode -> setVideoMode(mode.code) }
            TitleSummaryActionColumn(R.string.pref_playback_video_mode, R.string.pref_playback_video_mode_sum) { showVideoModeDialog = true }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp).padding(top = 10.dp))
            Text(stringResource(R.string.reassign_hardware_buttons), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            var showHardwareForwardButtonOptions by remember { mutableStateOf(false) }
            var tempFFSelectedOption by remember { mutableStateOf(R.string.keycode_media_fast_forward) }
            TitleSummaryActionColumn(R.string.pref_hardware_forward_button_title, R.string.pref_hardware_forward_button_summary) { showHardwareForwardButtonOptions = true }
            if (showHardwareForwardButtonOptions) {
                AlertDialog(onDismissRequest = { showHardwareForwardButtonOptions = false },
                    title = { Text(stringResource(R.string.pref_hardware_forward_button_title), style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Column {
                            PrefHardwareForwardButton.entries.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp).clickable { tempFFSelectedOption = option.res1 }) {
                                    Checkbox(checked = tempFFSelectedOption == option.res1, onCheckedChange = { tempFFSelectedOption = option.res1 })
                                    Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            appPrefs.edit().putString(UserPreferences.Prefs.prefHardwareForwardButton.name, tempFFSelectedOption.toString()).apply()
                            showHardwareForwardButtonOptions = false
                        }) { Text(text = "OK") }
                    },
                    dismissButton = { TextButton(onClick = { showHardwareForwardButtonOptions = false }) { Text(text = "Cancel") } }
                )
            }
            var showHardwarePreviousButtonOptions by remember { mutableStateOf(false) }
            var tempPRSelectedOption by remember { mutableStateOf(R.string.keycode_media_rewind) }
            TitleSummaryActionColumn(R.string.pref_hardware_previous_button_title, R.string.pref_hardware_previous_button_summary) { showHardwarePreviousButtonOptions = true }
            if (showHardwarePreviousButtonOptions) {
                AlertDialog(onDismissRequest = { showHardwarePreviousButtonOptions = false },
                    title = { Text(stringResource(R.string.pref_hardware_previous_button_title), style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Column {
                            PrefHardwareForwardButton.entries.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp).clickable { tempPRSelectedOption = option.res1 }) {
                                    Checkbox(checked = tempPRSelectedOption == option.res1, onCheckedChange = { tempPRSelectedOption = option.res1 })
                                    Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            appPrefs.edit().putString(UserPreferences.Prefs.prefHardwarePreviousButton.name, tempPRSelectedOption.toString()).apply()
                            showHardwarePreviousButtonOptions = false
                        }) { Text(text = "OK") }
                    },
                    dismissButton = { TextButton(onClick = { showHardwarePreviousButtonOptions = false }) { Text(text = "Cancel") } }
                )
            }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp).padding(top = 10.dp))
            Text(stringResource(R.string.queue_label), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            TitleSummarySwitchPrefRow(R.string.pref_enqueue_downloaded_title, R.string.pref_enqueue_downloaded_summary, UserPreferences.Prefs.prefEnqueueDownloaded.name)
            var showEnqueueLocationOptions by remember { mutableStateOf(false) }
            var tempLocationOption by remember { mutableStateOf(EnqueueLocation.BACK.name) }
            TitleSummaryActionColumn(R.string.pref_enqueue_location_title, R.string.pref_enqueue_location_sum) { showEnqueueLocationOptions = true }
            if (showEnqueueLocationOptions) {
                AlertDialog(onDismissRequest = { showEnqueueLocationOptions = false },
                    title = { Text(stringResource(R.string.pref_hardware_previous_button_title), style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Column {
                            EnqueueLocation.entries.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                                    .clickable { tempLocationOption = option.name }) {
                                    Checkbox(checked = tempLocationOption == option.name, onCheckedChange = { tempLocationOption = option.name })
                                    Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            appPrefs.edit().putString(UserPreferences.Prefs.prefEnqueueLocation.name, tempLocationOption).apply()
                            showEnqueueLocationOptions = false
                        }) { Text(text = "OK") }
                    },
                    dismissButton = { TextButton(onClick = { showEnqueueLocationOptions = false }) { Text(text = "Cancel") } }
                )
            }
            TitleSummarySwitchPrefRow(R.string.pref_followQueue_title, R.string.pref_followQueue_sum, UserPreferences.Prefs.prefFollowQueue.name)
            TitleSummarySwitchPrefRow(R.string.pref_skip_keeps_episodes_title, R.string.pref_skip_keeps_episodes_sum, UserPreferences.Prefs.prefSkipKeepsEpisode.name)
            TitleSummarySwitchPrefRow(R.string.pref_mark_played_removes_from_queue_title, R.string.pref_mark_played_removes_from_queue_sum, UserPreferences.Prefs.prefRemoveFromQueueMarkedPlayed.name)
        }
    }

    @Composable
    fun ImportExportPreferencesScreen() {
        val TAG = "ImportExportPreferencesScreen"
        val backupDirName = "Podcini-Backups"
        var prefsDirName = "Podcini-Prefs"
        val mediaFilesDirName = "Podcini-MediaFiles"

        class PreferencesTransporter {
            @Throws(IOException::class)
            fun exportToDocument(uri: Uri, context: Context) {
                try {
                    val chosenDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Destination directory is not valid")
                    val exportSubDir = chosenDir.createDirectory(prefsDirName) ?: throw IOException("Error creating subdirectory $prefsDirName")
                    val sharedPreferencesDir = context.applicationContext.filesDir.parentFile?.listFiles { file -> file.name.startsWith("shared_prefs") }?.firstOrNull()
                    if (sharedPreferencesDir != null) {
                        sharedPreferencesDir.listFiles()!!.forEach { file ->
                            val destFile = exportSubDir.createFile("text/xml", file.name)
                            if (destFile != null) copyFile(file, destFile, context)
                        }
                    } else Log.e("Error", "shared_prefs directory not found")
                } catch (e: IOException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    throw e
                } finally { }
            }
            private fun copyFile(sourceFile: File, destFile: DocumentFile, context: Context) {
                try {
                    val inputStream = FileInputStream(sourceFile)
                    val outputStream = context.contentResolver.openOutputStream(destFile.uri)
                    if (outputStream != null) copyStream(inputStream, outputStream)
                    inputStream.close()
                    outputStream?.close()
                } catch (e: IOException) {
                    Log.e("Error", "Error copying file: $e")
                    throw e
                }
            }
            private fun copyFile(sourceFile: DocumentFile, destFile: File, context: Context) {
                try {
                    val inputStream = context.contentResolver.openInputStream(sourceFile.uri)
                    val outputStream = FileOutputStream(destFile)
                    if (inputStream != null) copyStream(inputStream, outputStream)
                    inputStream?.close()
                    outputStream.close()
                } catch (e: IOException) {
                    Log.e("Error", "Error copying file: $e")
                    throw e
                }
            }
            private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) outputStream.write(buffer, 0, bytesRead)
            }
            @Throws(IOException::class)
            fun importBackup(uri: Uri, context: Context) {
                try {
                    val exportedDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Backup directory is not valid")
                    val sharedPreferencesDir = context.applicationContext.filesDir.parentFile?.listFiles { file -> file.name.startsWith("shared_prefs") }?.firstOrNull()
                    if (sharedPreferencesDir != null) sharedPreferencesDir.listFiles()?.forEach { file -> file.delete() }
                    else Log.e("Error", "shared_prefs directory not found")
                    val files = exportedDir.listFiles()
                    var hasPodciniRPrefs = false
                    for (file in files) {
                        if (file?.isFile == true && file.name?.endsWith(".xml") == true && file.name!!.contains("podcini.R")) {
                            hasPodciniRPrefs = true
                            break
                        }
                    }
                    for (file in files) {
                        if (file?.isFile == true && file.name?.endsWith(".xml") == true) {
                            var destName = file.name!!
                            if (destName.contains("PlayerWidgetPrefs")) continue
//                  for importing from Podcini version 5 and below
                            if (!hasPodciniRPrefs) {
                                when {
                                    destName.contains("podcini") -> destName = destName.replace("podcini", "podcini.R")
                                    destName.contains("EpisodeItemListRecyclerView") -> destName = destName.replace("EpisodeItemListRecyclerView", "EpisodesRecyclerView")
                                }
                            }
                            when {
                                BuildConfig.DEBUG && !destName.contains(".debug") -> destName = destName.replace("podcini.R", "podcini.R.debug")
                                !BuildConfig.DEBUG && destName.contains(".debug") -> destName = destName.replace(".debug", "")
                            }
                            val destFile = File(sharedPreferencesDir, destName)
                            copyFile(file, destFile, context)
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    throw e
                } finally { }
            }
        }
        class MediaFilesTransporter {
            var feed: Feed? = null
            private val nameFeedMap: MutableMap<String, Feed> = mutableMapOf()
            private val nameEpisodeMap: MutableMap<String, Episode> = mutableMapOf()
            @Throws(IOException::class)
            fun exportToDocument(uri: Uri, context: Context) {
                try {
                    val mediaDir = context.getExternalFilesDir("media") ?: return
                    val chosenDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Destination directory is not valid")
                    val exportSubDir = chosenDir.createDirectory(mediaFilesDirName) ?: throw IOException("Error creating subdirectory $mediaFilesDirName")
                    mediaDir.listFiles()?.forEach { file -> copyRecursive(context, file, mediaDir, exportSubDir) }
                } catch (e: IOException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    throw e
                } finally { }
            }
            private fun copyRecursive(context: Context, srcFile: File, srcRootDir: File, destRootDir: DocumentFile) {
                val relativePath = srcFile.absolutePath.substring(srcRootDir.absolutePath.length+1)
                if (srcFile.isDirectory) {
                    val dirFiles = srcFile.listFiles()
                    if (!dirFiles.isNullOrEmpty()) {
                        val destDir = destRootDir.findFile(relativePath) ?: destRootDir.createDirectory(relativePath) ?: return
                        dirFiles.forEach { file -> copyRecursive(context, file, srcFile, destDir) }
                    }
                } else {
                    val destFile = destRootDir.createFile("application/octet-stream", relativePath) ?: return
                    copyFile(srcFile, destFile, context)
                }
            }
            private fun copyFile(sourceFile: File, destFile: DocumentFile, context: Context) {
                try {
                    val outputStream = context.contentResolver.openOutputStream(destFile.uri) ?: return
                    val inputStream = FileInputStream(sourceFile)
                    copyStream(inputStream, outputStream)
                    inputStream.close()
                    outputStream.close()
                } catch (e: IOException) {
                    Log.e("Error", "Error copying file: $e")
                    throw e
                }
            }
            private fun copyRecursive(context: Context, srcFile: DocumentFile, srcRootDir: DocumentFile, destRootDir: File) {
                val relativePath = srcFile.uri.path?.substring(srcRootDir.uri.path!!.length+1) ?: return
                if (srcFile.isDirectory) {
                    Logd(TAG, "copyRecursive folder title: $relativePath")
                    feed = nameFeedMap[relativePath] ?: return
                    Logd(TAG, "copyRecursive found feed: ${feed?.title}")
                    nameEpisodeMap.clear()
                    feed!!.episodes.forEach { e -> if (!e.title.isNullOrEmpty()) nameEpisodeMap[generateFileName(e.title!!)] = e }
                    val destFile = File(destRootDir, relativePath)
                    if (!destFile.exists()) destFile.mkdirs()
                    srcFile.listFiles().forEach { file -> copyRecursive(context, file, srcFile, destFile) }
                } else {
                    val nameParts = relativePath.split(".")
                    if (nameParts.size < 3) return
                    val ext = nameParts[nameParts.size-1]
                    val title = nameParts.dropLast(2).joinToString(".")
                    Logd(TAG, "copyRecursive file title: $title")
                    val episode = nameEpisodeMap[title] ?: return
                    Logd(TAG, "copyRecursive found episode: ${episode.title}")
                    val destName = "$title.${episode.id}.$ext"
                    val destFile = File(destRootDir, destName)
                    if (!destFile.exists()) {
                        Logd(TAG, "copyRecursive copying file to: ${destFile.absolutePath}")
                        copyFile(srcFile, destFile, context)
                        upsertBlk(episode) {
                            it.media?.fileUrl = destFile.absolutePath
                            it.media?.setIsDownloaded()
                        }
                    }
                }
            }
            private fun copyFile(sourceFile: DocumentFile, destFile: File, context: Context) {
                try {
                    val inputStream = context.contentResolver.openInputStream(sourceFile.uri) ?: return
                    val outputStream = FileOutputStream(destFile)
                    copyStream(inputStream, outputStream)
                    inputStream.close()
                    outputStream.close()
                } catch (e: IOException) {
                    Log.e("Error", "Error copying file: $e")
                    throw e
                }
            }
            private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) outputStream.write(buffer, 0, bytesRead)
            }
            @Throws(IOException::class)
            fun importBackup(uri: Uri, context: Context) {
                try {
                    val exportedDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Backup directory is not valid")
                    if (exportedDir.name?.contains(mediaFilesDirName) != true) return
                    val mediaDir = context.getExternalFilesDir("media") ?: return
                    val fileList = exportedDir.listFiles()
                    if (fileList.isNotEmpty()) {
                        val feeds = getFeedList()
                        feeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
                        fileList.forEach { file -> copyRecursive(context, file, exportedDir, mediaDir) }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    throw e
                } finally {
                    nameFeedMap.clear()
                    nameEpisodeMap.clear()
                    feed = null
                }
            }
        }
        class DatabaseTransporter {
            @Throws(IOException::class)
            fun exportToDocument(uri: Uri?, context: Context) {
                var pfd: ParcelFileDescriptor? = null
                var fileOutputStream: FileOutputStream? = null
                try {
                    pfd = context.contentResolver.openFileDescriptor(uri!!, "wt")
                    fileOutputStream = FileOutputStream(pfd!!.fileDescriptor)
                    exportToStream(fileOutputStream, context)
                } catch (e: IOException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    throw e
                } finally {
                    IOUtils.closeQuietly(fileOutputStream)
                    if (pfd != null) try { pfd.close() } catch (e: IOException) { Logd(TAG, "Unable to close ParcelFileDescriptor") }
                }
            }
            @Throws(IOException::class)
            fun exportToStream(outFileStream: FileOutputStream, context: Context) {
                var src: FileChannel? = null
                var dst: FileChannel? = null
                try {
                    val realmPath = realm.configuration.path
                    Logd(TAG, "exportToStream realmPath: $realmPath")
                    val currentDB = File(realmPath)
                    if (currentDB.exists()) {
                        src = FileInputStream(currentDB).channel
                        dst = outFileStream.channel
                        val srcSize = src.size()
                        dst.transferFrom(src, 0, srcSize)
                        val newDstSize = dst.size()
                        if (newDstSize != srcSize)
                            throw IOException(String.format("Unable to write entire database. Expected to write %s, but wrote %s.", Formatter.formatShortFileSize(context, srcSize), Formatter.formatShortFileSize(context, newDstSize)))
                    } else throw IOException("Can not access current database")
                } catch (e: IOException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    throw e
                } finally {
                    IOUtils.closeQuietly(src)
                    IOUtils.closeQuietly(dst)
                }
            }
            @Throws(IOException::class)
            fun importBackup(inputUri: Uri?, context: Context) {
                val TEMP_DB_NAME = "temp.realm"
                var inputStream: InputStream? = null
                try {
                    val tempDB = context.getDatabasePath(TEMP_DB_NAME)
                    inputStream = context.contentResolver.openInputStream(inputUri!!)
                    FileUtils.copyInputStreamToFile(inputStream, tempDB)
                    val realmPath = realm.configuration.path
                    val currentDB = File(realmPath)
                    val success = currentDB.delete()
                    if (!success) throw IOException("Unable to delete old database")
                    FileUtils.moveFile(tempDB, currentDB)
                } catch (e: IOException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    throw e
                } finally { IOUtils.closeQuietly(inputStream) }
            }
        }
        class EpisodeProgressReader {
            fun readDocument(reader: Reader) {
                val jsonString = reader.readText()
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val jsonAction = jsonArray.getJSONObject(i)
                    Logd(TAG, "Loaded EpisodeActions message: $i $jsonAction")
                    val action = readFromJsonObject(jsonAction) ?: continue
                    Logd(TAG, "processing action: $action")
                    val result = processEpisodeAction(action) ?: continue
//                upsertBlk(result.second) {}
                }
            }
            private fun processEpisodeAction(action: EpisodeAction): Pair<Long, Episode>? {
                val guid = if (isValidGuid(action.guid)) action.guid else null
                var feedItem = getEpisodeByGuidOrUrl(guid, action.episode?:"", false) ?: return null
                if (feedItem.media == null) {
                    Logd(TAG, "Feed item has no media: $action")
                    return null
                }
                var idRemove = 0L
                feedItem = upsertBlk(feedItem) {
                    it.media!!.startPosition = action.started * 1000
                    it.media!!.setPosition(action.position * 1000)
                    it.media!!.playedDuration = action.playedDuration * 1000
                    it.media!!.setLastPlayedTime(action.timestamp!!.time)
                    it.rating = if (action.isFavorite) Rating.SUPER.code else Rating.UNRATED.code
                    it.playState = action.playState
                    if (hasAlmostEnded(it.media!!)) {
                        Logd(TAG, "Marking as played: $action")
                        it.setPlayed(true)
                        it.media!!.setPosition(0)
                        idRemove = it.id
                    } else Logd(TAG, "Setting position: $action")
                }
                return Pair(idRemove, feedItem)
            }
        }
        class EpisodesProgressWriter : ExportWriter {
            @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
            override fun writeDocument(feeds: List<Feed>, writer: Writer, context: Context) {
                Logd(TAG, "Starting to write document")
                val queuedEpisodeActions: MutableList<EpisodeAction> = mutableListOf()
                val pausedItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.paused.name), EpisodeSortOrder.DATE_NEW_OLD)
                val readItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.played.name), EpisodeSortOrder.DATE_NEW_OLD)
                val favoriteItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_NEW_OLD)
                val comItems = mutableSetOf<Episode>()
                comItems.addAll(pausedItems)
                comItems.addAll(readItems)
                comItems.addAll(favoriteItems)
                Logd(TAG, "Save state for all " + comItems.size + " played episodes")
                for (item in comItems) {
                    val media = item.media ?: continue
                    val played = EpisodeAction.Builder(item, EpisodeAction.PLAY)
                        .timestamp(Date(media.getLastPlayedTime()))
                        .started(media.startPosition / 1000)
                        .position(media.getPosition() / 1000)
                        .playedDuration(media.playedDuration / 1000)
                        .total(media.getDuration() / 1000)
                        .isFavorite(item.isSUPER)
                        .playState(item.playState)
                        .build()
                    queuedEpisodeActions.add(played)
                }
                if (queuedEpisodeActions.isNotEmpty()) {
                    try {
                        Logd(TAG, "Saving ${queuedEpisodeActions.size} actions: ${StringUtils.join(queuedEpisodeActions, ", ")}")
                        val list = JSONArray()
                        for (episodeAction in queuedEpisodeActions) {
                            val obj = episodeAction.writeToJsonObject()
                            if (obj != null) {
                                Logd(TAG, "saving EpisodeAction: $obj")
                                list.put(obj)
                            }
                        }
                        writer.write(list.toString())
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw SyncServiceException(e)
                    }
                }
                Logd(TAG, "Finished writing document")
            }
            override fun fileExtension(): String {
                return "json"
            }
        }
        class FavoritesWriter : ExportWriter {
            private val FAVORITE_TEMPLATE = "html-export-favorites-item-template.html"
            private val FEED_TEMPLATE = "html-export-feed-template.html"
            private val UTF_8 = "UTF-8"
            @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
            override fun writeDocument(feeds: List<Feed>, writer: Writer, context: Context) {
                Logd(TAG, "Starting to write document")
                val templateStream = context.assets.open("html-export-template.html")
                var template = IOUtils.toString(templateStream, UTF_8)
                template = template.replace("\\{TITLE\\}".toRegex(), "Favorites")
                val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val favTemplateStream = context.assets.open(FAVORITE_TEMPLATE)
                val favTemplate = IOUtils.toString(favTemplateStream, UTF_8)
                val feedTemplateStream = context.assets.open(FEED_TEMPLATE)
                val feedTemplate = IOUtils.toString(feedTemplateStream, UTF_8)
                val allFavorites = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_NEW_OLD)
                val favoritesByFeed = buildFeedMap(allFavorites)
                writer.append(templateParts[0])
                for (feedId in favoritesByFeed.keys) {
                    val favorites: List<Episode> = favoritesByFeed[feedId]!!
                    if (favorites[0].feed == null) continue
                    writer.append("<li><div>\n")
                    writeFeed(writer, favorites[0].feed!!, feedTemplate)
                    writer.append("<ul>\n")
                    for (item in favorites) writeFavoriteItem(writer, item, favTemplate)
                    writer.append("</ul></div></li>\n")
                }
                writer.append(templateParts[1])
                Logd(TAG, "Finished writing document")
            }
            /**
             * Group favorite episodes by feed, sorting them by publishing date in descending order.
             * @param favoritesList `List` of all favorite episodes.
             * @return A `Map` favorite episodes, keyed by feed ID.
             */
            private fun buildFeedMap(favoritesList: List<Episode>): Map<Long, MutableList<Episode>> {
                val feedMap: MutableMap<Long, MutableList<Episode>> = TreeMap()
                for (item in favoritesList) {
                    var feedEpisodes = feedMap[item.feedId]
                    if (feedEpisodes == null) {
                        feedEpisodes = ArrayList()
                        if (item.feedId != null) feedMap[item.feedId!!] = feedEpisodes
                    }
                    feedEpisodes.add(item)
                }
                return feedMap
            }
            @Throws(IOException::class)
            private fun writeFeed(writer: Writer, feed: Feed, feedTemplate: String) {
                val feedInfo = feedTemplate
                    .replace("{FEED_IMG}", feed.imageUrl?:"")
                    .replace("{FEED_TITLE}", feed.title?:" No title")
                    .replace("{FEED_LINK}", feed.link?: "")
                    .replace("{FEED_WEBSITE}", feed.downloadUrl?:"")
                writer.append(feedInfo)
            }
            @Throws(IOException::class)
            private fun writeFavoriteItem(writer: Writer, item: Episode, favoriteTemplate: String) {
                var favItem = favoriteTemplate.replace("{FAV_TITLE}", item.title!!.trim { it <= ' ' })
                favItem = if (item.link != null) favItem.replace("{FAV_WEBSITE}", item.link!!)
                else favItem.replace("{FAV_WEBSITE}", "")
                favItem =
                    if (item.media != null && item.media!!.downloadUrl != null) favItem.replace("{FAV_MEDIA}", item.media!!.downloadUrl!!)
                    else favItem.replace("{FAV_MEDIA}", "")
                writer.append(favItem)
            }
            override fun fileExtension(): String {
                return "html"
            }
        }
        class HtmlWriter : ExportWriter {
            /**
             * Takes a list of feeds and a writer and writes those into an HTML document.
             */
            @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
            override fun writeDocument(feeds: List<Feed>, writer: Writer, context: Context) {
                Logd(TAG, "Starting to write document")
                val templateStream = context.assets.open("html-export-template.html")
                var template = IOUtils.toString(templateStream, "UTF-8")
                template = template.replace("\\{TITLE\\}".toRegex(), "Subscriptions")
                val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                writer.append(templateParts[0])
                for (feed in feeds) {
                    writer.append("<li><div><img src=\"")
                    writer.append(feed.imageUrl)
                    writer.append("\" /><p>")
                    writer.append(feed.title)
                    writer.append(" <span><a href=\"")
                    writer.append(feed.link)
                    writer.append("\">Website</a> • <a href=\"")
                    writer.append(feed.downloadUrl)
                    writer.append("\">Feed</a></span></p></div></li>\n")
                }
                writer.append(templateParts[1])
                Logd(TAG, "Finished writing document")
            }
            override fun fileExtension(): String {
                return "html"
            }
        }

        var showProgress by remember { mutableStateOf(false) }
        fun isJsonFile(uri: Uri): Boolean {
            val fileName = uri.lastPathSegment ?: return false
            return fileName.endsWith(".json", ignoreCase = true)
        }
        fun isRealmFile(uri: Uri): Boolean {
            val fileName = uri.lastPathSegment ?: return false
            return fileName.trim().endsWith(".realm", ignoreCase = true)
        }
        fun isComboDir(uri: Uri): Boolean {
            val fileName = uri.lastPathSegment ?: return false
            return fileName.contains(backupDirName, ignoreCase = true)
        }
        fun showExportSuccessSnackbar(uri: Uri?, mimeType: String?) {
            Snackbar.make(findViewById(android.R.id.content), R.string.export_success_title, Snackbar.LENGTH_LONG)
                .setAction(R.string.share_label) { IntentBuilder(this@PreferenceActivity).setType(mimeType).addStream(uri!!).setChooserTitle(R.string.share_label).startChooser() }
                .show()
        }
        fun dateStampFilename(fname: String): String {
            return String.format(fname, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        }
        val showImporSuccessDialog = remember { mutableStateOf(false) }
        ComfirmDialog(titleRes = R.string.successful_import_label, message = stringResource(R.string.import_ok), showDialog = showImporSuccessDialog, cancellable = false) { forceRestart() }

        val showImporErrortDialog = remember { mutableStateOf(false) }
        var importErrorMessage by remember { mutableStateOf("") }
        ComfirmDialog(titleRes = R.string.import_export_error_label, message = importErrorMessage, showDialog = showImporErrortDialog) {}

        fun exportWithWriter(exportWriter: ExportWriter, uri: Uri?, exportType: ExportTypes) {
            val context: Context? = this@PreferenceActivity
            showProgress = true
            if (uri == null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val output = ExportWorker(exportWriter, this@PreferenceActivity).exportFile()
                        withContext(Dispatchers.Main) {
                            val fileUri = FileProvider.getUriForFile(context!!.applicationContext, context.getString(R.string.provider_authority), output!!)
                            showExportSuccessSnackbar(fileUri, exportType.contentType)
                        }
                    } catch (e: Exception) {
                        showProgress = false
                        importErrorMessage = e.message?:"Reason unknown"
                        showImporErrortDialog.value = true
                    } finally { showProgress = false }
                }
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    val worker = DocumentFileExportWorker(exportWriter, context!!, uri)
                    try {
                        val output = worker.exportFile()
                        withContext(Dispatchers.Main) { showExportSuccessSnackbar(output.uri, exportType.contentType) }
                    } catch (e: Exception) {
                        showProgress = false
                        importErrorMessage = e.message?:"Reason unknown"
                        showImporErrortDialog.value = true
                    } finally { showProgress = false }
                }
            }
        }

        val chooseOpmlExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
            val uri = result.data!!.data!!
            exportWithWriter(OpmlWriter(), uri, ExportTypes.OPML)
        }
        val chooseHtmlExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
            val uri = result.data!!.data!!
            exportWithWriter(HtmlWriter(), uri, ExportTypes.HTML)
        }
        val chooseFavoritesExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
            val uri = result.data!!.data!!
            exportWithWriter(FavoritesWriter(), uri, ExportTypes.FAVORITES)
        }
        val chooseProgressExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
            val uri = result.data!!.data!!
            exportWithWriter(EpisodesProgressWriter(), uri, ExportTypes.PROGRESS)
        }
        val restoreProgressLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != RESULT_OK || result.data?.data == null) return@rememberLauncherForActivityResult
            val uri = result.data!!.data
            uri?.let {
                if (isJsonFile(uri)) {
                    showProgress = true
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                                val reader = BufferedReader(InputStreamReader(inputStream))
                                EpisodeProgressReader().readDocument(reader)
                                reader.close()
                            }
                            withContext(Dispatchers.Main) {
                                showImporSuccessDialog.value = true
//                                showImportSuccessDialog()
                                showProgress = false
                            }
                        } catch (e: Throwable) {
                            showProgress = false
                            importErrorMessage = e.message?:"Reason unknown"
                            showImporErrortDialog.value = true
                        }
                    }
                } else {
                    val message = getString(R.string.import_file_type_toast) + ".json"
                    showProgress = false
                    importErrorMessage = message
                    showImporErrortDialog.value = true
                }
            }
        }
        var showOpmlImportSelectionDialog by remember { mutableStateOf(false) }
        val readElements = remember { mutableStateListOf<OpmlElement>() }
        val chooseOpmlImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            Logd(TAG, "chooseOpmlImportPathResult: uri: $uri")
            OpmlTransporter.startImport(this@PreferenceActivity, uri) {
                readElements.addAll(it)
                Logd(TAG, "readElements: ${readElements.size}")
            }
//        showImportSuccessDialog()
            showOpmlImportSelectionDialog = true
        }

        var comboRootUri by remember { mutableStateOf<Uri?>(null) }
        val comboDic = remember { mutableStateMapOf<String, Boolean>() }
        var showComboImportDialog by remember { mutableStateOf(false) }
        if (showComboImportDialog) {
            AlertDialog(onDismissRequest = { showComboImportDialog = false },
                title = { Text(stringResource(R.string.pref_select_properties), style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column {
                        comboDic.keys.forEach { option ->
                            if (option != "Media files" || comboDic["Database"] != true) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = comboDic[option] == true, onCheckedChange = {
                                    comboDic[option] = it
                                    if (option == "Database" && it) comboDic["Media files"] = false
                                })
                                Text(option, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val uri = comboRootUri!!
                        showProgress = true
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val rootFile = DocumentFile.fromTreeUri(this@PreferenceActivity, uri)
                                    if (rootFile != null && rootFile.isDirectory) {
                                        Logd(TAG, "comboDic[\"Preferences\"] ${comboDic["Preferences"]}")
                                        Logd(TAG, "comboDic[\"Media files\"] ${comboDic["Media files"]}")
                                        Logd(TAG, "comboDic[\"Database\"] ${comboDic["Database"]}")
                                        for (child in rootFile.listFiles()) {
                                            if (child.isDirectory) {
                                                if (child.name == prefsDirName) {
                                                    if (comboDic["Preferences"] == true) PreferencesTransporter().importBackup(child.uri, this@PreferenceActivity)
                                                } else if (child.name == mediaFilesDirName) {
                                                    if (comboDic["Media files"] == true) MediaFilesTransporter().importBackup(child.uri, this@PreferenceActivity)
                                                }
                                            } else if (isRealmFile(child.uri) && comboDic["Database"] == true) DatabaseTransporter().importBackup(child.uri, this@PreferenceActivity)
                                        }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    showImporSuccessDialog.value = true
//                                    showImportSuccessDialog()
                                    showProgress = false
                                }
                            } catch (e: Throwable) {
                                showProgress = false
                                importErrorMessage = e.message?:"Reason unknown"
                                showImporErrortDialog.value = true
                            }
                        }
                        showComboImportDialog = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showComboImportDialog = false }) { Text(text = "Cancel") } }
            )
        }
        var showComboExportDialog by remember { mutableStateOf(false) }
        if (showComboExportDialog) {
            AlertDialog(onDismissRequest = { showComboExportDialog = false },
                title = { Text(stringResource(R.string.pref_select_properties), style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column {
                        comboDic.keys.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = comboDic[option] == true, onCheckedChange = { comboDic[option] = it })
                                Text(option, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val uri = comboRootUri!!
                        showProgress = true
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                val chosenDir = DocumentFile.fromTreeUri(this@PreferenceActivity, uri) ?: throw IOException("Destination directory is not valid")
                                val exportSubDir = chosenDir.createDirectory(dateStampFilename("$backupDirName-%s")) ?: throw IOException("Error creating subdirectory $backupDirName")
                                val subUri: Uri = exportSubDir.uri
                                if (comboDic["Preferences"] == true) PreferencesTransporter().exportToDocument(subUri, this@PreferenceActivity)
                                if (comboDic["Media files"] == true) MediaFilesTransporter().exportToDocument(subUri, this@PreferenceActivity)
                                if (comboDic["Database"] == true) {
                                    val realmFile = exportSubDir.createFile("application/octet-stream", "backup.realm")
                                    if (realmFile != null) DatabaseTransporter().exportToDocument(realmFile.uri, this@PreferenceActivity)
                                }
                            }
                            withContext(Dispatchers.Main) { showProgress = false }
                        }
                        showComboExportDialog = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showComboExportDialog = false }) { Text(text = "Cancel") } }
            )
        }

        val restoreComboLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != RESULT_OK || result.data?.data == null) return@rememberLauncherForActivityResult
            val uri = result.data!!.data!!
            if (isComboDir(uri)) {
                val rootFile = DocumentFile.fromTreeUri(this@PreferenceActivity, uri)
                if (rootFile != null && rootFile.isDirectory) {
                    comboDic.clear()
                    for (child in rootFile.listFiles()) {
                        Logd(TAG, "restoreComboLauncher child: ${child.isDirectory} ${child.name} ${child.uri} ")
                        if (child.isDirectory) {
                            if (child.name == prefsDirName) comboDic["Preferences"] = true
                            else if (child.name == mediaFilesDirName) comboDic["Media files"] = false
                        } else if (isRealmFile(child.uri)) comboDic["Database"] = true
                    }
                }
                comboRootUri = uri
                showComboImportDialog = true
            } else {
                val message = getString(R.string.import_directory_toast) + backupDirName
                showProgress = false
                importErrorMessage = message
                showImporErrortDialog.value = true
            }
        }
        val backupComboLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val uri: Uri? = it.data?.data
                if (uri != null) {
                    comboDic.clear()
                    comboDic["Database"] = true
                    comboDic["Preferences"] = true
                    comboDic["Media files"] = true
                    comboRootUri = uri
                    showComboExportDialog = true
                }
            }
        }

        fun launchExportCombos() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            backupComboLauncher.launch(intent)
        }

        fun openExportPathPicker(exportType: ExportTypes, result: ActivityResultLauncher<Intent>, writer: ExportWriter) {
            val title = dateStampFilename(exportType.outputNameTemplate)
            val intentPickAction = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(exportType.contentType)
                .putExtra(Intent.EXTRA_TITLE, title)
            try {
                result.launch(intentPickAction)
                return
            } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") }
            // If we are using a SDK lower than API 21 or the implicit intent failed fallback to the legacy export process
            exportWithWriter(writer, null, exportType)
        }

        val textColor = MaterialTheme.colorScheme.onSurface
        if (showProgress) {
            Dialog(onDismissRequest = { showProgress = false }) {
                Surface(modifier = Modifier.size(100.dp), shape = RoundedCornerShape(8.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = {0.7f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp).align(Alignment.TopCenter))
                        Text("Loading...", color = textColor, modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        val scrollState = rememberScrollState()
        supportActionBar?.setTitle(R.string.import_export_pref)
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            TitleSummaryActionColumn(R.string.combo_export_label, R.string.combo_export_summary) { launchExportCombos() }
            val showComboImportDialog = remember { mutableStateOf(false) }
            ComfirmDialog(titleRes = R.string.combo_import_label, message = stringResource(R.string.combo_import_warning), showDialog = showComboImportDialog) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addCategory(Intent.CATEGORY_DEFAULT)
                restoreComboLauncher.launch(intent)
            }
            TitleSummaryActionColumn(R.string.combo_import_label, R.string.combo_import_summary) { showComboImportDialog.value = true }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp).padding(top = 10.dp, bottom = 10.dp))
            TitleSummaryActionColumn(R.string.opml_export_label, R.string.opml_export_summary) { openExportPathPicker(ExportTypes.OPML, chooseOpmlExportPathLauncher, OpmlWriter()) }
            if (showOpmlImportSelectionDialog) OpmlImportSelectionDialog(readElements) { showOpmlImportSelectionDialog = false }
            TitleSummaryActionColumn(R.string.opml_import_label, R.string.opml_import_summary) {
                try { chooseOpmlImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") } }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp).padding(top = 10.dp, bottom = 10.dp))
            TitleSummaryActionColumn(R.string.progress_export_label, R.string.progress_export_summary) { openExportPathPicker(ExportTypes.PROGRESS, chooseProgressExportPathLauncher, EpisodesProgressWriter()) }
            val showProgressImportDialog = remember { mutableStateOf(false) }
            ComfirmDialog(titleRes = R.string.progress_import_label, message = stringResource(R.string.progress_import_warning), showDialog = showProgressImportDialog) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.setType("*/*")
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                restoreProgressLauncher.launch(intent)
            }
            TitleSummaryActionColumn(R.string.progress_import_label, R.string.progress_import_summary) { showProgressImportDialog.value = true }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp).padding(top = 10.dp, bottom = 10.dp))
            TitleSummaryActionColumn(R.string.html_export_label, R.string.html_export_summary) { openExportPathPicker(ExportTypes.HTML, chooseHtmlExportPathLauncher, HtmlWriter()) }
            TitleSummaryActionColumn(R.string.favorites_export_label, R.string.favorites_export_summary) { openExportPathPicker(ExportTypes.FAVORITES, chooseFavoritesExportPathLauncher, FavoritesWriter()) }
        }
    }

    @Suppress("EnumEntryName")
    enum class MobileUpdateOptions(val res: Int) {
        feed_refresh(R.string.pref_mobileUpdate_refresh),
        episode_download(R.string.pref_mobileUpdate_episode_download),
        auto_download(R.string.pref_mobileUpdate_auto_download),
        streaming(R.string.pref_mobileUpdate_streaming),
        images(R.string.pref_mobileUpdate_images),
        sync(R.string.synchronization_pref);
    }

    @Composable
    fun DownloadsPreferencesScreen(navController: NavController) {
        class ProxyDialog(private val context: Context) {
            private lateinit var dialog: AlertDialog
            private lateinit var spType: Spinner
            private lateinit var etHost: EditText
            private lateinit var etPort: EditText
            private lateinit var etUsername: EditText
            private lateinit var etPassword: EditText
            private lateinit var txtvMessage: TextView
            private var testSuccessful = false
            private val port: Int
                get() {
                    val port = etPort.text.toString()
                    if (port.isNotEmpty()) try {
                        return port.toInt()
                    } catch (e: NumberFormatException) {
                    }
                    return 0
                }

            fun show(): Dialog {
                val content = View.inflate(context, R.layout.proxy_settings, null)
                val binding = ProxySettingsBinding.bind(content)
                spType = binding.spType
                dialog = MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.pref_proxy_title)
                    .setView(content)
                    .setNegativeButton(R.string.cancel_label, null)
                    .setPositiveButton(R.string.proxy_test_label, null)
                    .setNeutralButton(R.string.reset, null)
                    .show()
                // To prevent cancelling the dialog on button click
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    if (!testSuccessful) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        test()
                        return@setOnClickListener
                    }
                    setProxyConfig()
                    reinit()
                    dialog.dismiss()
                }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    etHost.text.clear()
                    etPort.text.clear()
                    etUsername.text.clear()
                    etPassword.text.clear()
                    setProxyConfig()
                }
                val types: MutableList<String> = ArrayList()
                types.add(Proxy.Type.DIRECT.name)
                types.add(Proxy.Type.HTTP.name)
                types.add(Proxy.Type.SOCKS.name)
                val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, types)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spType.setAdapter(adapter)
                val proxyConfig = proxyConfig
                spType.setSelection(adapter.getPosition(proxyConfig.type.name))
                etHost = binding.etHost
                if (!proxyConfig.host.isNullOrEmpty()) etHost.setText(proxyConfig.host)
                etHost.addTextChangedListener(requireTestOnChange)
                etPort = binding.etPort
                if (proxyConfig.port > 0) etPort.setText(proxyConfig.port.toString())
                etPort.addTextChangedListener(requireTestOnChange)
                etUsername = binding.etUsername
                if (!proxyConfig.username.isNullOrEmpty()) etUsername.setText(proxyConfig.username)
                etUsername.addTextChangedListener(requireTestOnChange)
                etPassword = binding.etPassword
                if (!proxyConfig.password.isNullOrEmpty()) etPassword.setText(proxyConfig.password)
                etPassword.addTextChangedListener(requireTestOnChange)
                if (proxyConfig.type == Proxy.Type.DIRECT) {
                    enableSettings(false)
                    setTestRequired(false)
                }
                spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = if (position == 0) View.GONE else View.VISIBLE
                        enableSettings(position > 0)
                        setTestRequired(position > 0)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        enableSettings(false)
                    }
                }
                txtvMessage = binding.txtvMessage
                checkValidity()
                return dialog
            }

            private fun setProxyConfig() {
                val type = spType.selectedItem as String
                val typeEnum = Proxy.Type.valueOf(type)
                val host = etHost.text.toString()
                val port = etPort.text.toString()
                var username: String? = etUsername.text.toString()
                if (username.isNullOrEmpty()) username = null
                var password: String? = etPassword.text.toString()
                if (password.isNullOrEmpty()) password = null
                var portValue = 0
                if (port.isNotEmpty()) portValue = port.toInt()
                val config = ProxyConfig(typeEnum, host, portValue, username, password)
                proxyConfig = config
                PodciniHttpClient.setProxyConfig(config)
            }

            private val requireTestOnChange: TextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    setTestRequired(true)
                }
            }

            private fun enableSettings(enable: Boolean) {
                etHost.isEnabled = enable
                etPort.isEnabled = enable
                etUsername.isEnabled = enable
                etPassword.isEnabled = enable
            }

            private fun checkValidity(): Boolean {
                var valid = true
                if (spType.selectedItemPosition > 0) valid = checkHost()
                valid = valid and checkPort()
                return valid
            }

            private fun checkHost(): Boolean {
                val host = etHost.text.toString()
                if (host.isEmpty()) {
                    etHost.error = context.getString(R.string.proxy_host_empty_error)
                    return false
                }
                if ("localhost" != host && !Patterns.DOMAIN_NAME.matcher(host).matches()) {
                    etHost.error = context.getString(R.string.proxy_host_invalid_error)
                    return false
                }
                return true
            }

            private fun checkPort(): Boolean {
                val port = port
                if (port < 0 || port > 65535) {
                    etPort.error = context.getString(R.string.proxy_port_invalid_error)
                    return false
                }
                return true
            }

            private fun setTestRequired(required: Boolean) {
                if (required) {
                    testSuccessful = false
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.proxy_test_label)
                } else {
                    testSuccessful = true
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(android.R.string.ok)
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
            }

            private fun test() {
                if (!checkValidity()) {
                    setTestRequired(true)
                    return
                }
                val res = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
                val textColorPrimary = res.getColor(0, 0)
                res.recycle()
                val checking = context.getString(R.string.proxy_checking)
                txtvMessage.setTextColor(textColorPrimary)
                txtvMessage.text = "{faw_circle_o_notch spin} $checking"
                txtvMessage.visibility = View.VISIBLE
                val coroutineScope = CoroutineScope(Dispatchers.Main)
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val type = spType.selectedItem as String
                        val host = etHost.text.toString()
                        val port = etPort.text.toString()
                        val username = etUsername.text.toString()
                        val password = etPassword.text.toString()
                        var portValue = 8080
                        if (port.isNotEmpty()) portValue = port.toInt()
                        val address: SocketAddress = InetSocketAddress.createUnresolved(host, portValue)
                        val proxyType = Proxy.Type.valueOf(type.uppercase())
                        val builder: OkHttpClient.Builder = newBuilder().connectTimeout(10, TimeUnit.SECONDS).proxy(Proxy(proxyType, address))
                        if (username.isNotEmpty()) {
                            builder.proxyAuthenticator { _: Route?, response: Response ->
                                val credentials = basic(username, password)
                                response.request.newBuilder().header("Proxy-Authorization", credentials).build()
                            }
                        }
                        val client: OkHttpClient = builder.build()
                        val request: Request = Builder().url("https://www.example.com").head().build()
                        try {
                            client.newCall(request).execute().use { response -> if (!response.isSuccessful) throw IOException(response.message) }
                        } catch (e: IOException) {
                            throw e
                        }
                        withContext(Dispatchers.Main) {
                            txtvMessage.setTextColor(getColorFromAttr(context, R.attr.icon_green))
                            val message = String.format("%s %s", "{faw_check}", context.getString(R.string.proxy_test_successful))
                            txtvMessage.text = message
                            setTestRequired(false)
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        txtvMessage.setTextColor(getColorFromAttr(context, R.attr.icon_red))
                        val message = String.format("%s %s: %s", "{faw_close}", context.getString(R.string.proxy_test_failed), e.message)
                        txtvMessage.text = message
                        setTestRequired(true)
                    }
                }
            }
        }

        var blockAutoDeleteLocal by remember { mutableStateOf(true) }
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        supportActionBar!!.setTitle(R.string.downloads_pref)
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            Text(stringResource(R.string.automation), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.feed_refresh_title), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    var interval by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefAutoUpdateIntervall.name, "12")!!) }
                    var showIcon by remember { mutableStateOf(false) }
                    TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("(hours)") },
                        singleLine = true, modifier = Modifier.weight(0.5f),
                        onValueChange = {
                            if (it.isEmpty() || it.toIntOrNull() != null) {
                                interval = it
                                showIcon = true
                            }
                        },
                        trailingIcon = {
                            if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                                modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                    if (interval.isEmpty()) interval = "0"
                                    appPrefs.edit().putString(UserPreferences.Prefs.prefAutoUpdateIntervall.name, interval).apply()
                                    showIcon = false
                                    restartUpdateAlarm(this@PreferenceActivity, true)
                                }))
                        })
                }
                Text(stringResource(R.string.feed_refresh_sum), color = textColor)
            }
            TitleSummaryActionColumn(R.string.pref_automatic_download_title, R.string.pref_automatic_download_sum) {
                navController.navigate(Screens.preferences_autodownload.tag)
//                openScreen(Screens.preferences_autodownload)
            }
            TitleSummarySwitchPrefRow(R.string.pref_auto_delete_title, R.string.pref_auto_delete_sum, UserPreferences.Prefs.prefAutoDelete.name)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_auto_local_delete_title), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pref_auto_local_delete_sum), color = textColor)
                }
                var isChecked by remember { mutableStateOf(appPrefs.getBoolean(UserPreferences.Prefs.prefAutoDeleteLocal.name, false)) }
                Switch(checked = isChecked, onCheckedChange = {
                    isChecked = it
                    if (blockAutoDeleteLocal && it) {
                        MaterialAlertDialogBuilder(this@PreferenceActivity)
                            .setMessage(R.string.pref_auto_local_delete_dialog_body)
                            .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                                blockAutoDeleteLocal = false
                                appPrefs.edit().putBoolean(UserPreferences.Prefs.prefAutoDeleteLocal.name, it).apply()
//                                                (findPreference<Preference>(Prefs.prefAutoDeleteLocal.name) as TwoStatePreference?)!!.isChecked = true
                                blockAutoDeleteLocal = true
                            }
                            .setNegativeButton(R.string.cancel_label, null)
                            .show()
                    }
                })
            }
            TitleSummarySwitchPrefRow(R.string.pref_keeps_important_episodes_title, R.string.pref_keeps_important_episodes_sum, UserPreferences.Prefs.prefFavoriteKeepsEpisode.name)
            TitleSummarySwitchPrefRow(R.string.pref_delete_removes_from_queue_title, R.string.pref_delete_removes_from_queue_sum, UserPreferences.Prefs.prefDeleteRemovesFromQueue.name)
            Text(stringResource(R.string.download_pref_details), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp))
            var showMeteredNetworkOptions by remember { mutableStateOf(false) }
            var tempSelectedOptions by remember { mutableStateOf(appPrefs.getStringSet(UserPreferences.Prefs.prefMobileUpdateTypes.name, setOf("images"))!!) }
            TitleSummaryActionColumn(R.string.pref_metered_network_title, R.string.pref_mobileUpdate_sum) { showMeteredNetworkOptions = true }
            if (showMeteredNetworkOptions) {
                AlertDialog(onDismissRequest = { showMeteredNetworkOptions = false },
                    title = { Text(stringResource(R.string.pref_metered_network_title), style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Column {
                            MobileUpdateOptions.entries.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                                    .clickable {
                                        tempSelectedOptions = if (tempSelectedOptions.contains(option.name)) tempSelectedOptions - option.name
                                        else tempSelectedOptions + option.name
                                    }) {
                                    Checkbox(checked = tempSelectedOptions.contains(option.name),
                                        onCheckedChange = {
                                            tempSelectedOptions = if (tempSelectedOptions.contains(option.name)) tempSelectedOptions - option.name
                                            else tempSelectedOptions + option.name
                                        })
                                    Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            appPrefs.edit().putStringSet(UserPreferences.Prefs.prefMobileUpdateTypes.name, tempSelectedOptions).apply()
                            showMeteredNetworkOptions = false
                        }) { Text(text = "OK") }
                    },
                    dismissButton = { TextButton(onClick = { showMeteredNetworkOptions = false }) { Text(text = "Cancel") } }
                )
            }
            TitleSummaryActionColumn(R.string.pref_proxy_title, R.string.pref_proxy_sum) { ProxyDialog(this@PreferenceActivity).show() }
        }
    }

    enum class EpisodeCleanupOptions(val res: Int, val num: Int) {
        ExceptFavorites(R.string.episode_cleanup_except_favorite, -3),
        Never(R.string.episode_cleanup_never, -2),
        NotInQueue(R.string.episode_cleanup_not_in_queue, -1),
        LimitBy(R.string.episode_cleanup_limit_by, 0)
    }

    @Composable
    fun AutoDownloadPreferencesScreen() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        supportActionBar!!.setTitle(R.string.pref_automatic_download_title)
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            var isEnabled by remember { mutableStateOf(appPrefs.getBoolean(UserPreferences.Prefs.prefEnableAutoDl.name, false)) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_automatic_download_title), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pref_automatic_download_sum), color = textColor)
                }
                Switch(checked = isEnabled, onCheckedChange = {
                    isEnabled = it
                    appPrefs.edit().putBoolean(UserPreferences.Prefs.prefEnableAutoDl.name, it).apply()
                })
            }
            if (isEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pref_episode_cache_title), color = textColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        var interval by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefEpisodeCacheSize.name, "25")!!) }
                        var showIcon by remember { mutableStateOf(false) }
                        TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("integer") },
                            singleLine = true, modifier = Modifier.weight(0.5f),
                            onValueChange = {
                                if (it.isEmpty() || it.toIntOrNull() != null) {
                                    interval = it
                                    showIcon = true
                                }
                            },
                            trailingIcon = {
                                if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                                    modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                        if (interval.isEmpty()) interval = "0"
                                        appPrefs.edit().putString(UserPreferences.Prefs.prefEpisodeCacheSize.name, interval).apply()
                                        showIcon = false
                                    }))
                            })
                    }
                    Text(stringResource(R.string.pref_episode_cache_summary), color = textColor)
                }
                var showCleanupOptions by remember { mutableStateOf(false) }
                TitleSummaryActionColumn(R.string.pref_episode_cleanup_title, R.string.pref_episode_cleanup_summary) { showCleanupOptions = true }
                if (showCleanupOptions) {
                    var tempCleanupOption by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefEpisodeCleanup.name, "-1")!!) }
                    var interval by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefEpisodeCleanup.name, "-1")!!) }
                    if ((interval.toIntOrNull() ?: -1) > 0) tempCleanupOption = EpisodeCleanupOptions.LimitBy.num.toString()
                    AlertDialog(onDismissRequest = { showCleanupOptions = false },
                        title = { Text(stringResource(R.string.pref_episode_cleanup_title), style = MaterialTheme.typography.titleLarge) },
                        text = {
                            Column {
                                EpisodeCleanupOptions.entries.forEach { option ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                                        .clickable { tempCleanupOption = option.num.toString() }) {
                                        Checkbox(checked = tempCleanupOption == option.num.toString(), onCheckedChange = { tempCleanupOption = option.num.toString() })
                                        Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                if (tempCleanupOption == EpisodeCleanupOptions.LimitBy.num.toString()) {
                                    TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("integer") }, singleLine = true,
                                        onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) interval = it })
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                var num = if (tempCleanupOption == EpisodeCleanupOptions.LimitBy.num.toString()) interval else tempCleanupOption
                                if (num.toIntOrNull() == null) num = EpisodeCleanupOptions.Never.num.toString()
                                appPrefs.edit().putString(UserPreferences.Prefs.prefEpisodeCleanup.name, num).apply()
                                showCleanupOptions = false
                            }) { Text(text = "OK") }
                        },
                        dismissButton = { TextButton(onClick = { showCleanupOptions = false }) { Text(text = "Cancel") } }
                    )
                }
                TitleSummarySwitchPrefRow(R.string.pref_automatic_download_on_battery_title, R.string.pref_automatic_download_on_battery_sum, UserPreferences.Prefs.prefEnableAutoDownloadOnBattery.name)
            }
        }
    }

    class NextcloudAuthenticationFragment : DialogFragment(), NextcloudLoginFlow.AuthenticationCallback {
        private val EXTRA_LOGIN_FLOW = "LoginFlow"
        private var binding: NextcloudAuthDialogBinding? = null
        private var nextcloudLoginFlow: NextcloudLoginFlow? = null
        private var shouldDismiss = false

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setTitle(R.string.gpodnetauth_login_butLabel)
            dialog.setNegativeButton(R.string.cancel_label, null)
            dialog.setCancelable(false)
            this.isCancelable = false

            binding = NextcloudAuthDialogBinding.inflate(layoutInflater)
            dialog.setView(binding!!.root)

            binding!!.chooseHostButton.setOnClickListener {
                nextcloudLoginFlow = NextcloudLoginFlow(getHttpClient(), binding!!.serverUrlText.text.toString(), requireContext(), this)
                startLoginFlow()
            }
            if (savedInstanceState?.getStringArrayList(EXTRA_LOGIN_FLOW) != null) {
                nextcloudLoginFlow = NextcloudLoginFlow.fromInstanceState(getHttpClient(), requireContext(), this,
                    savedInstanceState.getStringArrayList(EXTRA_LOGIN_FLOW)!!)
                startLoginFlow()
            }
            return dialog.create()
        }
        private fun startLoginFlow() {
            binding!!.errorText.visibility = View.GONE
            binding!!.chooseHostButton.visibility = View.GONE
            binding!!.loginProgressContainer.visibility = View.VISIBLE
            binding!!.serverUrlText.isEnabled = false
            nextcloudLoginFlow!!.start()
        }
        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            if (nextcloudLoginFlow != null) outState.putStringArrayList(EXTRA_LOGIN_FLOW, nextcloudLoginFlow!!.saveInstanceState())
        }
        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            nextcloudLoginFlow?.cancel()
        }
        override fun onResume() {
            super.onResume()
            nextcloudLoginFlow?.onResume()
            if (shouldDismiss) dismiss()
        }
        override fun onNextcloudAuthenticated(server: String, username: String, password: String) {
            setSelectedSyncProvider(SynchronizationProviderViewData.NEXTCLOUD_GPODDER)
            SynchronizationCredentials.clear(requireContext())
            SynchronizationCredentials.password = password
            SynchronizationCredentials.hosturl = server
            SynchronizationCredentials.username = username
            SyncService.fullSync(requireContext())
            if (isResumed) dismiss()
            else shouldDismiss = true
        }
        override fun onNextcloudAuthError(errorMessage: String?) {
            binding!!.loginProgressContainer.visibility = View.GONE
            binding!!.errorText.visibility = View.VISIBLE
            binding!!.errorText.text = errorMessage
            binding!!.chooseHostButton.visibility = View.VISIBLE
            binding!!.serverUrlText.isEnabled = true
        }

        companion object {
            val TAG = NextcloudAuthenticationFragment::class.simpleName ?: "Anonymous"
            private const val EXTRA_LOGIN_FLOW = "LoginFlow"
        }
    }

    class GpodderAuthenticationFragment : DialogFragment() {
        private var viewFlipper: ViewFlipper? = null
        private var currentStep = -1
        private var service: GpodnetService? = null
        @Volatile
        private var username: String? = null
        @Volatile
        private var password: String? = null
        @Volatile
        private var selectedDevice: GpodnetDevice? = null
        private var devices: List<GpodnetDevice>? = null

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setTitle(R.string.gpodnetauth_login_butLabel)
            dialog.setNegativeButton(R.string.cancel_label, null)
            dialog.setCancelable(false)
            this.isCancelable = false
            val binding = GpodnetauthDialogBinding.inflate(layoutInflater)
//        val root = View.inflate(context, R.layout.gpodnetauth_dialog, null)
            viewFlipper = binding.viewflipper
            advance()
            dialog.setView(binding.root)

            return dialog.create()
        }
        private fun setupHostView(view: View) {
            val binding = GpodnetauthHostBinding.bind(view)
            val selectHost = binding.chooseHostButton
            val serverUrlText = binding.serverUrlText
            selectHost.setOnClickListener {
                if (serverUrlText.text.isNullOrEmpty()) return@setOnClickListener

                SynchronizationCredentials.clear(requireContext())
                SynchronizationCredentials.hosturl = serverUrlText.text.toString()
                service = GpodnetService(getHttpClient(), SynchronizationCredentials.hosturl, SynchronizationCredentials.deviceID?:"",
                    SynchronizationCredentials.username?:"", SynchronizationCredentials.password?:"")
                dialog?.setTitle(SynchronizationCredentials.hosturl)
                advance()
            }
        }
        private fun setupLoginView(view: View) {
            val binding = GpodnetauthCredentialsBinding.bind(view)
            val username = binding.etxtUsername
            val password = binding.etxtPassword
            val login = binding.butLogin
            val txtvError = binding.credentialsError
            val progressBar = binding.progBarLogin
            val createAccountWarning = binding.createAccountWarning

            if (SynchronizationCredentials.hosturl != null && SynchronizationCredentials.hosturl!!.startsWith("http://"))
                createAccountWarning.visibility = View.VISIBLE

            password.setOnEditorActionListener { _: TextView?, actionID: Int, _: KeyEvent? -> actionID == EditorInfo.IME_ACTION_GO && login.performClick() }

            login.setOnClickListener {
                val usernameStr = username.text.toString()
                val passwordStr = password.text.toString()

                if (usernameHasUnwantedChars(usernameStr)) {
                    txtvError.setText(R.string.gpodnetsync_username_characters_error)
                    txtvError.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                login.isEnabled = false
                progressBar.visibility = View.VISIBLE
                txtvError.visibility = View.GONE
                val inputManager = requireContext().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                inputManager.hideSoftInputFromWindow(login.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            service?.setCredentials(usernameStr, passwordStr)
                            service?.login()
                            if (service != null) devices = service!!.devices
                            this@GpodderAuthenticationFragment.username = usernameStr
                            this@GpodderAuthenticationFragment.password = passwordStr
                        }
                        withContext(Dispatchers.Main) {
                            login.isEnabled = true
                            progressBar.visibility = View.GONE
                            advance()
                        }
                    } catch (e: Throwable) {
                        login.isEnabled = true
                        progressBar.visibility = View.GONE
                        txtvError.text = e.cause!!.message
                        txtvError.visibility = View.VISIBLE
                    }
                }
            }
        }
        private fun setupDeviceView(view: View) {
            val binding = GpodnetauthDeviceBinding.bind(view)
            val deviceName = binding.deviceName
            val devicesContainer = binding.devicesContainer
            deviceName.setText(generateDeviceName())

            val createDeviceButton = binding.createDeviceButton
            createDeviceButton.setOnClickListener { createDevice(view) }

            for (device in devices!!) {
                val rBinding = GpodnetauthDeviceRowBinding.inflate(layoutInflater)
//            val row = View.inflate(context, R.layout.gpodnetauth_device_row, null)
                val selectDeviceButton = rBinding.selectDeviceButton
                selectDeviceButton.setOnClickListener {
                    selectedDevice = device
                    advance()
                }
                selectDeviceButton.text = device.caption
                devicesContainer.addView(rBinding.root)
            }
        }
        private fun createDevice(view: View) {
            val binding = GpodnetauthDeviceBinding.bind(view)
            val deviceName = binding.deviceName
            val txtvError = binding.deviceSelectError
            val progBarCreateDevice = binding.progbarCreateDevice

            val deviceNameStr = deviceName.text.toString()
            if (isDeviceInList(deviceNameStr)) return

            progBarCreateDevice.visibility = View.VISIBLE
            txtvError.visibility = View.GONE
            deviceName.isEnabled = false

            lifecycleScope.launch {
                try {
                    val device = withContext(Dispatchers.IO) {
                        val deviceId = generateDeviceId(deviceNameStr)
                        service!!.configureDevice(deviceId, deviceNameStr, GpodnetDevice.DeviceType.MOBILE)
                        GpodnetDevice(deviceId, deviceNameStr, GpodnetDevice.DeviceType.MOBILE.toString(), 0)
                    }
                    withContext(Dispatchers.Main) {
                        progBarCreateDevice.visibility = View.GONE
                        selectedDevice = device
                        advance()
                    }
                } catch (e: Throwable) {
                    deviceName.isEnabled = true
                    progBarCreateDevice.visibility = View.GONE
                    txtvError.text = e.message
                    txtvError.visibility = View.VISIBLE
                }
            }
        }
        private fun generateDeviceName(): String {
            val baseName = getString(R.string.gpodnetauth_device_name_default, Build.MODEL)
            var name = baseName
            var num = 1
            while (isDeviceInList(name)) {
                name = "$baseName ($num)"
                num++
            }
            return name
        }
        private fun generateDeviceId(name: String): String {
            // devices names must be of a certain form:
            // https://gpoddernet.readthedocs.org/en/latest/api/reference/general.html#devices
            return generateFileName(name).replace("\\W".toRegex(), "_").lowercase()
        }
        private fun isDeviceInList(name: String): Boolean {
            if (devices == null) return false

            val id = generateDeviceId(name)
            for (device in devices!!) {
                if (device.id == id || device.caption == name) return true
            }
            return false
        }
        private fun setupFinishView(view: View) {
            val binding = GpodnetauthFinishBinding.bind(view)
            val sync = binding.butSyncNow

            sync.setOnClickListener {
                dismiss()
                SyncService.sync(requireContext())
            }
        }
        private fun advance() {
            if (currentStep < STEP_FINISH) {
                val view = viewFlipper!!.getChildAt(currentStep + 1)
                when (currentStep) {
                    STEP_DEFAULT -> setupHostView(view)
                    STEP_HOSTNAME -> setupLoginView(view)
                    STEP_LOGIN -> {
                        check(!(username == null || password == null)) { "Username and password must not be null here" }
                        setupDeviceView(view)
                    }
                    STEP_DEVICE -> {
                        checkNotNull(selectedDevice) { "Device must not be null here" }
                        setSelectedSyncProvider(SynchronizationProviderViewData.GPODDER_NET)
                        SynchronizationCredentials.username = username
                        SynchronizationCredentials.password = password
                        SynchronizationCredentials.deviceID = selectedDevice!!.id
                        setupFinishView(view)
                    }
                }
                if (currentStep != STEP_DEFAULT) viewFlipper!!.showNext()
                currentStep++
            } else dismiss()
        }
        private fun usernameHasUnwantedChars(username: String): Boolean {
            val special = Pattern.compile("[!@#$%&*()+=|<>?{}\\[\\]~]")
            val containsUnwantedChars = special.matcher(username)
            return containsUnwantedChars.find()
        }

        companion object {
            val TAG = GpodderAuthenticationFragment::class.simpleName ?: "Anonymous"

            private const val STEP_DEFAULT = -1
            private const val STEP_HOSTNAME = 0
            private const val STEP_LOGIN = 1
            private const val STEP_DEVICE = 2
            private const val STEP_FINISH = 3
        }
    }

    class WifiAuthenticationFragment : DialogFragment() {
        private var binding: WifiSyncDialogBinding? = null
        private var portNum = 0
        private var isGuest: Boolean? = null

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setTitle(R.string.connect_to_peer)
            dialog.setNegativeButton(R.string.cancel_label, null)
            dialog.setPositiveButton(R.string.confirm_label, null)

            binding = WifiSyncDialogBinding.inflate(layoutInflater)
            dialog.setView(binding!!.root)

            binding!!.hostAddressText.setText(SynchronizationCredentials.hosturl?:"")
            portNum = SynchronizationCredentials.hostport
            if (portNum == 0) portNum = hostPort
            binding!!.hostPortText.setText(portNum.toString())

            binding!!.guestButton.setOnClickListener {
                binding!!.hostAddressText.visibility = View.VISIBLE
                binding!!.hostPortText.visibility = View.VISIBLE
                binding!!.hostButton.visibility = View.INVISIBLE
                SynchronizationCredentials.hosturl = binding!!.hostAddressText.text.toString()
                portNum = binding!!.hostPortText.text.toString().toInt()
                isGuest = true
                SynchronizationCredentials.hostport = portNum
            }
            binding!!.hostButton.setOnClickListener {
                binding!!.hostAddressText.visibility = View.VISIBLE
                binding!!.hostPortText.visibility = View.VISIBLE
                binding!!.guestButton.visibility = View.INVISIBLE
                val wifiManager = requireContext().applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val ipAddress = wifiManager.connectionInfo.ipAddress
                val ipString = String.format(Locale.US, "%d.%d.%d.%d", ipAddress and 0xff, ipAddress shr 8 and 0xff, ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff)
                binding!!.hostAddressText.setText(ipString)
                binding!!.hostAddressText.isEnabled = false
                portNum = binding!!.hostPortText.text.toString().toInt()
                isGuest = false
                SynchronizationCredentials.hostport = portNum
            }
            procFlowEvents()
            return dialog.create()
        }
        override fun onDestroy() {
            cancelFlowEvents()
            super.onDestroy()
        }
        override fun onResume() {
            super.onResume()
            val d = dialog as? AlertDialog
            if (d != null) {
                val confirmButton = d.getButton(Dialog.BUTTON_POSITIVE) as Button
                confirmButton.setOnClickListener {
                    Logd(TAG, "confirm button pressed")
                    if (isGuest == null) {
                        Toast.makeText(requireContext(), R.string.host_or_guest, Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    binding!!.progressContainer.visibility = View.VISIBLE
                    confirmButton.visibility = View.INVISIBLE
                    val cancelButton = d.getButton(Dialog.BUTTON_NEGATIVE) as Button
                    cancelButton.visibility = View.INVISIBLE
                    portNum = binding!!.hostPortText.text.toString().toInt()
                    setWifiSyncEnabled(true)
                    startInstantSync(requireContext(), portNum, binding!!.hostAddressText.text.toString(), isGuest!!)
                }
            }
        }

        private var eventSink: Job?     = null
        private fun cancelFlowEvents() {
            eventSink?.cancel()
            eventSink = null
        }
        private fun procFlowEvents() {
            if (eventSink != null) return
            eventSink = lifecycleScope.launch {
                EventFlow.events.collectLatest { event ->
                    Logd(TAG, "Received event: ${event.TAG}")
                    when (event) {
                        is FlowEvent.SyncServiceEvent -> syncStatusChanged(event)
                        else -> {}
                    }
                }
            }
        }
        fun syncStatusChanged(event: FlowEvent.SyncServiceEvent) {
            when (event.messageResId) {
                R.string.sync_status_error -> {
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                    dialog?.dismiss()
                }
                R.string.sync_status_success -> {
                    Toast.makeText(requireContext(), R.string.sync_status_success, Toast.LENGTH_LONG).show()
                    dialog?.dismiss()
                }
                R.string.sync_status_in_progress -> binding!!.progressBar.progress = event.message.toInt()
                else -> {
                    Logd(TAG, "Sync result unknow ${event.messageResId}")
//                Toast.makeText(context, "Sync result unknow ${event.messageResId}", Toast.LENGTH_LONG).show()
                }
            }
        }

        companion object {
            val TAG = WifiAuthenticationFragment::class.simpleName ?: "Anonymous"
        }
    }

    @Composable
    fun SynchronizationPreferencesScreen() {
        abstract class AuthenticationDialog(context: Context, titleRes: Int, enableUsernameField: Boolean, usernameInitialValue: String?, passwordInitialValue: String?)
            : MaterialAlertDialogBuilder(context) {

            var passwordHidden: Boolean = true

            init {
                setTitle(titleRes)
                val viewBinding = AuthenticationDialogBinding.inflate(LayoutInflater.from(context))
                setView(viewBinding.root)

                viewBinding.usernameEditText.isEnabled = enableUsernameField
                if (usernameInitialValue != null) viewBinding.usernameEditText.setText(usernameInitialValue)
                if (passwordInitialValue != null) viewBinding.passwordEditText.setText(passwordInitialValue)

                viewBinding.showPasswordButton.setOnClickListener {
                    if (passwordHidden) {
                        viewBinding.passwordEditText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                        viewBinding.showPasswordButton.alpha = 1.0f
                    } else {
                        viewBinding.passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
                        viewBinding.showPasswordButton.alpha = 0.6f
                    }
                    passwordHidden = !passwordHidden
                }

                setOnCancelListener { onCancelled() }
                setNegativeButton(R.string.cancel_label) { _: DialogInterface?, _: Int -> onCancelled() }
                setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
                    onConfirmed(viewBinding.usernameEditText.text.toString(), viewBinding.passwordEditText.text.toString())
                }
            }

            protected open fun onCancelled() {}
            protected abstract fun onConfirmed(username: String, password: String)
        }

        val selectedSyncProviderKey: String = SynchronizationSettings.selectedSyncProviderKey?:""
        var selectedProvider by remember { mutableStateOf(SynchronizationProviderViewData.fromIdentifier(selectedSyncProviderKey)) }
        var loggedIn by remember { mutableStateOf(isProviderConnected) }

        fun updateLastSyncReport(successful: Boolean, lastTime: Long) {
            val status = String.format("%1\$s (%2\$s)", getString(if (successful) R.string.gpodnetsync_pref_report_successful else R.string.gpodnetsync_pref_report_failed),
                DateUtils.getRelativeDateTimeString(this@PreferenceActivity, lastTime, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_SHOW_TIME))
            supportActionBar!!.subtitle = status
        }
        fun syncStatusChanged(event: FlowEvent.SyncServiceEvent) {
            if (!isProviderConnected && !wifiSyncEnabledKey) return
            loggedIn = isProviderConnected
            if (event.messageResId == R.string.sync_status_error || event.messageResId == R.string.sync_status_success)
                updateLastSyncReport(SynchronizationSettings.isLastSyncSuccessful, SynchronizationSettings.lastSyncAttempt)
            else supportActionBar!!.setSubtitle(event.messageResId)
        }

        fun chooseProviderAndLogin() {
            val builder = MaterialAlertDialogBuilder(this@PreferenceActivity)
            builder.setTitle(R.string.dialog_choose_sync_service_title)

            val providers = SynchronizationProviderViewData.entries.toTypedArray()
            val adapter: ListAdapter = object : ArrayAdapter<SynchronizationProviderViewData?>(this@PreferenceActivity, R.layout.alertdialog_sync_provider_chooser, providers) {
                var holder: ViewHolder? = null

                inner class ViewHolder {
                    var icon: ImageView? = null
                    var title: TextView? = null
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    var convertView = convertView
                    val inflater = LayoutInflater.from(context)
                    if (convertView == null) {
                        convertView = inflater.inflate(R.layout.alertdialog_sync_provider_chooser, null)
                        val binding = AlertdialogSyncProviderChooserBinding.bind(convertView)
                        holder = ViewHolder()
                        if (holder != null) {
                            holder!!.icon = binding.icon
                            holder!!.title = binding.title
                            convertView.tag = holder
                        }
                    } else holder = convertView.tag as ViewHolder

                    val synchronizationProviderViewData = getItem(position)
                    holder!!.title!!.setText(synchronizationProviderViewData!!.summaryResource)
                    holder!!.icon!!.setImageResource(synchronizationProviderViewData.iconResource)
                    return convertView!!
                }
            }

            builder.setAdapter(adapter) { _: DialogInterface?, which: Int ->
                when (providers[which]) {
                    SynchronizationProviderViewData.GPODDER_NET -> GpodderAuthenticationFragment().show(supportFragmentManager, GpodderAuthenticationFragment.TAG)
                    SynchronizationProviderViewData.NEXTCLOUD_GPODDER -> NextcloudAuthenticationFragment().show(supportFragmentManager, NextcloudAuthenticationFragment.TAG)
                }
                loggedIn = isProviderConnected
            }

            builder.show()
        }

        fun isProviderSelected(provider: SynchronizationProviderViewData): Boolean {
            val selectedSyncProviderKey = selectedSyncProviderKey
            return provider.identifier == selectedSyncProviderKey
        }

        supportActionBar!!.setTitle(R.string.synchronization_pref)
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            IconTitleSummaryActionRow(R.drawable.wifi_sync, R.string.wifi_sync, R.string.wifi_sync_summary_unchoosen) { WifiAuthenticationFragment().show(supportFragmentManager, WifiAuthenticationFragment.TAG) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                var titleRes by remember { mutableStateOf(0) }
                var summaryRes by remember { mutableIntStateOf(R.string.synchronization_summary_unchoosen) }
                var iconRes by remember { mutableIntStateOf(R.drawable.ic_notification_sync) }
                var onClick: (() -> Unit)? = null
                if (loggedIn) {
                    selectedProvider = SynchronizationProviderViewData.fromIdentifier(selectedSyncProviderKey)
                    if (selectedProvider != null) {
                        summaryRes = selectedProvider!!.summaryResource
                        iconRes = selectedProvider!!.iconResource
                    }
                } else {
                    titleRes = R.string.synchronization_choose_title
                    summaryRes = R.string.synchronization_summary_unchoosen
                    iconRes = R.drawable.ic_cloud
                    onClick = { chooseProviderAndLogin() }
                }
                Icon(imageVector = ImageVector.vectorResource(iconRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                TitleSummaryActionColumn(titleRes, summaryRes) { onClick?.invoke() }
            }
            if (isProviderSelected(SynchronizationProviderViewData.GPODDER_NET)) {
                TitleSummaryActionColumn(R.string.pref_gpodnet_setlogin_information_title, R.string.pref_gpodnet_setlogin_information_sum) {
                    val dialog: AuthenticationDialog = object : AuthenticationDialog(this@PreferenceActivity, R.string.pref_gpodnet_setlogin_information_title,
                        false, SynchronizationCredentials.username, null) {
                        override fun onConfirmed(username: String, password: String) {
                            SynchronizationCredentials.password = password
                        }
                    }
                    dialog.show()
                }
            }
            if (loggedIn) {
                TitleSummaryActionColumn(R.string.synchronization_sync_changes_title, R.string.synchronization_sync_summary) { SyncService.syncImmediately(applicationContext) }
                TitleSummaryActionColumn(R.string.synchronization_full_sync_title, R.string.synchronization_force_sync_summary) { SyncService.fullSync(this@PreferenceActivity) }
//                val snackbarHostState = remember { SnackbarHostState() }
//                val scope = rememberCoroutineScope()
                TitleSummaryActionColumn(R.string.synchronization_logout, 0) {
                    SynchronizationCredentials.clear(this@PreferenceActivity)
//                    scope.launch {
//                        snackbarHostState.showSnackbar(getString(R.string.pref_synchronization_logout_toast), duration = SnackbarDuration.Long)
//                    }
                    Snackbar.make(findViewById(android.R.id.content), R.string.pref_synchronization_logout_toast, Snackbar.LENGTH_LONG).show()
                    setSelectedSyncProvider(null)
                    loggedIn = isProviderConnected
                }
            }
        }
    }

    @Composable
    fun NotificationPreferencesScreen() {
        supportActionBar!!.setTitle(R.string.notification_pref_fragment)
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            Text(stringResource(R.string.notification_group_errors), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TitleSummarySwitchPrefRow(R.string.notification_channel_download_error, R.string.notification_channel_download_error_description, UserPreferences.Prefs.prefShowDownloadReport.name)
            if (isProviderConnected)
                TitleSummarySwitchPrefRow(R.string.notification_channel_sync_error, R.string.notification_channel_sync_error_description, UserPreferences.Prefs.pref_gpodnet_notifications.name)
        }
    }

    @Suppress("EnumEntryName")
    enum class Screens(val titleRes: Int, val tag: String) {
        preferences_user_interface(R.string.user_interface_label, "InterfaceScreen"),
        preferences_playback(R.string.playback_pref, "PlaybackScreen"),
        preferences_downloads(R.string.downloads_pref, "DownloadScreen"),
        preferences_synchronization(R.string.synchronization_pref, "SynchronizationScreen"),
        preferences_import_export(R.string.import_export_pref, "ImportExportScreen"),
        preferences_notifications(R.string.notification_pref_fragment, "NotificationScreen"),
        preferences_autodownload(R.string.pref_automatic_download_title, "AutoDownloadScreen"),
        preferences_about(R.string.about_pref, "AboutScreen"),
        preferences_license(R.string.licenses, "LicensesScreen"),
        preferences_swipe(R.string.swipeactions_label, "SwipeScreen");
    }

    companion object {
        private const val FRAGMENT_TAG = "tag_preferences"
        const val OPEN_AUTO_DOWNLOAD_SETTINGS: String = "OpenAutoDownloadSettings"
    }
}
