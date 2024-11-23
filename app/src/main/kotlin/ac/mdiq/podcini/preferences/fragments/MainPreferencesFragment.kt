package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.BugReportActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity.Companion.getTitleOfPage
import ac.mdiq.podcini.ui.activity.PreferenceActivity.Screens
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.bytehamster.lib.preferencesearch.SearchPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.xml.parsers.DocumentBuilderFactory

class MainPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Logd("MainPreferencesFragment", "onCreatePreferences")

//  TODO: this can be expensive
        addPreferencesFromResource(R.xml.preferences)
        setupMainScreen()
        setupSearch()

        // If you are writing a spin-off, please update the details on screens like "About" and "Report bug"
        // and afterwards remove the following lines. Please keep in mind that Podcini is licensed under the GPL.
        // This means that your application needs to be open-source under the GPL, too.
        // It must also include a prominent copyright notice.
        val packageHash = requireContext().packageName.hashCode()
//        Logd("MainPreferencesFragment", "$packageHash ${"ac.mdiq.podcini.R".hashCode()}")
        when {
            packageHash != 1329568231 && packageHash != 1297601420 -> {
                findPreference<Preference>(Prefs.project.name)!!.isVisible = false
                val copyrightNotice = Preference(requireContext())
                copyrightNotice.setIcon(R.drawable.ic_info_white)
                copyrightNotice.icon!!.mutate().colorFilter = PorterDuffColorFilter(-0x340000, PorterDuff.Mode.MULTIPLY)
                copyrightNotice.summary = ("This application is based on Podcini."
                        + " The Podcini team does NOT provide support for this unofficial version."
                        + " If you can read this message, the developers of this modification"
                        + " violate the GNU General Public License (GPL).")
                findPreference<Preference>(Prefs.project.name)!!.parent!!.addPreference(copyrightNotice)
            }
            packageHash == 1297601420 -> {
                val debugNotice = Preference(requireContext())
                debugNotice.setIcon(R.drawable.ic_info_white)
                debugNotice.icon!!.mutate().colorFilter = PorterDuffColorFilter(-0x340000, PorterDuff.Mode.MULTIPLY)
                debugNotice.order = -1
                debugNotice.summary = "This is a development version of Podcini and not meant for daily use"
                findPreference<Preference>(Prefs.project.name)!!.parent!!.addPreference(debugNotice)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.settings_label)
    }

    @SuppressLint("CommitTransaction")
    private fun setupMainScreen() {
        findPreference<Preference>(Prefs.prefScreenInterface.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            (activity as PreferenceActivity).openScreen(R.xml.preferences_user_interface)
            true
        }
        findPreference<Preference>(Prefs.prefScreenPlayback.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            (activity as PreferenceActivity).openScreen(R.xml.preferences_playback)
            true
        }
        findPreference<Preference>(Prefs.prefScreenDownloads.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            (activity as PreferenceActivity).openScreen(R.xml.preferences_downloads)
            true
        }
        findPreference<Preference>(Prefs.prefScreenSynchronization.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            (activity as PreferenceActivity).openScreen(R.xml.preferences_synchronization)
            true
        }
        findPreference<Preference>(Prefs.prefScreenImportExport.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            (activity as PreferenceActivity).openScreen(Screens.preferences_import_export)
            true
        }
        findPreference<Preference>(Prefs.notifications.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            (activity as PreferenceActivity).openScreen(R.xml.preferences_notifications)
            true
        }
        val switchPreference = findPreference<SwitchPreferenceCompat>("prefOPMLBackup")
        switchPreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue is Boolean) {
                // Restart the app
                val intent = context?.packageManager?.getLaunchIntentForPackage(requireContext().packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context?.startActivity(intent)
            }
            true
        }
        findPreference<Preference>(Prefs.prefAbout.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            parentFragmentManager.beginTransaction().replace(R.id.settingsContainer, AboutFragment()).addToBackStack(getString(R.string.about_pref)).commit()
            true
        }
        findPreference<Preference>(Prefs.prefDocumentation.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini")
            true
        }
        findPreference<Preference>(Prefs.prefViewForum.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini/discussions")
            true
        }
        findPreference<Preference>(Prefs.prefContribute.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini")
            true
        }
        findPreference<Preference>(Prefs.prefSendBugReport.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(activity, BugReportActivity::class.java))
            true
        }
    }

    private fun setupSearch() {
        val searchPreference = findPreference<SearchPreference>("searchPreference")
        val config = searchPreference!!.searchConfiguration
        config.setActivity((activity as AppCompatActivity))
        config.setFragmentContainerViewId(R.id.settingsContainer)
        config.setBreadcrumbsEnabled(true)

        config.index(R.xml.preferences_user_interface).addBreadcrumb(getTitleOfPage(R.xml.preferences_user_interface))
        config.index(R.xml.preferences_playback).addBreadcrumb(getTitleOfPage(R.xml.preferences_playback))
        config.index(R.xml.preferences_downloads).addBreadcrumb(getTitleOfPage(R.xml.preferences_downloads))
//        config.index(R.xml.preferences_import_export).addBreadcrumb(getTitleOfPage(R.xml.preferences_import_export))
        config.index(R.xml.preferences_autodownload)
            .addBreadcrumb(getTitleOfPage(R.xml.preferences_downloads))
            .addBreadcrumb(R.string.automation)
            .addBreadcrumb(getTitleOfPage(R.xml.preferences_autodownload))
        config.index(R.xml.preferences_synchronization).addBreadcrumb(getTitleOfPage(R.xml.preferences_synchronization))
        config.index(R.xml.preferences_notifications).addBreadcrumb(getTitleOfPage(R.xml.preferences_notifications))
//        config.index(R.xml.feed_settings).addBreadcrumb(getTitleOfPage(R.xml.feed_settings))
//        config.index(R.xml.preferences_swipe)
//            .addBreadcrumb(getTitleOfPage(R.xml.preferences_user_interface))
//            .addBreadcrumb(getTitleOfPage(R.xml.preferences_swipe))
    }

    class AboutFragment : PreferenceFragmentCompat() {
        @SuppressLint("CommitTransaction")
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            (activity as PreferenceActivity).supportActionBar?.setTitle(R.string.about_pref)
            return ComposeView(requireContext()).apply {
                setContent {
                    CustomTheme(requireContext()) {
                        val textColor = MaterialTheme.colorScheme.onSurface
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Image(painter = painterResource(R.drawable.teaser), contentDescription = "")
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp)) {
                                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_star), contentDescription = "", tint = textColor)
                                Column(Modifier.padding(start = 10.dp).clickable(onClick = {
                                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(getString(R.string.bug_report_title), findPreference<Preference>("about_version")!!.summary)
                                    clipboard.setPrimaryClip(clip)
                                    if (Build.VERSION.SDK_INT <= 32) Snackbar.make(requireView(), R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show()
                                })) {
                                    Text(stringResource(R.string.podcini_version), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text(String.format("%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.COMMIT_HASH), color = textColor)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp)) {
                                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_questionmark), contentDescription = "", tint = textColor)
                                Column(Modifier.padding(start = 10.dp).clickable(onClick = {
                                    openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini/")
                                })) {
                                    Text(stringResource(R.string.online_help), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.online_help_sum), color = textColor)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp)) {
                                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_info), contentDescription = "", tint = textColor)
                                Column(Modifier.padding(start = 10.dp).clickable(onClick = {
                                    openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini/blob/main/PrivacyPolicy.md")
                                })) {
                                    Text(stringResource(R.string.privacy_policy), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text("Podcini PrivacyPolicy", color = textColor)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp)) {
                                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_info), contentDescription = "", tint = textColor)
                                Column(Modifier.padding(start = 10.dp).clickable(onClick = {
                                    parentFragmentManager.beginTransaction().replace(R.id.settingsContainer, LicensesFragment()).addToBackStack(getString(R.string.translators)).commit()
                                })) {
                                    Text(stringResource(R.string.licenses), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.licenses_summary), color = textColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        class LicensesFragment : Fragment() {
            private val licenses = mutableStateListOf<LicenseItem>()

            override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
                val composeView = ComposeView(requireContext()).apply { setContent { CustomTheme(requireContext()) { MainView() } } }
                lifecycleScope.launch(Dispatchers.IO) {
                    licenses.clear()
                    val stream = requireContext().assets.open("licenses.xml")
                    val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    val libraryList = docBuilder.parse(stream).getElementsByTagName("library")
                    for (i in 0 until libraryList.length) {
                        val lib = libraryList.item(i).attributes
                        licenses.add(LicenseItem(lib.getNamedItem("name").textContent,
                            String.format("By %s, %s license", lib.getNamedItem("author").textContent, lib.getNamedItem("license").textContent), lib.getNamedItem("website").textContent, lib.getNamedItem("licenseText").textContent))
                    }
                }.invokeOnCompletion { throwable -> if (throwable!= null) Toast.makeText(context, throwable.message, Toast.LENGTH_LONG).show() }
                return composeView
            }

            @Composable
            fun MainView() {
                val lazyListState = rememberLazyListState()
                val textColor = MaterialTheme.colorScheme.onSurface
                var showDialog by remember { mutableStateOf(false) }
                var curLicenseIndex by remember { mutableIntStateOf(-1) }
                if (showDialog) Dialog(onDismissRequest = { showDialog = false }) {
                    Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(licenses[curLicenseIndex].title, color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Row {
                                Button(onClick = { openInBrowser(requireContext(), licenses[curLicenseIndex].licenseUrl) }) { Text("View website") }
                                Spacer(Modifier.weight(1f))
                                Button(onClick = { showLicenseText(licenses[curLicenseIndex].licenseTextFile) }) { Text("View license") }
                            }
                        }
                    }
                }
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(licenses) { index, item ->
                        Column(Modifier.clickable(onClick = {
                            curLicenseIndex = index
                            showDialog = true
                        })) {
                            Text(item.title, color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(item.subtitle, color = textColor, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            private fun showLicenseText(licenseTextFile: String) {
                try {
                    val reader = BufferedReader(InputStreamReader(requireContext().assets.open(licenseTextFile), "UTF-8"))
                    val licenseText = StringBuilder()
                    var line = ""
                    while ((reader.readLine()?.also { line = it }) != null) licenseText.append(line).append("\n")
                    MaterialAlertDialogBuilder(requireContext()).setMessage(licenseText).show()
                } catch (e: IOException) { e.printStackTrace() }
            }

            override fun onStart() {
                super.onStart()
                (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.licenses)
            }

            private class LicenseItem(val title: String, val subtitle: String, val licenseUrl: String, val licenseTextFile: String)
        }
    }

    @Suppress("EnumEntryName")
    private enum class Prefs {
        prefScreenInterface,
        prefScreenPlayback,
        prefScreenDownloads,
        prefScreenImportExport,
        prefScreenSynchronization,
        prefDocumentation,
        prefViewForum,
        prefSendBugReport,
        project,
        prefAbout,
        notifications,
        prefContribute,
    }
}
