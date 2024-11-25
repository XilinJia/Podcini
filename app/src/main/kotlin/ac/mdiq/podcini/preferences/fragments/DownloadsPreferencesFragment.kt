package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ProxySettingsBinding
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.newBuilder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.reinit
import ac.mdiq.podcini.net.feed.FeedUpdateManager.restartUpdateAlarm
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.proxyConfig
import ac.mdiq.podcini.storage.model.ProxyConfig
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.utils.ThemeUtils.getColorFromAttr
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
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

class DownloadsPreferencesFragment : PreferenceFragmentCompat() {
    private var blockAutoDeleteLocal = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
//        addPreferencesFromResource(R.xml.preferences_downloads)
//        setupNetworkScreen()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.downloads_pref)
        return ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    val textColor = MaterialTheme.colorScheme.onSurface
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(scrollState)) {
                        Text(stringResource(R.string.automation), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.feed_refresh_title), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                var interval by remember { mutableStateOf(appPrefs.getString(UserPreferences.Prefs.prefAutoUpdateIntervall.name, "12")!!) }
                                TextField(value = interval, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) interval = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("(hours)") },
                                    singleLine = true, modifier = Modifier.weight(0.5f),
                                    trailingIcon = {
                                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                                            modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                                if (interval.isEmpty()) interval = "0"
                                                appPrefs.edit().putString(UserPreferences.Prefs.prefAutoUpdateIntervall.name, interval).apply()
                                                restartUpdateAlarm(requireContext(), true)
                                            }))
                                    })
                            }
                            Text(stringResource(R.string.feed_refresh_sum), color = textColor)
                        }
                        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp).clickable(onClick = {
                            (activity as PreferenceActivity).openScreen(R.xml.preferences_autodownload)
                        })) {
                            Text(stringResource(R.string.pref_automatic_download_title), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.pref_automatic_download_sum), color = textColor)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.pref_auto_delete_title), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.pref_auto_delete_sum), color = textColor)
                            }
                            Switch(checked = false, onCheckedChange = { appPrefs.edit().putBoolean(UserPreferences.Prefs.prefAutoDelete.name, it).apply() })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.pref_auto_local_delete_title), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.pref_auto_local_delete_sum), color = textColor)
                            }
                            Switch(checked = false, onCheckedChange = {
                                if (blockAutoDeleteLocal && it) {
//                                    showAutoDeleteEnableDialog()
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setMessage(R.string.pref_auto_local_delete_dialog_body)
                                        .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                                            blockAutoDeleteLocal = false
                                            (findPreference<Preference>(Prefs.prefAutoDeleteLocal.name) as TwoStatePreference?)!!.isChecked = true
                                            blockAutoDeleteLocal = true
                                        }
                                        .setNegativeButton(R.string.cancel_label, null)
                                        .show()
                                }
                            })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.pref_keeps_important_episodes_title), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.pref_keeps_important_episodes_sum), color = textColor)
                            }
                            Switch(checked = true, onCheckedChange = { appPrefs.edit().putBoolean(UserPreferences.Prefs.prefFavoriteKeepsEpisode.name, it).apply() })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.pref_delete_removes_from_queue_title), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.pref_delete_removes_from_queue_sum), color = textColor)
                            }
                            Switch(checked = true, onCheckedChange = { appPrefs.edit().putBoolean(UserPreferences.Prefs.prefDeleteRemovesFromQueue.name, it).apply() })
                        }
                        Text(stringResource(R.string.download_pref_details), color = textColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp))
                        var showMeteredNetworkOptions by remember { mutableStateOf(false) }
                        var tempSelectedOptions by remember { mutableStateOf(appPrefs.getStringSet(UserPreferences.Prefs.prefMobileUpdateTypes.name, setOf("images"))!!) }
                        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp).clickable(onClick = { showMeteredNetworkOptions = true })) {
                            Text(stringResource(R.string.pref_metered_network_title), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.pref_mobileUpdate_sum), color = textColor)
                        }
                        if (showMeteredNetworkOptions) {
                            AlertDialog(onDismissRequest = { showMeteredNetworkOptions = false },
                                title = { Text(stringResource(R.string.pref_metered_network_title), style = MaterialTheme.typography.headlineSmall) },
                                text = {
                                    Column {
                                        MobileUpdateOptions.entries.forEach { option ->
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(5.dp)
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

                        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp).clickable(onClick = {
                            ProxyDialog(requireContext()).show()
                        })) {
                            Text(stringResource(R.string.pref_proxy_title), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.pref_proxy_sum), color = textColor)
                        }
                    }
                }
            }
        }
    }

    enum class MobileUpdateOptions(val res: Int) {
        feed_refresh(R.string.pref_mobileUpdate_refresh),
        episode_download(R.string.pref_mobileUpdate_episode_download),
        auto_download(R.string.pref_mobileUpdate_auto_download),
        streaming(R.string.pref_mobileUpdate_streaming),
        images(R.string.pref_mobileUpdate_images),
        sync(R.string.synchronization_pref);
    }

    class ProxyDialog(private val context: Context) {
        private lateinit var dialog: AlertDialog
        private lateinit var spType: Spinner
        private lateinit var etHost: EditText
        private lateinit var etPort: EditText
        private lateinit var etUsername: EditText
        private lateinit var etPassword: EditText
        private lateinit var txtvMessage: TextView
        private var testSuccessful = false
        private val port: Int
            get() {
                val port = etPort.text.toString()
                if (port.isNotEmpty()) try { return port.toInt() } catch (e: NumberFormatException) { }
                return 0
            }

        fun show(): Dialog {
            val content = View.inflate(context, R.layout.proxy_settings, null)
            val binding = ProxySettingsBinding.bind(content)
            spType = binding.spType
            dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_proxy_title)
                .setView(content)
                .setNegativeButton(R.string.cancel_label, null)
                .setPositiveButton(R.string.proxy_test_label, null)
                .setNeutralButton(R.string.reset, null)
                .show()
            // To prevent cancelling the dialog on button click
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (!testSuccessful) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    test()
                    return@setOnClickListener
                }
                setProxyConfig()
                reinit()
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                etHost.text.clear()
                etPort.text.clear()
                etUsername.text.clear()
                etPassword.text.clear()
                setProxyConfig()
            }
            val types: MutableList<String> = ArrayList()
            types.add(Proxy.Type.DIRECT.name)
            types.add(Proxy.Type.HTTP.name)
            types.add(Proxy.Type.SOCKS.name)
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, types)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spType.setAdapter(adapter)
            val proxyConfig = proxyConfig
            spType.setSelection(adapter.getPosition(proxyConfig.type.name))
            etHost = binding.etHost
            if (!proxyConfig.host.isNullOrEmpty()) etHost.setText(proxyConfig.host)
            etHost.addTextChangedListener(requireTestOnChange)
            etPort = binding.etPort
            if (proxyConfig.port > 0) etPort.setText(proxyConfig.port.toString())
            etPort.addTextChangedListener(requireTestOnChange)
            etUsername = binding.etUsername
            if (!proxyConfig.username.isNullOrEmpty()) etUsername.setText(proxyConfig.username)
            etUsername.addTextChangedListener(requireTestOnChange)
            etPassword = binding.etPassword
            if (!proxyConfig.password.isNullOrEmpty()) etPassword.setText(proxyConfig.password)
            etPassword.addTextChangedListener(requireTestOnChange)
            if (proxyConfig.type == Proxy.Type.DIRECT) {
                enableSettings(false)
                setTestRequired(false)
            }
            spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = if (position == 0) View.GONE else View.VISIBLE
                    enableSettings(position > 0)
                    setTestRequired(position > 0)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    enableSettings(false)
                }
            }
            txtvMessage = binding.txtvMessage
            checkValidity()
            return dialog
        }
        private fun setProxyConfig() {
            val type = spType.selectedItem as String
            val typeEnum = Proxy.Type.valueOf(type)
            val host = etHost.text.toString()
            val port = etPort.text.toString()
            var username: String? = etUsername.text.toString()
            if (username.isNullOrEmpty()) username = null
            var password: String? = etPassword.text.toString()
            if (password.isNullOrEmpty()) password = null
            var portValue = 0
            if (port.isNotEmpty()) portValue = port.toInt()
            val config = ProxyConfig(typeEnum, host, portValue, username, password)
            proxyConfig = config
            PodciniHttpClient.setProxyConfig(config)
        }
        private val requireTestOnChange: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                setTestRequired(true)
            }
        }
        private fun enableSettings(enable: Boolean) {
            etHost.isEnabled = enable
            etPort.isEnabled = enable
            etUsername.isEnabled = enable
            etPassword.isEnabled = enable
        }
        private fun checkValidity(): Boolean {
            var valid = true
            if (spType.selectedItemPosition > 0) valid = checkHost()
            valid = valid and checkPort()
            return valid
        }
        private fun checkHost(): Boolean {
            val host = etHost.text.toString()
            if (host.isEmpty()) {
                etHost.error = context.getString(R.string.proxy_host_empty_error)
                return false
            }
            if ("localhost" != host && !Patterns.DOMAIN_NAME.matcher(host).matches()) {
                etHost.error = context.getString(R.string.proxy_host_invalid_error)
                return false
            }
            return true
        }
        private fun checkPort(): Boolean {
            val port = port
            if (port < 0 || port > 65535) {
                etPort.error = context.getString(R.string.proxy_port_invalid_error)
                return false
            }
            return true
        }
        private fun setTestRequired(required: Boolean) {
            if (required) {
                testSuccessful = false
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.proxy_test_label)
            } else {
                testSuccessful = true
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(android.R.string.ok)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        }
        private fun test() {
            if (!checkValidity()) {
                setTestRequired(true)
                return
            }
            val res = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
            val textColorPrimary = res.getColor(0, 0)
            res.recycle()
            val checking = context.getString(R.string.proxy_checking)
            txtvMessage.setTextColor(textColorPrimary)
            txtvMessage.text = "{faw_circle_o_notch spin} $checking"
            txtvMessage.visibility = View.VISIBLE
            val coroutineScope = CoroutineScope(Dispatchers.Main)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val type = spType.selectedItem as String
                    val host = etHost.text.toString()
                    val port = etPort.text.toString()
                    val username = etUsername.text.toString()
                    val password = etPassword.text.toString()
                    var portValue = 8080
                    if (port.isNotEmpty()) portValue = port.toInt()
                    val address: SocketAddress = InetSocketAddress.createUnresolved(host, portValue)
                    val proxyType = Proxy.Type.valueOf(type.uppercase())
                    val builder: OkHttpClient.Builder = newBuilder().connectTimeout(10, TimeUnit.SECONDS).proxy(Proxy(proxyType, address))
                    if (username.isNotEmpty()) {
                        builder.proxyAuthenticator { _: Route?, response: Response ->
                            val credentials = basic(username, password)
                            response.request.newBuilder().header("Proxy-Authorization", credentials).build()
                        }
                    }
                    val client: OkHttpClient = builder.build()
                    val request: Request = Builder().url("https://www.example.com").head().build()
                    try {
                        client.newCall(request).execute().use { response -> if (!response.isSuccessful) throw IOException(response.message) }
                    } catch (e: IOException) { throw e }
                    withContext(Dispatchers.Main) {
                        txtvMessage.setTextColor(getColorFromAttr(context, R.attr.icon_green))
                        val message = String.format("%s %s", "{faw_check}", context.getString(R.string.proxy_test_successful))
                        txtvMessage.text = message
                        setTestRequired(false)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    txtvMessage.setTextColor(getColorFromAttr(context, R.attr.icon_red))
                    val message = String.format("%s %s: %s", "{faw_close}", context.getString(R.string.proxy_test_failed), e.message)
                    txtvMessage.text = message
                    setTestRequired(true)
                }
            }
        }
    }

    @Suppress("EnumEntryName")
    private enum class Prefs {
        prefAutoDownloadSettings,
        prefAutoDeleteLocal,
        prefProxy,
    }
}
