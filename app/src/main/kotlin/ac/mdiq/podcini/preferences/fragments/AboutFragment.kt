package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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

class AboutFragment : PreferenceFragmentCompat() {
    @SuppressLint("CommitTransaction")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_about)

        findPreference<Preference>("about_version")!!.summary = String.format("%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.COMMIT_HASH)
        findPreference<Preference>("about_version")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(getString(R.string.bug_report_title), findPreference<Preference>("about_version")!!.summary)
                clipboard.setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT <= 32) Snackbar.make(requireView(), R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show()
                true
            }
        findPreference<Preference>("about_help")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini/")
                true
            }
        findPreference<Preference>("about_privacy_policy")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini/blob/main/PrivacyPolicy.md")
                true
            }
        findPreference<Preference>("about_licenses")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                parentFragmentManager.beginTransaction().replace(R.id.settingsContainer, LicensesFragment()).addToBackStack(getString(R.string.translators)).commit()
                true
            }
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.about_pref)
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
