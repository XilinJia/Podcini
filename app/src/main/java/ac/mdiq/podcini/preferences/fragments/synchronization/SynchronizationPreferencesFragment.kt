package ac.mdiq.podcini.preferences.fragments.synchronization

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AlertdialogSyncProviderChooserBinding
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.SynchronizationCredentials
import ac.mdiq.podcini.net.sync.SynchronizationProviderViewData
import ac.mdiq.podcini.net.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.wifiSyncEnabledKey
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.dialog.AuthenticationDialog
import ac.mdiq.podcini.ui.fragment.AudioPlayerFragment.InternalPlayerFragment
import ac.mdiq.podcini.ui.fragment.AudioPlayerFragment.InternalPlayerFragment.Companion
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Activity
import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SynchronizationPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_synchronization)
        setupScreen()
        updateScreen()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.synchronization_pref)
        updateScreen()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        
        (activity as PreferenceActivity).supportActionBar!!.subtitle = ""
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd("SynchronizationPreferencesFragment", "Received event: ${event}")
                when (event) {
                    is FlowEvent.SyncServiceEvent -> syncStatusChanged(event)
                    else -> {}
                }
            }
        }
    }

    fun syncStatusChanged(event: FlowEvent.SyncServiceEvent) {
        if (!isProviderConnected && !wifiSyncEnabledKey) return

        updateScreen()
        if (event.messageResId == R.string.sync_status_error || event.messageResId == R.string.sync_status_success)
            updateLastSyncReport(SynchronizationSettings.isLastSyncSuccessful, SynchronizationSettings.lastSyncAttempt)
        else (activity as PreferenceActivity).supportActionBar!!.setSubtitle(event.messageResId)
    }

    private fun setupScreen() {
        val activity: Activity? = activity
        findPreference<Preference>(PREFERENCE_GPODNET_SETLOGIN_INFORMATION)?.setOnPreferenceClickListener {
            val dialog: AuthenticationDialog = object : AuthenticationDialog(requireContext(), R.string.pref_gpodnet_setlogin_information_title,
                false, SynchronizationCredentials.username, null) {
                override fun onConfirmed(username: String, password: String) {
                    SynchronizationCredentials.password = password
                }
            }
            dialog.show()
            true
        }
        findPreference<Preference>(PREFERENCE_SYNC)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SyncService.syncImmediately(requireActivity().applicationContext)
            true
        }
        findPreference<Preference>(PREFERENCE_FORCE_FULL_SYNC)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SyncService.fullSync(requireContext())
            true
        }
        findPreference<Preference>(PREFERENCE_LOGOUT)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SynchronizationCredentials.clear(requireContext())
            Snackbar.make(requireView(), R.string.pref_synchronization_logout_toast, Snackbar.LENGTH_LONG).show()
            SynchronizationSettings.setSelectedSyncProvider(null)
            updateScreen()
            true
        }
    }

    private fun updateScreen() {
        val preferenceInstantSync = findPreference<Preference>(PREFERENCE_INSTANT_SYNC)
        preferenceInstantSync!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            WifiAuthenticationFragment().show(childFragmentManager, WifiAuthenticationFragment.TAG)
            true
        }

        val loggedIn = isProviderConnected
        val preferenceHeader = findPreference<Preference>(PREFERENCE_SYNCHRONIZATION_DESCRIPTION)
        if (loggedIn) {
            val selectedProvider = SynchronizationProviderViewData.fromIdentifier(selectedSyncProviderKey)
            preferenceHeader!!.title = ""
            if (selectedProvider != null) {
                preferenceHeader.setSummary(selectedProvider.summaryResource)
                preferenceHeader.setIcon(selectedProvider.iconResource)
            }
            preferenceHeader.onPreferenceClickListener = null
        } else {
            preferenceHeader!!.setTitle(R.string.synchronization_choose_title)
            preferenceHeader.setSummary(R.string.synchronization_summary_unchoosen)
            preferenceHeader.setIcon(R.drawable.ic_cloud)
            preferenceHeader.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                chooseProviderAndLogin()
                true
            }
        }

        val gpodnetSetLoginPreference = findPreference<Preference>(PREFERENCE_GPODNET_SETLOGIN_INFORMATION)
        gpodnetSetLoginPreference!!.isVisible = isProviderSelected(SynchronizationProviderViewData.GPODDER_NET)
        gpodnetSetLoginPreference.isEnabled = loggedIn
        findPreference<Preference>(PREFERENCE_SYNC)!!.isVisible = loggedIn
        findPreference<Preference>(PREFERENCE_FORCE_FULL_SYNC)!!.isVisible = loggedIn
        findPreference<Preference>(PREFERENCE_LOGOUT)!!.isVisible = loggedIn
        if (loggedIn) {
            val summary = getString(R.string.synchronization_login_status,
                SynchronizationCredentials.username, SynchronizationCredentials.hosturl)
            val formattedSummary = HtmlCompat.fromHtml(summary, HtmlCompat.FROM_HTML_MODE_LEGACY)
            findPreference<Preference>(PREFERENCE_LOGOUT)!!.summary = formattedSummary
            updateLastSyncReport(SynchronizationSettings.isLastSyncSuccessful, SynchronizationSettings.lastSyncAttempt)
        } else {
            findPreference<Preference>(PREFERENCE_LOGOUT)?.summary = ""
            (activity as PreferenceActivity).supportActionBar?.setSubtitle("")
        }
    }

    private fun chooseProviderAndLogin() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(R.string.dialog_choose_sync_service_title)

        val providers = SynchronizationProviderViewData.entries.toTypedArray()
        val adapter: ListAdapter = object : ArrayAdapter<SynchronizationProviderViewData?>(requireContext(), R.layout.alertdialog_sync_provider_chooser, providers) {
            var holder: ViewHolder? = null

            inner class ViewHolder {
                var icon: ImageView? = null
                var title: TextView? = null
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var convertView = convertView
                val inflater = LayoutInflater.from(context)
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.alertdialog_sync_provider_chooser, null)
                    val binding = AlertdialogSyncProviderChooserBinding.bind(convertView)
                    holder = ViewHolder()
                    if (holder != null) {
                        holder!!.icon = binding.icon
                        holder!!.title = binding.title
                        convertView.tag = holder
                    }
                } else holder = convertView.tag as ViewHolder

                val synchronizationProviderViewData = getItem(position)
                holder!!.title!!.setText(synchronizationProviderViewData!!.summaryResource)
                holder!!.icon!!.setImageResource(synchronizationProviderViewData.iconResource)
                return convertView!!
            }
        }

        builder.setAdapter(adapter) { _: DialogInterface?, which: Int ->
            when (providers[which]) {
                SynchronizationProviderViewData.GPODDER_NET -> GpodderAuthenticationFragment().show(childFragmentManager, GpodderAuthenticationFragment.TAG)
                SynchronizationProviderViewData.NEXTCLOUD_GPODDER -> NextcloudAuthenticationFragment().show(childFragmentManager, NextcloudAuthenticationFragment.TAG)
            }
            updateScreen()
        }

        builder.show()
    }

    private fun isProviderSelected(provider: SynchronizationProviderViewData): Boolean {
        val selectedSyncProviderKey = selectedSyncProviderKey
        return provider.identifier == selectedSyncProviderKey
    }

    private val selectedSyncProviderKey: String
        get() = SynchronizationSettings.selectedSyncProviderKey?:""

    private fun updateLastSyncReport(successful: Boolean, lastTime: Long) {
        val status = String.format("%1\$s (%2\$s)", getString(if (successful) R.string.gpodnetsync_pref_report_successful else R.string.gpodnetsync_pref_report_failed),
            DateUtils.getRelativeDateTimeString(context, lastTime, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_SHOW_TIME))
        (activity as PreferenceActivity).supportActionBar!!.subtitle = status
    }

    companion object {
        private const val PREFERENCE_INSTANT_SYNC = "preference_instant_sync"
        private const val PREFERENCE_SYNCHRONIZATION_DESCRIPTION = "preference_synchronization_description"
        private const val PREFERENCE_GPODNET_SETLOGIN_INFORMATION = "pref_gpodnet_setlogin_information"
        private const val PREFERENCE_SYNC = "pref_synchronization_sync"
        private const val PREFERENCE_FORCE_FULL_SYNC = "pref_synchronization_force_full_sync"
        private const val PREFERENCE_LOGOUT = "pref_synchronization_logout"
    }
}
