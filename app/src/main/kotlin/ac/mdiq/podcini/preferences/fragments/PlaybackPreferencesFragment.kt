package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.prefPlaybackSpeed
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UsageStatistics.doNotAskAgain
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.fallbackSpeed
import ac.mdiq.podcini.preferences.UserPreferences.setVideoMode
import ac.mdiq.podcini.preferences.UserPreferences.speedforwardSpeed
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.PlaybackSpeedDialog
import ac.mdiq.podcini.ui.dialog.SkipPreferenceDialog
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.collection.ArrayMap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.round

class PlaybackPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_playback)
        setupPlaybackScreen()
//        buildSmartMarkAsPlayedPreference()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar?.setTitle(R.string.playback_pref)
    }

    private fun setupPlaybackScreen() {
        findPreference<Preference>(Prefs.prefPlaybackSpeedLauncher.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val composeView = ComposeView(requireContext()).apply {
                setContent {
                    val showDialog = remember { mutableStateOf(true) }
                    CustomTheme(requireContext()) {
                        PlaybackSpeedDialog(listOf(), initSpeed = prefPlaybackSpeed, maxSpeed = 3f, isGlobal = true, onDismiss = {
                            showDialog.value = false
                            (view as? ViewGroup)?.removeView(this@apply)
                        }) { speed -> UserPreferences.setPlaybackSpeed(speed) }
                    }
                }
            }
            (view as? ViewGroup)?.addView(composeView)
            true
        }
        findPreference<Preference>(Prefs.prefPlaybackRewindDeltaLauncher.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_REWIND, null)
            true
        }
        findPreference<Preference>(Prefs.prefPlaybackVideoModeLauncher.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            VideoModeDialog.showDialog(requireContext())
            true
        }

        findPreference<Preference>(Prefs.prefPlaybackSpeedForwardLauncher.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val composeView = ComposeView(requireContext()).apply {
                setContent {
                    val showDialog = remember { mutableStateOf(true) }
                    CustomTheme(requireContext()) {
                        PlaybackSpeedDialog(listOf(), initSpeed = speedforwardSpeed, maxSpeed = 10f, isGlobal = true, onDismiss = {
                            showDialog.value = false
                            (view as? ViewGroup)?.removeView(this@apply)
                        }) { speed ->
                            val speed_ = when {
                                speed < 0.0f -> 0.0f
                                speed > 10.0f -> 10.0f
                                else -> 0f
                            }
                            speedforwardSpeed = round(10 * speed_) / 10
                        }
                    }
                }
            }
            (view as? ViewGroup)?.addView(composeView)
            true
        }
        findPreference<Preference>(Prefs.prefPlaybackFallbackSpeedLauncher.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val composeView = ComposeView(requireContext()).apply {
                setContent {
                    val showDialog = remember { mutableStateOf(true) }
                    CustomTheme(requireContext()) {
                        PlaybackSpeedDialog(listOf(), initSpeed = fallbackSpeed, maxSpeed = 3f, isGlobal = true, onDismiss = {
                            showDialog.value = false
                            (view as? ViewGroup)?.removeView(this@apply)
                        }) { speed ->
                            val speed_ = when {
                                speed < 0.0f -> 0.0f
                                speed > 3.0f -> 3.0f
                                else -> 0f
                            }
                            fallbackSpeed = round(100 * speed_) / 100f
                        }
                    }
                }
            }
            (view as? ViewGroup)?.addView(composeView)
            true
        }
        findPreference<Preference>(Prefs.prefPlaybackFastForwardDeltaLauncher.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, null)
            true
        }
        findPreference<Preference>(Prefs.prefStreamOverDownload.name)?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, _: Any? ->
            // Update all visible lists to reflect new streaming action button
//            TODO: need another event type?
            EventFlow.postEvent(FlowEvent.EpisodePlayedEvent())
            // User consciously decided whether to prefer the streaming button, disable suggestion to change that
            doNotAskAgain(UsageStatistics.ACTION_STREAM)
            true
        }
        if (Build.VERSION.SDK_INT >= 31) {
            findPreference<Preference>(UserPreferences.Prefs.prefUnpauseOnHeadsetReconnect.name)?.isVisible = false
            findPreference<Preference>(UserPreferences.Prefs.prefUnpauseOnBluetoothReconnect.name)?.isVisible = false
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

        val pref = requirePreference<ListPreference>(UserPreferences.Prefs.prefEnqueueLocation.name)
        pref.summary = res.getString(R.string.pref_enqueue_location_sum, options[pref.value])
        pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
            if (newValue !is String) return@OnPreferenceChangeListener false
            pref.summary = res.getString(R.string.pref_enqueue_location_sum, options[newValue])
            true
        }
    }

    private fun <T : Preference?> requirePreference(key: CharSequence): T {
        // Possibly put it to a common method in abstract base class
        return  findPreference<T>(key) ?: throw IllegalArgumentException("Preference with key '$key' is not found")
    }

    object VideoModeDialog {
        fun showDialog(context: Context) {
            val dialog = MaterialAlertDialogBuilder(context)
            dialog.setTitle(context.getString(R.string.pref_playback_video_mode))
            dialog.setNegativeButton(android.R.string.cancel) { d: DialogInterface, _: Int -> d.dismiss() }
            val selected = videoPlayMode
            val entryValues = listOf(*context.resources.getStringArray(R.array.video_mode_options_values))
            val selectedIndex =  entryValues.indexOf("" + selected)
            val items = context.resources.getStringArray(R.array.video_mode_options)
            dialog.setSingleChoiceItems(items, selectedIndex) { d: DialogInterface, which: Int ->
                if (selectedIndex != which) setVideoMode(entryValues[which].toInt())
                d.dismiss()
            }
            dialog.show()
        }
    }

    private enum class Prefs {
        prefPlaybackSpeedLauncher,
        prefPlaybackRewindDeltaLauncher,
        prefPlaybackFallbackSpeedLauncher,
        prefPlaybackSpeedForwardLauncher,
        prefPlaybackFastForwardDeltaLauncher,
        prefStreamOverDownload,
        prefPlaybackVideoModeLauncher,
    }
}
