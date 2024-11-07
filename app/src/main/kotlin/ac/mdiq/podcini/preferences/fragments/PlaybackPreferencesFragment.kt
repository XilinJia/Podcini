package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UsageStatistics.doNotAskAgain
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.fallbackSpeed
import ac.mdiq.podcini.preferences.UserPreferences.setVideoMode
import ac.mdiq.podcini.preferences.UserPreferences.speedforwardSpeed
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.dialog.SkipPreferenceDialog
import ac.mdiq.podcini.ui.dialog.VariableSpeedDialog
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.view.LayoutInflater
import androidx.collection.ArrayMap
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference
import kotlin.math.round

class PlaybackPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_playback)

        setupPlaybackScreen()
//        buildSmartMarkAsPlayedPreference()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.playback_pref)
    }

    
    private fun setupPlaybackScreen() {
        findPreference<Preference>(Prefs.prefPlaybackSpeedLauncher.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            VariableSpeedDialog.newInstance(booleanArrayOf(false, false, true),2)?.show(childFragmentManager, null)
            true
        }
        findPreference<Preference>(Prefs.prefPlaybackRewindDeltaLauncher.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_REWIND, null)
            true
        }
        findPreference<Preference>(Prefs.prefPlaybackVideoModeLauncher.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            VideoModeDialog.showDialog(requireContext())
            true
        }

        findPreference<Preference>(Prefs.prefPlaybackSpeedForwardLauncher.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            EditForwardSpeedDialog(requireActivity()).show()
            true
        }
        findPreference<Preference>(Prefs.prefPlaybackFallbackSpeedLauncher.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            EditFallbackSpeedDialog(requireActivity()).show()
            true
        }
        findPreference<Preference>(Prefs.prefPlaybackFastForwardDeltaLauncher.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, null)
            true
        }
        findPreference<Preference>(Prefs.prefStreamOverDownload.name)!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, _: Any? ->
            // Update all visible lists to reflect new streaming action button
//            TODO: need another event type?
            EventFlow.postEvent(FlowEvent.EpisodePlayedEvent())
            // User consciously decided whether to prefer the streaming button, disable suggestion to change that
            doNotAskAgain(UsageStatistics.ACTION_STREAM)
            true
        }
        if (Build.VERSION.SDK_INT >= 31) {
            findPreference<Preference>(UserPreferences.Prefs.prefUnpauseOnHeadsetReconnect.name)!!.isVisible = false
            findPreference<Preference>(UserPreferences.Prefs.prefUnpauseOnBluetoothReconnect.name)!!.isVisible = false
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
    
    class EditFallbackSpeedDialog(activity: Activity) {
        val TAG = this::class.simpleName ?: "Anonymous"
        private val activityRef = WeakReference(activity)

        fun show() {
            val activity = activityRef.get() ?: return
            val binding = EditTextDialogBinding.inflate(LayoutInflater.from(activity))
            binding.editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            binding.editText.text = Editable.Factory.getInstance().newEditable(fallbackSpeed.toString())
            MaterialAlertDialogBuilder(activity)
                .setView(binding.root)
                .setTitle(R.string.edit_fallback_speed)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                    var speed = binding.editText.text.toString().toFloatOrNull() ?: 0.0f
                    when {
                        speed < 0.0f -> speed = 0.0f
                        speed > 3.0f -> speed = 3.0f
                    }
                    fallbackSpeed = round(100 * speed) / 100
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }
    }

    
    class EditForwardSpeedDialog(activity: Activity) {
        val TAG = this::class.simpleName ?: "Anonymous"
        private val activityRef = WeakReference(activity)

        fun show() {
            val activity = activityRef.get() ?: return
            val binding = EditTextDialogBinding.inflate(LayoutInflater.from(activity))
            binding.editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            binding.editText.text = Editable.Factory.getInstance().newEditable(speedforwardSpeed.toString())
            MaterialAlertDialogBuilder(activity)
                .setView(binding.root)
                .setTitle(R.string.edit_fast_forward_speed)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                    var speed = binding.editText.text.toString().toFloatOrNull() ?: 0.0f
                    when {
                        speed < 0.0f -> speed = 0.0f
                        speed > 10.0f -> speed = 10.0f
                    }
                    speedforwardSpeed = round(10 * speed) / 10
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }
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
