package ac.mdiq.podcini.preferences.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AuthenticationDialogBinding
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.SynchronizationCredentials
import ac.mdiq.podcini.net.sync.SynchronizationProviderViewData
import ac.mdiq.podcini.net.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.setSelectedSyncProvider
import ac.mdiq.podcini.net.sync.SynchronizationSettings.wifiSyncEnabledKey
import ac.mdiq.podcini.net.sync.nextcloud.NextcloudLoginFlow
import ac.mdiq.podcini.net.sync.nextcloud.NextcloudLoginFlow.AuthenticationCallback
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.fragments.GpodderAuthenticationFragment
import ac.mdiq.podcini.preferences.fragments.WifiAuthenticationFragment
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.IconTitleSummaryActionRow
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.DialogInterface
import android.text.format.DateUtils
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@Composable
fun SynchronizationPreferencesScreen(activity: PreferenceActivity) {
    var showToast by remember { mutableStateOf(false) }
    var toastMassege by remember { mutableStateOf("") }

    abstract class AuthenticationDialog(context: Context, titleRes: Int, enableUsernameField: Boolean, usernameInitialValue: String?, passwordInitialValue: String?)
        : MaterialAlertDialogBuilder(context) {

        var passwordHidden: Boolean = true

        init {
            setTitle(titleRes)
            val viewBinding = AuthenticationDialogBinding.inflate(LayoutInflater.from(context))
            setView(viewBinding.root)

            viewBinding.usernameEditText.isEnabled = enableUsernameField
            if (usernameInitialValue != null) viewBinding.usernameEditText.setText(usernameInitialValue)
            if (passwordInitialValue != null) viewBinding.passwordEditText.setText(passwordInitialValue)

            viewBinding.showPasswordButton.setOnClickListener {
                if (passwordHidden) {
                    viewBinding.passwordEditText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                    viewBinding.showPasswordButton.alpha = 1.0f
                } else {
                    viewBinding.passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
                    viewBinding.showPasswordButton.alpha = 0.6f
                }
                passwordHidden = !passwordHidden
            }

            setOnCancelListener { onCancelled() }
            setNegativeButton(R.string.cancel_label) { _: DialogInterface?, _: Int -> onCancelled() }
            setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
                onConfirmed(viewBinding.usernameEditText.text.toString(), viewBinding.passwordEditText.text.toString())
            }
        }

        protected open fun onCancelled() {}
        protected abstract fun onConfirmed(username: String, password: String)
    }

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
        var serverUrlText by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.pref_nextcloud_server_address.name, "")!!) }
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

        AlertDialog(onDismissRequest = { onDismissRequest() },
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
                    appPrefs.edit().putString(UserPreferences.Prefs.pref_nextcloud_server_address.name, serverUrlText).apply()
                    nextcloudLoginFlow = NextcloudLoginFlow(getHttpClient(), serverUrlText, activity, nextCloudAuthCallback)
                    errorText = ""
                    showChooseHost = false
                    nextcloudLoginFlow.start()
//                        onDismissRequest()
                }) { Text(stringResource(R.string.proceed_to_login_butLabel)) }
            },
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(text = "Cancel") } }
        )
    }

    var showNextCloudAuthDialog by remember { mutableStateOf(false) }
    if (showNextCloudAuthDialog) NextcloudAuthenticationDialog { showNextCloudAuthDialog = false }

    @Composable
    fun ChooseProviderAndLoginDialog(onDismissRequest: ()->Unit) {
        AlertDialog(onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.dialog_choose_sync_service_title), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    SynchronizationProviderViewData.entries.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                            .clickable {
                                when (option) {
                                    SynchronizationProviderViewData.GPODDER_NET -> GpodderAuthenticationFragment().show(activity.supportFragmentManager, GpodderAuthenticationFragment.TAG)
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
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(text = "Cancel") } }
        )
    }

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
        IconTitleSummaryActionRow(R.drawable.wifi_sync, R.string.wifi_sync, R.string.wifi_sync_summary_unchoosen) { WifiAuthenticationFragment().show(activity.supportFragmentManager, WifiAuthenticationFragment.TAG) }
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
        if (isProviderSelected(SynchronizationProviderViewData.GPODDER_NET)) {
            TitleSummaryActionColumn(R.string.pref_gpodnet_setlogin_information_title, R.string.pref_gpodnet_setlogin_information_sum) {
                val dialog: AuthenticationDialog = object : AuthenticationDialog(activity, R.string.pref_gpodnet_setlogin_information_title,
                    false, SynchronizationCredentials.username, null) {
                    override fun onConfirmed(username: String, password: String) {
                        SynchronizationCredentials.password = password
                    }
                }
                dialog.show()
            }
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
