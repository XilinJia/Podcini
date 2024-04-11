package ac.mdiq.podcini.preferences.fragments

import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.net.download.FeedUpdateManager.restartUpdateAlarm
import ac.mdiq.podcini.ui.dialog.ChooseDataFolderDialog
import ac.mdiq.podcini.ui.dialog.ProxyDialog
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.getDataFolder
import ac.mdiq.podcini.preferences.UserPreferences.setDataFolder
import kotlin.Any
import kotlin.Int
import kotlin.String

class DownloadsPreferencesFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
    private var blockAutoDeleteLocal = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_downloads)
        setupNetworkScreen()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.downloads_pref)
        PreferenceManager.getDefaultSharedPreferences(requireContext()).registerOnSharedPreferenceChangeListener(
            this)
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager.getDefaultSharedPreferences(requireContext()).unregisterOnSharedPreferenceChangeListener(
            this)
    }

    override fun onResume() {
        super.onResume()
        setDataFolderText()
    }

    private fun setupNetworkScreen() {
        findPreference<Preference>(PREF_SCREEN_AUTODL)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                (activity as PreferenceActivity).openScreen(R.xml.preferences_autodownload)
                true
            }
        // validate and set correct value: number of downloads between 1 and 50 (inclusive)
        findPreference<Preference>(PREF_PROXY)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val dialog = ProxyDialog(requireContext())
                dialog.show()
                true
            }
        findPreference<Preference>(PREF_CHOOSE_DATA_DIR)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                ChooseDataFolderDialog.showDialog(requireContext()) { path: String? ->
                    setDataFolder(path!!)
                    setDataFolderText()
                }
                true
            }
        findPreference<Preference>(PREF_AUTO_DELETE_LOCAL)!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                if (blockAutoDeleteLocal && newValue as Boolean) {
                    showAutoDeleteEnableDialog()
                    return@OnPreferenceChangeListener false
                } else {
                    return@OnPreferenceChangeListener true
                }
            }
    }

    private fun setDataFolderText() {
        val f = getDataFolder(null)
        if (f != null) {
            findPreference<Preference>(PREF_CHOOSE_DATA_DIR)!!.summary = f.absolutePath
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (UserPreferences.PREF_UPDATE_INTERVAL == key) {
            restartUpdateAlarm(requireContext(), true)
        }
    }

    private fun showAutoDeleteEnableDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.pref_auto_local_delete_dialog_body)
            .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                blockAutoDeleteLocal = false
                (findPreference<Preference>(PREF_AUTO_DELETE_LOCAL) as TwoStatePreference?)!!.isChecked = true
                blockAutoDeleteLocal = true
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    companion object {
        private const val PREF_SCREEN_AUTODL = "prefAutoDownloadSettings"
        private const val PREF_AUTO_DELETE_LOCAL = "prefAutoDeleteLocal"
        private const val PREF_PROXY = "prefProxy"
        private const val PREF_CHOOSE_DATA_DIR = "prefChooseDataDir"
    }
}
