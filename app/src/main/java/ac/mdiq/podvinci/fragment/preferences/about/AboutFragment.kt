package ac.mdiq.podvinci.fragment.preferences.about

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podvinci.BuildConfig
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.activity.PreferenceActivity
import ac.mdiq.podvinci.core.util.IntentUtils.openInBrowser

class AboutFragment : PreferenceFragmentCompat() {
    @SuppressLint("CommitTransaction")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_about)

        findPreference<Preference>("about_version")!!.summary = String.format(
            "%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.COMMIT_HASH)
        findPreference<Preference>("about_version")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(getString(R.string.bug_report_title),
                    findPreference<Preference>("about_version")!!.summary)
                clipboard.setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT <= 32) {
                    Snackbar.make(requireView(), R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show()
                }
                true
            }
        findPreference<Preference>("about_contributors")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.settingsContainer, ContributorsPagerFragment())
                    .addToBackStack(getString(R.string.contributors))
                    .commit()
                true
            }
        findPreference<Preference>("about_privacy_policy")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                openInBrowser(requireContext(), "https://github.com/XilinJia/PodVinci/PrivacyPolicy.md")
                true
            }
        findPreference<Preference>("about_licenses")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.settingsContainer, LicensesFragment())
                    .addToBackStack(getString(R.string.translators))
                    .commit()
                true
            }
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity?)!!.supportActionBar!!.setTitle(R.string.about_pref)
    }
}
