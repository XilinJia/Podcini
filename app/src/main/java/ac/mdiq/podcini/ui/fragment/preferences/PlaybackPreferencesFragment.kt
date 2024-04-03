package ac.mdiq.podcini.ui.fragment.preferences

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UsageStatistics.doNotAskAgain
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.dialog.EditFallbackSpeedDialog
import ac.mdiq.podcini.ui.dialog.EditForwardSpeedDialog
import ac.mdiq.podcini.ui.dialog.SkipPreferenceDialog
import ac.mdiq.podcini.ui.dialog.VariableSpeedDialog
import ac.mdiq.podcini.util.event.UnreadItemsUpdateEvent
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.collection.ArrayMap
import androidx.media3.common.util.UnstableApi
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.greenrobot.eventbus.EventBus

class PlaybackPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_playback)

        setupPlaybackScreen()
        buildSmartMarkAsPlayedPreference()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.playback_pref)
    }

    @OptIn(UnstableApi::class) private fun setupPlaybackScreen() {
        val activity: Activity? = activity

        findPreference<Preference>(PREF_PLAYBACK_SPEED_LAUNCHER)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                VariableSpeedDialog.newInstance("Global").show(childFragmentManager, null)
                true
            }
        findPreference<Preference>(PREF_PLAYBACK_REWIND_DELTA_LAUNCHER)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_REWIND, null)
                true
            }
        findPreference<Preference>(PREF_PLAYBACK_SPEED_FORWARD_LAUNCHER)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                EditForwardSpeedDialog(requireActivity()).show()
                true
            }
        findPreference<Preference>(PREF_PLAYBACK_FALLBACK_SPEED_LAUNCHER)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                EditFallbackSpeedDialog(requireActivity()).show()
                true
            }
        findPreference<Preference>(PREF_PLAYBACK_FAST_FORWARD_DELTA_LAUNCHER)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, null)
                true
            }
        findPreference<Preference>(PREF_PLAYBACK_PREFER_STREAMING)!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, _: Any? ->
                // Update all visible lists to reflect new streaming action button
                EventBus.getDefault().post(UnreadItemsUpdateEvent())
                // User consciously decided whether to prefer the streaming button, disable suggestion to change that
                doNotAskAgain(UsageStatistics.ACTION_STREAM)
                true
            }
        if (Build.VERSION.SDK_INT >= 31) {
            findPreference<Preference>(UserPreferences.PREF_UNPAUSE_ON_HEADSET_RECONNECT)!!.isVisible = false
            findPreference<Preference>(UserPreferences.PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT)!!.isVisible = false
        }

        buildEnqueueLocationPreference()
    }

    private fun buildEnqueueLocationPreference() {
        val res = requireActivity().resources
        val options: MutableMap<String, String> = ArrayMap()
        run {
            val keys = res.getStringArray(R.array.enqueue_location_values)
            val values = res.getStringArray(R.array.enqueue_location_options)
            for (i in keys.indices) {
                options[keys[i]] = values[i]
            }
        }

        val pref = requirePreference<ListPreference>(UserPreferences.PREF_ENQUEUE_LOCATION)
        pref.summary = res.getString(R.string.pref_enqueue_location_sum, options[pref.value])

        pref.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                if (newValue !is String) {
                    return@OnPreferenceChangeListener false
                }
                pref.summary = res.getString(R.string.pref_enqueue_location_sum, options[newValue])
                true
            }
    }

    private fun <T : Preference?> requirePreference(key: CharSequence): T {
        // Possibly put it to a common method in abstract base class
        val result = findPreference<T>(key)
            ?: throw IllegalArgumentException("Preference with key '$key' is not found")
        return result
    }

    private fun buildSmartMarkAsPlayedPreference() {
        val res = requireActivity().resources

        val pref = findPreference<ListPreference>(UserPreferences.PREF_SMART_MARK_AS_PLAYED_SECS)
        val values = res.getStringArray(R.array.smart_mark_as_played_values)
        val entries = arrayOfNulls<String>(values.size)
        for (x in values.indices) {
            if (x == 0) {
                entries[x] = res.getString(R.string.pref_smart_mark_as_played_disabled)
            } else {
                var v = values[x].toInt()
                if (v < 60) {
                    entries[x] = res.getQuantityString(R.plurals.time_seconds_quantified, v, v)
                } else {
                    v /= 60
                    entries[x] = res.getQuantityString(R.plurals.time_minutes_quantified, v, v)
                }
            }
        }
        pref!!.entries = entries
    }

    companion object {
        private const val PREF_PLAYBACK_SPEED_LAUNCHER = "prefPlaybackSpeedLauncher"
        private const val PREF_PLAYBACK_REWIND_DELTA_LAUNCHER = "prefPlaybackRewindDeltaLauncher"
        private const val PREF_PLAYBACK_FALLBACK_SPEED_LAUNCHER = "prefPlaybackFallbackSpeedLauncher"
        private const val PREF_PLAYBACK_SPEED_FORWARD_LAUNCHER = "prefPlaybackSpeedForwardLauncher"
        private const val PREF_PLAYBACK_FAST_FORWARD_DELTA_LAUNCHER = "prefPlaybackFastForwardDeltaLauncher"
        private const val PREF_PLAYBACK_PREFER_STREAMING = "prefStreamOverDownload"
    }
}
