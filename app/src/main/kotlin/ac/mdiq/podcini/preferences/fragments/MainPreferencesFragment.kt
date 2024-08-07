package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.BugReportActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity.Companion.getTitleOfPage
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.bytehamster.lib.preferencesearch.SearchPreference

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
            (activity as PreferenceActivity).openScreen(R.xml.preferences_import_export)
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
            parentFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, AboutFragment())
                .addToBackStack(getString(R.string.about_pref))
                .commit()
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
        config.index(R.xml.preferences_import_export).addBreadcrumb(getTitleOfPage(R.xml.preferences_import_export))
        config.index(R.xml.preferences_autodownload)
            .addBreadcrumb(getTitleOfPage(R.xml.preferences_downloads))
            .addBreadcrumb(R.string.automation)
            .addBreadcrumb(getTitleOfPage(R.xml.preferences_autodownload))
        config.index(R.xml.preferences_synchronization).addBreadcrumb(getTitleOfPage(R.xml.preferences_synchronization))
        config.index(R.xml.preferences_notifications).addBreadcrumb(getTitleOfPage(R.xml.preferences_notifications))
//        config.index(R.xml.feed_settings).addBreadcrumb(getTitleOfPage(R.xml.feed_settings))
        config.index(R.xml.preferences_swipe)
            .addBreadcrumb(getTitleOfPage(R.xml.preferences_user_interface))
            .addBreadcrumb(getTitleOfPage(R.xml.preferences_swipe))
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
