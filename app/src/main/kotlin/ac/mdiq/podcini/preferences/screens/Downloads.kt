package ac.mdiq.podcini.preferences.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.newBuilder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.reinit
import ac.mdiq.podcini.net.feed.FeedUpdateManager.restartUpdateAlarm
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.proxyConfig
import ac.mdiq.podcini.storage.model.ProxyConfig
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.activity.PreferenceActivity.Screens
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.Spinner
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchPrefRow
import android.content.DialogInterface
import android.util.Patterns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials.basic
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Request.Builder
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress
import java.util.concurrent.TimeUnit

@Suppress("EnumEntryName")
enum class MobileUpdateOptions(val res: Int) {
    feed_refresh(R.string.pref_mobileUpdate_refresh),
    episode_download(R.string.pref_mobileUpdate_episode_download),
    auto_download(R.string.pref_mobileUpdate_auto_download),
    streaming(R.string.pref_mobileUpdate_streaming),
    images(R.string.pref_mobileUpdate_images),
    sync(R.string.synchronization_pref);
}

@Composable
fun DownloadsPreferencesScreen(activity: PreferenceActivity, navController: NavController) {
    @Composable
    fun ProxyDialog(onDismissRequest: ()->Unit) {
        val textColor = MaterialTheme.colorScheme.onSurface
        val types = remember { mutableStateListOf<String>() }

        LaunchedEffect(Unit) {
            types.add(Proxy.Type.DIRECT.name)
            types.add(Proxy.Type.HTTP.name)
            types.add(Proxy.Type.SOCKS.name)
        }
        var testSuccessful by remember { mutableStateOf(false) }
        var type by remember { mutableStateOf(proxyConfig.type.name) }
        var typePos by remember { mutableIntStateOf(0) }
        var host by remember { mutableStateOf(proxyConfig.host?:"") }
        var hostError by remember { mutableStateOf("") }
        var port by remember { mutableStateOf(if (proxyConfig.port > 0) proxyConfig.port.toString() else "") }
        var portError by remember { mutableStateOf("") }
        var portValue by remember { mutableIntStateOf(proxyConfig.port) }
        var username by remember { mutableStateOf<String?>(proxyConfig.username) }
        var password by remember { mutableStateOf<String?>(proxyConfig.password) }
        var message by remember { mutableStateOf("") }
        var messageColor by remember { mutableStateOf(textColor) }
        var showOKButton by remember { mutableStateOf(false) }
        var OKbuttonTextRes by remember { mutableIntStateOf(R.string.proxy_test_label) }

        fun setProxyConfig() {
            val typeEnum = Proxy.Type.valueOf(type)
            if (username.isNullOrEmpty()) username = null
            if (password.isNullOrEmpty()) password = null
            if (port.isNotEmpty()) portValue = port.toInt()
            val config = ProxyConfig(typeEnum, host, portValue, username, password)
            proxyConfig = config
            PodciniHttpClient.setProxyConfig(config)
        }
        fun setTestRequired(required: Boolean) {
            if (required) {
                testSuccessful = false
                OKbuttonTextRes = R.string.proxy_test_label
            } else {
                testSuccessful = true
                OKbuttonTextRes = android.R.string.ok
            }
        }
        fun checkHost(): Boolean {
            if (host.isEmpty()) {
                hostError = activity.getString(R.string.proxy_host_empty_error)
                return false
            }
            if ("localhost" != host && !Patterns.DOMAIN_NAME.matcher(host).matches()) {
                hostError = activity.getString(R.string.proxy_host_invalid_error)
                return false
            }
            return true
        }
        fun checkPort(): Boolean {
            if (portValue < 0 || portValue > 65535) {
                portError = activity.getString(R.string.proxy_port_invalid_error)
                return false
            }
            return true
        }
        fun checkValidity(): Boolean {
            var valid = true
            if (typePos > 0) valid = checkHost()
            valid = valid and checkPort()
            return valid
        }
        fun test() {
            if (!checkValidity()) {
                setTestRequired(true)
                return
            }
            val checking = activity.getString(R.string.proxy_checking)
            messageColor = textColor
            message = "{faw_circle_o_notch spin} $checking"
            val coroutineScope = CoroutineScope(Dispatchers.Main)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    if (port.isNotEmpty()) portValue = port.toInt()
                    val address: SocketAddress = InetSocketAddress.createUnresolved(host, portValue)
                    val proxyType = Proxy.Type.valueOf(type.uppercase())
                    val builder: OkHttpClient.Builder = newBuilder().connectTimeout(10, TimeUnit.SECONDS).proxy(Proxy(proxyType, address))
                    if (!username.isNullOrBlank())
                        builder.proxyAuthenticator { _: Route?, response: Response -> response.request.newBuilder().header("Proxy-Authorization", basic(username?:"", password?:"")).build() }
                    val client: OkHttpClient = builder.build()
                    val request: Request = Builder().url("https://www.example.com").head().build()
                    try { client.newCall(request).execute().use { response -> if (!response.isSuccessful) throw IOException(response.message) }
                    } catch (e: IOException) { throw e }
                    withContext(Dispatchers.Main) {
                        message = String.format("%s %s", "{faw_check}", activity.getString(R.string.proxy_test_successful))
                        messageColor = Color.Green
                        setTestRequired(false)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    messageColor = Color.Red
                    message = String.format("%s %s: %s", "{faw_close}", activity.getString(R.string.proxy_test_failed), e.message)
                    setTestRequired(true)
                }
            }
        }
        AlertDialog(onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.pref_proxy_title), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.proxy_type_label))
                        Spinner(items = types, selectedItem = proxyConfig.type.name) { position ->
                            val name = Proxy.Type.entries.getOrNull(position)?.name
                            if (!name.isNullOrBlank()) {
                                typePos = position
                                type = name
                                showOKButton = position != 0
                                setTestRequired(position > 0)
                            }
                        }
                    }
                    if (typePos > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.host_label))
                            TextField(value = host, label = { Text("www.example.com") },
                                onValueChange = { host = it },
                                isError = !checkHost(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.port_label))
                            TextField(value = port, label = { Text("8080") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                onValueChange = {
                                    port = it
                                    portValue = it.toIntOrNull() ?: -1
                                },
                                isError = !checkPort(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.username_label))
                            TextField(value = username ?: "", label = { Text(stringResource(R.string.optional_hint)) },
                                onValueChange = { username = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.password_label))
                            TextField(value = password ?: "", label = { Text(stringResource(R.string.optional_hint)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (message.isNotBlank()) Text(message)
                }
            },
            confirmButton = {
                if (showOKButton) TextButton(onClick = {
                    if (!testSuccessful) {
                        test()
                        return@TextButton
                    }
                    setProxyConfig()
                    reinit()
                    onDismissRequest()
                }) { Text(stringResource(OKbuttonTextRes)) }
            },
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(text = "Cancel") } }
        )
    }

    var blockAutoDeleteLocal by remember { mutableStateOf(true) }
    val textColor = MaterialTheme.colorScheme.onSurface
    val scrollState = rememberScrollState()
//        supportActionBar!!.setTitle(R.string.downloads_pref)
    var showProxyDialog by remember { mutableStateOf(false) }
    if (showProxyDialog) ProxyDialog {showProxyDialog = false }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
        Text(stringResource(R.string.automation), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.feed_refresh_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                var interval by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefAutoUpdateIntervall.name, "12")!!) }
                var showIcon by remember { mutableStateOf(false) }
                TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("(hours)") },
                    singleLine = true, modifier = Modifier.weight(0.5f),
                    onValueChange = {
                        if (it.isEmpty() || it.toIntOrNull() != null) {
                            interval = it
                            showIcon = true
                        }
                    },
                    trailingIcon = {
                        if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                            modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                if (interval.isEmpty()) interval = "0"
                                appPrefs.edit().putString(UserPreferences.Prefs.prefAutoUpdateIntervall.name, interval).apply()
                                showIcon = false
                                restartUpdateAlarm(activity, true)
                            }))
                    })
            }
            Text(stringResource(R.string.feed_refresh_sum), color = textColor)
        }
        TitleSummaryActionColumn(R.string.pref_automatic_download_title, R.string.pref_automatic_download_sum) {
            navController.navigate(Screens.autodownload.tag)
//                openScreen(Screens.preferences_autodownload)
        }
        TitleSummarySwitchPrefRow(R.string.pref_auto_delete_title, R.string.pref_auto_delete_sum, UserPreferences.Prefs.prefAutoDelete.name)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pref_auto_local_delete_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pref_auto_local_delete_sum), color = textColor)
            }
            var isChecked by remember { mutableStateOf(appPrefs.getBoolean(UserPreferences.Prefs.prefAutoDeleteLocal.name, false)) }
            Switch(checked = isChecked, onCheckedChange = {
                isChecked = it
                if (blockAutoDeleteLocal && it) {
                    MaterialAlertDialogBuilder(activity)
                        .setMessage(R.string.pref_auto_local_delete_dialog_body)
                        .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                            blockAutoDeleteLocal = false
                            appPrefs.edit().putBoolean(UserPreferences.Prefs.prefAutoDeleteLocal.name, it).apply()
//                                                (findPreference<Preference>(Prefs.prefAutoDeleteLocal.name) as TwoStatePreference?)!!.isChecked = true
                            blockAutoDeleteLocal = true
                        }
                        .setNegativeButton(R.string.cancel_label, null)
                        .show()
                }
            })
        }
        TitleSummarySwitchPrefRow(R.string.pref_keeps_important_episodes_title, R.string.pref_keeps_important_episodes_sum, UserPreferences.Prefs.prefFavoriteKeepsEpisode.name, true)
        TitleSummarySwitchPrefRow(R.string.pref_delete_removes_from_queue_title, R.string.pref_delete_removes_from_queue_sum, UserPreferences.Prefs.prefDeleteRemovesFromQueue.name, true)
        Text(stringResource(R.string.download_pref_details), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp))
        var showMeteredNetworkOptions by remember { mutableStateOf(false) }
        var tempSelectedOptions by remember { mutableStateOf(appPrefs.getStringSet(UserPreferences.Prefs.prefMobileUpdateTypes.name, setOf("images"))!!) }
        TitleSummaryActionColumn(R.string.pref_metered_network_title, R.string.pref_mobileUpdate_sum) { showMeteredNetworkOptions = true }
        if (showMeteredNetworkOptions) {
            AlertDialog(onDismissRequest = { showMeteredNetworkOptions = false },
                title = { Text(stringResource(R.string.pref_metered_network_title), style = CustomTextStyles.titleCustom) },
                text = {
                    Column {
                        MobileUpdateOptions.entries.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                                .clickable {
                                    tempSelectedOptions = if (tempSelectedOptions.contains(option.name)) tempSelectedOptions - option.name
                                    else tempSelectedOptions + option.name
                                }) {
                                Checkbox(checked = tempSelectedOptions.contains(option.name),
                                    onCheckedChange = {
                                        tempSelectedOptions = if (tempSelectedOptions.contains(option.name)) tempSelectedOptions - option.name
                                        else tempSelectedOptions + option.name
                                    })
                                Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        appPrefs.edit().putStringSet(UserPreferences.Prefs.prefMobileUpdateTypes.name, tempSelectedOptions).apply()
                        showMeteredNetworkOptions = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showMeteredNetworkOptions = false }) { Text(text = "Cancel") } }
            )
        }
        TitleSummaryActionColumn(R.string.pref_proxy_title, R.string.pref_proxy_sum) {
            showProxyDialog = true
//                ProxyDialog(this@PreferenceActivity).show()
        }
    }
}

enum class EpisodeCleanupOptions(val res: Int, val num: Int) {
    ExceptFavorites(R.string.episode_cleanup_except_favorite, -3),
    Never(R.string.episode_cleanup_never, -2),
    NotInQueue(R.string.episode_cleanup_not_in_queue, -1),
    LimitBy(R.string.episode_cleanup_limit_by, 0)
}

@Composable
fun AutoDownloadPreferencesScreen() {
    val textColor = MaterialTheme.colorScheme.onSurface
    val scrollState = rememberScrollState()
//        supportActionBar!!.setTitle(R.string.pref_automatic_download_title)
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
        var isEnabled by remember { mutableStateOf(appPrefs.getBoolean(UserPreferences.Prefs.prefEnableAutoDl.name, false)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pref_automatic_download_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pref_automatic_download_sum), color = textColor)
            }
            Switch(checked = isEnabled, onCheckedChange = {
                isEnabled = it
                appPrefs.edit().putBoolean(UserPreferences.Prefs.prefEnableAutoDl.name, it).apply()
            })
        }
        if (isEnabled) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pref_episode_cache_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    var interval by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefEpisodeCacheSize.name, "25")!!) }
                    var showIcon by remember { mutableStateOf(false) }
                    TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("integer") },
                        singleLine = true, modifier = Modifier.weight(0.5f),
                        onValueChange = {
                            if (it.isEmpty() || it.toIntOrNull() != null) {
                                interval = it
                                showIcon = true
                            }
                        },
                        trailingIcon = {
                            if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                                modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                    if (interval.isEmpty()) interval = "0"
                                    appPrefs.edit().putString(UserPreferences.Prefs.prefEpisodeCacheSize.name, interval).apply()
                                    showIcon = false
                                }))
                        })
                }
                Text(stringResource(R.string.pref_episode_cache_summary), color = textColor)
            }
            var showCleanupOptions by remember { mutableStateOf(false) }
            TitleSummaryActionColumn(R.string.pref_episode_cleanup_title, R.string.pref_episode_cleanup_summary) { showCleanupOptions = true }
            if (showCleanupOptions) {
                var tempCleanupOption by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefEpisodeCleanup.name, "-1")!!) }
                var interval by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefEpisodeCleanup.name, "-1")!!) }
                if ((interval.toIntOrNull() ?: -1) > 0) tempCleanupOption = EpisodeCleanupOptions.LimitBy.num.toString()
                AlertDialog(onDismissRequest = { showCleanupOptions = false },
                    title = { Text(stringResource(R.string.pref_episode_cleanup_title), style = CustomTextStyles.titleCustom) },
                    text = {
                        Column {
                            EpisodeCleanupOptions.entries.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                                    .clickable { tempCleanupOption = option.num.toString() }) {
                                    Checkbox(checked = tempCleanupOption == option.num.toString(), onCheckedChange = { tempCleanupOption = option.num.toString() })
                                    Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (tempCleanupOption == EpisodeCleanupOptions.LimitBy.num.toString()) {
                                TextField(value = interval, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("integer") }, singleLine = true,
                                    onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) interval = it })
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            var num = if (tempCleanupOption == EpisodeCleanupOptions.LimitBy.num.toString()) interval else tempCleanupOption
                            if (num.toIntOrNull() == null) num = EpisodeCleanupOptions.Never.num.toString()
                            appPrefs.edit().putString(UserPreferences.Prefs.prefEpisodeCleanup.name, num).apply()
                            showCleanupOptions = false
                        }) { Text(text = "OK") }
                    },
                    dismissButton = { TextButton(onClick = { showCleanupOptions = false }) { Text(text = "Cancel") } }
                )
            }
            TitleSummarySwitchPrefRow(R.string.pref_automatic_download_on_battery_title, R.string.pref_automatic_download_on_battery_sum, UserPreferences.Prefs.prefEnableAutoDownloadOnBattery.name)
        }
    }
}
