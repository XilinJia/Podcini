package ac.mdiq.podcini.fragment.preferences

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ac.mdiq.podcini.R
import ac.mdiq.podcini.activity.PreferenceActivity
import ac.mdiq.podcini.core.sync.SynchronizationSettings

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
        findPreference<Preference>(PREF_GPODNET_NOTIFICATIONS)!!.isEnabled =
            SynchronizationSettings.isProviderConnected
    }

    companion object {
        private const val PREF_GPODNET_NOTIFICATIONS = "pref_gpodnet_notifications"
    }
}
