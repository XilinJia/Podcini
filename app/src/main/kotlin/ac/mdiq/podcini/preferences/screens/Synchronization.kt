package ac.mdiq.podcini.preferences.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.SynchronizationCredentials
import ac.mdiq.podcini.net.sync.SynchronizationProviderViewData
import ac.mdiq.podcini.net.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.setSelectedSyncProvider
import ac.mdiq.podcini.net.sync.SynchronizationSettings.setWifiSyncEnabled
import ac.mdiq.podcini.net.sync.SynchronizationSettings.wifiSyncEnabledKey
import ac.mdiq.podcini.net.sync.nextcloud.NextcloudLoginFlow
import ac.mdiq.podcini.net.sync.nextcloud.NextcloudLoginFlow.AuthenticationCallback
import ac.mdiq.podcini.net.sync.wifi.WifiSyncService.Companion.startInstantSync
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.IconTitleSummaryActionRow
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun SynchronizationPreferencesScreen(activity: PreferenceActivity) {
    var showToast by remember { mutableStateOf(false) }
    var toastMassege by remember { mutableStateOf("") }

    val selectedSyncProviderKey: String = SynchronizationSettings.selectedSyncProviderKey?:""
    var selectedProvider by remember { mutableStateOf(SynchronizationProviderViewData.fromIdentifier(selectedSyncProviderKey)) }
    var loggedIn by remember { mutableStateOf(isProviderConnected) }

    fun updateLastSyncReport(successful: Boolean, lastTime: Long) {
        val status = String.format("%1\$s (%2\$s)", activity.getString(if (successful) R.string.gpodnetsync_pref_report_successful else R.string.gpodnetsync_pref_report_failed),
            DateUtils.getRelativeDateTimeString(activity, lastTime, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_SHOW_TIME))
//            supportActionBar!!.subtitle = status
    }
    fun syncStatusChanged(event: FlowEvent.SyncServiceEvent) {
        if (!isProviderConnected && !wifiSyncEnabledKey) return
        loggedIn = isProviderConnected
        if (event.messageResId == R.string.sync_status_error || event.messageResId == R.string.sync_status_success)
            updateLastSyncReport(SynchronizationSettings.isLastSyncSuccessful, SynchronizationSettings.lastSyncAttempt)
//            else supportActionBar!!.setSubtitle(event.messageResId)
    }

    if (showToast) CustomToast(message = toastMassege, onDismiss = { showToast = false })

    @Composable
    fun NextcloudAuthenticationDialog(onDismissRequest: ()->Unit) {
        var nextcloudLoginFlow = remember<NextcloudLoginFlow?> { null }
        var showUrlEdit by remember { mutableStateOf(true) }
        var serverUrlText by remember { mutableStateOf(getPref(AppPrefs.pref_nextcloud_server_address, "")!!) }
        var errorText by remember { mutableStateOf("") }
        var showChooseHost by remember { mutableStateOf(serverUrlText.isNotBlank()) }

        val nextCloudAuthCallback = object : AuthenticationCallback {
            override fun onNextcloudAuthenticated(server: String, username: String, password: String) {
                Logd("NextcloudAuthenticationDialog", "onNextcloudAuthenticated: $server")
                setSelectedSyncProvider(SynchronizationProviderViewData.NEXTCLOUD_GPODDER)
                SynchronizationCredentials.clear(activity)
                SynchronizationCredentials.password = password
                SynchronizationCredentials.hosturl = server
                SynchronizationCredentials.username = username
                SyncService.fullSync(activity)
                loggedIn = isProviderConnected
                onDismissRequest()
            }
            override fun onNextcloudAuthError(errorMessage: String?) {
                errorText = errorMessage ?: ""
                showChooseHost = true
                showUrlEdit = true
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) nextcloudLoginFlow?.poll() }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.gpodnetauth_login_butLabel), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    Text(stringResource(R.string.synchronization_host_explanation))
                    if (showUrlEdit) TextField(value = serverUrlText, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.synchronization_host_label)) },
                        onValueChange = {
                            serverUrlText = it
                            showChooseHost = serverUrlText.isNotBlank()
                        }
                    )
                    Text(stringResource(R.string.synchronization_nextcloud_authenticate_browser))
                    if (errorText.isNotBlank()) Text(errorText)
                }
            },
            confirmButton = {
                if (showChooseHost) TextButton(onClick = {
                    putPref(AppPrefs.pref_nextcloud_server_address, serverUrlText)
                    nextcloudLoginFlow = NextcloudLoginFlow(getHttpClient(), serverUrlText, activity, nextCloudAuthCallback)
                    errorText = ""
                    showChooseHost = false
                    nextcloudLoginFlow.start()
//                        onDismissRequest()
                }) { Text(stringResource(R.string.proceed_to_login_butLabel)) }
            },
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var showNextCloudAuthDialog by remember { mutableStateOf(false) }
    if (showNextCloudAuthDialog) NextcloudAuthenticationDialog { showNextCloudAuthDialog = false }

    @Composable
    fun ChooseProviderAndLoginDialog(onDismissRequest: ()->Unit) {
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.dialog_choose_sync_service_title), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    SynchronizationProviderViewData.entries.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                            .clickable {
                                when (option) {
//                                    SynchronizationProviderViewData.GPODDER_NET -> GpodderAuthenticationFragment().show(activity.supportFragmentManager, GpodderAuthenticationFragment.TAG)
                                    SynchronizationProviderViewData.NEXTCLOUD_GPODDER -> showNextCloudAuthDialog = true
                                }
                                loggedIn = isProviderConnected
                                onDismissRequest()
                            }) {
                            Icon(painter = painterResource(id = option.iconResource), contentDescription = "", modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Text(stringResource(option.summaryResource), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    @Composable
    fun WifiAuthenticationDialog(onDismissRequest: ()->Unit) {
        val TAG = "WifiAuthenticationDialog"
        val textColor = MaterialTheme.colorScheme.onSurface
        val context = LocalContext.current
        var progressMessage by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        scope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.SyncServiceEvent -> {
                        when (event.messageResId) {
                            R.string.sync_status_error -> {
                                errorMessage = event.message
                                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                                onDismissRequest()
                            }
                            R.string.sync_status_success -> {
                                Toast.makeText(context, R.string.sync_status_success, Toast.LENGTH_LONG).show()
                                onDismissRequest()
                            }
                            R.string.sync_status_in_progress -> progressMessage = event.message
                            else -> {
                                Logd(TAG, "Sync result unknow ${event.messageResId}")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
        var portNum by remember { mutableIntStateOf(SynchronizationCredentials.hostport) }
        var isGuest by remember { mutableStateOf<Boolean?>(null) }
        var hostAddress by remember { mutableStateOf(SynchronizationCredentials.hosturl?:"") }
        var showHostAddress by remember { mutableStateOf(true)  }
        var portString by remember { mutableStateOf(SynchronizationCredentials.hostport.toString()) }
        var showProgress by remember { mutableStateOf(false) }
        var showConfirm by remember { mutableStateOf(true)  }
        var showCancel by remember { mutableStateOf(true)  }
        AlertDialog(modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.connect_to_peer), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    Text(stringResource(R.string.wifisync_explanation_message), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = {
                            val wifiManager = context.getSystemService(WIFI_SERVICE) as WifiManager
                            val ipAddress = wifiManager.connectionInfo.ipAddress
                            val ipString = String.format(Locale.US, "%d.%d.%d.%d", ipAddress and 0xff, ipAddress shr 8 and 0xff, ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff)
                            hostAddress = ipString
                            showHostAddress = false
                            portNum = portString.toInt()
                            isGuest = false
                            SynchronizationCredentials.hostport = portNum
                        }) { Text(stringResource(R.string.host_butLabel)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            SynchronizationCredentials.hosturl = hostAddress
                            showHostAddress = true
                            portNum = portString.toInt()
                            isGuest = true
                            SynchronizationCredentials.hostport = portNum
                        }) { Text(stringResource(R.string.guest_butLabel)) }
                    }
                    Row {
                        if (showHostAddress) TextField(value = hostAddress, modifier = Modifier.weight(0.6f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            onValueChange = { input -> hostAddress = input },
                            label = { Text(stringResource(id = R.string.synchronization_host_address_label)) })
                        TextField(value = portString, modifier = Modifier.weight(0.4f).padding(start = 3.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            onValueChange = { input ->
                                portString = input
                                portNum = input.toInt()
                            },
                            label = { Text(stringResource(id = R.string.synchronization_host_port_label)) })
                    }
                    if (showProgress)  {
                        CircularProgressIndicator(progress = {0.6f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp))
                        Text(stringResource(R.string.wifisync_progress_message) + " " + progressMessage, color = textColor)
                    }
                    Text(errorMessage, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                if (showConfirm) TextButton(onClick = {
                    Logd(TAG, "confirm button pressed")
                    if (isGuest == null) {
                        Toast.makeText(getAppContext(), R.string.host_or_guest, Toast.LENGTH_LONG).show()
                        return@TextButton
                    }
                    showProgress = true
                    showConfirm = false
                    showCancel = false
                    setWifiSyncEnabled(true)
                    startInstantSync(getAppContext(), portNum, hostAddress, isGuest!!)
                }) { Text(stringResource(R.string.confirm_label)) }
            },
            dismissButton = { if (showCancel) TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var ShowWifiAuthenticationDialog by remember { mutableStateOf(false) }
    if (ShowWifiAuthenticationDialog) WifiAuthenticationDialog { ShowWifiAuthenticationDialog = false }

    var ChooseProviderAndLoginDialog by remember { mutableStateOf(false) }
    if (ChooseProviderAndLoginDialog) ChooseProviderAndLoginDialog { ChooseProviderAndLoginDialog = false }

    fun isProviderSelected(provider: SynchronizationProviderViewData): Boolean {
        val selectedSyncProviderKey = selectedSyncProviderKey
        return provider.identifier == selectedSyncProviderKey
    }

//        supportActionBar!!.setTitle(R.string.synchronization_pref)
    val textColor = MaterialTheme.colorScheme.onSurface
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
        IconTitleSummaryActionRow(R.drawable.wifi_sync, R.string.wifi_sync, R.string.wifi_sync_summary_unchoosen) {
            ShowWifiAuthenticationDialog = true
//            WifiAuthenticationFragment().show(activity.supportFragmentManager, WifiAuthenticationFragment.TAG)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
            var titleRes by remember { mutableStateOf(0) }
            var summaryRes by remember { mutableIntStateOf(R.string.synchronization_summary_unchoosen) }
            var iconRes by remember { mutableIntStateOf(R.drawable.ic_notification_sync) }
            var onClick: (() -> Unit)? = null
            if (loggedIn) {
                selectedProvider = SynchronizationProviderViewData.fromIdentifier(selectedSyncProviderKey)
                if (selectedProvider != null) {
                    summaryRes = selectedProvider!!.summaryResource
                    iconRes = selectedProvider!!.iconResource
                    Icon(painter = painterResource(id = iconRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                }
            } else {
                titleRes = R.string.synchronization_choose_title
                summaryRes = R.string.synchronization_summary_unchoosen
                iconRes = R.drawable.ic_cloud
                onClick = { ChooseProviderAndLoginDialog = true }
                Icon(imageVector = ImageVector.vectorResource(iconRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
            }
            TitleSummaryActionColumn(titleRes, summaryRes) { onClick?.invoke() }
        }
        if (loggedIn) {
            TitleSummaryActionColumn(R.string.synchronization_sync_changes_title, R.string.synchronization_sync_summary) { SyncService.syncImmediately(activity.applicationContext) }
            TitleSummaryActionColumn(R.string.synchronization_full_sync_title, R.string.synchronization_force_sync_summary) { SyncService.fullSync(activity) }
            TitleSummaryActionColumn(R.string.synchronization_logout, 0) {
                SynchronizationCredentials.clear(activity)
                toastMassege = activity.getString(R.string.pref_synchronization_logout_toast)
                showToast = true
                setSelectedSyncProvider(null)
                loggedIn = isProviderConnected
            }
        }
    }
}
