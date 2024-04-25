package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FeedsettingsBinding
import ac.mdiq.podcini.databinding.PlaybackSpeedFeedSettingDialogBinding
import ac.mdiq.podcini.net.download.FeedUpdateManager.runOnce
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedFilter
import ac.mdiq.podcini.storage.model.feed.FeedPreferences
import ac.mdiq.podcini.storage.model.feed.FeedPreferences.AutoDeleteAction
import ac.mdiq.podcini.storage.model.feed.VolumeAdaptionSetting
import ac.mdiq.podcini.ui.dialog.AuthenticationDialog
import ac.mdiq.podcini.ui.dialog.EpisodeFilterDialog
import ac.mdiq.podcini.ui.dialog.FeedPreferenceSkipDialog
import ac.mdiq.podcini.ui.dialog.TagSettingsDialog
import ac.mdiq.podcini.util.event.settings.SkipIntroEndingChangedEvent
import ac.mdiq.podcini.util.event.settings.SpeedPresetChangedEvent
import ac.mdiq.podcini.util.event.settings.VolumeAdaptionChangedEvent
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.MaybeOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.util.*
import java.util.concurrent.ExecutionException

class FeedSettingsFragment : Fragment() {
    private var _binding: FeedsettingsBinding? = null
    private val binding get() = _binding!!

    private var disposable: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FeedsettingsBinding.inflate(inflater)

        val feedId = requireArguments().getLong(EXTRA_FEED_ID)
        Log.d(TAG, "fragment onCreateView")

        val toolbar = binding.toolbar
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        parentFragmentManager.beginTransaction()
            .replace(R.id.settings_fragment_container, FeedSettingsPreferenceFragment.newInstance(feedId), "settings_fragment")
            .commitAllowingStateLoss()

        disposable = Maybe.create(MaybeOnSubscribe { emitter: MaybeEmitter<Feed> ->
            val feed = DBReader.getFeed(feedId)
            if (feed != null) emitter.onSuccess(feed)
            else emitter.onComplete()
        } as MaybeOnSubscribe<Feed>)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: Feed -> toolbar.subtitle = result.title },
                { error: Throwable? -> Log.d(TAG, Log.getStackTraceString(error)) },
                {})

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        disposable?.dispose()
    }

    class FeedSettingsPreferenceFragment : PreferenceFragmentCompat() {
        private var feed: Feed? = null
        private var disposable: Disposable? = null
        private var feedPreferences: FeedPreferences? = null

        var notificationPermissionDenied: Boolean = false
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

            val feedId = requireArguments().getLong(EXTRA_FEED_ID)
            disposable = Maybe.create { emitter: MaybeEmitter<Feed?> ->
                val feed = DBReader.getFeed(feedId)
                if (feed != null) emitter.onSuccess(feed)
                else emitter.onComplete()
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result: Feed? ->
                    feed = result
                    feedPreferences = feed!!.preferences

                    setupAutoDownloadGlobalPreference()
                    setupAutoDownloadPreference()
                    setupKeepUpdatedPreference()
                    setupAutoDeletePreference()
                    setupVolumeAdaptationPreferences()
//                    setupNewEpisodesAction()
                    setupAuthentificationPreference()
                    setupEpisodeFilterPreference()
                    setupPlaybackSpeedPreference()
                    setupFeedAutoSkipPreference()
//                    setupEpisodeNotificationPreference()
                    setupTags()

                    updateAutoDeleteSummary()
                    updateVolumeAdaptationValue()
                    updateAutoDownloadEnabled()
//                    updateNewEpisodesAction()

                    if (feed!!.isLocalFeed) {
                        findPreference<Preference>(PREF_AUTHENTICATION)!!.isVisible = false
                        findPreference<Preference>(PREF_CATEGORY_AUTO_DOWNLOAD)!!.isVisible = false
                    }
                    findPreference<Preference>(PREF_SCREEN)!!.isVisible = true
                }, { error: Throwable? -> Log.d(TAG, Log.getStackTraceString(error)) }, {})
        }

        override fun onDestroy() {
            super.onDestroy()
            disposable?.dispose()
        }

        private fun setupFeedAutoSkipPreference() {
            if (feedPreferences == null) return
            findPreference<Preference>(PREF_AUTO_SKIP)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                object : FeedPreferenceSkipDialog(requireContext(), feedPreferences!!.feedSkipIntro, feedPreferences!!.feedSkipEnding) {
                    @UnstableApi override fun onConfirmed(skipIntro: Int, skipEnding: Int) {
                        feedPreferences!!.feedSkipIntro = skipIntro
                        feedPreferences!!.feedSkipEnding = skipEnding
                        DBWriter.setFeedPreferences(feedPreferences!!)
                        EventBus.getDefault().post(SkipIntroEndingChangedEvent(feedPreferences!!.feedSkipIntro, feedPreferences!!.feedSkipEnding, feed!!.id))
                    }
                }.show()
                false
            }
        }

        @UnstableApi private fun setupPlaybackSpeedPreference() {
            val feedPlaybackSpeedPreference = findPreference<Preference>(PREF_FEED_PLAYBACK_SPEED)
            feedPlaybackSpeedPreference!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val viewBinding = PlaybackSpeedFeedSettingDialogBinding.inflate(layoutInflater)
                viewBinding.seekBar.setProgressChangedListener { speed: Float? ->
                    viewBinding.currentSpeedLabel.text = String.format(Locale.getDefault(), "%.2fx", speed)
                }
                viewBinding.useGlobalCheckbox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                    viewBinding.seekBar.isEnabled = !isChecked
                    viewBinding.seekBar.alpha = if (isChecked) 0.4f else 1f
                    viewBinding.currentSpeedLabel.alpha = if (isChecked) 0.4f else 1f
                }
                if (feedPreferences != null) {
                    val speed = feedPreferences!!.feedPlaybackSpeed
                    viewBinding.useGlobalCheckbox.isChecked = speed == FeedPreferences.SPEED_USE_GLOBAL
                    viewBinding.seekBar.updateSpeed(if (speed == FeedPreferences.SPEED_USE_GLOBAL) 1f else speed)
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.playback_speed)
                    .setView(viewBinding.root)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                        val newSpeed = if (viewBinding.useGlobalCheckbox.isChecked) FeedPreferences.SPEED_USE_GLOBAL
                        else viewBinding.seekBar.currentSpeed
                        feedPreferences!!.feedPlaybackSpeed = newSpeed
                        if (feedPreferences != null) DBWriter.setFeedPreferences(feedPreferences!!)
                        EventBus.getDefault().post(SpeedPresetChangedEvent(feedPreferences!!.feedPlaybackSpeed, feed!!.id))
                    }
                    .setNegativeButton(R.string.cancel_label, null)
                    .show()
                true
            }
        }

        private fun setupEpisodeFilterPreference() {
            if (feedPreferences == null) return
            findPreference<Preference>(PREF_EPISODE_FILTER)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                object : EpisodeFilterDialog(requireContext(), feedPreferences!!.filter) {
                    @UnstableApi override fun onConfirmed(filter: FeedFilter) {
                        feedPreferences!!.filter = filter
                        DBWriter.setFeedPreferences(feedPreferences!!)
                    }
                }.show()
                false
            }
        }

        private fun setupAuthentificationPreference() {
            if (feedPreferences == null) return
            findPreference<Preference>(PREF_AUTHENTICATION)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                object : AuthenticationDialog(requireContext(), R.string.authentication_label, true, feedPreferences!!.username, feedPreferences!!.password) {
                    @UnstableApi override fun onConfirmed(username: String, password: String) {
                        feedPreferences!!.username = username
                        feedPreferences!!.password = password
                        val setPreferencesFuture = DBWriter.setFeedPreferences(feedPreferences!!)
                        Thread({
                            try {
                                setPreferencesFuture.get()
                            } catch (e: InterruptedException) {
                                e.printStackTrace()
                            } catch (e: ExecutionException) {
                                e.printStackTrace()
                            }
                            runOnce(context, feed)
                        }, "RefreshAfterCredentialChange").start()
                    }
                }.show()
                false
            }
        }

        @UnstableApi private fun setupAutoDeletePreference() {
            if (feedPreferences == null) return
            findPreference<Preference>(PREF_AUTO_DELETE)!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                when (newValue as String?) {
                    "global" -> feedPreferences!!.currentAutoDelete = AutoDeleteAction.GLOBAL
                    "always" -> feedPreferences!!.currentAutoDelete = AutoDeleteAction.ALWAYS
                    "never" -> feedPreferences!!.currentAutoDelete = AutoDeleteAction.NEVER
                    else -> {}
                }
                DBWriter.setFeedPreferences(feedPreferences!!)
                updateAutoDeleteSummary()
                false
            }
        }

        private fun updateAutoDeleteSummary() {
            if (feedPreferences == null) return
            val autoDeletePreference = findPreference<ListPreference>(PREF_AUTO_DELETE)

            when (feedPreferences!!.currentAutoDelete) {
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
            if (feedPreferences == null) return
            val volumeAdaptationPreference = findPreference<ListPreference>("volumeReduction")
            volumeAdaptationPreference!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                when (newValue as String?) {
                    "off" -> feedPreferences!!.volumeAdaptionSetting = VolumeAdaptionSetting.OFF
                    "light" -> feedPreferences!!.volumeAdaptionSetting = VolumeAdaptionSetting.LIGHT_REDUCTION
                    "heavy" -> feedPreferences!!.volumeAdaptionSetting = VolumeAdaptionSetting.HEAVY_REDUCTION
                    "light_boost" -> feedPreferences!!.volumeAdaptionSetting = VolumeAdaptionSetting.LIGHT_BOOST
                    "medium_boost" -> feedPreferences!!.volumeAdaptionSetting = VolumeAdaptionSetting.MEDIUM_BOOST
                    "heavy_boost" -> feedPreferences!!.volumeAdaptionSetting = VolumeAdaptionSetting.HEAVY_BOOST
                    else -> {}
                }
                DBWriter.setFeedPreferences(feedPreferences!!)
                updateVolumeAdaptationValue()
                if (feed != null && feedPreferences!!.volumeAdaptionSetting != null)
                    EventBus.getDefault().post(VolumeAdaptionChangedEvent(feedPreferences!!.volumeAdaptionSetting!!, feed!!.id))
                false
            }
        }

        private fun updateVolumeAdaptationValue() {
            if (feedPreferences == null) return

            val volumeAdaptationPreference = findPreference<ListPreference>("volumeReduction")

            when (feedPreferences!!.volumeAdaptionSetting) {
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
            if (feedPreferences == null) return
            val pref = findPreference<SwitchPreferenceCompat>("keepUpdated")

            pref!!.isChecked = feedPreferences!!.keepUpdated
            pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val checked = newValue == true
                feedPreferences!!.keepUpdated = checked
                DBWriter.setFeedPreferences(feedPreferences!!)
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
            if (feedPreferences == null) return
            val pref = findPreference<SwitchPreferenceCompat>("autoDownload")

            pref!!.isEnabled = isEnableAutodownload
            if (isEnableAutodownload) {
                pref.isChecked = feedPreferences!!.autoDownload
            } else {
                pref.isChecked = false
                pref.setSummary(R.string.auto_download_disabled_globally)
            }

            pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val checked = newValue == true
                feedPreferences!!.autoDownload = checked
                DBWriter.setFeedPreferences(feedPreferences!!)
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
                if (feedPreferences != null) TagSettingsDialog.newInstance(listOf(feedPreferences!!))
                    .show(childFragmentManager, TagSettingsDialog.TAG)
                true
            }
        }

//        @OptIn(UnstableApi::class) private fun setupEpisodeNotificationPreference() {
//            val pref = findPreference<SwitchPreferenceCompat>("episodeNotification")
//
//            pref!!.isChecked = feedPreferences!!.showEpisodeNotification
//            pref.onPreferenceChangeListener =
//                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
//                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
//                                requireContext(),
//                                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
//                        return@OnPreferenceChangeListener false
//                    }
//                    val checked = newValue == true
//                    feedPreferences!!.showEpisodeNotification = checked
//                    if (feedPreferences != null) DBWriter.setFeedPreferences(feedPreferences!!)
//                    pref.isChecked = checked
//                    false
//                }
//        }

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

            fun newInstance(feedId: Long): FeedSettingsPreferenceFragment {
                val fragment = FeedSettingsPreferenceFragment()
                val arguments = Bundle()
                arguments.putLong(EXTRA_FEED_ID, feedId)
                fragment.arguments = arguments
                return fragment
            }
        }
    }

    companion object {
        private const val TAG = "FeedSettingsFragment"
        private const val EXTRA_FEED_ID = "ac.mdiq.podcini.extra.feedId"

        fun newInstance(feed: Feed): FeedSettingsFragment {
            val fragment = FeedSettingsFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_FEED_ID, feed.id)
            fragment.arguments = arguments
            return fragment
        }
    }
}
