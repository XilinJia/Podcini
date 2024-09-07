package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AutodownloadFilterDialogBinding
import ac.mdiq.podcini.databinding.FeedsettingsBinding
import ac.mdiq.podcini.databinding.PlaybackSpeedFeedSettingDialogBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.base.VideoMode.Companion.videoModeTags
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.storage.database.Feeds.persistFeedPreferences
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.FeedPreferences.*
import ac.mdiq.podcini.storage.model.FeedPreferences.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.ui.adapter.SimpleChipAdapter
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.Spinner
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
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
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
                val textColor = MaterialTheme.colors.onSurface
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    //                    refresh
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
                                    feed = upsertBlk(feed!!) { f ->
                                        f.preferences?.keepUpdated = checked
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
                    if (feed?.hasVideoMedia == true) {
                        //                    prefer play audio only
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(
                                    text = stringResource(R.string.feed_video_mode_label),
                                    style = MaterialTheme.typography.h6,
                                    color = textColor,
                                    modifier = Modifier.clickable(onClick = {
                                        val composeView = ComposeView(requireContext()).apply {
                                            setContent {
                                                val showDialog = remember { mutableStateOf(true) }
                                                CustomTheme(requireContext()) {
                                                    VideoModeDialog(showDialog.value, onDismissRequest = { showDialog.value = false })
                                                }
                                            }
                                        }
                                        (view as? ViewGroup)?.addView(composeView)
                                    })
                                )
                            }
                            Text(
                                text = stringResource(videoModeSummaryResId),
                                style = MaterialTheme.typography.body2,
                                color = textColor
                            )
                        }
                    }
                    if (feed?.type != Feed.FeedType.YOUTUBE.name) {
                        //                    prefer streaming
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
                                var checked by remember {
                                    mutableStateOf(feed?.preferences?.prefStreamOverDownload ?: false)
                                }
                                Switch(
                                    checked = checked,
                                    modifier = Modifier.height(24.dp),
                                    onCheckedChange = {
                                        checked = it
                                        feed = upsertBlk(feed!!) { f ->
                                            f.preferences?.prefStreamOverDownload = checked
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
                    }
                    //                    associated queue
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
                    //                    auto add new to queue
                    if (curPrefQueue != "None") {
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Icon(ImageVector.vectorResource(id = androidx.media3.session.R.drawable.media3_icon_queue_add),
                                    "",
                                    tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(
                                    text = stringResource(R.string.audo_add_new_queue),
                                    style = MaterialTheme.typography.h6,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                var checked by remember { mutableStateOf(feed?.preferences?.autoAddNewToQueue ?: true) }
                                Switch(
                                    checked = checked,
                                    modifier = Modifier.height(24.dp),
                                    onCheckedChange = {
                                        checked = it
                                        feed = upsertBlk(feed!!) { f ->
                                            f.preferences?.autoAddNewToQueue = checked
                                        }
                                    }
                                )
                            }
                            Text(
                                text = stringResource(R.string.audo_add_new_queue_summary),
                                style = MaterialTheme.typography.body2,
                                color = textColor
                            )
                        }
                    }
                    if (feed?.type != Feed.FeedType.YOUTUBE.name) {
                        //                    auto delete
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
                    //                    tags
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_tag), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = stringResource(R.string.feed_tags_label),
                                style = MaterialTheme.typography.h6,
                                color = textColor,
                                modifier = Modifier.clickable(onClick = {
                                    val dialog = TagSettingsDialog.newInstance(listOf(feed!!))
                                    dialog.show(parentFragmentManager, TagSettingsDialog.TAG)
                                })
                            )
                        }
                        Text(
                            text = stringResource(R.string.feed_tags_summary),
                            style = MaterialTheme.typography.body2,
                            color = textColor
                        )
                    }
                    //                    playback speed
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_playback_speed), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = stringResource(R.string.playback_speed),
                                style = MaterialTheme.typography.h6,
                                color = textColor,
                                modifier = Modifier.clickable(onClick = {
                                    PlaybackSpeedDialog().show()
                                })
                            )
                        }
                        Text(
                            text = stringResource(R.string.pref_feed_playback_speed_sum),
                            style = MaterialTheme.typography.body2,
                            color = textColor
                        )
                    }
                    //                    auto skip
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_skip_24dp), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = stringResource(R.string.pref_feed_skip),
                                style = MaterialTheme.typography.h6,
                                color = textColor,
                                modifier = Modifier.clickable(onClick = {
                                    val composeView = ComposeView(requireContext()).apply {
                                        setContent {
                                            val showDialog = remember { mutableStateOf(true) }
                                            CustomTheme(requireContext()) {
                                                AutoSkipDialog(showDialog.value, onDismiss = { showDialog.value = false })
                                            }
                                        }
                                    }
                                    (view as? ViewGroup)?.addView(composeView)
                                })
                            )
                        }
                        Text(
                            text = stringResource(R.string.pref_feed_skip_sum),
                            style = MaterialTheme.typography.body2,
                            color = textColor
                        )
                    }
                    //                    volume adaption
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_volume_adaption), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = stringResource(R.string.feed_volume_adapdation),
                                style = MaterialTheme.typography.h6,
                                color = textColor,
                                modifier = Modifier.clickable(onClick = {
                                    val composeView = ComposeView(requireContext()).apply {
                                        setContent {
                                            val showDialog = remember { mutableStateOf(true) }
                                            CustomTheme(requireContext()) {
                                                VolumeAdaptionDialog(showDialog.value, onDismissRequest = { showDialog.value = false })
                                            }
                                        }
                                    }
                                    (view as? ViewGroup)?.addView(composeView)
                                })
                            )
                        }
                        Text(
                            text = stringResource(R.string.feed_volume_adaptation_summary),
                            style = MaterialTheme.typography.body2,
                            color = textColor
                        )
                    }
                    //                    authentication
                    if (feed?.isLocalFeed != true) {
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_key), "", tint = textColor)
                                Spacer(modifier = Modifier.width(20.dp))
                                Text(
                                    text = stringResource(R.string.authentication_label),
                                    style = MaterialTheme.typography.h6,
                                    color = textColor,
                                    modifier = Modifier.clickable(onClick = {
                                        val composeView = ComposeView(requireContext()).apply {
                                            setContent {
                                                val showDialog = remember { mutableStateOf(true) }
                                                CustomTheme(requireContext()) {
                                                    AuthenticationDialog(showDialog.value,
                                                        onDismiss = { showDialog.value = false })
                                                }
                                            }
                                        }
                                        (view as? ViewGroup)?.addView(composeView)
                                    })
                                )
                            }
                            Text(
                                text = stringResource(R.string.authentication_descr),
                                style = MaterialTheme.typography.body2,
                                color = textColor
                            )
                        }
                    }
                    if (isEnableAutodownload && feed?.type != Feed.FeedType.YOUTUBE.name) {
                        //                    auto download
                        var audoDownloadChecked by remember { mutableStateOf(feed?.preferences?.autoDownload ?: true) }
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.auto_download_label),
                                    style = MaterialTheme.typography.h6,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(
                                    checked = audoDownloadChecked,
                                    modifier = Modifier.height(24.dp),
                                    onCheckedChange = {
                                        audoDownloadChecked = it
                                        feed = upsertBlk(feed!!) { f ->
                                            f.preferences?.autoDownload = audoDownloadChecked
                                        }
                                    }
                                )
                            }
                            if (!isEnableAutodownload) {
                                Text(
                                    text = stringResource(R.string.auto_download_disabled_globally),
                                    style = MaterialTheme.typography.body2,
                                    color = textColor
                                )
                            }
                        }
                        if (audoDownloadChecked) {
                            //                    auto download policy
                            Column (modifier = Modifier.padding(start = 20.dp)){
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.feed_auto_download_policy),
                                        style = MaterialTheme.typography.h6,
                                        color = textColor,
                                        modifier = Modifier.clickable(onClick = {
                                            val composeView = ComposeView(requireContext()).apply {
                                                setContent {
                                                    val showDialog = remember { mutableStateOf(true) }
                                                    CustomTheme(requireContext()) {
                                                        AutoDownloadPolicyDialog(showDialog.value,
                                                            onDismissRequest = { showDialog.value = false })
                                                    }
                                                }
                                            }
                                            (view as? ViewGroup)?.addView(composeView)
                                        })
                                    )
                                }
                            }
                            //                    episode cache
                            Column (modifier = Modifier.padding(start = 20.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.pref_episode_cache_title),
                                        style = MaterialTheme.typography.h6,
                                        color = textColor,
                                        modifier = Modifier.clickable(onClick = {
                                            val composeView = ComposeView(requireContext()).apply {
                                                setContent {
                                                    val showDialog = remember { mutableStateOf(true) }
                                                    CustomTheme(requireContext()) {
                                                        SetEpisodesCacheDialog(showDialog.value,
                                                            onDismiss = { showDialog.value = false })
                                                    }
                                                }
                                            }
                                            (view as? ViewGroup)?.addView(composeView)
                                        })
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.pref_episode_cache_summary),
                                    style = MaterialTheme.typography.body2,
                                    color = textColor
                                )
                            }
                            //                    counting played
                            Column (modifier = Modifier.padding(start = 20.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.pref_auto_download_counting_played_title),
                                        style = MaterialTheme.typography.h6,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    var checked by remember {
                                        mutableStateOf(feed?.preferences?.countingPlayed ?: true)
                                    }
                                    Switch(
                                        checked = checked,
                                        modifier = Modifier.height(24.dp),
                                        onCheckedChange = {
                                            checked = it
                                            feed = upsertBlk(feed!!) { f ->
                                                f.preferences?.countingPlayed = checked
                                            }
                                        }
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.pref_auto_download_counting_played_summary),
                                    style = MaterialTheme.typography.body2,
                                    color = textColor
                                )
                            }
                            //                    inclusive filter
                            Column (modifier = Modifier.padding(start = 20.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.episode_inclusive_filters_label),
                                        style = MaterialTheme.typography.h6,
                                        color = textColor,
                                        modifier = Modifier.clickable(onClick = {
                                            object : AutoDownloadFilterPrefDialog(requireContext(),
                                                feed?.preferences!!.autoDownloadFilter!!,
                                                1) {
                                                @UnstableApi
                                                override fun onConfirmed(filter: FeedAutoDownloadFilter) {
                                                    feed = upsertBlk(feed!!) {
                                                        it.preferences?.autoDownloadFilter = filter
                                                    }
                                                }
                                            }.show()
                                        })
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.episode_filters_description),
                                    style = MaterialTheme.typography.body2,
                                    color = textColor
                                )
                            }
                            //                    exclusive filter
                            Column (modifier = Modifier.padding(start = 20.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.episode_exclusive_filters_label),
                                        style = MaterialTheme.typography.h6,
                                        color = textColor,
                                        modifier = Modifier.clickable(onClick = {
                                            object : AutoDownloadFilterPrefDialog(requireContext(),
                                                feed?.preferences!!.autoDownloadFilter!!,
                                                -1) {
                                                @UnstableApi
                                                override fun onConfirmed(filter: FeedAutoDownloadFilter) {
                                                    feed = upsertBlk(feed!!) {
                                                        it.preferences?.autoDownloadFilter = filter
                                                    }
                                                }
                                            }.show()
                                        })
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.episode_filters_description),
                                    style = MaterialTheme.typography.body2,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
        if (feed != null) {
            toolbar.subtitle = feed!!.title
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
    fun VideoModeDialog(showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(videoMode) }
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
                            videoModeTags.forEach { text ->
                                Row(Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
    fun VolumeAdaptionDialog(showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(feed?.preferences?.volumeAdaptionSetting ?: VolumeAdaptionSetting.OFF) }
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
                            VolumeAdaptionSetting.entries.forEach { item ->
                                Row(Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = (item == selectedOption),
                                        onCheckedChange = { _ ->
                                            Logd(TAG, "row clicked: $item $selectedOption")
                                            if (item != selectedOption) {
                                                onOptionSelected(item)
                                                feed = upsertBlk(feed!!) {
                                                    it.preferences?.volumeAdaptionSetting = item
                                                }
                                                onDismissRequest()
                                            }
                                        }
                                    )
                                    Text(
                                        text = stringResource(item.resId),
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
    fun AutoDownloadPolicyDialog(showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(feed?.preferences?.autoDLPolicy ?: AutoDownloadPolicy.ONLY_NEW) }
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
                            AutoDownloadPolicy.entries.forEach { item ->
                                Row(Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = (item == selectedOption),
                                        onCheckedChange = {
                                            Logd(TAG, "row clicked: $item $selectedOption")
                                            if (item != selectedOption) {
                                                onOptionSelected(item)
                                                feed = upsertBlk(feed!!) {
                                                    it.preferences?.autoDLPolicy = item
                                                }
//                                                getAutoDeletePolicy()
                                                onDismissRequest()
                                            }
                                        }
                                    )
                                    Text(
                                        text = stringResource(item.resId),
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
    fun SetEpisodesCacheDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier
                    .wrapContentSize(align = Alignment.Center)
                    .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var newCache by remember { mutableStateOf((feed?.preferences?.autoDLMaxEpisodes ?: 1).toString()) }
                        TextField(value = newCache,
                            onValueChange = { if (it.toIntOrNull() != null) newCache = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = PositiveIntegerTransform(),
                            label = { Text("Max episodes allowed") }
                        )
                        Button(onClick = {
                            if (newCache.isNotEmpty()) {
                                runOnIOScope {
                                    feed = upsertBlk(feed!!) {
                                        it.preferences?.autoDLMaxEpisodes = newCache.toIntOrNull() ?: 1
                                    }
                                }
                                onDismiss()
                            }
                        }) {
                            Text("Confirm")
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

    @Composable
    fun AuthenticationDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier
                    .wrapContentSize(align = Alignment.Center)
                    .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val oldName = feed?.preferences?.username?:""
                        var newName by remember { mutableStateOf(oldName) }
                        TextField(value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Username") }
                        )
                        val oldPW = feed?.preferences?.password?:""
                        var newPW by remember { mutableStateOf(oldPW) }
                        TextField(value = newPW,
                            onValueChange = { newPW = it },
                            label = { Text("Password") }
                        )
                        Button(onClick = {
                            if (newName.isNotEmpty() && oldName != newName) {
                                feed = upsertBlk(feed!!) {
                                    it.preferences?.username = newName
                                    it.preferences?.password = newPW
                                }
                                Thread({ runOnce(requireContext(), feed) }, "RefreshAfterCredentialChange").start()
                                onDismiss()
                            }
                        }) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AutoSkipDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier
                    .wrapContentSize(align = Alignment.Center)
                    .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var intro by remember { mutableStateOf((feed?.preferences?.introSkip ?: 0).toString()) }
                        TextField(value = intro,
                            onValueChange = { if (it.toIntOrNull() != null) intro = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = PositiveIntegerTransform(),
                            label = { Text("Skip first (seconds)") }
                        )
                        var ending by remember { mutableStateOf((feed?.preferences?.endingSkip ?: 0).toString()) }
                        TextField(value = ending,
                            onValueChange = { if (it.toIntOrNull() != null) ending = it  },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = PositiveIntegerTransform(),
                            label = { Text("Skip last (seconds)") }
                        )
                        Button(onClick = {
                            if (intro.isNotEmpty() || ending.isNotEmpty()) {
                                feed = upsertBlk(feed!!) {
                                    it.preferences?.introSkip = intro.toIntOrNull() ?: 0
                                    it.preferences?.endingSkip = ending.toIntOrNull() ?: 0
                                }
                                onDismiss()
                            }
                        }) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }

    fun PlaybackSpeedDialog(): AlertDialog {
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
        return MaterialAlertDialogBuilder(requireContext())
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
            .create()
    }

    class PositiveIntegerTransform : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText {
            val trimmedText = text.text.filter { it.isDigit() }
            val transformedText = if (trimmedText.isNotEmpty() && trimmedText.toInt() > 0) trimmedText else ""
            return TransformedText(AnnotatedString(transformedText), OffsetMapping.Identity)
        }
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
            setPositiveButton(R.string.confirm_label) { _: DialogInterface, _: Int ->
//                this.onConfirmClick(dialog, which)
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
