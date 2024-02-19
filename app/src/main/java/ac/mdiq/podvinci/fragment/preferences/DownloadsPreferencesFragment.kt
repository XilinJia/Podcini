package ac.mdiq.podvinci.fragment.preferences

import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.activity.PreferenceActivity
import ac.mdiq.podvinci.core.util.download.FeedUpdateManager.restartUpdateAlarm
import ac.mdiq.podvinci.dialog.ChooseDataFolderDialog
import ac.mdiq.podvinci.dialog.ProxyDialog
import ac.mdiq.podvinci.storage.preferences.UserPreferences
import ac.mdiq.podvinci.storage.preferences.UserPreferences.getDataFolder
import ac.mdiq.podvinci.storage.preferences.UserPreferences.setDataFolder
import java.lang.Boolean
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
            Preference.OnPreferenceClickListener { preference: Preference? ->
                (activity as PreferenceActivity).openScreen(R.xml.preferences_autodownload)
                true
            }
        // validate and set correct value: number of downloads between 1 and 50 (inclusive)
        findPreference<Preference>(PREF_PROXY)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                val dialog = ProxyDialog(requireContext())
                dialog.show()
                true
            }
        findPreference<Preference>(PREF_CHOOSE_DATA_DIR)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                ChooseDataFolderDialog.showDialog(context) { path: String? ->
                    setDataFolder(path!!)
                    setDataFolderText()
                }
                true
            }
        findPreference<Preference>(PREF_AUTO_DELETE_LOCAL)!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
                if (blockAutoDeleteLocal && newValue === Boolean.TRUE) {
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
            restartUpdateAlarm(context, true)
        }
    }

    private fun showAutoDeleteEnableDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.pref_auto_local_delete_dialog_body)
            .setPositiveButton(R.string.yes) { dialog: DialogInterface?, which: Int ->
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
