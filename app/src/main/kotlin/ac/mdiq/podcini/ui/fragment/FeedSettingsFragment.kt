package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EpisodeFilterDialogBinding
import ac.mdiq.podcini.databinding.FeedPrefSkipDialogBinding
import ac.mdiq.podcini.databinding.FeedsettingsBinding
import ac.mdiq.podcini.databinding.PlaybackSpeedFeedSettingDialogBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.storage.database.Feeds.persistFeedPreferences
import ac.mdiq.podcini.storage.database.RealmDB.unmanagedCopy
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.FeedPreferences
import ac.mdiq.podcini.storage.model.FeedPreferences.AutoDeleteAction
import ac.mdiq.podcini.storage.utils.FeedEpisodesFilter
import ac.mdiq.podcini.storage.utils.VolumeAdaptionSetting
import ac.mdiq.podcini.ui.adapter.SimpleChipAdapter
import ac.mdiq.podcini.ui.dialog.AuthenticationDialog
import ac.mdiq.podcini.ui.dialog.TagSettingsDialog
import ac.mdiq.podcini.ui.utils.ItemOffsetDecoration
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*

class FeedSettingsFragment : Fragment() {
    private var _binding: FeedsettingsBinding? = null
    private val binding get() = _binding!!
    private var feed: Feed? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FeedsettingsBinding.inflate(inflater)
        Logd(TAG, "fragment onCreateView")

        val toolbar = binding.toolbar
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        if (feed != null) {
            toolbar.subtitle = feed!!.title
            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fragment_container, FeedSettingsPreferenceFragment.newInstance(feed!!), "settings_fragment")
                .commitAllowingStateLoss()
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class FeedSettingsPreferenceFragment : PreferenceFragmentCompat() {
        private var feed: Feed? = null
        private var feedPrefs: FeedPreferences? = null

        private var notificationPermissionDenied: Boolean = false
        private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) return@registerForActivityResult

            if (notificationPermissionDenied) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.setData(uri)
                startActivity(intent)
                return@registerForActivityResult
            }
            Toast.makeText(context, R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
            notificationPermissionDenied = true
        }

        override fun onCreateRecyclerView(inflater: LayoutInflater, parent: ViewGroup, state: Bundle?): RecyclerView {
            val view = super.onCreateRecyclerView(inflater, parent, state)
            // To prevent transition animation because of summary update
            view.itemAnimator = null
            view.layoutAnimation = null
            return view
        }

        @OptIn(UnstableApi::class) override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.feed_settings)
            // To prevent displaying partially loaded data
            findPreference<Preference>(PREF_SCREEN)!!.isVisible = false

            if (feed != null) {
                if (feed!!.preferences == null) {
                    feed!!.preferences = FeedPreferences(feed!!.id, false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
                    persistFeedPreferences(feed!!)
                }
                feedPrefs = feed!!.preferences

                setupAutoDownloadGlobalPreference()
                setupAutoDownloadPreference()
                setupKeepUpdatedPreference()
                setupAutoDeletePreference()
                setupVolumeAdaptationPreferences()
                setupAuthentificationPreference()
                setupEpisodeFilterPreference()
                setupPlaybackSpeedPreference()
                setupFeedAutoSkipPreference()
                setupTags()

                updateAutoDeleteSummary()
                updateVolumeAdaptationValue()
                updateAutoDownloadEnabled()

                if (feed!!.isLocalFeed) {
                    findPreference<Preference>(PREF_AUTHENTICATION)!!.isVisible = false
                    findPreference<Preference>(PREF_CATEGORY_AUTO_DOWNLOAD)!!.isVisible = false
                }
                findPreference<Preference>(PREF_SCREEN)!!.isVisible = true
            }
        }

        private fun setupFeedAutoSkipPreference() {
            if (feedPrefs == null) return
            findPreference<Preference>(PREF_AUTO_SKIP)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                object : FeedPreferenceSkipDialog(requireContext(), feedPrefs!!.introSkip, feedPrefs!!.endingSkip) {
                    @UnstableApi override fun onConfirmed(skipIntro: Int, skipEnding: Int) {
                        feedPrefs!!.introSkip = skipIntro
                        feedPrefs!!.endingSkip = skipEnding
                        persistFeedPreferences(feed!!)
//                        EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feedPrefs!!.feedID))
//                        EventFlow.postEvent(FlowEvent.SkipIntroEndingChangedEvent(feedPrefs!!.introSkip, feedPrefs!!.endingSkip, feed!!.id))
                    }
                }.show()
                false
            }
        }

        @UnstableApi private fun setupPlaybackSpeedPreference() {
            val feedPlaybackSpeedPreference = findPreference<Preference>(PREF_FEED_PLAYBACK_SPEED)
            feedPlaybackSpeedPreference!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val binding = PlaybackSpeedFeedSettingDialogBinding.inflate(layoutInflater)
                binding.seekBar.setProgressChangedListener { speed: Float? ->
                    binding.currentSpeedLabel.text = String.format(Locale.getDefault(), "%.2fx", speed)
                }
                binding.useGlobalCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                    binding.seekBar.isEnabled = !isChecked
                    binding.seekBar.alpha = if (isChecked) 0.4f else 1f
                    binding.currentSpeedLabel.alpha = if (isChecked) 0.4f else 1f
                }
                if (feedPrefs != null) {
                    val speed = feedPrefs!!.playSpeed
                    binding.useGlobalCheckbox.isChecked = speed == FeedPreferences.SPEED_USE_GLOBAL
                    binding.seekBar.updateSpeed(if (speed == FeedPreferences.SPEED_USE_GLOBAL) 1f else speed)
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.playback_speed)
                    .setView(binding.root)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                        val newSpeed = if (binding.useGlobalCheckbox.isChecked) FeedPreferences.SPEED_USE_GLOBAL
                        else binding.seekBar.currentSpeed
                        if (feedPrefs != null) {
                            feedPrefs!!.playSpeed = newSpeed
                            persistFeedPreferences(feed!!)
//                            EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feedPrefs!!.feedID))
//                            EventFlow.postEvent(FlowEvent.FeedPrefsChangeEvent(feedPrefs!!))
                        }
                    }
                    .setNegativeButton(R.string.cancel_label, null)
                    .show()
                true
            }
        }

        private fun setupEpisodeFilterPreference() {
            if (feedPrefs == null) return
            findPreference<Preference>(PREF_EPISODE_FILTER)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                object : EpisodeFilterPrefDialog(requireContext(), feedPrefs!!.filter) {
                    @UnstableApi override fun onConfirmed(filter: FeedEpisodesFilter) {
                        if (feedPrefs != null) {
                            feedPrefs!!.filter = filter
                            persistFeedPreferences(feed!!)
//                            EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feedPrefs!!.feedID))
                        }
                    }
                }.show()
                false
            }
        }

        private fun setupAuthentificationPreference() {
            if (feedPrefs == null) return
            findPreference<Preference>(PREF_AUTHENTICATION)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                object : AuthenticationDialog(requireContext(), R.string.authentication_label, true, feedPrefs!!.username, feedPrefs!!.password) {
                    @UnstableApi override fun onConfirmed(username: String, password: String) {
                        if (feedPrefs != null) {
                            feedPrefs!!.username = username
                            feedPrefs!!.password = password
                            persistFeedPreferences(feed!!)
//                            EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feedPrefs!!.feedID))
                        }
                        Thread({ runOnce(context, feed) }, "RefreshAfterCredentialChange").start()
                    }
                }.show()
                false
            }
        }

        @UnstableApi private fun setupAutoDeletePreference() {
            if (feedPrefs == null) return
            findPreference<Preference>(PREF_AUTO_DELETE)!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                if (feedPrefs != null) {
                    when (newValue as String?) {
                        "global" -> feedPrefs!!.currentAutoDelete = AutoDeleteAction.GLOBAL
                        "always" -> feedPrefs!!.currentAutoDelete = AutoDeleteAction.ALWAYS
                        "never" -> feedPrefs!!.currentAutoDelete = AutoDeleteAction.NEVER
                        else -> {}
                    }
                    persistFeedPreferences(feed!!)
//                    EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feedPrefs!!.feedID))
                }
                updateAutoDeleteSummary()
                false
            }
        }

        private fun updateAutoDeleteSummary() {
            if (feedPrefs == null) return
            val autoDeletePreference = findPreference<ListPreference>(PREF_AUTO_DELETE)

            when (feedPrefs!!.currentAutoDelete) {
                AutoDeleteAction.GLOBAL -> {
                    autoDeletePreference!!.setSummary(R.string.global_default)
                    autoDeletePreference.value = "global"
                }
                AutoDeleteAction.ALWAYS -> {
                    autoDeletePreference!!.setSummary(R.string.feed_auto_download_always)
                    autoDeletePreference.value = "always"
                }
                AutoDeleteAction.NEVER -> {
                    autoDeletePreference!!.setSummary(R.string.feed_auto_download_never)
                    autoDeletePreference.value = "never"
                }
            }
        }

        @UnstableApi private fun setupVolumeAdaptationPreferences() {
            if (feedPrefs == null) return
            val volumeAdaptationPreference = findPreference<ListPreference>("volumeReduction")
            volumeAdaptationPreference!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                if (feedPrefs != null) {
                    when (newValue as String?) {
                        "off" -> feedPrefs!!.volumeAdaptionSetting = VolumeAdaptionSetting.OFF
                        "light" -> feedPrefs!!.volumeAdaptionSetting = VolumeAdaptionSetting.LIGHT_REDUCTION
                        "heavy" -> feedPrefs!!.volumeAdaptionSetting = VolumeAdaptionSetting.HEAVY_REDUCTION
                        "light_boost" -> feedPrefs!!.volumeAdaptionSetting = VolumeAdaptionSetting.LIGHT_BOOST
                        "medium_boost" -> feedPrefs!!.volumeAdaptionSetting = VolumeAdaptionSetting.MEDIUM_BOOST
                        "heavy_boost" -> feedPrefs!!.volumeAdaptionSetting = VolumeAdaptionSetting.HEAVY_BOOST
                        else -> {}
                    }
                    persistFeedPreferences(feed!!)
//                    EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feedPrefs!!.feedID))
                }
                updateVolumeAdaptationValue()
                if (feed != null && feedPrefs!!.volumeAdaptionSetting != null)
                    EventFlow.postEvent(FlowEvent.VolumeAdaptionChangedEvent(feedPrefs!!.volumeAdaptionSetting!!, feed!!.id))
                false
            }
        }

        private fun updateVolumeAdaptationValue() {
            if (feedPrefs == null) return

            val volumeAdaptationPreference = findPreference<ListPreference>("volumeReduction")

            when (feedPrefs!!.volumeAdaptionSetting) {
                VolumeAdaptionSetting.OFF -> volumeAdaptationPreference!!.value = "off"
                VolumeAdaptionSetting.LIGHT_REDUCTION -> volumeAdaptationPreference!!.value = "light"
                VolumeAdaptionSetting.HEAVY_REDUCTION -> volumeAdaptationPreference!!.value = "heavy"
                VolumeAdaptionSetting.LIGHT_BOOST -> volumeAdaptationPreference!!.value = "light_boost"
                VolumeAdaptionSetting.MEDIUM_BOOST -> volumeAdaptationPreference!!.value = "medium_boost"
                VolumeAdaptionSetting.HEAVY_BOOST -> volumeAdaptationPreference!!.value = "heavy_boost"
                else -> {}
            }
        }

//        @OptIn(UnstableApi::class) private fun setupNewEpisodesAction() {
//            if (feedPreferences == null) return
//
//            findPreference<Preference>(PREF_NEW_EPISODES_ACTION)!!.onPreferenceChangeListener =
//                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
//                    val code = (newValue as String).toInt()
//                    feedPreferences!!.newEpisodesAction = NewEpisodesAction.fromCode(code)
//                    DBWriter.setFeedPreferences(feedPreferences!!)
//                    updateNewEpisodesAction()
//                    false
//                }
//        }

//        private fun updateNewEpisodesAction() {
//            if (feedPreferences == null || feedPreferences!!.newEpisodesAction == null) return
//            val newEpisodesAction = findPreference<ListPreference>(PREF_NEW_EPISODES_ACTION)
//            newEpisodesAction!!.value = "" + feedPreferences!!.newEpisodesAction!!.code
//
//            when (feedPreferences!!.newEpisodesAction) {
//                NewEpisodesAction.GLOBAL -> newEpisodesAction.setSummary(R.string.global_default)
////                NewEpisodesAction.ADD_TO_INBOX -> newEpisodesAction.setSummary(R.string.feed_new_episodes_action_add_to_inbox)
//                NewEpisodesAction.NOTHING -> newEpisodesAction.setSummary(R.string.feed_new_episodes_action_nothing)
//                else -> {}
//            }
//        }

        @OptIn(UnstableApi::class) private fun setupKeepUpdatedPreference() {
            if (feedPrefs == null) return
            val pref = findPreference<SwitchPreferenceCompat>("keepUpdated")

            pref!!.isChecked = feedPrefs!!.keepUpdated
            pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val checked = newValue == true
                if (feedPrefs != null) {
                    feedPrefs!!.keepUpdated = checked
                    persistFeedPreferences(feed!!)
//                    EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feedPrefs!!.feedID))
                }
                pref.isChecked = checked
                false
            }
        }

        private fun setupAutoDownloadGlobalPreference() {
            if (!isEnableAutodownload) {
                val autodl = findPreference<SwitchPreferenceCompat>("autoDownload")
                autodl!!.isChecked = false
                autodl.isEnabled = false
                autodl.setSummary(R.string.auto_download_disabled_globally)
                findPreference<Preference>(PREF_EPISODE_FILTER)!!.isEnabled = false
            }
        }

        @OptIn(UnstableApi::class) private fun setupAutoDownloadPreference() {
            if (feedPrefs == null) return
            val pref = findPreference<SwitchPreferenceCompat>("autoDownload")

            pref!!.isEnabled = isEnableAutodownload
            if (isEnableAutodownload) {
                pref.isChecked = feedPrefs!!.autoDownload
            } else {
                pref.isChecked = false
                pref.setSummary(R.string.auto_download_disabled_globally)
            }

            pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val checked = newValue == true
                if (feedPrefs != null) {
                    feedPrefs!!.autoDownload = checked
                    persistFeedPreferences(feed!!)
//                    EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feedPrefs!!.feedID))
                }
                updateAutoDownloadEnabled()
                pref.isChecked = checked
                false
            }
        }

        private fun updateAutoDownloadEnabled() {
            if (feed != null && feed!!.preferences != null) {
                val enabled = feed!!.preferences!!.autoDownload && isEnableAutodownload
                findPreference<Preference>(PREF_EPISODE_FILTER)!!.isEnabled = enabled
            }
        }

        private fun setupTags() {
            findPreference<Preference>(PREF_TAGS)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                if (feedPrefs != null) TagSettingsDialog.newInstance(listOf(feed!!))
                    .show(childFragmentManager, TagSettingsDialog.TAG)
                true
            }
        }

        fun setFeed(feed_: Feed) {
            feed = feed_
        }

        companion object {
            private val PREF_EPISODE_FILTER: CharSequence = "episodeFilter"
            private val PREF_SCREEN: CharSequence = "feedSettingsScreen"
            private val PREF_AUTHENTICATION: CharSequence = "authentication"
            private val PREF_AUTO_DELETE: CharSequence = "autoDelete"
            private val PREF_CATEGORY_AUTO_DOWNLOAD: CharSequence = "autoDownloadCategory"
//            private val PREF_NEW_EPISODES_ACTION: CharSequence = "feedNewEpisodesAction"
            private const val PREF_FEED_PLAYBACK_SPEED = "feedPlaybackSpeed"
            private const val PREF_AUTO_SKIP = "feedAutoSkip"
            private const val PREF_TAGS = "tags"

            fun newInstance(feed: Feed): FeedSettingsPreferenceFragment {
                val fragment = FeedSettingsPreferenceFragment()
                fragment.setFeed(feed)
                return fragment
            }
        }
    }

    /**
     * Displays a dialog with a username and password text field and an optional checkbox to save username and preferences.
     */
    abstract class FeedPreferenceSkipDialog(context: Context, skipIntroInitialValue: Int, skipEndInitialValue: Int)
        : MaterialAlertDialogBuilder(context) {

        init {
            setTitle(R.string.pref_feed_skip)
            val binding = FeedPrefSkipDialogBinding.bind(View.inflate(context, R.layout.feed_pref_skip_dialog, null))
            setView(binding.root)

            val etxtSkipIntro = binding.etxtSkipIntro
            val etxtSkipEnd = binding.etxtSkipEnd

            etxtSkipIntro.setText(skipIntroInitialValue.toString())
            etxtSkipEnd.setText(skipEndInitialValue.toString())

            setNegativeButton(R.string.cancel_label, null)
            setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
                val skipIntro = try {
                    etxtSkipIntro.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    0
                }
                val skipEnding = try {
                    etxtSkipEnd.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    0
                }
                onConfirmed(skipIntro, skipEnding)
            }
        }

        protected abstract fun onConfirmed(skipIntro: Int, skipEndig: Int)
    }

    /**
     * Displays a dialog with a text box for filtering episodes and two radio buttons for exclusion/inclusion
     */
    abstract class EpisodeFilterPrefDialog(context: Context, filter: FeedEpisodesFilter) :
        MaterialAlertDialogBuilder(context) {

        private val binding = EpisodeFilterDialogBinding.inflate(LayoutInflater.from(context))
        private val termList: MutableList<String>

        init {
            setTitle(R.string.episode_filters_label)
            setView(binding.root)

            binding.durationCheckBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                binding.episodeFilterDurationText.isEnabled = isChecked
            }
            if (filter.hasMinimalDurationFilter()) {
                binding.durationCheckBox.isChecked = true
                // Store minimal duration in seconds, show in minutes
                binding.episodeFilterDurationText.setText((filter.minimalDurationFilter / 60).toString())
            } else binding.episodeFilterDurationText.isEnabled = false

            if (filter.excludeOnly()) {
                termList = filter.getExcludeFilter().toMutableList()
                binding.excludeRadio.isChecked = true
            } else {
                termList = filter.getIncludeFilter().toMutableList()
                binding.includeRadio.isChecked = true
            }
            setupWordsList()

            setNegativeButton(R.string.cancel_label, null)
            setPositiveButton(R.string.confirm_label) { dialog: DialogInterface, which: Int ->
                this.onConfirmClick(dialog, which)
            }
        }

        private fun setupWordsList() {
            binding.termsRecycler.layoutManager = GridLayoutManager(context, 2)
            binding.termsRecycler.addItemDecoration(ItemOffsetDecoration(context, 4))
            val adapter: SimpleChipAdapter = object : SimpleChipAdapter(context) {
                override fun getChips(): List<String> {
                    return termList
                }

                override fun onRemoveClicked(position: Int) {
                    termList.removeAt(position)
                    notifyDataSetChanged()
                }
            }
            binding.termsRecycler.adapter = adapter
            binding.termsTextInput.setEndIconOnClickListener {
                val newWord = binding.termsTextInput.editText!!.text.toString().replace("\"", "").trim { it <= ' ' }
                if (newWord.isEmpty() || termList.contains(newWord)) return@setEndIconOnClickListener

                termList.add(newWord)
                binding.termsTextInput.editText!!.setText("")
                adapter.notifyDataSetChanged()
            }
        }

        protected abstract fun onConfirmed(filter: FeedEpisodesFilter)

        private fun onConfirmClick(dialog: DialogInterface, which: Int) {
            var minimalDuration = -1
            if (binding.durationCheckBox.isChecked) {
                try {
                    // Store minimal duration in seconds
                    minimalDuration = binding.episodeFilterDurationText.text.toString().toInt() * 60
                } catch (e: NumberFormatException) {
                    // Do not change anything on error
                }
            }
            var excludeFilter = ""
            var includeFilter = ""
            if (binding.includeRadio.isChecked) includeFilter = toFilterString(termList)
            else excludeFilter = toFilterString(termList)

            onConfirmed(FeedEpisodesFilter(includeFilter, excludeFilter, minimalDuration))
        }

        private fun toFilterString(words: List<String>?): String {
            val result = StringBuilder()
            for (word in words!!) {
                result.append("\"").append(word).append("\" ")
            }
            return result.toString()
        }
    }

    fun setFeed(feed_: Feed) {
        feed = unmanagedCopy(feed_)
    }

    companion object {
        private val TAG: String = FeedSettingsFragment::class.simpleName ?: "Anonymous"
        private const val EXTRA_FEED_ID = "ac.mdiq.podcini.extra.feedId"

        fun newInstance(feed: Feed): FeedSettingsFragment {
            val fragment = FeedSettingsFragment()
            fragment.setFeed(feed)
            return fragment
        }
    }
}
