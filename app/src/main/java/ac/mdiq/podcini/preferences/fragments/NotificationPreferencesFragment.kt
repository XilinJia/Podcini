package ac.mdiq.podcini.preferences.fragments

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.net.sync.SynchronizationSettings

class NotificationPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_notifications)
        setUpScreen()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.notification_pref_fragment)
    }

    private fun setUpScreen() {
        findPreference<Preference>(PREF_GPODNET_NOTIFICATIONS)!!.isEnabled = SynchronizationSettings.isProviderConnected
    }

    companion object {
        private const val PREF_GPODNET_NOTIFICATIONS = "pref_gpodnet_notifications"
    }
}
