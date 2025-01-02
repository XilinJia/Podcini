package ac.mdiq.podcini.preferences.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.prefPlaybackSpeed
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.fallbackSpeed
import ac.mdiq.podcini.preferences.UserPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.UserPreferences.getPref
import ac.mdiq.podcini.preferences.UserPreferences.putPref
import ac.mdiq.podcini.preferences.UserPreferences.rewindSecs
import ac.mdiq.podcini.preferences.UserPreferences.setVideoMode
import ac.mdiq.podcini.preferences.UserPreferences.speedforwardSpeed
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.storage.database.Queues.EnqueueLocation
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.PlaybackSpeedDialog
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchPrefRow
import ac.mdiq.podcini.ui.compose.VideoModeDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.round

enum class PrefHardwareForwardButton(val res: Int, val res1: Int) {
    FF(R.string.button_action_fast_forward, R.string.keycode_media_fast_forward),
    RW(R.string.button_action_rewind, R.string.keycode_media_rewind),
    SKIP(R.string.button_action_skip_episode, R.string.keycode_media_next),
    START(R.string.button_action_restart_episode, R.string.keycode_media_previous);
}

@Composable
fun PlaybackPreferencesScreen() {
    val textColor = MaterialTheme.colorScheme.onSurface
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
        Text(stringResource(R.string.interruptions), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        var prefUnpauseOnHeadsetReconnect by remember { mutableStateOf(getPref(UserPreferences.Prefs.prefPauseOnHeadsetDisconnect, true)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pref_pauseOnHeadsetDisconnect_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pref_pauseOnDisconnect_sum), color = textColor)
            }
            Switch(checked = prefUnpauseOnHeadsetReconnect, onCheckedChange = {
                prefUnpauseOnHeadsetReconnect = it
                putPref(UserPreferences.Prefs.prefPauseOnHeadsetDisconnect, it)
            })
        }
        if (prefUnpauseOnHeadsetReconnect) {
            TitleSummarySwitchPrefRow(R.string.pref_unpauseOnHeadsetReconnect_title, R.string.pref_unpauseOnHeadsetReconnect_sum, UserPreferences.Prefs.prefUnpauseOnHeadsetReconnect.name)
            TitleSummarySwitchPrefRow(R.string.pref_unpauseOnBluetoothReconnect_title, R.string.pref_unpauseOnBluetoothReconnect_sum, UserPreferences.Prefs.prefUnpauseOnBluetoothReconnect.name)
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        Text(stringResource(R.string.playback_control), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pref_fast_forward), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                var interval by remember { mutableStateOf(fastForwardSecs.toString()) }
                var showIcon by remember { mutableStateOf(false) }
                TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("seconds") },
                    singleLine = true, modifier = Modifier.weight(0.6f),
                    onValueChange = {
                        if (it.isEmpty() || it.toIntOrNull() != null) interval = it
                        if (it.toIntOrNull() != null) showIcon = true
                    },
                    trailingIcon = {
                        if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                            modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                if (interval.isNotBlank()) {
                                    fastForwardSecs = interval.toInt()
                                    showIcon = false
                                }
                            }))
                    })
            }
            Text(stringResource(R.string.pref_fast_forward_sum), color = textColor)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pref_rewind), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                var interval by remember { mutableStateOf(rewindSecs.toString()) }
                var showIcon by remember { mutableStateOf(false) }
                TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("seconds") },
                    singleLine = true, modifier = Modifier.weight(0.6f),
                    onValueChange = {
                        if (it.isEmpty() || it.toIntOrNull() != null) interval = it
                        if (it.toIntOrNull() != null) showIcon = true
                    },
                    trailingIcon = {
                        if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                            modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                if (interval.isNotBlank()) {
                                    rewindSecs = interval.toInt()
                                    showIcon = false
                                }
                            }))
                    })
            }
            Text(stringResource(R.string.pref_rewind_sum), color = textColor)
        }
        var showSpeedDialog by remember { mutableStateOf(false) }
        if (showSpeedDialog) PlaybackSpeedDialog(listOf(), initSpeed = prefPlaybackSpeed, maxSpeed = 3f, isGlobal = true,
            onDismiss = { showSpeedDialog = false }) { speed -> UserPreferences.setPlaybackSpeed(speed) }
        TitleSummaryActionColumn(R.string.playback_speed, R.string.pref_playback_speed_sum) { showSpeedDialog = true }
        var showFBSpeedDialog by remember { mutableStateOf(false) }
        if (showFBSpeedDialog) PlaybackSpeedDialog(listOf(), initSpeed = fallbackSpeed, maxSpeed = 3f, isGlobal = true,
            onDismiss = { showFBSpeedDialog = false }) { speed ->
            val speed_ = when {
                speed < 0.0f -> 0.0f
                speed > 3.0f -> 3.0f
                else -> 0f
            }
            fallbackSpeed = round(100 * speed_) / 100f
        }
        TitleSummaryActionColumn(R.string.pref_fallback_speed, R.string.pref_fallback_speed_sum) { showFBSpeedDialog = true }
        TitleSummarySwitchPrefRow(R.string.pref_playback_time_respects_speed_title, R.string.pref_playback_time_respects_speed_sum, UserPreferences.Prefs.prefPlaybackTimeRespectsSpeed.name)
        var showFFSpeedDialog by remember { mutableStateOf(false) }
        if (showFFSpeedDialog) PlaybackSpeedDialog(listOf(), initSpeed = speedforwardSpeed, maxSpeed = 10f, isGlobal = true,
            onDismiss = { showFFSpeedDialog = false }) { speed ->
            val speed_ = when {
                speed < 0.0f -> 0.0f
                speed > 10.0f -> 10.0f
                else -> 0f
            }
            speedforwardSpeed = round(10 * speed_) / 10
        }
        TitleSummaryActionColumn(R.string.pref_speed_forward, R.string.pref_speed_forward_sum) { showFFSpeedDialog = true }
        TitleSummarySwitchPrefRow(R.string.pref_stream_over_download_title, R.string.pref_stream_over_download_sum, UserPreferences.Prefs.prefStreamOverDownload.name)
        TitleSummarySwitchPrefRow(R.string.pref_low_quality_on_mobile_title, R.string.pref_low_quality_on_mobile_sum, UserPreferences.Prefs.prefLowQualityOnMobile.name)
        TitleSummarySwitchPrefRow(R.string.pref_use_adaptive_progress_title, R.string.pref_use_adaptive_progress_sum, UserPreferences.Prefs.prefUseAdaptiveProgressUpdate.name, true)
        var showVideoModeDialog by remember { mutableStateOf(false) }
        if (showVideoModeDialog) VideoModeDialog(initMode =  VideoMode.fromCode(videoPlayMode), onDismissRequest = { showVideoModeDialog = false }) { mode -> setVideoMode(mode.code) }
        TitleSummaryActionColumn(R.string.pref_playback_video_mode, R.string.pref_playback_video_mode_sum) { showVideoModeDialog = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        Text(stringResource(R.string.reassign_hardware_buttons), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        var showHardwareForwardButtonOptions by remember { mutableStateOf(false) }
        var tempFFSelectedOption by remember { mutableStateOf(R.string.keycode_media_fast_forward) }
        TitleSummaryActionColumn(R.string.pref_hardware_forward_button_title, R.string.pref_hardware_forward_button_summary) { showHardwareForwardButtonOptions = true }
        if (showHardwareForwardButtonOptions) {
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showHardwareForwardButtonOptions = false },
                title = { Text(stringResource(R.string.pref_hardware_forward_button_title), style = CustomTextStyles.titleCustom) },
                text = {
                    Column {
                        PrefHardwareForwardButton.entries.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp).clickable { tempFFSelectedOption = option.res1 }) {
                                Checkbox(checked = tempFFSelectedOption == option.res1, onCheckedChange = { tempFFSelectedOption = option.res1 })
                                Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        putPref(UserPreferences.Prefs.prefHardwareForwardButton, tempFFSelectedOption.toString())
                        showHardwareForwardButtonOptions = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showHardwareForwardButtonOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        var showHardwarePreviousButtonOptions by remember { mutableStateOf(false) }
        var tempPRSelectedOption by remember { mutableStateOf(R.string.keycode_media_rewind) }
        TitleSummaryActionColumn(R.string.pref_hardware_previous_button_title, R.string.pref_hardware_previous_button_summary) { showHardwarePreviousButtonOptions = true }
        if (showHardwarePreviousButtonOptions) {
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showHardwarePreviousButtonOptions = false },
                title = { Text(stringResource(R.string.pref_hardware_previous_button_title), style = CustomTextStyles.titleCustom) },
                text = {
                    Column {
                        PrefHardwareForwardButton.entries.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp).clickable { tempPRSelectedOption = option.res1 }) {
                                Checkbox(checked = tempPRSelectedOption == option.res1, onCheckedChange = { tempPRSelectedOption = option.res1 })
                                Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        putPref(UserPreferences.Prefs.prefHardwarePreviousButton, tempPRSelectedOption.toString())
                        showHardwarePreviousButtonOptions = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showHardwarePreviousButtonOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        Text(stringResource(R.string.queue_label), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        TitleSummarySwitchPrefRow(R.string.pref_enqueue_downloaded_title, R.string.pref_enqueue_downloaded_summary, UserPreferences.Prefs.prefEnqueueDownloaded.name, true)
        var showEnqueueLocationOptions by remember { mutableStateOf(false) }
        var tempLocationOption by remember { mutableStateOf(EnqueueLocation.BACK.name) }
        TitleSummaryActionColumn(R.string.pref_enqueue_location_title, R.string.pref_enqueue_location_sum) { showEnqueueLocationOptions = true }
        if (showEnqueueLocationOptions) {
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showEnqueueLocationOptions = false },
                title = { Text(stringResource(R.string.pref_hardware_previous_button_title), style = CustomTextStyles.titleCustom) },
                text = {
                    Column {
                        EnqueueLocation.entries.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                                .clickable { tempLocationOption = option.name }) {
                                Checkbox(checked = tempLocationOption == option.name, onCheckedChange = { tempLocationOption = option.name })
                                Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        putPref(UserPreferences.Prefs.prefEnqueueLocation, tempLocationOption)
                        showEnqueueLocationOptions = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showEnqueueLocationOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        TitleSummarySwitchPrefRow(R.string.pref_followQueue_title, R.string.pref_followQueue_sum, UserPreferences.Prefs.prefFollowQueue.name, true)
        TitleSummarySwitchPrefRow(R.string.pref_skip_keeps_episodes_title, R.string.pref_skip_keeps_episodes_sum, UserPreferences.Prefs.prefSkipKeepsEpisode.name)
        TitleSummarySwitchPrefRow(R.string.pref_mark_played_removes_from_queue_title, R.string.pref_mark_played_removes_from_queue_sum, UserPreferences.Prefs.prefRemoveFromQueueMarkedPlayed.name, true)
    }
}
