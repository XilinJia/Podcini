package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FeedsettingsBinding
import ac.mdiq.podcini.databinding.PlaybackSpeedFeedSettingDialogBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.base.VideoMode.Companion.videoModeTags
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.storage.database.Feeds.persistFeedPreferences
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_NATURAL_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.FeedPreferences.AutoDeleteAction
import ac.mdiq.podcini.storage.model.FeedPreferences.AutoDownloadPolicy
import ac.mdiq.podcini.storage.model.FeedPreferences.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.Spinner
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.util.Logd
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
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*

class FeedSettingsFragment : Fragment() {
    private var _binding: FeedsettingsBinding? = null
    private val binding get() = _binding!!
    private var feed: Feed? = null
    private var autoDeleteSummaryResId by mutableIntStateOf(R.string.global_default)
    private var curPrefQueue by mutableStateOf(feed?.preferences?.queueTextExt ?: "Default")
    private var autoDeletePolicy = AutoDeleteAction.GLOBAL.name
    private var videoModeSummaryResId by mutableIntStateOf(R.string.global_default)
    private var videoMode = VideoMode.NONE.name
    private var queues: List<PlayQueue>? = null

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FeedsettingsBinding.inflate(inflater)
        Logd(TAG, "fragment onCreateView")

        val toolbar = binding.toolbar
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        getVideoModePolicy()
        getAutoDeletePolicy()

        binding.composeView.setContent {
            CustomTheme(requireContext()) {
                val textColor = MaterialTheme.colorScheme.onSurface
                Column(modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if ((feed?.id ?: 0) > MAX_SYNTHETIC_ID) {
                        //                    refresh
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_refresh), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(text = stringResource(R.string.keep_updated), style = MaterialTheme.typography.titleLarge, color = textColor)
                                Spacer(modifier = Modifier.weight(1f))
                                var checked by remember { mutableStateOf(feed?.preferences?.keepUpdated != false) }
                                Switch(checked = checked, modifier = Modifier.height(24.dp),
                                    onCheckedChange = {
                                        checked = it
                                        feed = upsertBlk(feed!!) { f -> f.preferences?.keepUpdated = checked }
                                    }
                                )
                            }
                            Text(text = stringResource(R.string.keep_updated_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }
                    Column {
                        var showDialog by remember { mutableStateOf(false) }
                        var selectedOption by remember { mutableStateOf(feed?.preferences?.audioTypeSetting?.tag ?: FeedPreferences.AudioType.SPEECH.tag) }
                        if (showDialog) SetAudioType(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.pref_feed_audio_type), style = MaterialTheme.typography.titleLarge, color = textColor,
                                modifier = Modifier.clickable(onClick = {
                                    selectedOption = feed!!.preferences?.audioTypeSetting?.tag ?: FeedPreferences.AudioType.SPEECH.tag
                                    showDialog = true
                                })
                            )
                        }
                        Text(text = stringResource(R.string.pref_feed_audio_type_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    if ((feed?.id?:0) >= MAX_NATURAL_SYNTHETIC_ID && feed?.hasVideoMedia == true) {
                        //                    video mode
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                val showDialog = remember { mutableStateOf(false) }
                                if (showDialog.value) VideoModeDialog(onDismissRequest = { showDialog.value = false })
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(text = stringResource(R.string.feed_video_mode_label), style = MaterialTheme.typography.titleLarge, color = textColor,
                                    modifier = Modifier.clickable(onClick = { showDialog.value = true })
                                )
                            }
                            Text(text = stringResource(videoModeSummaryResId), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }
                    if (feed?.type != Feed.FeedType.YOUTUBE.name) {
                        //                    prefer streaming
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_stream), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(text = stringResource(R.string.pref_stream_over_download_title), style = MaterialTheme.typography.titleLarge, color = textColor)
                                Spacer(modifier = Modifier.weight(1f))
                                var checked by remember { mutableStateOf(feed?.preferences?.prefStreamOverDownload == true) }
                                Switch(checked = checked, modifier = Modifier.height(24.dp),
                                    onCheckedChange = {
                                        checked = it
                                        feed = upsertBlk(feed!!) { f -> f.preferences?.prefStreamOverDownload = checked }
                                    }
                                )
                            }
                            Text(text = stringResource(R.string.pref_stream_over_download_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }
                    if (feed?.type == Feed.FeedType.YOUTUBE.name) {
                        //                    audio quality
                        Column {
                            var showDialog by remember { mutableStateOf(false) }
                            var selectedOption by remember { mutableStateOf(feed?.preferences?.audioQualitySetting?.tag ?: FeedPreferences.AVQuality.GLOBAL.tag) }
                            if (showDialog) SetAudioQuality(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                            Row(Modifier.fillMaxWidth()) {
                                Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(text = stringResource(R.string.pref_feed_audio_quality), style = MaterialTheme.typography.titleLarge, color = textColor,
                                    modifier = Modifier.clickable(onClick = {
                                        selectedOption = feed!!.preferences?.audioQualitySetting?.tag ?: FeedPreferences.AVQuality.GLOBAL.tag
                                        showDialog = true
                                    })
                                )
                            }
                            Text(text = stringResource(R.string.pref_feed_audio_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        if (feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY) {
                            //                    video quality
                            Column {
                                var showDialog by remember { mutableStateOf(false) }
                                var selectedOption by remember { mutableStateOf(feed?.preferences?.videoQualitySetting?.tag ?: FeedPreferences.AVQuality.GLOBAL.tag) }
                                if (showDialog) SetVideoQuality(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                                Row(Modifier.fillMaxWidth()) {
                                    Icon(ImageVector.vectorResource(id = R.drawable.ic_videocam), "", tint = textColor)
                                    Spacer(modifier = Modifier.width(20.dp))
                                    Text(text = stringResource(R.string.pref_feed_video_quality), style = MaterialTheme.typography.titleLarge, color = textColor,
                                        modifier = Modifier.clickable(onClick = {
                                            selectedOption = feed!!.preferences?.videoQualitySetting?.tag ?: FeedPreferences.AVQuality.GLOBAL.tag
                                            showDialog = true
                                        })
                                    )
                                }
                                Text(text = stringResource(R.string.pref_feed_video_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                        }
                    }
                    //                    associated queue
                    Column {
                        curPrefQueue = feed?.preferences?.queueTextExt ?: "Default"
                        var showDialog by remember { mutableStateOf(false) }
                        var selectedOption by remember { mutableStateOf(feed?.preferences?.queueText ?: "Default") }
                        if (showDialog) SetAssociatedQueue(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.pref_feed_associated_queue), style = MaterialTheme.typography.titleLarge, color = textColor,
                                modifier = Modifier.clickable(onClick = {
                                    selectedOption = feed?.preferences?.queueText ?: "Default"
                                    showDialog = true
                                })
                            )
                        }
                        Text(text = curPrefQueue + " : " + stringResource(R.string.pref_feed_associated_queue_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    //                    auto add new to queue
                    if (curPrefQueue != "None") {
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Icon(ImageVector.vectorResource(id = androidx.media3.session.R.drawable.media3_icon_queue_add), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(text = stringResource(R.string.audo_add_new_queue), style = MaterialTheme.typography.titleLarge, color = textColor)
                                Spacer(modifier = Modifier.weight(1f))
                                var checked by remember { mutableStateOf(feed?.preferences?.autoAddNewToQueue != false) }
                                Switch(checked = checked, modifier = Modifier.height(24.dp),
                                    onCheckedChange = {
                                        checked = it
                                        feed = upsertBlk(feed!!) { f -> f.preferences?.autoAddNewToQueue = checked }
                                    }
                                )
                            }
                            Text(text = stringResource(R.string.audo_add_new_queue_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }
                    if (feed?.type != Feed.FeedType.YOUTUBE.name) {
                        //                    auto delete
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                val showDialog = remember { mutableStateOf(false) }
                                if (showDialog.value) AutoDeleteDialog(onDismissRequest = { showDialog.value = false })
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(text = stringResource(R.string.auto_delete_label), style = MaterialTheme.typography.titleLarge, color = textColor,
                                    modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                            }
                            Text(text = stringResource(autoDeleteSummaryResId), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }
                    //                    tags
                    Column {
                        var showDialog by remember { mutableStateOf(false) }
                        if (showDialog) TagSettingDialog(feeds = listOf(feed!!), onDismiss = { showDialog = false })
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_tag), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.feed_tags_label), style = MaterialTheme.typography.titleLarge, color = textColor,
                                modifier = Modifier.clickable(onClick = { showDialog = true }))
                        }
                        Text(text = stringResource(R.string.feed_tags_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    //                    playback speed
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_playback_speed), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.playback_speed), style = MaterialTheme.typography.titleLarge, color = textColor,
                                modifier = Modifier.clickable(onClick = { PlaybackSpeedDialog().show() }))
                        }
                        Text(text = stringResource(R.string.pref_feed_playback_speed_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    //                    auto skip
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            val showDialog = remember { mutableStateOf(false) }
                            if (showDialog.value) AutoSkipDialog(onDismiss = { showDialog.value = false })
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_skip_24dp), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.pref_feed_skip), style = MaterialTheme.typography.titleLarge, color = textColor,
                                modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                        }
                        Text(text = stringResource(R.string.pref_feed_skip_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    //                    volume adaption
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            val showDialog = remember { mutableStateOf(false) }
                            if (showDialog.value) VolumeAdaptionDialog(onDismissRequest = { showDialog.value = false })
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_volume_adaption), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.feed_volume_adapdation), style = MaterialTheme.typography.titleLarge, color = textColor,
                                modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                        }
                        Text(text = stringResource(R.string.feed_volume_adaptation_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    //                    authentication
                    if ((feed?.id?:0) > 0 && feed?.isLocalFeed != true) {
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                val showDialog = remember { mutableStateOf(false) }
                                if (showDialog.value) AuthenticationDialog(onDismiss = { showDialog.value = false })
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_key), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(text = stringResource(R.string.authentication_label), style = MaterialTheme.typography.titleLarge, color = textColor,
                                    modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                            }
                            Text(text = stringResource(R.string.authentication_descr), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                    }
                    if (isEnableAutodownload && feed?.type != Feed.FeedType.YOUTUBE.name) {
                        //                    auto download
                        var audoDownloadChecked by remember { mutableStateOf(feed?.preferences?.autoDownload == true) }
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Text(text = stringResource(R.string.auto_download_label), style = MaterialTheme.typography.titleLarge, color = textColor)
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(checked = audoDownloadChecked, modifier = Modifier.height(24.dp),
                                    onCheckedChange = {
                                        audoDownloadChecked = it
                                        feed = upsertBlk(feed!!) { f -> f.preferences?.autoDownload = audoDownloadChecked }
                                    })
                            }
                            if (!isEnableAutodownload)
                                Text(text = stringResource(R.string.auto_download_disabled_globally), style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        if (audoDownloadChecked) {
                            //                    auto download policy
                            Column (modifier = Modifier.padding(start = 20.dp)){
                                Row(Modifier.fillMaxWidth()) {
                                    val showDialog = remember { mutableStateOf(false) }
                                    if (showDialog.value) AutoDownloadPolicyDialog(onDismissRequest = { showDialog.value = false })
                                    Text(text = stringResource(R.string.feed_auto_download_policy), style = MaterialTheme.typography.titleLarge, color = textColor,
                                        modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                                }
                            }
                            //                    episode cache
                            Column (modifier = Modifier.padding(start = 20.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    val showDialog = remember { mutableStateOf(false) }
                                    if (showDialog.value) SetEpisodesCacheDialog(onDismiss = { showDialog.value = false })
                                    Text(text = stringResource(R.string.pref_episode_cache_title), style = MaterialTheme.typography.titleLarge, color = textColor,
                                        modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                                }
                                Text(text = stringResource(R.string.pref_episode_cache_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                            //                    counting played
                            Column (modifier = Modifier.padding(start = 20.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(text = stringResource(R.string.pref_auto_download_counting_played_title), style = MaterialTheme.typography.titleLarge, color = textColor)
                                    Spacer(modifier = Modifier.weight(1f))
                                    var checked by remember { mutableStateOf(feed?.preferences?.countingPlayed != false) }
                                    Switch(checked = checked, modifier = Modifier.height(24.dp),
                                        onCheckedChange = {
                                            checked = it
                                            feed = upsertBlk(feed!!) { f -> f.preferences?.countingPlayed = checked }
                                        }
                                    )
                                }
                                Text(text = stringResource(R.string.pref_auto_download_counting_played_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                            //                    inclusive filter
                            Column (modifier = Modifier.padding(start = 20.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    val showDialog = remember { mutableStateOf(false) }
                                    if (showDialog.value) AutoDownloadFilterDialog(feed?.preferences!!.autoDownloadFilter!!, ADLIncExc.INCLUDE, onDismiss = { showDialog.value = false }) { filter ->
                                        feed = upsertBlk(feed!!) { it.preferences?.autoDownloadFilter = filter }
                                    }
                                    Text(text = stringResource(R.string.episode_inclusive_filters_label), style = MaterialTheme.typography.titleLarge, color = textColor,
                                        modifier = Modifier.clickable(onClick = { showDialog.value = true })
                                    )
                                }
                                Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                            //                    exclusive filter
                            Column (modifier = Modifier.padding(start = 20.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    val showDialog = remember { mutableStateOf(false) }
                                    if (showDialog.value) AutoDownloadFilterDialog(feed?.preferences!!.autoDownloadFilter!!, ADLIncExc.EXCLUDE, onDismiss = { showDialog.value = false }) { filter ->
                                        feed = upsertBlk(feed!!) { it.preferences?.autoDownloadFilter = filter }
                                    }
                                    Text(text = stringResource(R.string.episode_exclusive_filters_label), style = MaterialTheme.typography.titleLarge, color = textColor,
                                        modifier = Modifier.clickable(onClick = { showDialog.value = true })
                                    )
                                }
                                Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                        }
                    }
                }
            }
        }
        if (feed != null) toolbar.subtitle = feed!!.title
        return binding.root
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        feed = null
        queues = null
        super.onDestroyView()
    }

    private fun getVideoModePolicy() {
        when (feed?.preferences!!.videoModePolicy) {
            VideoMode.NONE -> {
                videoModeSummaryResId = R.string.global_default
                videoMode = VideoMode.NONE.tag
            }
            VideoMode.WINDOW_VIEW -> {
                videoModeSummaryResId = R.string.feed_video_mode_window
                videoMode = VideoMode.WINDOW_VIEW.tag
            }
            VideoMode.FULL_SCREEN_VIEW -> {
                videoModeSummaryResId = R.string.feed_video_mode_fullscreen
                videoMode = VideoMode.FULL_SCREEN_VIEW.tag
            }
            VideoMode.AUDIO_ONLY -> {
                videoModeSummaryResId = R.string.feed_video_mode_audioonly
                videoMode = VideoMode.AUDIO_ONLY.tag
            }
        }
    }
    @Composable
    fun VideoModeDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(videoMode) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        videoModeTags.forEach { text ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = (text == selectedOption),
                                    onCheckedChange = {
                                        Logd(TAG, "row clicked: $text $selectedOption")
                                        if (text != selectedOption) {
                                            onOptionSelected(text)
                                            val mode_ = when (text) {
                                                VideoMode.NONE.tag -> VideoMode.NONE
                                                VideoMode.WINDOW_VIEW.tag -> VideoMode.WINDOW_VIEW
                                                VideoMode.FULL_SCREEN_VIEW.tag -> VideoMode.FULL_SCREEN_VIEW
                                                VideoMode.AUDIO_ONLY.tag -> VideoMode.AUDIO_ONLY
                                                else -> VideoMode.NONE
                                            }
                                            feed = upsertBlk(feed!!) { it.preferences?.videoModePolicy = mode_ }
                                            getVideoModePolicy()
                                            onDismissRequest()
                                        }
                                    }
                                )
                                Text(text = text, style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getAutoDeletePolicy() {
        when (feed?.preferences!!.autoDeleteAction) {
            AutoDeleteAction.GLOBAL -> {
                autoDeleteSummaryResId = R.string.global_default
                autoDeletePolicy = AutoDeleteAction.GLOBAL.tag
            }
            AutoDeleteAction.ALWAYS -> {
                autoDeleteSummaryResId = R.string.feed_auto_download_always
                autoDeletePolicy = AutoDeleteAction.ALWAYS.tag
            }
            AutoDeleteAction.NEVER -> {
                autoDeleteSummaryResId = R.string.feed_auto_download_never
                autoDeletePolicy = AutoDeleteAction.NEVER.tag
            }
        }
    }
    @Composable
    fun AutoDeleteDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(autoDeletePolicy) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        FeedAutoDeleteOptions.forEach { text ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = (text == selectedOption),
                                    onCheckedChange = {
                                        Logd(TAG, "row clicked: $text $selectedOption")
                                        if (text != selectedOption) {
                                            onOptionSelected(text)
                                            val action_ = when (text) {
                                                AutoDeleteAction.GLOBAL.tag -> AutoDeleteAction.GLOBAL
                                                AutoDeleteAction.ALWAYS.tag -> AutoDeleteAction.ALWAYS
                                                AutoDeleteAction.NEVER.tag -> AutoDeleteAction.NEVER
                                                else -> AutoDeleteAction.GLOBAL
                                            }
                                            feed = upsertBlk(feed!!) { it.preferences?.autoDeleteAction = action_ }
                                            getAutoDeletePolicy()
                                            onDismissRequest()
                                        }
                                    }
                                )
                                Text(text = text, style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun VolumeAdaptionDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(feed?.preferences?.volumeAdaptionSetting ?: VolumeAdaptionSetting.OFF) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        VolumeAdaptionSetting.entries.forEach { item ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = (item == selectedOption),
                                    onCheckedChange = { _ ->
                                        Logd(TAG, "row clicked: $item $selectedOption")
                                        if (item != selectedOption) {
                                            onOptionSelected(item)
                                            feed = upsertBlk(feed!!) { it.preferences?.volumeAdaptionSetting = item }
                                            onDismissRequest()
                                        }
                                    }
                                )
                                Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AutoDownloadPolicyDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(feed?.preferences?.autoDLPolicy ?: AutoDownloadPolicy.ONLY_NEW) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        AutoDownloadPolicy.entries.forEach { item ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = (item == selectedOption),
                                    onCheckedChange = {
                                        Logd(TAG, "row clicked: $item $selectedOption")
                                        if (item != selectedOption) {
                                            onOptionSelected(item)
                                            feed = upsertBlk(feed!!) { it.preferences?.autoDLPolicy = item }
//                                                getAutoDeletePolicy()
                                            onDismissRequest()
                                        }
                                    }
                                )
                                Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SetEpisodesCacheDialog(onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var newCache by remember { mutableStateOf((feed?.preferences?.autoDLMaxEpisodes ?: 1).toString()) }
                    TextField(value = newCache, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) newCache = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(R.string.max_episodes_cache)) })
                    Button(onClick = {
                        if (newCache.isNotEmpty()) {
                            feed = upsertBlk(feed!!) { it.preferences?.autoDLMaxEpisodes = newCache.toIntOrNull() ?: 1 }
                            onDismiss()
                        }
                    }) { Text("Confirm") }
                }
            }
        }
    }

    @Composable
    private fun SetAssociatedQueue(selectedOption: String, onDismissRequest: () -> Unit) {
        var selected by remember {mutableStateOf(selectedOption)}
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    queueSettingOptions.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                        Logd(TAG, "queues: ${queues?.size}")
                        Spinner(items = queues!!.map { it.name }, selectedItem = feed?.preferences?.queue?.name ?: "Default") { index ->
                            Logd(TAG, "Queue selected: $queues[index].name")
                            val q = queues!![index]
                            feed = upsertBlk(feed!!) { it.preferences?.queue = q }
                            curPrefQueue = q.name
                            onDismissRequest()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SetAudioType(selectedOption: String, onDismissRequest: () -> Unit) {
        var selected by remember {mutableStateOf(selectedOption)}
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedPreferences.AudioType.entries.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = option.tag == selected,
                                onCheckedChange = { isChecked ->
                                    selected = option.tag
                                    if (isChecked) Logd(TAG, "$option is checked")
                                    val type = FeedPreferences.AudioType.fromTag(selected)
                                    feed = upsertBlk(feed!!) { it.preferences?.audioType = type.code }
                                    onDismissRequest()
                                }
                            )
                            Text(option.tag)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SetAudioQuality(selectedOption: String, onDismissRequest: () -> Unit) {
        var selected by remember {mutableStateOf(selectedOption)}
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedPreferences.AVQuality.entries.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = option.tag == selected,
                                onCheckedChange = { isChecked ->
                                    selected = option.tag
                                    if (isChecked) Logd(TAG, "$option is checked")
                                    val type = FeedPreferences.AVQuality.fromTag(selected)
                                    feed = upsertBlk(feed!!) { it.preferences?.audioQuality = type.code }
                                    onDismissRequest()
                                })
                            Text(option.tag)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SetVideoQuality(selectedOption: String, onDismissRequest: () -> Unit) {
        var selected by remember {mutableStateOf(selectedOption)}
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedPreferences.AVQuality.entries.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = option.tag == selected,
                                onCheckedChange = { isChecked ->
                                    selected = option.tag
                                    if (isChecked) Logd(TAG, "$option is checked")
                                    val type = FeedPreferences.AVQuality.fromTag(selected)
                                    feed = upsertBlk(feed!!) { it.preferences?.videoQuality = type.code }
                                    onDismissRequest()
                                })
                            Text(option.tag)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AuthenticationDialog(onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val oldName = feed?.preferences?.username?:""
                    var newName by remember { mutableStateOf(oldName) }
                    TextField(value = newName, onValueChange = { newName = it }, label = { Text("Username") })
                    val oldPW = feed?.preferences?.password?:""
                    var newPW by remember { mutableStateOf(oldPW) }
                    TextField(value = newPW, onValueChange = { newPW = it }, label = { Text("Password") })
                    Button(onClick = {
                        if (newName.isNotEmpty() && oldName != newName) {
                            feed = upsertBlk(feed!!) {
                                it.preferences?.username = newName
                                it.preferences?.password = newPW
                            }
                            Thread({ runOnce(requireContext(), feed) }, "RefreshAfterCredentialChange").start()
                            onDismiss()
                        }
                    }) { Text("Confirm") }
                }
            }
        }
    }

    @Composable
    fun AutoSkipDialog(onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var intro by remember { mutableStateOf((feed?.preferences?.introSkip ?: 0).toString()) }
                    TextField(value = intro, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) intro = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Skip first (seconds)") })
                    var ending by remember { mutableStateOf((feed?.preferences?.endingSkip ?: 0).toString()) }
                    TextField(value = ending, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) ending = it  },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Skip last (seconds)") })
                    Button(onClick = {
                        if (intro.isNotEmpty() || ending.isNotEmpty()) {
                            feed = upsertBlk(feed!!) {
                                it.preferences?.introSkip = intro.toIntOrNull() ?: 0
                                it.preferences?.endingSkip = ending.toIntOrNull() ?: 0
                            }
                            onDismiss()
                        }
                    }) { Text("Confirm") }
                }
            }
        }
    }

    private fun PlaybackSpeedDialog(): AlertDialog {
        val binding = PlaybackSpeedFeedSettingDialogBinding.inflate(LayoutInflater.from(requireContext()))
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
        return MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.playback_speed).setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                val newSpeed = if (binding.useGlobalCheckbox.isChecked) FeedPreferences.SPEED_USE_GLOBAL
                else binding.seekBar.currentSpeed
                feed = upsertBlk(feed!!) { it.preferences?.playSpeed = newSpeed }
            }
            .setNegativeButton(R.string.cancel_label, null)
            .create()
    }

//    class PositiveIntegerTransform : VisualTransformation {
//        override fun filter(text: AnnotatedString): TransformedText {
//            val trimmedText = text.text.filter { it.isDigit() }
//            val transformedText = if (trimmedText.isNotEmpty() && trimmedText.toInt() > 0) trimmedText else ""
//            return TransformedText(AnnotatedString(transformedText), OffsetMapping.Identity)
//        }
//    }

    enum class ADLIncExc {
        INCLUDE,
        EXCLUDE
    }
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun AutoDownloadFilterDialog(filter: FeedAutoDownloadFilter, inexcl: ADLIncExc, onDismiss: () -> Unit, onConfirmed: (FeedAutoDownloadFilter) -> Unit) {
        fun toFilterString(words: List<String>?): String {
            val result = StringBuilder()
            for (word in words!!) result.append("\"").append(word).append("\" ")
            return result.toString()
        }
        Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
            Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                val textColor = MaterialTheme.colorScheme.onSurface
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.episode_filters_label), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    var termList = remember { if (inexcl == ADLIncExc.EXCLUDE) filter.getExcludeFilter().toMutableStateList() else filter.getIncludeFilter().toMutableStateList() }
                    var filterDuration by remember { mutableStateOf(filter.hasMinimalDurationFilter()) }
                    var excludeChecked by remember { mutableStateOf(filter.hasExcludeFilter()) }
                    var includeChecked by remember { mutableStateOf(filter.hasIncludeFilter()) }
                    Row {
                        if (inexcl != ADLIncExc.EXCLUDE) {
                            Checkbox(checked = includeChecked, onCheckedChange = { isChecked -> includeChecked = isChecked })
                            Text(text = stringResource(R.string.include_terms), style = MaterialTheme.typography.bodyMedium.merge(), modifier = Modifier.weight(1f))
                        } else {
                            Checkbox(checked = excludeChecked, onCheckedChange = { isChecked -> excludeChecked = isChecked })
                            Text(text = stringResource(R.string.exclude_terms), style = MaterialTheme.typography.bodyMedium.merge(), modifier = Modifier.weight(1f))
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        termList.forEach {
                            FilterChip(onClick = {  }, label = { Text(it) }, selected = false,
                                trailingIcon = { Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = {  })) })
                        }
                    }
                    var text by remember { mutableStateOf("") }
                    TextField(value = text, onValueChange = { newTerm -> text = newTerm },
                        placeholder = { Text(stringResource(R.string.add_term)) }, keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (text.isNotBlank()) {
                                    val newWord = text.replace("\"", "").trim { it <= ' ' }
                                    if (newWord.isNotBlank() && newWord !in termList) {
                                        termList.add(newWord)
                                        text = ""
                                    }
                                }
                            }
                        ),
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
                    var filterDurationMinutes by remember { mutableStateOf((filter.minimalDurationFilter / 60).toString()) }
                    var markPlayedChecked by remember { mutableStateOf(filter.markExcludedPlayed) }
                    if (inexcl == ADLIncExc.EXCLUDE) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterDuration, onCheckedChange = { isChecked -> filterDuration = isChecked })
                            Text(text = stringResource(R.string.exclude_episodes_shorter_than), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (filterDuration) {
                                BasicTextField(value = filterDurationMinutes, onValueChange = { if (it.all { it.isDigit() }) filterDurationMinutes = it },
                                    textStyle = TextStyle(fontSize = 16.sp, color = textColor),
                                    modifier = Modifier.width(40.dp).height(30.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                )
                                Text(stringResource(R.string.time_minutes), color = textColor)
                            }
                        }
                    }
                    Row {
                        Checkbox(checked = markPlayedChecked, onCheckedChange = { isChecked -> markPlayedChecked = isChecked })
                        Text(text = stringResource(R.string.mark_excluded_episodes_played), style = MaterialTheme.typography.bodyMedium.merge())
                    }
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                        Button(onClick = {
                            if (inexcl == ADLIncExc.EXCLUDE) {
                                var minimalDuration = -1
                                if (filterDuration) minimalDuration = filterDurationMinutes.toInt()
                                val excludeFilter = toFilterString(termList)
                                onConfirmed(FeedAutoDownloadFilter(filter.includeFilterRaw, excludeFilter, minimalDuration, markPlayedChecked))
                            } else {
                                val includeFilter = toFilterString(termList)
                                onConfirmed(FeedAutoDownloadFilter(includeFilter, filter.excludeFilterRaw, filter.minimalDurationFilter, filter.markExcludedPlayed))
                            }
                            onDismiss()
                        }) { Text("Confirm") }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { onDismiss() }) { Text("Cancel") }
                    }
                }
            }
        }
    }

    fun setFeed(feed_: Feed) {
        feed = feed_
        if (feed!!.preferences == null) {
            feed!!.preferences = FeedPreferences(feed!!.id, false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
            persistFeedPreferences(feed!!)
        }
    }

    companion object {
        private val TAG: String = FeedSettingsFragment::class.simpleName ?: "Anonymous"
        val queueSettingOptions = listOf("Default", "Active", "None", "Custom")

        fun newInstance(feed: Feed): FeedSettingsFragment {
            val fragment = FeedSettingsFragment()
            fragment.setFeed(feed)
            return fragment
        }
    }
}
