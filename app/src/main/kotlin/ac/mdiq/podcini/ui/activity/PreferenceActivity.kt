package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SettingsActivityBinding
import ac.mdiq.podcini.preferences.ThemeSwitcher.getTheme
import ac.mdiq.podcini.preferences.fragments.*
import ac.mdiq.podcini.preferences.fragments.SynchronizationPreferencesFragment
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
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

        if (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.settingsContainer.id, MainPreferencesFragment(), FRAGMENT_TAG)
                .commit()
        }
        val intent = intent
        if (intent.getBooleanExtra(OPEN_AUTO_DOWNLOAD_SETTINGS, false)) {
            openScreen(R.xml.preferences_autodownload)
        }
    }

    private fun getPreferenceScreen(screen: Int): PreferenceFragmentCompat? {
        var prefFragment: PreferenceFragmentCompat? = null

        when (screen) {
            R.xml.preferences_user_interface -> prefFragment = UserInterfacePreferencesFragment()
            R.xml.preferences_downloads -> prefFragment = DownloadsPreferencesFragment()
            R.xml.preferences_import_export -> prefFragment = ImportExportPreferencesFragment()
            R.xml.preferences_autodownload -> prefFragment = AutoDownloadPreferencesFragment()
            R.xml.preferences_synchronization -> prefFragment = SynchronizationPreferencesFragment()
            R.xml.preferences_playback -> prefFragment = PlaybackPreferencesFragment()
            R.xml.preferences_notifications -> prefFragment = NotificationPreferencesFragment()
            R.xml.preferences_swipe -> prefFragment = SwipePreferencesFragment()
        }
        return prefFragment
    }

    @SuppressLint("CommitTransaction")
    fun openScreen(screen: Int): PreferenceFragmentCompat? {
        val fragment = getPreferenceScreen(screen)
        if (screen == R.xml.preferences_notifications && Build.VERSION.SDK_INT >= 26) {
            val intent = Intent()
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } else {
            supportFragmentManager.beginTransaction()
                .replace(binding.settingsContainer.id, fragment!!)
                .addToBackStack(getString(getTitleOfPage(screen)))
                .commit()
        }

        return fragment
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount == 0) {
                finish()
            } else {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                var view = currentFocus
                //If no view currently has focus, create a new one, just so we can grab a window token from it
                if (view == null) {
                    view = View(this)
                }
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                supportFragmentManager.popBackStack()
            }
            return true
        }
        return false
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        when (val screen = result.resourceFile) {
//            R.xml.feed_settings -> {
//                val builder = MaterialAlertDialogBuilder(this)
//                builder.setTitle(R.string.feed_settings_label)
//                builder.setMessage(R.string.pref_feed_settings_dialog_msg)
//                builder.setPositiveButton(android.R.string.ok, null)
//                builder.show()
//            }
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
        if (event.action != null) {
            s.setAction(event.actionText) { event.action.accept(this) }
        }
        s.show()
    }

    companion object {
        private const val FRAGMENT_TAG = "tag_preferences"
        const val OPEN_AUTO_DOWNLOAD_SETTINGS: String = "OpenAutoDownloadSettings"
        @JvmStatic
        fun getTitleOfPage(preferences: Int): Int {
            when (preferences) {
                R.xml.preferences_downloads -> {
                    return R.string.downloads_pref
                }
                R.xml.preferences_autodownload -> {
                    return R.string.pref_automatic_download_title
                }
                R.xml.preferences_playback -> {
                    return R.string.playback_pref
                }
                R.xml.preferences_import_export -> {
                    return R.string.import_export_pref
                }
                R.xml.preferences_user_interface -> {
                    return R.string.user_interface_label
                }
                R.xml.preferences_synchronization -> {
                    return R.string.synchronization_pref
                }
                R.xml.preferences_notifications -> {
                    return R.string.notification_pref_fragment
                }
//                R.xml.feed_settings -> {
//                    return R.string.feed_settings_label
//                }
                R.xml.preferences_swipe -> {
                    return R.string.swipeactions_label
                }
                else -> return R.string.settings_label
            }
        }
    }
}
