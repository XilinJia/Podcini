package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.AppPreferences.isEnableAutodownload
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.AutoDeleteAction
import ac.mdiq.podcini.storage.model.Feed.AutoDownloadPolicy
import ac.mdiq.podcini.storage.model.Feed.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_NATURAL_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.FeedAutoDownloadFilter
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.util.Logd
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
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

class FeedSettingsFragment : Fragment() {
    @set:JvmName("setFeedProperty")
    private var feed by mutableStateOf<Feed?>(null)

    private var autoDeleteSummaryResId by mutableIntStateOf(R.string.global_default)
    private var curPrefQueue by mutableStateOf(feed?.queueTextExt ?: "Default")
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
        Logd(TAG, "fragment onCreateView")
        getVideoModePolicy()
        getAutoDeletePolicy()

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        val scrollState = rememberScrollState()
                        Column(modifier = Modifier.padding(innerPadding).padding(start = 20.dp, end = 16.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column {
                                Row(Modifier.fillMaxWidth()) {
                                    Icon(ImageVector.vectorResource(id = R.drawable.rounded_responsive_layout_24), "", tint = textColor)
                                    Spacer(modifier = Modifier.width(20.dp))
                                    Text(text = stringResource(R.string.use_wide_layout), style = CustomTextStyles.titleCustom, color = textColor)
                                    Spacer(modifier = Modifier.weight(1f))
                                    var checked by remember { mutableStateOf(feed?.useWideLayout == true) }
                                    Switch(checked = checked, modifier = Modifier.height(24.dp),
                                        onCheckedChange = {
                                            checked = it
                                            feed = upsertBlk(feed!!) { f -> f.useWideLayout = checked }
                                        }
                                    )
                                }
                                Text(text = stringResource(R.string.use_wide_layout_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                            if ((feed?.id ?: 0) > MAX_SYNTHETIC_ID) {
                                //                    refresh
                                Column {
                                    Row(Modifier.fillMaxWidth()) {
                                        Icon(ImageVector.vectorResource(id = R.drawable.ic_refresh), "", tint = textColor)
                                        Spacer(modifier = Modifier.width(20.dp))
                                        Text(text = stringResource(R.string.keep_updated), style = CustomTextStyles.titleCustom, color = textColor)
                                        Spacer(modifier = Modifier.weight(1f))
                                        var checked by remember { mutableStateOf(feed?.keepUpdated == true) }
                                        Switch(checked = checked, modifier = Modifier.height(24.dp),
                                            onCheckedChange = {
                                                checked = it
                                                feed = upsertBlk(feed!!) { f -> f.keepUpdated = checked }
                                            }
                                        )
                                    }
                                    Text(text = stringResource(R.string.keep_updated_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                                }
                            }
                            Column {
                                var showDialog by remember { mutableStateOf(false) }
                                var selectedOption by remember { mutableStateOf(feed?.audioTypeSetting?.tag ?: Feed.AudioType.SPEECH.tag) }
                                if (showDialog) SetAudioType(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                                Row(Modifier.fillMaxWidth()) {
                                    Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                                    Spacer(modifier = Modifier.width(20.dp))
                                    Text(text = stringResource(R.string.pref_feed_audio_type), style = CustomTextStyles.titleCustom, color = textColor,
                                        modifier = Modifier.clickable(onClick = {
                                            selectedOption = feed!!.audioTypeSetting.tag
                                            showDialog = true
                                        })
                                    )
                                }
                                Text(text = stringResource(R.string.pref_feed_audio_type_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                            if ((feed?.id ?: 0) >= MAX_NATURAL_SYNTHETIC_ID && feed?.hasVideoMedia == true) {
                                //                    video mode
                                Column {
                                    Row(Modifier.fillMaxWidth()) {
                                        var showDialog by remember { mutableStateOf(false) }
                                        if (showDialog) VideoModeDialog(initMode = feed?.videoModePolicy, onDismissRequest = { showDialog = false }) { mode ->
                                            feed = upsertBlk(feed!!) { it.videoModePolicy = mode }
                                            getVideoModePolicy()
                                        }
                                        Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                                        Spacer(modifier = Modifier.width(20.dp))
                                        Text(text = stringResource(R.string.feed_video_mode_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog = true }))
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
                                        Text(text = stringResource(R.string.pref_stream_over_download_title), style = CustomTextStyles.titleCustom, color = textColor)
                                        Spacer(modifier = Modifier.weight(1f))
                                        var checked by remember { mutableStateOf(feed?.prefStreamOverDownload == true) }
                                        Switch(checked = checked, modifier = Modifier.height(24.dp),
                                            onCheckedChange = {
                                                checked = it
                                                feed = upsertBlk(feed!!) { f -> f.prefStreamOverDownload = checked }
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
                                    var selectedOption by remember { mutableStateOf(feed?.audioQualitySetting?.tag ?: Feed.AVQuality.GLOBAL.tag) }
                                    if (showDialog) SetAudioQuality(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                                    Row(Modifier.fillMaxWidth()) {
                                        Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                                        Spacer(modifier = Modifier.width(20.dp))
                                        Text(text = stringResource(R.string.pref_feed_audio_quality), style = CustomTextStyles.titleCustom, color = textColor,
                                            modifier = Modifier.clickable(onClick = {
                                                selectedOption = feed!!.audioQualitySetting.tag
                                                showDialog = true
                                            })
                                        )
                                    }
                                    Text(text = stringResource(R.string.pref_feed_audio_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                                }
                                if (feed?.videoModePolicy != VideoMode.AUDIO_ONLY) {
                                    //                    video quality
                                    Column {
                                        var showDialog by remember { mutableStateOf(false) }
                                        var selectedOption by remember { mutableStateOf(feed?.videoQualitySetting?.tag ?: Feed.AVQuality.GLOBAL.tag) }
                                        if (showDialog) SetVideoQuality(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                                        Row(Modifier.fillMaxWidth()) {
                                            Icon(ImageVector.vectorResource(id = R.drawable.ic_videocam), "", tint = textColor)
                                            Spacer(modifier = Modifier.width(20.dp))
                                            Text(text = stringResource(R.string.pref_feed_video_quality), style = CustomTextStyles.titleCustom, color = textColor,
                                                modifier = Modifier.clickable(onClick = {
                                                    selectedOption = feed!!.videoQualitySetting.tag
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
                                curPrefQueue = feed?.queueTextExt ?: "Default"
                                var showDialog by remember { mutableStateOf(false) }
                                var selectedOption by remember { mutableStateOf(feed?.queueText ?: "Default") }
                                if (showDialog) SetAssociatedQueue(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                                Row(Modifier.fillMaxWidth()) {
                                    Icon(ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "", tint = textColor)
                                    Spacer(modifier = Modifier.width(20.dp))
                                    Text(text = stringResource(R.string.pref_feed_associated_queue), style = CustomTextStyles.titleCustom, color = textColor,
                                        modifier = Modifier.clickable(onClick = {
                                            selectedOption = feed?.queueText ?: "Default"
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
                                        Text(text = stringResource(R.string.audo_add_new_queue), style = CustomTextStyles.titleCustom, color = textColor)
                                        Spacer(modifier = Modifier.weight(1f))
                                        var checked by remember { mutableStateOf(feed?.autoAddNewToQueue != false) }
                                        Switch(checked = checked, modifier = Modifier.height(24.dp),
                                            onCheckedChange = {
                                                checked = it
                                                feed = upsertBlk(feed!!) { f -> f.autoAddNewToQueue = checked }
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
                                        Text(text = stringResource(R.string.auto_delete_label), style = CustomTextStyles.titleCustom, color = textColor,
                                            modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                                    }
                                    Text(text = stringResource(autoDeleteSummaryResId), style = MaterialTheme.typography.bodyMedium, color = textColor)
                                }
                            }
                            //                    tags
                            Column {
                                var showDialog by remember { mutableStateOf(false) }
                                if (showDialog) TagSettingDialog(feeds_ = listOf(feed!!), onDismiss = { showDialog = false })
                                Row(Modifier.fillMaxWidth()) {
                                    Icon(ImageVector.vectorResource(id = R.drawable.ic_tag), "", tint = textColor)
                                    Spacer(modifier = Modifier.width(20.dp))
                                    Text(text = stringResource(R.string.feed_tags_label), style = CustomTextStyles.titleCustom, color = textColor,
                                        modifier = Modifier.clickable(onClick = { showDialog = true }))
                                }
                                Text(text = stringResource(R.string.feed_tags_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                            //                    playback speed
                            Column {
                                Row(Modifier.fillMaxWidth()) {
                                    val showDialog = remember { mutableStateOf(false) }
                                    if (showDialog.value) PlaybackSpeedDialog(listOf(feed!!), initSpeed = feed!!.playSpeed, maxSpeed = 3f,
                                        onDismiss = { showDialog.value = false }) { newSpeed ->
                                        feed = upsertBlk(feed!!) { it.playSpeed = newSpeed }
                                    }
                                    Icon(ImageVector.vectorResource(id = R.drawable.ic_playback_speed), "", tint = textColor)
                                    Spacer(modifier = Modifier.width(20.dp))
                                    Text(text = stringResource(R.string.playback_speed), style = CustomTextStyles.titleCustom, color = textColor,
                                        modifier = Modifier.clickable(onClick = { showDialog.value = true }))
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
                                    Text(text = stringResource(R.string.pref_feed_skip), style = CustomTextStyles.titleCustom, color = textColor,
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
                                    Text(text = stringResource(R.string.feed_volume_adapdation), style = CustomTextStyles.titleCustom, color = textColor,
                                        modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                                }
                                Text(text = stringResource(R.string.feed_volume_adaptation_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                            }
                            //                    authentication
                            if ((feed?.id ?: 0) > 0 && feed?.isLocalFeed != true) {
                                Column {
                                    Row(Modifier.fillMaxWidth()) {
                                        val showDialog = remember { mutableStateOf(false) }
                                        if (showDialog.value) AuthenticationDialog(onDismiss = { showDialog.value = false })
                                        Icon(ImageVector.vectorResource(id = R.drawable.ic_key), "", tint = textColor)
                                        Spacer(modifier = Modifier.width(20.dp))
                                        Text(text = stringResource(R.string.authentication_label), style = CustomTextStyles.titleCustom, color = textColor,
                                            modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                                    }
                                    Text(text = stringResource(R.string.authentication_descr), style = MaterialTheme.typography.bodyMedium, color = textColor)
                                }
                            }
                            var autoDownloadChecked by remember { mutableStateOf(feed?.autoDownload == true) }
                            if (isEnableAutodownload && feed?.type != Feed.FeedType.YOUTUBE.name) {
                                //                    auto download
                                Column {
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(text = stringResource(R.string.auto_download_label), style = CustomTextStyles.titleCustom, color = textColor)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Switch(checked = autoDownloadChecked, modifier = Modifier.height(24.dp),
                                            onCheckedChange = {
                                                autoDownloadChecked = it
                                                feed = upsertBlk(feed!!) { f -> f.autoDownload = autoDownloadChecked }
                                            })
                                    }
                                    if (!isEnableAutodownload)
                                        Text(text = stringResource(R.string.auto_download_disabled_globally), style = MaterialTheme.typography.bodyMedium, color = textColor)
                                }
                            }
                            if (autoDownloadChecked) {
                                //                    auto download policy
                                Column(modifier = Modifier.padding(start = 20.dp)) {
                                    Row(Modifier.fillMaxWidth()) {
                                        val showDialog = remember { mutableStateOf(false) }
                                        if (showDialog.value) AutoDownloadPolicyDialog(onDismissRequest = { showDialog.value = false })
                                        Text(text = stringResource(R.string.feed_auto_download_policy), style = CustomTextStyles.titleCustom, color = textColor,
                                            modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                                    }
                                }
                                //                    episode cache
                                Column(modifier = Modifier.padding(start = 20.dp)) {
                                    Row(Modifier.fillMaxWidth()) {
                                        val showDialog = remember { mutableStateOf(false) }
                                        if (showDialog.value) SetEpisodesCacheDialog(onDismiss = { showDialog.value = false })
                                        Text(text = stringResource(R.string.pref_episode_cache_title), style = CustomTextStyles.titleCustom, color = textColor,
                                            modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                                    }
                                    Text(text = stringResource(R.string.pref_episode_cache_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                                }
                                //                    counting played
                                Column(modifier = Modifier.padding(start = 20.dp)) {
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(text = stringResource(R.string.pref_auto_download_counting_played_title), style = CustomTextStyles.titleCustom, color = textColor)
                                        Spacer(modifier = Modifier.weight(1f))
                                        var checked by remember { mutableStateOf(feed?.countingPlayed != false) }
                                        Switch(checked = checked, modifier = Modifier.height(24.dp),
                                            onCheckedChange = {
                                                checked = it
                                                feed = upsertBlk(feed!!) { f -> f.countingPlayed = checked }
                                            }
                                        )
                                    }
                                    Text(text = stringResource(R.string.pref_auto_download_counting_played_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                                }
                                //                    inclusive filter
                                Column(modifier = Modifier.padding(start = 20.dp)) {
                                    Row(Modifier.fillMaxWidth()) {
                                        val showDialog = remember { mutableStateOf(false) }
                                        if (showDialog.value) AutoDownloadFilterDialog(feed?.autoDownloadFilter!!, ADLIncExc.INCLUDE, onDismiss = { showDialog.value = false }) { filter ->
                                            feed = upsertBlk(feed!!) { it.autoDownloadFilter = filter }
                                        }
                                        Text(text = stringResource(R.string.episode_inclusive_filters_label), style = CustomTextStyles.titleCustom, color = textColor,
                                            modifier = Modifier.clickable(onClick = { showDialog.value = true })
                                        )
                                    }
                                    Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                                }
                                //                    exclusive filter
                                Column(modifier = Modifier.padding(start = 20.dp)) {
                                    Row(Modifier.fillMaxWidth()) {
                                        val showDialog = remember { mutableStateOf(false) }
                                        if (showDialog.value) AutoDownloadFilterDialog(feed?.autoDownloadFilter!!, ADLIncExc.EXCLUDE, onDismiss = { showDialog.value = false }) { filter ->
                                            feed = upsertBlk(feed!!) { it.autoDownloadFilter = filter }
                                        }
                                        Text(text = stringResource(R.string.episode_exclusive_filters_label), style = CustomTextStyles.titleCustom, color = textColor,
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
        }
//        if (feed != null) toolbar.subtitle = feed!!.title
        return composeView
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
//        _binding = null
        feed = null
        queues = null
        super.onDestroyView()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar(title = {
            Column {
                Text(text = stringResource(R.string.feed_settings_label), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (!feed?.title.isNullOrBlank()) Text(text = feed!!.title!!, fontSize = 16.sp)
            }
        },
            navigationIcon = { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
        )
    }

    private fun getVideoModePolicy() {
        when (feed?.videoModePolicy) {
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
            else -> {}
        }
    }
    private fun getAutoDeletePolicy() {
        when (feed?.autoDeleteAction) {
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
            else -> {}
        }
    }
    @Composable
    fun AutoDeleteDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(autoDeletePolicy) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        feed = upsertBlk(feed!!) { it.autoDeleteAction = action_ }
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

    @Composable
    fun VolumeAdaptionDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(feed?.volumeAdaptionSetting ?: VolumeAdaptionSetting.OFF) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeAdaptionSetting.entries.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = (item == selectedOption),
                                onCheckedChange = { _ ->
                                    Logd(TAG, "row clicked: $item $selectedOption")
                                    if (item != selectedOption) {
                                        onOptionSelected(item)
                                        feed = upsertBlk(feed!!) { it.volumeAdaptionSetting = item }
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

    @Composable
    fun AutoDownloadPolicyDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(feed?.autoDLPolicy ?: AutoDownloadPolicy.ONLY_NEW) }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.pref_custom_media_dir_title), style = CustomTextStyles.titleCustom) },
            text = {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoDownloadPolicy.entries.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = (item == selectedOption), onCheckedChange = { onOptionSelected(item) })
                            Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                        }
                        if (selectedOption == AutoDownloadPolicy.ONLY_NEW && item == selectedOption)
                            Row(Modifier.fillMaxWidth().padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                                var replaceChecked by remember { mutableStateOf(item.replace) }
                                Checkbox(checked = replaceChecked, onCheckedChange = {
                                    replaceChecked = it
                                    item.replace = it
                                })
                                Text(text = stringResource(R.string.replace), style = MaterialTheme.typography.bodyMedium.merge(), modifier = Modifier.padding(start = 16.dp))
                            }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Logd(TAG, "autoDLPolicy: ${selectedOption.name} ${selectedOption.replace}")
                    feed = upsertBlk(feed!!) { it.autoDLPolicy = selectedOption }
                    onDismissRequest()
                }) { Text(stringResource(R.string.confirm_label)) }
            },
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
        )


//        Dialog(onDismissRequest = { onDismissRequest() }) {
//            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
//                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
//                    AutoDownloadPolicy.entries.forEach { item ->
//                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
//                            Checkbox(checked = (item == selectedOption),
//                                onCheckedChange = {
//                                    Logd(TAG, "row clicked: $item $selectedOption")
//                                    if (item != selectedOption) {
//                                        onOptionSelected(item)
//                                        feed = upsertBlk(feed!!) { it.autoDLPolicy = item }
//                                        onDismissRequest()
//                                    }
//                                }
//                            )
//                            Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
//                        }
//                        if (item == AutoDownloadPolicy.ONLY_NEW) Row(Modifier.fillMaxWidth().padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
//                            var replaceChecked by remember { mutableStateOf(item.replace) }
//                            Checkbox(checked = replaceChecked, onCheckedChange = {
//                                replaceChecked = it
//                                item.replace = it
//                                feed = upsertBlk(feed!!) { it.autoDLPolicy = item }
//                            })
//                            Text(text = stringResource(R.string.replace), style = MaterialTheme.typography.bodyMedium.merge(), modifier = Modifier.padding(start = 16.dp))
//                        }
//                    }
//                }
//            }
//        }
    }

    @Composable
    fun SetEpisodesCacheDialog(onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var newCache by remember { mutableStateOf((feed?.autoDLMaxEpisodes ?: 1).toString()) }
                    TextField(value = newCache, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) newCache = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(R.string.max_episodes_cache)) })
                    Button(onClick = {
                        if (newCache.isNotEmpty()) {
                            feed = upsertBlk(feed!!) { it.autoDLMaxEpisodes = newCache.toIntOrNull() ?: 1 }
                            onDismiss()
                        }
                    }) { Text(stringResource(R.string.confirm_label)) }
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
                                            feed = upsertBlk(feed!!) { it.queueId = 0L }
                                            curPrefQueue = selected
                                            onDismissRequest()
                                        }
                                        "Active" -> {
                                            feed = upsertBlk(feed!!) { it.queueId = -1L }
                                            curPrefQueue = selected
                                            onDismissRequest()
                                        }
                                        "None" -> {
                                            feed = upsertBlk(feed!!) { it.queueId = -2L }
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
                        Spinner(items = queues!!.map { it.name }, selectedItem = feed?.queue?.name ?: "Default") { index ->
                            Logd(TAG, "Queue selected: $queues[index].name")
                            val q = queues!![index]
                            feed = upsertBlk(feed!!) { it.queue = q }
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
                    Feed.AudioType.entries.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = option.tag == selected,
                                onCheckedChange = { isChecked ->
                                    selected = option.tag
                                    if (isChecked) Logd(TAG, "$option is checked")
                                    val type = Feed.AudioType.fromTag(selected)
                                    feed = upsertBlk(feed!!) { it.audioType = type.code }
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
                    Feed.AVQuality.entries.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = option.tag == selected,
                                onCheckedChange = { isChecked ->
                                    selected = option.tag
                                    if (isChecked) Logd(TAG, "$option is checked")
                                    val type = Feed.AVQuality.fromTag(selected)
                                    feed = upsertBlk(feed!!) { it.audioQuality = type.code }
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
                    Feed.AVQuality.entries.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = option.tag == selected,
                                onCheckedChange = { isChecked ->
                                    selected = option.tag
                                    if (isChecked) Logd(TAG, "$option is checked")
                                    val type = Feed.AVQuality.fromTag(selected)
                                    feed = upsertBlk(feed!!) { it.videoQuality = type.code }
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
                    val oldName = feed?.username?:""
                    var newName by remember { mutableStateOf(oldName) }
                    TextField(value = newName, onValueChange = { newName = it }, label = { Text("Username") })
                    val oldPW = feed?.password?:""
                    var newPW by remember { mutableStateOf(oldPW) }
                    TextField(value = newPW, onValueChange = { newPW = it }, label = { Text("Password") })
                    Button(onClick = {
                        if (newName.isNotEmpty() && oldName != newName) {
                            feed = upsertBlk(feed!!) {
                                it.username = newName
                                it.password = newPW
                            }
                            Thread({ runOnce(requireContext(), feed) }, "RefreshAfterCredentialChange").start()
                            onDismiss()
                        }
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }

    @Composable
    fun AutoSkipDialog(onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var intro by remember { mutableStateOf((feed?.introSkip ?: 0).toString()) }
                    TextField(value = intro, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) intro = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Skip first (seconds)") })
                    var ending by remember { mutableStateOf((feed?.endingSkip ?: 0).toString()) }
                    TextField(value = ending, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) ending = it  },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Skip last (seconds)") })
                    Button(onClick = {
                        if (intro.isNotEmpty() || ending.isNotEmpty()) {
                            feed = upsertBlk(feed!!) {
                                it.introSkip = intro.toIntOrNull() ?: 0
                                it.endingSkip = ending.toIntOrNull() ?: 0
                            }
                            onDismiss()
                        }
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
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
                    var termList = remember { if (inexcl == ADLIncExc.EXCLUDE) filter.excludeTerms.toMutableStateList() else filter.includeTerms.toMutableStateList() }
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
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
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
                        }) { Text(stringResource(R.string.confirm_label)) }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel_label)) }
                    }
                }
            }
        }
    }

    @JvmName("setFeedFunction")
    fun setFeed(feed_: Feed) {
        feed = feed_
//        if (feed!!.preferences == null) {
//            feed!!.preferences = FeedPreferences(feed!!.id, false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
//            persistFeedPreferences(feed!!)
//        }
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
