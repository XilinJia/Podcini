package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.preferences.ThemeSwitcher.getNoTitleTheme
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.DefaultPages
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.screens.*
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.xml.parsers.DocumentBuilderFactory

class PreferenceActivity : AppCompatActivity() {
    var copyrightNoticeText by mutableStateOf("")
    var showToast by  mutableStateOf(false)
    var toastMassege by mutableStateOf("")
    var topAppBarTitle by mutableStateOf("Home")

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getNoTitleTheme(this))
        super.onCreate(savedInstanceState)
        Logd("PreferenceActivity", "onCreate")

        val packageHash = packageName.hashCode()
        when {
            packageHash != 1329568231 && packageHash != 1297601420 -> {
                copyrightNoticeText = ("This application is based on Podcini."
                        + " The Podcini team does NOT provide support for this unofficial version."
                        + " If you can read this message, the developers of this modification violate the GNU General Public License (GPL).")
            }
            packageHash == 1297601420 -> copyrightNoticeText = "This is a development version of Podcini and not meant for daily use"
        }

        setContent {
            val navController = rememberNavController()
            CustomTheme(this) {
                if (showToast) CustomToast(message = toastMassege, onDismiss = { showToast = false })
                Scaffold(topBar = { TopAppBar(title = { Text(topAppBarTitle) },
                    navigationIcon = { IconButton(onClick = {
                        if (navController.previousBackStackEntry != null) navController.popBackStack()
                        else onBackPressed()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }
                ) { innerPadding ->
                    NavHost(navController = navController, startDestination = "Main", Modifier.padding(innerPadding)) {
                        composable(Screens.main.tag) {
                            topAppBarTitle = stringResource(Screens.main.titleRes)
                            MainPreferencesScreen(navController) }
                        composable(Screens.ui.tag) {
                            topAppBarTitle = stringResource(Screens.ui.titleRes)
                            UserInterfacePreferencesScreen() }
                        composable(Screens.downloads.tag) {
                            topAppBarTitle = stringResource(Screens.downloads.titleRes)
                            DownloadsPreferencesScreen(this@PreferenceActivity, navController) }
                        composable(Screens.ie.tag) {
                            topAppBarTitle = stringResource(Screens.ie.titleRes)
                            ImportExportPreferencesScreen(this@PreferenceActivity) }
                        composable(Screens.autodownload.tag) {
                            topAppBarTitle = stringResource(Screens.autodownload.titleRes)
                            AutoDownloadPreferencesScreen() }
                        composable(Screens.synchronization.tag) {
                            topAppBarTitle = stringResource(Screens.synchronization.titleRes)
                            SynchronizationPreferencesScreen(this@PreferenceActivity) }
                        composable(Screens.playback.tag) {
                            topAppBarTitle = stringResource(Screens.playback.titleRes)
                            PlaybackPreferencesScreen() }
                        composable(Screens.notifications.tag) {
                            topAppBarTitle = stringResource(Screens.notifications.titleRes)
                            NotificationPreferencesScreen() }
                        composable(Screens.about.tag) {
                            topAppBarTitle = stringResource(Screens.about.titleRes)
                            AboutScreen(navController) }
                        composable(Screens.license.tag) {
                            topAppBarTitle = stringResource(Screens.license.titleRes)
                            LicensesScreen() }
                    }
                }
            }
        }
    }

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
        val s = Snackbar.make(findViewById(android.R.id.content), event.message, Snackbar.LENGTH_LONG)
        if (event.action != null) s.setAction(event.actionText) { event.action.accept(this) }
        s.show()
    }

    @Composable
    fun MainPreferencesScreen(navController: NavController) {
        @Composable
        fun IconTitleSummaryScreenRow(vecRes: Int, titleRes: Int, summaryRes: Int, screen: String) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                Icon(imageVector = ImageVector.vectorResource(vecRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                Column(modifier = Modifier.weight(1f).clickable(onClick = {
                    navController.navigate(screen)
                })) {
                    Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    Text(stringResource(summaryRes), color = textColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        @Composable
        fun IconTitleActionRow(vecRes: Int, titleRes: Int, callback: ()-> Unit) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                Icon(imageVector = ImageVector.vectorResource(vecRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                Column(modifier = Modifier.weight(1f).clickable(onClick = { callback() })) {
                    Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
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
            IconTitleSummaryScreenRow(R.drawable.ic_appearance, R.string.user_interface_label, R.string.user_interface_sum, Screens.ui.tag)
            IconTitleSummaryScreenRow(R.drawable.ic_play_24dp, R.string.playback_pref, R.string.playback_pref_sum, Screens.playback.tag)
            IconTitleSummaryScreenRow(R.drawable.ic_download, R.string.downloads_pref, R.string.downloads_pref_sum, Screens.downloads.tag)
            IconTitleSummaryScreenRow(R.drawable.ic_cloud, R.string.synchronization_pref, R.string.synchronization_sum, Screens.synchronization.tag)
            IconTitleSummaryScreenRow(R.drawable.ic_storage, R.string.import_export_pref, R.string.import_export_summary, Screens.ie.tag)
            IconTitleActionRow(R.drawable.ic_notifications, R.string.notification_pref_fragment) { navController.navigate(Screens.notifications.tag) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_backup_on_google_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pref_backup_on_google_sum), color = textColor, style = MaterialTheme.typography.bodySmall)
                }
                var isChecked by remember { mutableStateOf(getPref(AppPrefs.prefOPMLBackup, true)) }
                Switch(checked = isChecked, onCheckedChange = {
                    isChecked = it
                    putPref(AppPrefs.prefOPMLBackup, it)
                    val intent = packageManager?.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                })
            }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
            Text(stringResource(R.string.project_pref), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            IconTitleActionRow(R.drawable.ic_questionmark, R.string.documentation_support) { openInBrowser(this@PreferenceActivity, "https://github.com/XilinJia/Podcini") }
            IconTitleActionRow(R.drawable.ic_chat, R.string.visit_user_forum) { openInBrowser(this@PreferenceActivity, "https://github.com/XilinJia/Podcini/discussions") }
            IconTitleActionRow(R.drawable.ic_contribute, R.string.pref_contribute) { openInBrowser(this@PreferenceActivity, "https://github.com/XilinJia/Podcini") }
            IconTitleActionRow(R.drawable.ic_bug, R.string.bug_report_title) { startActivity(Intent(this@PreferenceActivity, BugReportActivity::class.java)) }
            IconTitleActionRow(R.drawable.ic_info, R.string.about_pref) {
                navController.navigate(Screens.about.tag)
            }
        }
    }

    @Composable
    fun AboutScreen(navController: NavController) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp)) {
            Image(painter = painterResource(R.drawable.teaser), contentDescription = "")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp)) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_star), contentDescription = "", tint = textColor)
                Column(Modifier.padding(start = 10.dp).clickable(onClick = {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(getString(R.string.bug_report_title), PreferenceManager.getDefaultSharedPreferences(this@PreferenceActivity).getString("about_version", "Default summary"))
                    clipboard.setPrimaryClip(clip)
                    if (Build.VERSION.SDK_INT <= 32) {
                        toastMassege = getString(R.string.copied_to_clipboard)
                        showToast = true
                    }
                })) {
                    Text(stringResource(R.string.podcini_version), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    Text(String.format("%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.COMMIT_HASH), color = textColor)
                }
            }
            IconTitleSummaryActionRow(R.drawable.ic_questionmark, R.string.online_help, R.string.online_help_sum) { openInBrowser(this@PreferenceActivity, "https://github.com/XilinJia/Podcini/") }
            IconTitleSummaryActionRow(R.drawable.ic_info, R.string.privacy_policy, R.string.privacy_policy) { openInBrowser(this@PreferenceActivity, "https://github.com/XilinJia/Podcini/blob/main/PrivacyPolicy.md") }
            IconTitleSummaryActionRow(R.drawable.ic_info, R.string.licenses, R.string.licenses_summary) { navController.navigate(Screens.license.tag) }
            IconTitleSummaryActionRow(R.drawable.baseline_mail_outline_24, R.string.email_developer, R.string.email_sum) {
                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("xilin.vw@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Regarding Podcini")
                    setType("message/rfc822")
                }
                if (emailIntent.resolveActivity(packageManager) != null) startActivity(emailIntent)
                else {
                    toastMassege = getString(R.string.need_email_client)
                    showToast = true
                }
            }
        }
    }

    @Composable
    fun LicensesScreen() {
        class LicenseItem(val title: String, val subtitle: String, val licenseUrl: String, val licenseTextFile: String)
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
                    Text(licenses[curLicenseIndex].title, color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
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
                    Text(item.title, color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    Text(item.subtitle, color = textColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @Composable
    fun UserInterfacePreferencesScreen() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            Text(stringResource(R.string.appearance), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                var checkIndex by remember { mutableIntStateOf(
                    when(AppPreferences.theme) {
                        AppPreferences.ThemePreference.SYSTEM -> 0
                        AppPreferences.ThemePreference.LIGHT -> 1
                        AppPreferences.ThemePreference.DARK -> 2
                        else -> 0
                    }) }
                Spacer(Modifier.weight(1f))
                RadioButton(selected = checkIndex == 0, onClick = {
                    checkIndex = 0
                    AppPreferences.theme = AppPreferences.ThemePreference.SYSTEM
                    ActivityCompat.recreate(this@PreferenceActivity)
                })
                Text(stringResource(R.string.pref_theme_title_automatic), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                RadioButton(selected = checkIndex == 1, onClick = {
                    checkIndex = 1
                    AppPreferences.theme = AppPreferences.ThemePreference.LIGHT
                    ActivityCompat.recreate(this@PreferenceActivity)
                })
                Text(stringResource(R.string.pref_theme_title_light), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                RadioButton(selected = checkIndex == 2, onClick = {
                    checkIndex = 2
                    AppPreferences.theme = AppPreferences.ThemePreference.DARK
                    ActivityCompat.recreate(this@PreferenceActivity)
                })
                Text(stringResource(R.string.pref_theme_title_dark), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_black_theme_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pref_black_theme_message), color = textColor)
                }
                var isChecked by remember { mutableStateOf(getPref(AppPrefs.prefThemeBlack, false)) }
                Switch(checked = isChecked, onCheckedChange = {
                    isChecked = it
                    putPref(AppPrefs.prefThemeBlack, it)
                    ActivityCompat.recreate(this@PreferenceActivity)
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_tinted_theme_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pref_tinted_theme_message), color = textColor)
                }
                var isChecked by remember { mutableStateOf(getPref(AppPrefs.prefTintedColors, false)) }
                Switch(checked = isChecked, onCheckedChange = {
                    isChecked = it
                    putPref(AppPrefs.prefTintedColors, it)
                    ActivityCompat.recreate(this@PreferenceActivity)
                })
            }
            TitleSummarySwitchPrefRow(R.string.pref_episode_cover_title, R.string.pref_episode_cover_summary, AppPrefs.prefEpisodeCover.name)
            TitleSummarySwitchPrefRow(R.string.pref_show_remain_time_title, R.string.pref_show_remain_time_summary, AppPrefs.showTimeLeft.name)
            Text(stringResource(R.string.subscriptions_label), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            TitleSummarySwitchPrefRow(R.string.pref_swipe_refresh_title, R.string.pref_swipe_refresh_sum, AppPrefs.prefSwipeToRefreshAll.name, true)
            TitleSummarySwitchPrefRow(R.string.pref_feedGridLayout_title, R.string.pref_feedGridLayout_sum, AppPrefs.prefFeedGridLayout.name)
            Text(stringResource(R.string.external_elements), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            if (Build.VERSION.SDK_INT < 26) {
                TitleSummarySwitchPrefRow(R.string.pref_expandNotify_title, R.string.pref_expandNotify_sum, AppPrefs.prefExpandNotify.name)
            }
//            TitleSummarySwitchPrefRow(R.string.pref_persistNotify_title, R.string.pref_persistNotify_sum, UserPreferences.Prefs.prefPersistNotify.name)
            TitleSummarySwitchPrefRow(R.string.pref_show_notification_skip_title, R.string.pref_show_notification_skip_sum, AppPrefs.prefShowSkip.name, true)
            Text(stringResource(R.string.behavior), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            var showDefaultPageOptions by remember { mutableStateOf(false) }
            var tempSelectedOption by remember { mutableStateOf(getPref(AppPrefs.prefDefaultPage, DefaultPages.SubscriptionsFragment.name)!!) }
            TitleSummaryActionColumn(R.string.pref_default_page, R.string.pref_default_page_sum) { showDefaultPageOptions = true }
            if (showDefaultPageOptions) {
                AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showDefaultPageOptions = false },
                    title = { Text(stringResource(R.string.pref_default_page), style = CustomTextStyles.titleCustom) },
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
                            putPref(AppPrefs.prefDefaultPage, tempSelectedOption)
                            showDefaultPageOptions = false
                        }) { Text(text = "OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDefaultPageOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
                )
            }
            TitleSummarySwitchPrefRow(R.string.pref_back_button_opens_drawer, R.string.pref_back_button_opens_drawer_summary, AppPrefs.prefBackButtonOpensDrawer.name)
        }
    }

    @Composable
    fun NotificationPreferencesScreen() {
        if (Build.VERSION.SDK_INT >= 26) {
            val intent = Intent()
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } else {
//            supportActionBar!!.setTitle(R.string.notification_pref_fragment)
            val textColor = MaterialTheme.colorScheme.onSurface
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
                Text(stringResource(R.string.notification_group_errors), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TitleSummarySwitchPrefRow(R.string.notification_channel_download_error, R.string.notification_channel_download_error_description, AppPrefs.prefShowDownloadReport.name, true)
                if (isProviderConnected)
                    TitleSummarySwitchPrefRow(R.string.notification_channel_sync_error, R.string.notification_channel_sync_error_description, AppPrefs.pref_gpodnet_notifications.name, true)
            }
        }
    }

    @Suppress("EnumEntryName")
    enum class Screens(val titleRes: Int, val tag: String) {
        main(R.string.settings_label, "Main"),
        ui(R.string.user_interface_label, "InterfaceScreen"),
        playback(R.string.playback_pref, "PlaybackScreen"),
        downloads(R.string.downloads_pref, "DownloadScreen"),
        synchronization(R.string.synchronization_pref, "SynchronizationScreen"),
        ie(R.string.import_export_pref, "ImportExportScreen"),
        notifications(R.string.notification_pref_fragment, "NotificationScreen"),
        autodownload(R.string.pref_automatic_download_title, "AutoDownloadScreen"),
        about(R.string.about_pref, "AboutScreen"),
        license(R.string.licenses, "LicensesScreen"),
        swipe(R.string.swipeactions_label, "SwipeScreen");
    }
}
