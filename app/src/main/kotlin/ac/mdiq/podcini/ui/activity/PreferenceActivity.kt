package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SettingsActivityBinding
import ac.mdiq.podcini.preferences.ThemeSwitcher.getTheme
import ac.mdiq.podcini.preferences.fragments.*
import ac.mdiq.podcini.preferences.fragments.SynchronizationPreferencesFragment
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.fragment.AllEpisodesFragment
import ac.mdiq.podcini.ui.fragment.DownloadsFragment
import ac.mdiq.podcini.ui.fragment.FeedEpisodesFragment
import ac.mdiq.podcini.ui.fragment.HistoryFragment
import ac.mdiq.podcini.ui.fragment.QueuesFragment
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * PreferenceActivity for API 11+. In order to change the behavior of the preference UI, see
 * PreferenceController.
 */
class PreferenceActivity : AppCompatActivity(), SearchPreferenceResultListener {
    private var _binding: SettingsActivityBinding? = null
    private val binding get() = _binding!!

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTheme(this))
        super.onCreate(savedInstanceState)

        Logd("PreferenceActivity", "onCreate")
        val ab = supportActionBar
        ab?.setDisplayHomeAsUpEnabled(true)

        _binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) == null)
            supportFragmentManager.beginTransaction().replace(binding.settingsContainer.id, MainPreferencesFragment(), FRAGMENT_TAG).commit()

        val intent = intent
        if (intent.getBooleanExtra(OPEN_AUTO_DOWNLOAD_SETTINGS, false)) openScreen(R.xml.preferences_autodownload)
    }

    @SuppressLint("CommitTransaction")
    fun openScreen(screen: Int): PreferenceFragmentCompat {
        val fragment = when (screen) {
            R.xml.preferences_user_interface -> UserInterfacePreferencesFragment()
//            R.xml.preferences_downloads -> DownloadsPreferencesFragment()
//            R.xml.preferences_import_export -> ImportExportPreferencesFragment()
            R.xml.preferences_autodownload -> AutoDownloadPreferencesFragment()
            R.xml.preferences_synchronization -> SynchronizationPreferencesFragment()
            R.xml.preferences_playback -> PlaybackPreferencesFragment()
            R.xml.preferences_notifications -> NotificationPreferencesFragment()
//            R.xml.preferences_swipe -> SwipePreferencesFragment()
            else -> UserInterfacePreferencesFragment()
        }

        if (screen == R.xml.preferences_notifications && Build.VERSION.SDK_INT >= 26) {
            val intent = Intent()
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } else
            supportFragmentManager.beginTransaction().replace(binding.settingsContainer.id, fragment).addToBackStack(getString(getTitleOfPage(screen))).commit()
        return fragment
    }

    fun openScreen(screen: Screens): PreferenceFragmentCompat {
        val fragment = when (screen) {
            Screens.preferences_user_interface -> UserInterfacePreferencesFragment()
            Screens.preferences_downloads -> DownloadsPreferencesFragment()
            Screens.preferences_import_export -> ImportExportPreferencesFragment()
            Screens.preferences_autodownload -> AutoDownloadPreferencesFragment()
            Screens.preferences_synchronization -> SynchronizationPreferencesFragment()
            Screens.preferences_playback -> PlaybackPreferencesFragment()
            Screens.preferences_notifications -> NotificationPreferencesFragment()
            Screens.preferences_swipe -> SwipePreferencesFragment()
        }
        supportFragmentManager.beginTransaction().replace(binding.settingsContainer.id, fragment).addToBackStack(getString(screen.titleRes)).commit()
        return fragment
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount == 0) finish()
            else {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                var view = currentFocus
                //If no view currently has focus, create a new one, just so we can grab a window token from it
                if (view == null) view = View(this)
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                supportFragmentManager.popBackStack()
            }
            return true
        }
        return false
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        when (val screen = result.resourceFile) {
            R.xml.preferences_notifications -> openScreen(screen)
            else -> {
                val fragment = openScreen(result.resourceFile)
                result.highlight(fragment)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd("PreferenceActivity", "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.MessageEvent -> onEventMainThread(event)
                    else -> {}
                }
            }
        }
    }

    fun onEventMainThread(event: FlowEvent.MessageEvent) {
//        Logd(FRAGMENT_TAG, "onEvent($event)")
        val s = Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
        if (event.action != null) s.setAction(event.actionText) { event.action.accept(this) }
        s.show()
    }

    class SwipePreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            (activity as PreferenceActivity).supportActionBar?.setTitle(R.string.swipeactions_label)
            return ComposeView(requireContext()).apply {
                setContent {
                    CustomTheme(requireContext()) {
                        val textColor = MaterialTheme.colorScheme.onSurface
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            for (e in Prefs.entries) {
                                val showDialog = remember { mutableStateOf(false) }
                                if (showDialog.value) SwipeActionsDialog(e.tag, onDismissRequest = { showDialog.value = false }) {}
                                Text(stringResource(e.res), color = textColor, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 10.dp).clickable(onClick = {
                                    showDialog.value = true
                                }))
                            }
                        }
                    }
                }
            }
        }
        @Suppress("EnumEntryName")
        private enum class Prefs(val res: Int, val tag: String) {
            prefSwipeQueue(R.string.queue_label, QueuesFragment.TAG),
            prefSwipeEpisodes(R.string.episodes_label, AllEpisodesFragment.TAG),
            prefSwipeDownloads(R.string.downloads_label, DownloadsFragment.TAG),
            prefSwipeFeed(R.string.individual_subscription, FeedEpisodesFragment.TAG),
            prefSwipeHistory(R.string.playback_history_label, HistoryFragment.TAG)
        }
    }

    @Suppress("EnumEntryName")
    enum class Screens(val titleRes: Int) {
        preferences_swipe(R.string.swipeactions_label),
        preferences_downloads(R.string.downloads_pref),
        preferences_autodownload(R.string.pref_automatic_download_title),
        preferences_playback(R.string.playback_pref),
        preferences_import_export(R.string.import_export_pref),
        preferences_user_interface(R.string.user_interface_label),
        preferences_synchronization(R.string.synchronization_pref),
        preferences_notifications(R.string.notification_pref_fragment);
    }

    companion object {
        private const val FRAGMENT_TAG = "tag_preferences"
        const val OPEN_AUTO_DOWNLOAD_SETTINGS: String = "OpenAutoDownloadSettings"
        @JvmStatic
        fun getTitleOfPage(preferences: Int): Int {
            return when (preferences) {
//                R.xml.preferences_downloads -> R.string.downloads_pref
                R.xml.preferences_autodownload -> R.string.pref_automatic_download_title
                R.xml.preferences_playback -> R.string.playback_pref
//                R.xml.preferences_import_export -> R.string.import_export_pref
                R.xml.preferences_user_interface -> R.string.user_interface_label
                R.xml.preferences_synchronization -> R.string.synchronization_pref
                R.xml.preferences_notifications -> R.string.notification_pref_fragment
//                R.xml.preferences_swipe -> R.string.swipeactions_label
                else -> R.string.settings_label
            }
        }
    }
}
