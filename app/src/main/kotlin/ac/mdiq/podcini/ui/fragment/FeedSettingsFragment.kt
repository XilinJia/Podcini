package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AutodownloadFilterDialogBinding
import ac.mdiq.podcini.databinding.FeedPrefSkipDialogBinding
import ac.mdiq.podcini.databinding.FeedsettingsBinding
import ac.mdiq.podcini.databinding.PlaybackSpeedFeedSettingDialogBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.storage.database.Feeds.persistFeedPreferences
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.FeedPreferences.AutoDeleteAction
import ac.mdiq.podcini.storage.model.FeedPreferences.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.ui.adapter.SimpleChipAdapter
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.Spinner
import ac.mdiq.podcini.ui.dialog.AuthenticationDialog
import ac.mdiq.podcini.ui.dialog.TagSettingsDialog
import ac.mdiq.podcini.ui.utils.ItemOffsetDecoration
import ac.mdiq.podcini.util.Logd
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    private var autoDeleteSummaryResId by mutableIntStateOf(R.string.global_default)
    private var curPrefQueue by mutableStateOf(feed?.preferences?.queueTextExt ?: "Default")
    private var autoDeletePolicy = "global"
    private var queues: List<PlayQueue>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FeedsettingsBinding.inflate(inflater)
        Logd(TAG, "fragment onCreateView")

        val toolbar = binding.toolbar
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        getAutoDeletePolicy()

        binding.composeView.setContent {
            CustomTheme(requireContext()) {
                val textColor = MaterialTheme.colors.onSurface
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_refresh), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = stringResource(R.string.keep_updated),
                                style = MaterialTheme.typography.h6,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            var checked by remember { mutableStateOf(feed?.preferences?.keepUpdated ?: true) }
                            Switch(
                                checked = checked,
                                modifier = Modifier.height(24.dp),
                                onCheckedChange = {
                                    checked = it
                                    feed = upsertBlk(feed!!) {
                                        it.preferences?.keepUpdated = checked
                                    }
                                }
                            )
                        }
                        Text(
                            text = stringResource(R.string.keep_updated_summary),
                            style = MaterialTheme.typography.body2,
                            color = textColor
                        )
                    }
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_stream), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = stringResource(R.string.pref_stream_over_download_title),
                                style = MaterialTheme.typography.h6,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            var checked by remember { mutableStateOf(feed?.preferences?.prefStreamOverDownload ?: false) }
                            Switch(
                                checked = checked,
                                modifier = Modifier.height(24.dp),
                                onCheckedChange = {
                                    checked = it
                                    feed = upsertBlk(feed!!) {
                                        it.preferences?.prefStreamOverDownload = checked
                                    }
                                }
                            )
                        }
                        Text(
                            text = stringResource(R.string.pref_stream_over_download_sum),
                            style = MaterialTheme.typography.body2,
                            color = textColor
                        )
                    }
                    Column {
                        curPrefQueue = feed?.preferences?.queueTextExt ?: "Default"
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = stringResource(R.string.pref_feed_associated_queue),
                                style = MaterialTheme.typography.h6,
                                color = textColor,
                                modifier = Modifier.clickable(onClick = {
                                    val selectedOption = feed?.preferences?.queueText ?: "Default"
                                    val composeView = ComposeView(requireContext()).apply {
                                        setContent {
                                            val showDialog = remember { mutableStateOf(true) }
                                            CustomTheme(requireContext()) {
                                                SetAssociatedQueue(showDialog.value, selectedOption = selectedOption, onDismissRequest = { showDialog.value = false })
                                            }
                                        }
                                    }
                                    (view as? ViewGroup)?.addView(composeView)
                                })
                            )
                        }
                        Text(
                            text = curPrefQueue + " : " + stringResource(R.string.pref_feed_associated_queue_sum),
                            style = MaterialTheme.typography.body2,
                            color = textColor
                        )
                    }
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = stringResource(R.string.auto_delete_label),
                                style = MaterialTheme.typography.h6,
                                color = textColor,
                                modifier = Modifier.clickable(onClick = {
                                    val composeView = ComposeView(requireContext()).apply {
                                        setContent {
                                            val showDialog = remember { mutableStateOf(true) }
                                            CustomTheme(requireContext()) {
                                                AutoDeleteDialog(showDialog.value, onDismissRequest = { showDialog.value = false })
                                            }
                                        }
                                    }
                                    (view as? ViewGroup)?.addView(composeView)
                                })
                            )
                        }
                        Text(
                            text = stringResource(autoDeleteSummaryResId),
                            style = MaterialTheme.typography.body2,
                            color = textColor
                        )
                    }
                }
            }
        }
        if (feed != null) {
            toolbar.subtitle = feed!!.title
            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fragment_container, FeedSettingsPreferenceFragment.newInstance(feed!!), "settings_fragment")
                .commitAllowingStateLoss()
        }
        return binding.root
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        feed = null
        queues = null
        super.onDestroyView()
    }

    private fun getAutoDeletePolicy() {
        when (feed?.preferences!!.autoDeleteAction) {
            AutoDeleteAction.GLOBAL -> {
                autoDeleteSummaryResId = R.string.global_default
                autoDeletePolicy = "global"
            }
            AutoDeleteAction.ALWAYS -> {
                autoDeleteSummaryResId = R.string.feed_auto_download_always
                autoDeletePolicy = "always"
            }
            AutoDeleteAction.NEVER -> {
                autoDeleteSummaryResId = R.string.feed_auto_download_never
                autoDeletePolicy = "never"
            }
        }
    }
    @Composable
    fun AutoDeleteDialog(showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(autoDeletePolicy) }
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(
                    modifier = Modifier
                        .wrapContentSize(align = Alignment.Center)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            FeedAutoDeleteOptions.forEach { text ->
                                Row(Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = (text == selectedOption),
                                        onCheckedChange = { isChecked ->
                                            Logd(TAG, "row clicked: $text $selectedOption")
                                            if (text != selectedOption) {
                                                onOptionSelected(text)
                                                val action_ = when (text) {
                                                    "global" -> AutoDeleteAction.GLOBAL
                                                    "always" -> AutoDeleteAction.ALWAYS
                                                    "never" -> AutoDeleteAction.NEVER
                                                    else -> AutoDeleteAction.GLOBAL
                                                }
                                                feed = upsertBlk(feed!!) {
                                                    it.preferences?.autoDeleteAction = action_
                                                }
                                                getAutoDeletePolicy()
                                                onDismissRequest()
                                            }
                                        }
                                    )
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.body1.merge(),
//                                        color = textColor,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SetAssociatedQueue(showDialog: Boolean, selectedOption: String, onDismissRequest: () -> Unit) {
        var selected by remember {mutableStateOf(selectedOption)}
        if (showDialog) {
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier
                    .wrapContentSize(align = Alignment.Center)
                    .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        queueSettingOptions.forEach { option ->
                            Row(modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = option == selected,
                                    onCheckedChange = { isChecked ->
                                        selected = option
                                        if (isChecked) Logd(TAG, "$option is checked")
                                        when (selected) {
                                            "Default" -> {
                                                feed = upsertBlk(feed!!) { it.preferences?.queueId = 0L }
                                                curPrefQueue = selected
                                                onDismissRequest()
                                            }
                                            "Active" -> {
                                                feed = upsertBlk(feed!!) { it.preferences?.queueId = -1L }
                                                curPrefQueue = selected
                                                onDismissRequest()
                                            }
                                            "None" -> {
                                                feed = upsertBlk(feed!!) { it.preferences?.queueId = -2L }
                                                curPrefQueue = selected
                                                onDismissRequest()
                                            }
                                            "Custom" -> {}
                                        }
                                    }
                                )
                                Text(option)
                            }
                        }
                        if (selected == "Custom") {
                            if (queues == null) queues = realm.query(PlayQueue::class).find()
                            Spinner(items = queues!!.map { it.name }, selectedItem = feed?.preferences?.queue?.name ?: "Default") { name ->
                                Logd(TAG, "Queue selected: $name")
                                val q = queues?.firstOrNull { it.name == name }
                                feed = upsertBlk(feed!!) { it.preferences?.queue = q }
                                if (q != null) curPrefQueue = q.name
                                onDismissRequest()
                            }
                        }
                    }
                }
            }
        }
    }

    class FeedSettingsPreferenceFragment : PreferenceFragmentCompat() {
        private var feed: Feed? = null
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
            findPreference<Preference>(Prefs.feedSettingsScreen.name)!!.isVisible = false
            if (feed != null) {
                if (feed!!.preferences == null) {
                    feed!!.preferences = FeedPreferences(feed!!.id, false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
                    persistFeedPreferences(feed!!)
                }
                setupAutoDownloadGlobalPreference()
                setupAutoDownloadPreference()
                setupVolumeAdaptationPreferences()
                setupAuthentificationPreference()
                updateAutoDownloadPolicy()
                setupAutoDownloadPolicy()
                setupAutoDownloadCacheSize()
                setupCountingPlayedPreference()
                setupAutoDownloadFilterPreference()
                setupPlaybackSpeedPreference()
                setupFeedAutoSkipPreference()
                setupTags()
                updateVolumeAdaptationValue()
                updateAutoDownloadEnabled()
                if (feed!!.isLocalFeed) {
                    findPreference<Preference>(Prefs.authentication.name)!!.isVisible = false
                    findPreference<Preference>(Prefs.autoDownloadCategory.name)!!.isVisible = false
                }
                findPreference<Preference>(Prefs.feedSettingsScreen.name)!!.isVisible = true
            }
        }
        override fun onDestroyView() {
            Logd(TAG, "onDestroyView")
            feed = null
            super.onDestroyView()
        }
        private fun setupFeedAutoSkipPreference() {
            findPreference<Preference>(Prefs.feedAutoSkip.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    object : FeedPreferenceSkipDialog(requireContext(), feed?.preferences!!.introSkip, feed?.preferences!!.endingSkip) {
                        @UnstableApi
                        override fun onConfirmed(skipIntro: Int, skipEnding: Int) {
                            feed = upsertBlk(feed!!) {
                                it.preferences?.introSkip = skipIntro
                                it.preferences?.endingSkip = skipEnding
                            }
                        }
                    }.show()

                false
            }
        }
        @UnstableApi private fun setupPlaybackSpeedPreference() {
            val feedPlaybackSpeedPreference = findPreference<Preference>(Prefs.feedPlaybackSpeed.name)
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
                    val speed = feed?.preferences!!.playSpeed
                    binding.useGlobalCheckbox.isChecked = speed == FeedPreferences.SPEED_USE_GLOBAL
                    binding.seekBar.updateSpeed(if (speed == FeedPreferences.SPEED_USE_GLOBAL) 1f else speed)

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.playback_speed)
                    .setView(binding.root)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                        val newSpeed = if (binding.useGlobalCheckbox.isChecked) FeedPreferences.SPEED_USE_GLOBAL
                        else binding.seekBar.currentSpeed
                        feed = upsertBlk(feed!!) {
                            it.preferences?.playSpeed = newSpeed
                        }
                    }
                    .setNegativeButton(R.string.cancel_label, null)
                    .show()
                true
            }
        }
        @UnstableApi private fun setupAutoDownloadPolicy() {
            val policyPref = findPreference<Preference>(Prefs.feedAutoDownloadPolicy.name)
            policyPref!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                feed = upsertBlk(feed!!) {
                    it.preferences?.autoDLPolicyCode = newValue.toString().toInt()
                }
                updateAutoDownloadPolicy()
                false
            }
        }
        private fun updateAutoDownloadPolicy() {
            val policyPref = findPreference<ListPreference>(Prefs.feedAutoDownloadPolicy.name)
            when (feed?.preferences!!.autoDLPolicy) {
                FeedPreferences.AutoDLPolicy.ONLY_NEW -> policyPref!!.value = FeedPreferences.AutoDLPolicy.ONLY_NEW.ordinal.toString()
                FeedPreferences.AutoDLPolicy.NEWER -> policyPref!!.value = FeedPreferences.AutoDLPolicy.NEWER.ordinal.toString()
                FeedPreferences.AutoDLPolicy.OLDER -> policyPref!!.value = FeedPreferences.AutoDLPolicy.OLDER.ordinal.toString()
            }
        }
        @UnstableApi private fun setupAutoDownloadCacheSize() {
            val cachePref = findPreference<ListPreference>(Prefs.feedEpisodeCacheSize.name)
            cachePref!!.value = feed?.preferences!!.autoDLMaxEpisodes.toString()
            cachePref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                feed = upsertBlk(feed!!) {
                    it.preferences?.autoDLMaxEpisodes = newValue.toString().toInt()
                }
                cachePref.value = newValue.toString()
                false
            }
        }
        @OptIn(UnstableApi::class) private fun setupCountingPlayedPreference() {
            val pref = findPreference<SwitchPreferenceCompat>(Prefs.countingPlayed.name)
            pref!!.isChecked = feed?.preferences!!.countingPlayed
            pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val checked = newValue == true
                feed = upsertBlk(feed!!) {
                    it.preferences?.countingPlayed = checked
                }
                pref.isChecked = checked
                false
            }
        }
        private fun setupAutoDownloadFilterPreference() {
            findPreference<Preference>(Prefs.episodeInclusiveFilter.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    object : AutoDownloadFilterPrefDialog(requireContext(), feed?.preferences!!.autoDownloadFilter!!, 1) {
                        @UnstableApi
                        override fun onConfirmed(filter: FeedAutoDownloadFilter) {
                            feed = upsertBlk(feed!!) {
                                it.preferences?.autoDownloadFilter = filter
                            }
                        }
                    }.show()
                false
            }
            findPreference<Preference>(Prefs.episodeExclusiveFilter.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    object : AutoDownloadFilterPrefDialog(requireContext(), feed?.preferences!!.autoDownloadFilter!!, -1) {
                        @UnstableApi
                        override fun onConfirmed(filter: FeedAutoDownloadFilter) {
                            feed = upsertBlk(feed!!) {
                                it.preferences?.autoDownloadFilter = filter
                            }
                        }
                    }.show()
                false
            }
        }
        private fun setupAuthentificationPreference() {
            findPreference<Preference>(Prefs.authentication.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    object : AuthenticationDialog(requireContext(), R.string.authentication_label, true, feed?.preferences!!.username, feed?.preferences!!.password) {
                        @UnstableApi
                        override fun onConfirmed(username: String, password: String) {
                            feed = upsertBlk(feed!!) {
                                it.preferences?.username = username
                                it.preferences?.password = password
                            }
                            Thread({ runOnce(context, feed) }, "RefreshAfterCredentialChange").start()
                        }
                    }.show()
                false
            }
        }
        @UnstableApi private fun setupVolumeAdaptationPreferences() {
            val volumeAdaptationPreference = findPreference<ListPreference>("volumeReduction") ?: return
            volumeAdaptationPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                    val vAdapt = when (newValue as String?) {
                        "off" -> VolumeAdaptionSetting.OFF
                        "light" -> VolumeAdaptionSetting.LIGHT_REDUCTION
                        "heavy" -> VolumeAdaptionSetting.HEAVY_REDUCTION
                        "light_boost" -> VolumeAdaptionSetting.LIGHT_BOOST
                        "medium_boost" -> VolumeAdaptionSetting.MEDIUM_BOOST
                        "heavy_boost" -> VolumeAdaptionSetting.HEAVY_BOOST
                        else -> VolumeAdaptionSetting.OFF
                    }
                    feed = upsertBlk(feed!!) {
                        it.preferences?.volumeAdaptionSetting = vAdapt
                    }
                    updateVolumeAdaptationValue()
                false
            }
        }
        private fun updateVolumeAdaptationValue() {
            val volumeAdaptationPreference = findPreference<ListPreference>("volumeReduction") ?: return
            when (feed?.preferences?.volumeAdaptionSetting) {
                VolumeAdaptionSetting.OFF -> volumeAdaptationPreference.value = "off"
                VolumeAdaptionSetting.LIGHT_REDUCTION -> volumeAdaptationPreference.value = "light"
                VolumeAdaptionSetting.HEAVY_REDUCTION -> volumeAdaptationPreference.value = "heavy"
                VolumeAdaptionSetting.LIGHT_BOOST -> volumeAdaptationPreference.value = "light_boost"
                VolumeAdaptionSetting.MEDIUM_BOOST -> volumeAdaptationPreference.value = "medium_boost"
                VolumeAdaptionSetting.HEAVY_BOOST -> volumeAdaptationPreference.value = "heavy_boost"
                else -> {}
            }
        }
        private fun setupAutoDownloadGlobalPreference() {
            if (!isEnableAutodownload || feed?.preferences?.autoDownload != true) {
                val autodl = findPreference<SwitchPreferenceCompat>(Prefs.autoDownload.name)
                autodl!!.isChecked = false
                autodl.isEnabled = false
                autodl.setSummary(R.string.auto_download_disabled_globally)
                findPreference<Preference>(Prefs.feedAutoDownloadPolicy.name)!!.isEnabled = false
                findPreference<Preference>(Prefs.feedEpisodeCacheSize.name)!!.isEnabled = false
                findPreference<Preference>(Prefs.countingPlayed.name)!!.isEnabled = false
                findPreference<Preference>(Prefs.episodeInclusiveFilter.name)!!.isEnabled = false
                findPreference<Preference>(Prefs.episodeExclusiveFilter.name)!!.isEnabled = false
            }
        }
        @OptIn(UnstableApi::class) private fun setupAutoDownloadPreference() {
            val pref = findPreference<SwitchPreferenceCompat>(Prefs.autoDownload.name)
            pref!!.isEnabled = isEnableAutodownload
            if (isEnableAutodownload) pref.isChecked = feed?.preferences!!.autoDownload
            else {
                pref.isChecked = false
                pref.setSummary(R.string.auto_download_disabled_globally)
            }
            pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val checked = newValue == true
                feed = upsertBlk(feed!!) {
                    it.preferences?.autoDownload = checked
                }
                updateAutoDownloadEnabled()
                pref.isChecked = checked
                false
            }
        }
        private fun updateAutoDownloadEnabled() {
            if (feed?.preferences != null) {
                val enabled = feed!!.preferences!!.autoDownload && isEnableAutodownload
                findPreference<Preference>(Prefs.feedAutoDownloadPolicy.name)!!.isEnabled = enabled
                findPreference<Preference>(Prefs.feedEpisodeCacheSize.name)!!.isEnabled = enabled
                findPreference<Preference>(Prefs.countingPlayed.name)!!.isEnabled = enabled
                findPreference<Preference>(Prefs.episodeInclusiveFilter.name)!!.isEnabled = enabled
                findPreference<Preference>(Prefs.episodeExclusiveFilter.name)!!.isEnabled = enabled
            }
        }
        private fun setupTags() {
            findPreference<Preference>(Prefs.tags.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                TagSettingsDialog.newInstance(listOf(feed!!)).show(childFragmentManager, TagSettingsDialog.TAG)
                true
            }
        }

        fun setFeed(feed_: Feed) {
            feed = feed_
        }

        @Suppress("EnumEntryName")
        private enum class Prefs {
            feedSettingsScreen,
            keepUpdated,
            authentication,
            associatedQueue,
            autoDelete,
            feedPlaybackSpeed,
            feedAutoSkip,
            tags,
            autoDownloadCategory,
            autoDownload,
            feedAutoDownloadPolicy,
            feedEpisodeCacheSize,
            countingPlayed,
            episodeInclusiveFilter,
            episodeExclusiveFilter,
        }

        companion object {
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
    abstract class FeedPreferenceSkipDialog(context: Context, skipIntroInitialValue: Int, skipEndInitialValue: Int) : MaterialAlertDialogBuilder(context) {
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
                val skipIntro = try { etxtSkipIntro.text.toString().toInt() } catch (e: NumberFormatException) { 0 }
                val skipEnding = try { etxtSkipEnd.text.toString().toInt() } catch (e: NumberFormatException) { 0 }
                onConfirmed(skipIntro, skipEnding)
            }
        }
        protected abstract fun onConfirmed(skipIntro: Int, skipEnding: Int)
    }

    /**
     * Displays a dialog with a text box for filtering episodes and two radio buttons for exclusion/inclusion
     */
    abstract class AutoDownloadFilterPrefDialog(context: Context, val filter: FeedAutoDownloadFilter, val inexcl: Int) : MaterialAlertDialogBuilder(context) {
        private val binding = AutodownloadFilterDialogBinding.inflate(LayoutInflater.from(context))
        private var termList: MutableList<String> = mutableListOf()

        init {
            setTitle(R.string.episode_filters_label)
            setView(binding.root)
            if (inexcl == -1) {
//                exclusive
                binding.durationCheckBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                    binding.episodeFilterDurationText.isEnabled = isChecked
                }
                if (filter.hasMinimalDurationFilter()) {
                    binding.durationCheckBox.isChecked = true
                    // Store minimal duration in seconds, show in minutes
                    binding.episodeFilterDurationText.setText((filter.minimalDurationFilter / 60).toString())
                } else binding.episodeFilterDurationText.isEnabled = false
                if (filter.hasExcludeFilter()) {
                    termList = filter.getExcludeFilter().toMutableList()
                    binding.excludeRadio.isChecked = true
                }
                binding.markPlayedCheckBox.isChecked = filter.markExcludedPlayed
                binding.includeRadio.visibility = View.GONE
            } else {
//                inclusive
                binding.durationBlock.visibility = View.GONE
                if (filter.hasIncludeFilter()) {
                    termList = filter.getIncludeFilter().toMutableList()
                    binding.includeRadio.isChecked = true
                }
                binding.excludeRadio.visibility = View.GONE
                binding.markPlayedCheckBox.visibility = View.GONE
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
        protected abstract fun onConfirmed(filter: FeedAutoDownloadFilter)
        private fun onConfirmClick(dialog: DialogInterface, which: Int) {
            if (inexcl == -1) {
                var minimalDuration = -1
                if (binding.durationCheckBox.isChecked) {
                    try {
                        // Store minimal duration in seconds
                        minimalDuration = binding.episodeFilterDurationText.text.toString().toInt() * 60
                    } catch (e: NumberFormatException) {
                        // Do not change anything on error
                    }
                }
                val excludeFilter = toFilterString(termList)
                onConfirmed(FeedAutoDownloadFilter(filter.includeFilterRaw, excludeFilter, minimalDuration, binding.markPlayedCheckBox.isChecked))
            } else {
                val includeFilter = toFilterString(termList)
                onConfirmed(FeedAutoDownloadFilter(includeFilter, filter.excludeFilterRaw, filter.minimalDurationFilter, filter.markExcludedPlayed))
            }
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
        feed = feed_
    }

    companion object {
        private val TAG: String = FeedSettingsFragment::class.simpleName ?: "Anonymous"
        private const val EXTRA_FEED_ID = "ac.mdiq.podcini.extra.feedId"

        val queueSettingOptions = listOf("Default", "Active", "None", "Custom")

        fun newInstance(feed: Feed): FeedSettingsFragment {
            val fragment = FeedSettingsFragment()
            fragment.setFeed(feed)
            return fragment
        }
    }
}
