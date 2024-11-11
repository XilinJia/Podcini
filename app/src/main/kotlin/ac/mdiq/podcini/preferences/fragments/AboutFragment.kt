package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SimpleIconListItemBinding
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.ListFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    class LicensesFragment : ListFragment() {
        private val licenses = ArrayList<LicenseItem>()

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            listView.divider = null

            lifecycleScope.launch(Dispatchers.IO) {
                licenses.clear()
                val stream = requireContext().assets.open("licenses.xml")
                val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                val libraryList = docBuilder.parse(stream).getElementsByTagName("library")
                for (i in 0 until libraryList.length) {
                    val lib = libraryList.item(i).attributes
                    licenses.add(LicenseItem(lib.getNamedItem("name").textContent,
                        String.format("By %s, %s license", lib.getNamedItem("author").textContent, lib.getNamedItem("license").textContent),
                        "", lib.getNamedItem("website").textContent, lib.getNamedItem("licenseText").textContent))
                }
                withContext(Dispatchers.Main) { listAdapter = SimpleIconListAdapter(requireContext(), licenses) }
            }.invokeOnCompletion { throwable -> if (throwable!= null) Toast.makeText(context, throwable.message, Toast.LENGTH_LONG).show() }
        }

        private class LicenseItem(title: String, subtitle: String, imageUrl: String, val licenseUrl: String, val licenseTextFile: String)
            : SimpleIconListAdapter.ListItem(title, subtitle, imageUrl)

        override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
            super.onListItemClick(l, v, position, id)

            val item = licenses[position]
            val items = arrayOf<CharSequence>("View website", "View license")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.title)
                .setItems(items) { _: DialogInterface?, which: Int ->
                    when (which) {
                        0 -> openInBrowser(requireContext(), item.licenseUrl)
                        1 -> showLicenseText(item.licenseTextFile)
                    }
                }.show()
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
    }

    class SimpleIconListAdapter<T : SimpleIconListAdapter.ListItem>(private val context: Context, private val listItems: List<T>)
        : ArrayAdapter<T>(context, R.layout.simple_icon_list_item, listItems) {

        override fun getView(position: Int, view: View?, parent: ViewGroup): View {
            var view = view
            if (view == null) view = View.inflate(context, R.layout.simple_icon_list_item, null)

            val item: ListItem = listItems[position]
            val binding = SimpleIconListItemBinding.bind(view!!)
            binding.title.text = item.title
            binding.subtitle.text = item.subtitle
            binding.icon.load(item.imageUrl)
            return view
        }

        open class ListItem(val title: String, val subtitle: String, val imageUrl: String)
    }
}
