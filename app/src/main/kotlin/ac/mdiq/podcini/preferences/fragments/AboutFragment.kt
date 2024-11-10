package ac.mdiq.podcini.preferences.fragments

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.PagerFragmentBinding
import ac.mdiq.podcini.databinding.SimpleIconListItemBinding
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import android.R.color
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.ListFragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
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
        findPreference<Preference>("about_contributors")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.settingsContainer, ContributorsPagerFragment())
                    .addToBackStack(getString(R.string.contributors))
                    .commit()
                true
            }
        findPreference<Preference>("about_privacy_policy")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                openInBrowser(requireContext(), "https://github.com/XilinJia/Podcini/blob/main/PrivacyPolicy.md")
                true
            }
        findPreference<Preference>("about_licenses")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.settingsContainer, LicensesFragment())
                    .addToBackStack(getString(R.string.translators))
                    .commit()
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
                withContext(Dispatchers.Main) {
                    listAdapter = ContributorsPagerFragment.SimpleIconListAdapter(requireContext(), licenses)
                }
            }.invokeOnCompletion { throwable ->
                if (throwable!= null) Toast.makeText(context, throwable.message, Toast.LENGTH_LONG).show()
            }
        }

        private class LicenseItem(title: String, subtitle: String, imageUrl: String, val licenseUrl: String, val licenseTextFile: String)
            : ContributorsPagerFragment.SimpleIconListAdapter.ListItem(title, subtitle, imageUrl)

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
                while ((reader.readLine()?.also { line = it }) != null) {
                    licenseText.append(line).append("\n")
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(licenseText)
                    .show()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        override fun onStart() {
            super.onStart()
            (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.licenses)
        }
    }

    /**
     * Displays the 'about->Contributors' pager screen.
     */
    class ContributorsPagerFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            super.onCreateView(inflater, container, savedInstanceState)
            setHasOptionsMenu(true)
            val binding = PagerFragmentBinding.inflate(inflater)
            val viewPager = binding.viewpager
            viewPager.adapter = StatisticsPagerAdapter(this)
            // Give the TabLayout the ViewPager
            val tabLayout = binding.slidingTabs
            TabLayoutMediator(tabLayout, viewPager) { tab: TabLayout.Tab, position: Int ->
                when (position) {
                    POS_DEVELOPERS -> tab.setText(R.string.developers)
                    POS_TRANSLATORS -> tab.setText(R.string.translators)
                    POS_SPECIAL_THANKS -> tab.setText(R.string.special_thanks)
                    else -> {}
                }
            }.attach()

            binding.toolbar.visibility = View.GONE

            return binding.root
        }

        override fun onStart() {
            super.onStart()
            (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.contributors)
        }

        class StatisticsPagerAdapter internal constructor(fragment: Fragment) : FragmentStateAdapter(fragment) {
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    POS_TRANSLATORS -> TranslatorsFragment()
                    POS_SPECIAL_THANKS -> SpecialThanksFragment()
                    POS_DEVELOPERS -> DevelopersFragment()
                    else -> DevelopersFragment()
                }
            }
            override fun getItemCount(): Int {
                return TOTAL_COUNT
            }
        }

        class TranslatorsFragment : ListFragment() {
            override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                super.onViewCreated(view, savedInstanceState)
                listView.divider = null
                listView.setSelector(color.transparent)

                lifecycleScope.launch(Dispatchers.IO) {
                    val translators = ArrayList<SimpleIconListAdapter.ListItem>()
                    val reader = BufferedReader(InputStreamReader(requireContext().assets.open("translators.csv"), "UTF-8"))
                    var line = ""
                    while ((reader.readLine()?.also { line = it }) != null) {
                        val info = line.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        translators.add(SimpleIconListAdapter.ListItem(info[0], info[1], ""))
                    }
                    withContext(Dispatchers.Main) {
                        listAdapter = SimpleIconListAdapter(requireContext(), translators)         }
                }.invokeOnCompletion { throwable ->
                    if (throwable != null) Toast.makeText(context, throwable.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        class SpecialThanksFragment : ListFragment() {
            override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                super.onViewCreated(view, savedInstanceState)
                listView.divider = null
                listView.setSelector(color.transparent)

                lifecycleScope.launch(Dispatchers.IO) {
                    val translators = ArrayList<SimpleIconListAdapter.ListItem>()
                    val reader = BufferedReader(InputStreamReader(requireContext().assets.open("special_thanks.csv"), "UTF-8"))
                    var line = ""
                    while ((reader.readLine()?.also { line = it }) != null) {
                        val info = line.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        translators.add(SimpleIconListAdapter.ListItem(info[0], info[1], info[2]))
                    }
                    withContext(Dispatchers.Main) {
                        listAdapter = SimpleIconListAdapter(requireContext(), translators)
                    }
                }.invokeOnCompletion { throwable ->
                    if (throwable!= null) Toast.makeText(context, throwable.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        class DevelopersFragment : ListFragment() {
            override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
                super.onViewCreated(view, savedInstanceState)
                listView.divider = null
                listView.setSelector(color.transparent)

                lifecycleScope.launch(Dispatchers.IO) {
                    val developers = ArrayList<SimpleIconListAdapter.ListItem>()
                    val reader = BufferedReader(InputStreamReader(requireContext().assets.open("developers.csv"), "UTF-8"))
                    var line = ""
                    while ((reader.readLine()?.also { line = it }) != null) {
                        val info = line.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        developers.add(SimpleIconListAdapter.ListItem(info[0], info[2], "https://avatars2.githubusercontent.com/u/" + info[1] + "?s=60&v=4"))
                    }
                    withContext(Dispatchers.Main) {
                        listAdapter = SimpleIconListAdapter(requireContext(), developers)            }
                }.invokeOnCompletion { throwable ->
                    if (throwable != null) Toast.makeText(context, throwable.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        /**
         * Displays a list of items that have a subtitle and an icon.
         */
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

        companion object {
            private const val POS_DEVELOPERS = 0
            private const val POS_TRANSLATORS = 1
            private const val POS_SPECIAL_THANKS = 2
            private const val TOTAL_COUNT = 3
        }
    }
}
