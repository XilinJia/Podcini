package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.ui.activity.BugReportActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity.Screens
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.xml.parsers.DocumentBuilderFactory

class MainPreferencesFragment : PreferenceFragmentCompat() {

    var copyrightNoticeText by mutableStateOf("")

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.settings_label)
        val packageHash = requireContext().packageName.hashCode()
        when {
            packageHash != 1329568231 && packageHash != 1297601420 -> {
                copyrightNoticeText = ("This application is based on Podcini."
                        + " The Podcini team does NOT provide support for this unofficial version."
                        + " If you can read this message, the developers of this modification"
                        + " violate the GNU General Public License (GPL).")
            }
            packageHash == 1297601420 -> copyrightNoticeText = "This is a development version of Podcini and not meant for daily use"
        }

        return ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    val textColor = MaterialTheme.colorScheme.onSurface
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp).verticalScroll(scrollState)) {
                        if (copyrightNoticeText.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                                Icon(imageVector = Icons.Filled.Info, contentDescription = "", tint = Color.Red, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                                Text(copyrightNoticeText, color = textColor)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_appearance), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                (activity as PreferenceActivity).openScreen(R.xml.preferences_user_interface)
                            })) {
                                Text(stringResource(R.string.user_interface_label), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.user_interface_sum), color = textColor)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_play_24dp), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                (activity as PreferenceActivity).openScreen(R.xml.preferences_playback)
                            })) {
                                Text(stringResource(R.string.playback_pref), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.playback_pref_sum), color = textColor)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_download), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                (activity as PreferenceActivity).openScreen(Screens.preferences_downloads)
                            })) {
                                Text(stringResource(R.string.downloads_pref), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.downloads_pref_sum), color = textColor)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_cloud), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                (activity as PreferenceActivity).openScreen(R.xml.preferences_synchronization)
                            })) {
                                Text(stringResource(R.string.synchronization_pref), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.synchronization_sum), color = textColor)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_storage), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                (activity as PreferenceActivity).openScreen(Screens.preferences_import_export)
                            })) {
                                Text(stringResource(R.string.import_export_pref), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.import_export_summary), color = textColor)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_notifications), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                (activity as PreferenceActivity).openScreen(R.xml.preferences_notifications)
                            })) {
                                Text(stringResource(R.string.notification_pref_fragment), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.pref_backup_on_google_title), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.pref_backup_on_google_sum), color = textColor)
                            }
                            Switch(checked = true, onCheckedChange = {
                                appPrefs.edit().putBoolean(UserPreferences.Prefs.prefOPMLBackup.name, it).apply()
                                // Restart the app
                                val intent = context?.packageManager?.getLaunchIntentForPackage(requireContext().packageName)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context?.startActivity(intent)
                            })
                        }
                        HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp).padding(top = 10.dp))
                        Text(stringResource(R.string.project_pref), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_questionmark), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini")
                            })) {
                                Text(stringResource(R.string.documentation_support), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_chat), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini/discussions")
                            })) {
                                Text(stringResource(R.string.visit_user_forum), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_contribute), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini")
                            })) {
                                Text(stringResource(R.string.pref_contribute), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_bug), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                startActivity(Intent(activity, BugReportActivity::class.java))
                            })) {
                                Text(stringResource(R.string.bug_report_title), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_info), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Column(modifier = Modifier.weight(1f).clickable(onClick = {
                                parentFragmentManager.beginTransaction().replace(R.id.settingsContainer, AboutFragment()).addToBackStack(getString(R.string.about_pref)).commit()
                            })) {
                                Text(stringResource(R.string.about_pref), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
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
                        Column(modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp)) {
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
}
