package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ChooseDataFolderDialogBinding
import ac.mdiq.podcini.databinding.ChooseDataFolderDialogEntryBinding
import ac.mdiq.podcini.databinding.ProxySettingsBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager.restartUpdateAlarm
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.newBuilder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.reinit
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.getDataFolder
import ac.mdiq.podcini.preferences.UserPreferences.proxyConfig
import ac.mdiq.podcini.preferences.UserPreferences.setDataFolder
import ac.mdiq.podcini.storage.model.ProxyConfig
import ac.mdiq.podcini.storage.utils.StorageUtils.getFreeSpaceAvailable
import ac.mdiq.podcini.storage.utils.StorageUtils.getTotalSpaceAvailable
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.utils.ThemeUtils.getColorFromAttr
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.util.Consumer
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress
import java.util.concurrent.TimeUnit

class DownloadsPreferencesFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
    private var blockAutoDeleteLocal = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_downloads)
        setupNetworkScreen()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.downloads_pref)
        appPrefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        appPrefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        setDataFolderText()
    }

    private fun setupNetworkScreen() {
        findPreference<Preference>(PREF_SCREEN_AUTODL)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            (activity as PreferenceActivity).openScreen(R.xml.preferences_autodownload)
            true
        }
        // validate and set correct value: number of downloads between 1 and 50 (inclusive)
        findPreference<Preference>(PREF_PROXY)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val dialog = ProxyDialog(requireContext())
            dialog.show()
            true
        }
        findPreference<Preference>(PREF_CHOOSE_DATA_DIR)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            ChooseDataFolderDialog.showDialog(requireContext()) { path: String? ->
                setDataFolder(path!!)
                setDataFolderText()
            }
            true
        }
        findPreference<Preference>(PREF_AUTO_DELETE_LOCAL)!!.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
            if (blockAutoDeleteLocal && newValue as Boolean) {
                showAutoDeleteEnableDialog()
                return@OnPreferenceChangeListener false
            } else return@OnPreferenceChangeListener true
        }
    }

    private fun setDataFolderText() {
        val f = getDataFolder(null)
        if (f != null) findPreference<Preference>(PREF_CHOOSE_DATA_DIR)!!.summary = f.absolutePath
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (UserPreferences.PREF_UPDATE_INTERVAL == key) restartUpdateAlarm(requireContext(), true)
    }

    private fun showAutoDeleteEnableDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.pref_auto_local_delete_dialog_body)
            .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                blockAutoDeleteLocal = false
                (findPreference<Preference>(PREF_AUTO_DELETE_LOCAL) as TwoStatePreference?)!!.isChecked = true
                blockAutoDeleteLocal = true
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    object ChooseDataFolderDialog {
        fun showDialog(context: Context, handlerFunc: Consumer<String?>) {
            val content = View.inflate(context, R.layout.choose_data_folder_dialog, null)
            val dialog = MaterialAlertDialogBuilder(context)
                .setView(content)
                .setTitle(R.string.choose_data_directory)
                .setMessage(R.string.choose_data_directory_message)
                .setNegativeButton(R.string.cancel_label, null)
                .create()
            val binding = ChooseDataFolderDialogBinding.bind(content)
            val recyclerView = binding.recyclerView
            recyclerView.layoutManager = LinearLayoutManager(context)
            val adapter = DataFolderAdapter(context) { path: String? ->
                dialog.dismiss()
                handlerFunc.accept(path)
            }
            recyclerView.adapter = adapter

            if (adapter.itemCount != 0) {
                dialog.show()
            }
        }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) types.add(Proxy.Type.SOCKS.name)

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

        private val port: Int
            get() {
                val port = etPort.text.toString()
                if (port.isNotEmpty()) {
                    try {
                        return port.toInt()
                    } catch (e: NumberFormatException) {
                        // ignore
                    }
                }
                return 0
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
                    val builder: OkHttpClient.Builder = newBuilder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .proxy(Proxy(proxyType, address))
                    if (username.isNotEmpty()) {
                        builder.proxyAuthenticator { _: Route?, response: Response ->
                            val credentials = basic(username, password)
                            response.request.newBuilder()
                                .header("Proxy-Authorization", credentials)
                                .build()
                        }
                    }
                    val client: OkHttpClient = builder.build()
                    val request: Request = Builder().url("https://www.example.com").head().build()
                    try {
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw IOException(response.message)
                        }
                    } catch (e: IOException) {
                        throw e
                    }
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

    class DataFolderAdapter(context: Context, selectionHandler: Consumer<String>) : RecyclerView.Adapter<DataFolderAdapter.ViewHolder?>() {

        private val selectionHandler: Consumer<String>
        private val currentPath: String?
        private val entries: List<StoragePath>
        private val freeSpaceString: String

        init {
            this.entries = getStorageEntries(context)
            this.currentPath = getCurrentPath()
            this.selectionHandler = selectionHandler
            this.freeSpaceString = context.getString(R.string.choose_data_directory_available_space)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val entryView = inflater.inflate(R.layout.choose_data_folder_dialog_entry, parent, false)
            return ViewHolder(entryView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val storagePath = entries[position]
            val context = holder.root.context
            val freeSpace = Formatter.formatShortFileSize(context, storagePath.availableSpace)
            val totalSpace = Formatter.formatShortFileSize(context, storagePath.totalSpace)

            holder.path.text = storagePath.shortPath
            holder.size.text = String.format(freeSpaceString, freeSpace, totalSpace)
            holder.progressBar.progress = storagePath.usagePercentage
            val selectListener = View.OnClickListener { _: View? ->
                selectionHandler.accept(storagePath.fullPath)
            }
            holder.root.setOnClickListener(selectListener)
            holder.radioButton.setOnClickListener(selectListener)

            if (storagePath.fullPath == currentPath) holder.radioButton.toggle()
        }

        override fun getItemCount(): Int {
            return entries.size
        }

        private fun getCurrentPath(): String? {
            val dataFolder = getDataFolder(null)
            return dataFolder?.absolutePath
        }

        private fun getStorageEntries(context: Context): List<StoragePath> {
            val mediaDirs = context.getExternalFilesDirs(null)
            val entries: MutableList<StoragePath> = ArrayList(mediaDirs.size)
            for (dir in mediaDirs) {
                if (!isWritable(dir)) continue
                entries.add(StoragePath(dir.absolutePath))
            }
            if (entries.isEmpty() && isWritable(context.filesDir)) entries.add(StoragePath(context.filesDir.absolutePath))
            return entries
        }

        private fun isWritable(dir: File?): Boolean {
            return dir != null && dir.exists() && dir.canRead() && dir.canWrite()
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val binding = ChooseDataFolderDialogEntryBinding.bind(itemView)
            val root: View = binding.root
            val path: TextView = binding.path
            val size: TextView = binding.size
            val radioButton: RadioButton = binding.radioButton
            val progressBar: ProgressBar = binding.usedSpace
        }

        internal class StoragePath(val fullPath: String) {
            val shortPath: String
                get() {
                    val prefixIndex = fullPath.indexOf("Android")
                    return if ((prefixIndex > 0)) fullPath.substring(0, prefixIndex) else fullPath
                }

            val availableSpace: Long
                get() = getFreeSpaceAvailable(fullPath)

            val totalSpace: Long
                get() = getTotalSpaceAvailable(fullPath)

            val usagePercentage: Int
                get() = 100 - (100 * availableSpace / totalSpace.toFloat()).toInt()
        }
    }

    companion object {
        private const val PREF_SCREEN_AUTODL = "prefAutoDownloadSettings"
        private const val PREF_AUTO_DELETE_LOCAL = "prefAutoDeleteLocal"
        private const val PREF_PROXY = "prefProxy"
        private const val PREF_CHOOSE_DATA_DIR = "prefChooseDataDir"
    }
}
