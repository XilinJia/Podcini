package ac.mdiq.podcini.preferences.fragments

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.*
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.SynchronizationCredentials
import ac.mdiq.podcini.net.sync.SynchronizationProviderViewData
import ac.mdiq.podcini.net.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.setSelectedSyncProvider
import ac.mdiq.podcini.net.sync.SynchronizationSettings.setWifiSyncEnabled
import ac.mdiq.podcini.net.sync.SynchronizationSettings.wifiSyncEnabledKey
import ac.mdiq.podcini.net.sync.gpoddernet.GpodnetService
import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetDevice
import ac.mdiq.podcini.net.sync.nextcloud.NextcloudLoginFlow
import ac.mdiq.podcini.net.sync.wifi.WifiSyncService.Companion.hostPort
import ac.mdiq.podcini.net.sync.wifi.WifiSyncService.Companion.startInstantSync
import ac.mdiq.podcini.storage.utils.FileNameGenerator.generateFileName
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.content.DialogInterface
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.regex.Pattern

class SynchronizationPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_synchronization)
        setupScreen()
        updateScreen()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.synchronization_pref)
        updateScreen()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
        (activity as PreferenceActivity).supportActionBar!!.subtitle = ""
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd("SynchronizationPreferencesFragment", "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.SyncServiceEvent -> syncStatusChanged(event)
                    else -> {}
                }
            }
        }
    }

    fun syncStatusChanged(event: FlowEvent.SyncServiceEvent) {
        if (!isProviderConnected && !wifiSyncEnabledKey) return

        updateScreen()
        if (event.messageResId == R.string.sync_status_error || event.messageResId == R.string.sync_status_success)
            updateLastSyncReport(SynchronizationSettings.isLastSyncSuccessful, SynchronizationSettings.lastSyncAttempt)
        else (activity as PreferenceActivity).supportActionBar!!.setSubtitle(event.messageResId)
    }

    private fun setupScreen() {
        val activity: Activity? = activity
        findPreference<Preference>(Prefs.pref_gpodnet_setlogin_information.name)?.setOnPreferenceClickListener {
            val dialog: AuthenticationDialog = object : AuthenticationDialog(requireContext(), R.string.pref_gpodnet_setlogin_information_title,
                false, SynchronizationCredentials.username, null) {
                override fun onConfirmed(username: String, password: String) {
                    SynchronizationCredentials.password = password
                }
            }
            dialog.show()
            true
        }
        findPreference<Preference>(Prefs.pref_synchronization_sync.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SyncService.syncImmediately(requireActivity().applicationContext)
            true
        }
        findPreference<Preference>(Prefs.pref_synchronization_force_full_sync.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SyncService.fullSync(requireContext())
            true
        }
        findPreference<Preference>(Prefs.pref_synchronization_logout.name)!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SynchronizationCredentials.clear(requireContext())
            Snackbar.make(requireView(), R.string.pref_synchronization_logout_toast, Snackbar.LENGTH_LONG).show()
            setSelectedSyncProvider(null)
            updateScreen()
            true
        }
    }

    private fun updateScreen() {
        val preferenceInstantSync = findPreference<Preference>(Prefs.preference_instant_sync.name)
        preferenceInstantSync!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            WifiAuthenticationFragment().show(childFragmentManager, WifiAuthenticationFragment.TAG)
            true
        }

        val loggedIn = isProviderConnected
        val preferenceHeader = findPreference<Preference>(Prefs.preference_synchronization_description.name)
        if (loggedIn) {
            val selectedProvider = SynchronizationProviderViewData.fromIdentifier(selectedSyncProviderKey)
            preferenceHeader!!.title = ""
            if (selectedProvider != null) {
                preferenceHeader.setSummary(selectedProvider.summaryResource)
                preferenceHeader.setIcon(selectedProvider.iconResource)
            }
            preferenceHeader.onPreferenceClickListener = null
        } else {
            preferenceHeader!!.setTitle(R.string.synchronization_choose_title)
            preferenceHeader.setSummary(R.string.synchronization_summary_unchoosen)
            preferenceHeader.setIcon(R.drawable.ic_cloud)
            preferenceHeader.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                chooseProviderAndLogin()
                true
            }
        }

        val gpodnetSetLoginPreference = findPreference<Preference>(Prefs.pref_gpodnet_setlogin_information.name)
        gpodnetSetLoginPreference!!.isVisible = isProviderSelected(SynchronizationProviderViewData.GPODDER_NET)
        gpodnetSetLoginPreference.isEnabled = loggedIn
        findPreference<Preference>(Prefs.pref_synchronization_sync.name)!!.isVisible = loggedIn
        findPreference<Preference>(Prefs.pref_synchronization_force_full_sync.name)!!.isVisible = loggedIn
        findPreference<Preference>(Prefs.pref_synchronization_logout.name)!!.isVisible = loggedIn
        if (loggedIn) {
            val summary = getString(R.string.synchronization_login_status,
                SynchronizationCredentials.username, SynchronizationCredentials.hosturl)
            val formattedSummary = HtmlCompat.fromHtml(summary, HtmlCompat.FROM_HTML_MODE_LEGACY)
            findPreference<Preference>(Prefs.pref_synchronization_logout.name)!!.summary = formattedSummary
            updateLastSyncReport(SynchronizationSettings.isLastSyncSuccessful, SynchronizationSettings.lastSyncAttempt)
        } else {
            findPreference<Preference>(Prefs.pref_synchronization_logout.name)?.summary = ""
            (activity as PreferenceActivity).supportActionBar?.setSubtitle("")
        }
    }

    private fun chooseProviderAndLogin() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(R.string.dialog_choose_sync_service_title)

        val providers = SynchronizationProviderViewData.entries.toTypedArray()
        val adapter: ListAdapter = object : ArrayAdapter<SynchronizationProviderViewData?>(requireContext(), R.layout.alertdialog_sync_provider_chooser, providers) {
            var holder: ViewHolder? = null

            inner class ViewHolder {
                var icon: ImageView? = null
                var title: TextView? = null
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var convertView = convertView
                val inflater = LayoutInflater.from(context)
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.alertdialog_sync_provider_chooser, null)
                    val binding = AlertdialogSyncProviderChooserBinding.bind(convertView)
                    holder = ViewHolder()
                    if (holder != null) {
                        holder!!.icon = binding.icon
                        holder!!.title = binding.title
                        convertView.tag = holder
                    }
                } else holder = convertView.tag as ViewHolder

                val synchronizationProviderViewData = getItem(position)
                holder!!.title!!.setText(synchronizationProviderViewData!!.summaryResource)
                holder!!.icon!!.setImageResource(synchronizationProviderViewData.iconResource)
                return convertView!!
            }
        }

        builder.setAdapter(adapter) { _: DialogInterface?, which: Int ->
            when (providers[which]) {
                SynchronizationProviderViewData.GPODDER_NET -> GpodderAuthenticationFragment().show(childFragmentManager,
                    GpodderAuthenticationFragment.TAG)
                SynchronizationProviderViewData.NEXTCLOUD_GPODDER -> NextcloudAuthenticationFragment().show(childFragmentManager,
                    NextcloudAuthenticationFragment.TAG)
            }
            updateScreen()
        }

        builder.show()
    }

    private fun isProviderSelected(provider: SynchronizationProviderViewData): Boolean {
        val selectedSyncProviderKey = selectedSyncProviderKey
        return provider.identifier == selectedSyncProviderKey
    }

    private val selectedSyncProviderKey: String
        get() = SynchronizationSettings.selectedSyncProviderKey?:""

    private fun updateLastSyncReport(successful: Boolean, lastTime: Long) {
        val status = String.format("%1\$s (%2\$s)", getString(if (successful) R.string.gpodnetsync_pref_report_successful else R.string.gpodnetsync_pref_report_failed),
            DateUtils.getRelativeDateTimeString(context, lastTime, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_SHOW_TIME))
        (activity as PreferenceActivity).supportActionBar!!.subtitle = status
    }

    /**
     * Displays a dialog with a username and password text field and an optional checkbox to save username and preferences.
     */
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

    /**
     * Guides the user through the authentication process.
     */
    class NextcloudAuthenticationFragment : DialogFragment(), NextcloudLoginFlow.AuthenticationCallback {
        private var binding: NextcloudAuthDialogBinding? = null
        private var nextcloudLoginFlow: NextcloudLoginFlow? = null
        private var shouldDismiss = false

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setTitle(R.string.gpodnetauth_login_butLabel)
            dialog.setNegativeButton(R.string.cancel_label, null)
            dialog.setCancelable(false)
            this.isCancelable = false

            binding = NextcloudAuthDialogBinding.inflate(layoutInflater)
            dialog.setView(binding!!.root)

            binding!!.chooseHostButton.setOnClickListener {
                nextcloudLoginFlow = NextcloudLoginFlow(getHttpClient(), binding!!.serverUrlText.text.toString(), requireContext(), this)
                startLoginFlow()
            }
            if (savedInstanceState?.getStringArrayList(EXTRA_LOGIN_FLOW) != null) {
                nextcloudLoginFlow = NextcloudLoginFlow.fromInstanceState(getHttpClient(), requireContext(), this,
                    savedInstanceState.getStringArrayList(EXTRA_LOGIN_FLOW)!!)
                startLoginFlow()
            }
            return dialog.create()
        }
        private fun startLoginFlow() {
            binding!!.errorText.visibility = View.GONE
            binding!!.chooseHostButton.visibility = View.GONE
            binding!!.loginProgressContainer.visibility = View.VISIBLE
            binding!!.serverUrlText.isEnabled = false
            nextcloudLoginFlow!!.start()
        }
        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            if (nextcloudLoginFlow != null) outState.putStringArrayList(EXTRA_LOGIN_FLOW, nextcloudLoginFlow!!.saveInstanceState())
        }
        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            nextcloudLoginFlow?.cancel()
        }
        override fun onResume() {
            super.onResume()
            nextcloudLoginFlow?.onResume()

            if (shouldDismiss) dismiss()
        }
        override fun onNextcloudAuthenticated(server: String, username: String, password: String) {
            setSelectedSyncProvider(SynchronizationProviderViewData.NEXTCLOUD_GPODDER)
            SynchronizationCredentials.clear(requireContext())
            SynchronizationCredentials.password = password
            SynchronizationCredentials.hosturl = server
            SynchronizationCredentials.username = username
            SyncService.fullSync(requireContext())
            if (isResumed) dismiss()
            else shouldDismiss = true
        }
        override fun onNextcloudAuthError(errorMessage: String?) {
            binding!!.loginProgressContainer.visibility = View.GONE
            binding!!.errorText.visibility = View.VISIBLE
            binding!!.errorText.text = errorMessage
            binding!!.chooseHostButton.visibility = View.VISIBLE
            binding!!.serverUrlText.isEnabled = true
        }

        companion object {
            val TAG = NextcloudAuthenticationFragment::class.simpleName ?: "Anonymous"
            private const val EXTRA_LOGIN_FLOW = "LoginFlow"
        }
    }

    /**
     * Guides the user through the authentication process.
     */
    class GpodderAuthenticationFragment : DialogFragment() {
        private var viewFlipper: ViewFlipper? = null
        private var currentStep = -1
        private var service: GpodnetService? = null
        @Volatile
        private var username: String? = null
        @Volatile
        private var password: String? = null
        @Volatile
        private var selectedDevice: GpodnetDevice? = null
        private var devices: List<GpodnetDevice>? = null

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setTitle(R.string.gpodnetauth_login_butLabel)
            dialog.setNegativeButton(R.string.cancel_label, null)
            dialog.setCancelable(false)
            this.isCancelable = false
            val binding = GpodnetauthDialogBinding.inflate(layoutInflater)
//        val root = View.inflate(context, R.layout.gpodnetauth_dialog, null)
            viewFlipper = binding.viewflipper
            advance()
            dialog.setView(binding.root)

            return dialog.create()
        }
        private fun setupHostView(view: View) {
            val binding = GpodnetauthHostBinding.bind(view)
            val selectHost = binding.chooseHostButton
            val serverUrlText = binding.serverUrlText
            selectHost.setOnClickListener {
                if (serverUrlText.text.isNullOrEmpty()) return@setOnClickListener

                SynchronizationCredentials.clear(requireContext())
                SynchronizationCredentials.hosturl = serverUrlText.text.toString()
                service = GpodnetService(getHttpClient(), SynchronizationCredentials.hosturl, SynchronizationCredentials.deviceID?:"",
                    SynchronizationCredentials.username?:"", SynchronizationCredentials.password?:"")
                dialog?.setTitle(SynchronizationCredentials.hosturl)
                advance()
            }
        }
        private fun setupLoginView(view: View) {
            val binding = GpodnetauthCredentialsBinding.bind(view)
            val username = binding.etxtUsername
            val password = binding.etxtPassword
            val login = binding.butLogin
            val txtvError = binding.credentialsError
            val progressBar = binding.progBarLogin
            val createAccountWarning = binding.createAccountWarning

            if (SynchronizationCredentials.hosturl != null && SynchronizationCredentials.hosturl!!.startsWith("http://"))
                createAccountWarning.visibility = View.VISIBLE

            password.setOnEditorActionListener { _: TextView?, actionID: Int, _: KeyEvent? -> actionID == EditorInfo.IME_ACTION_GO && login.performClick() }

            login.setOnClickListener {
                val usernameStr = username.text.toString()
                val passwordStr = password.text.toString()

                if (usernameHasUnwantedChars(usernameStr)) {
                    txtvError.setText(R.string.gpodnetsync_username_characters_error)
                    txtvError.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                login.isEnabled = false
                progressBar.visibility = View.VISIBLE
                txtvError.visibility = View.GONE
                val inputManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputManager.hideSoftInputFromWindow(login.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            service?.setCredentials(usernameStr, passwordStr)
                            service?.login()
                            if (service != null) devices = service!!.devices
                            this@GpodderAuthenticationFragment.username = usernameStr
                            this@GpodderAuthenticationFragment.password = passwordStr
                        }
                        withContext(Dispatchers.Main) {
                            login.isEnabled = true
                            progressBar.visibility = View.GONE
                            advance()
                        }
                    } catch (e: Throwable) {
                        login.isEnabled = true
                        progressBar.visibility = View.GONE
                        txtvError.text = e.cause!!.message
                        txtvError.visibility = View.VISIBLE
                    }
                }
            }
        }
        private fun setupDeviceView(view: View) {
            val binding = GpodnetauthDeviceBinding.bind(view)
            val deviceName = binding.deviceName
            val devicesContainer = binding.devicesContainer
            deviceName.setText(generateDeviceName())

            val createDeviceButton = binding.createDeviceButton
            createDeviceButton.setOnClickListener { createDevice(view) }

            for (device in devices!!) {
                val rBinding = GpodnetauthDeviceRowBinding.inflate(layoutInflater)
//            val row = View.inflate(context, R.layout.gpodnetauth_device_row, null)
                val selectDeviceButton = rBinding.selectDeviceButton
                selectDeviceButton.setOnClickListener {
                    selectedDevice = device
                    advance()
                }
                selectDeviceButton.text = device.caption
                devicesContainer.addView(rBinding.root)
            }
        }
        private fun createDevice(view: View) {
            val binding = GpodnetauthDeviceBinding.bind(view)
            val deviceName = binding.deviceName
            val txtvError = binding.deviceSelectError
            val progBarCreateDevice = binding.progbarCreateDevice

            val deviceNameStr = deviceName.text.toString()
            if (isDeviceInList(deviceNameStr)) return

            progBarCreateDevice.visibility = View.VISIBLE
            txtvError.visibility = View.GONE
            deviceName.isEnabled = false

            lifecycleScope.launch {
                try {
                    val device = withContext(Dispatchers.IO) {
                        val deviceId = generateDeviceId(deviceNameStr)
                        service!!.configureDevice(deviceId, deviceNameStr, GpodnetDevice.DeviceType.MOBILE)
                        GpodnetDevice(deviceId, deviceNameStr, GpodnetDevice.DeviceType.MOBILE.toString(), 0)
                    }
                    withContext(Dispatchers.Main) {
                        progBarCreateDevice.visibility = View.GONE
                        selectedDevice = device
                        advance()
                    }
                } catch (e: Throwable) {
                    deviceName.isEnabled = true
                    progBarCreateDevice.visibility = View.GONE
                    txtvError.text = e.message
                    txtvError.visibility = View.VISIBLE
                }
            }
        }
        private fun generateDeviceName(): String {
            val baseName = getString(R.string.gpodnetauth_device_name_default, Build.MODEL)
            var name = baseName
            var num = 1
            while (isDeviceInList(name)) {
                name = "$baseName ($num)"
                num++
            }
            return name
        }
        private fun generateDeviceId(name: String): String {
            // devices names must be of a certain form:
            // https://gpoddernet.readthedocs.org/en/latest/api/reference/general.html#devices
            return generateFileName(name).replace("\\W".toRegex(), "_").lowercase()
        }
        private fun isDeviceInList(name: String): Boolean {
            if (devices == null) return false

            val id = generateDeviceId(name)
            for (device in devices!!) {
                if (device.id == id || device.caption == name) return true
            }
            return false
        }
        private fun setupFinishView(view: View) {
            val binding = GpodnetauthFinishBinding.bind(view)
            val sync = binding.butSyncNow

            sync.setOnClickListener {
                dismiss()
                SyncService.sync(requireContext())
            }
        }
        private fun advance() {
            if (currentStep < STEP_FINISH) {
                val view = viewFlipper!!.getChildAt(currentStep + 1)
                when (currentStep) {
                    STEP_DEFAULT -> setupHostView(view)
                    STEP_HOSTNAME -> setupLoginView(view)
                    STEP_LOGIN -> {
                        check(!(username == null || password == null)) { "Username and password must not be null here" }
                        setupDeviceView(view)
                    }
                    STEP_DEVICE -> {
                        checkNotNull(selectedDevice) { "Device must not be null here" }
                        setSelectedSyncProvider(SynchronizationProviderViewData.GPODDER_NET)
                        SynchronizationCredentials.username = username
                        SynchronizationCredentials.password = password
                        SynchronizationCredentials.deviceID = selectedDevice!!.id
                        setupFinishView(view)
                    }
                }
                if (currentStep != STEP_DEFAULT) viewFlipper!!.showNext()
                currentStep++
            } else dismiss()
        }
        private fun usernameHasUnwantedChars(username: String): Boolean {
            val special = Pattern.compile("[!@#$%&*()+=|<>?{}\\[\\]~]")
            val containsUnwantedChars = special.matcher(username)
            return containsUnwantedChars.find()
        }

        companion object {
            val TAG = GpodderAuthenticationFragment::class.simpleName ?: "Anonymous"

            private const val STEP_DEFAULT = -1
            private const val STEP_HOSTNAME = 0
            private const val STEP_LOGIN = 1
            private const val STEP_DEVICE = 2
            private const val STEP_FINISH = 3
        }
    }

     class WifiAuthenticationFragment : DialogFragment() {
        private var binding: WifiSyncDialogBinding? = null
        private var portNum = 0
        private var isGuest: Boolean? = null

         override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = MaterialAlertDialogBuilder(requireContext())
            dialog.setTitle(R.string.connect_to_peer)
            dialog.setNegativeButton(R.string.cancel_label, null)
            dialog.setPositiveButton(R.string.confirm_label, null)

            binding = WifiSyncDialogBinding.inflate(layoutInflater)
            dialog.setView(binding!!.root)

            binding!!.hostAddressText.setText(SynchronizationCredentials.hosturl?:"")
            portNum = SynchronizationCredentials.hostport
            if (portNum == 0) portNum = hostPort
            binding!!.hostPortText.setText(portNum.toString())

            binding!!.guestButton.setOnClickListener {
                binding!!.hostAddressText.visibility = View.VISIBLE
                binding!!.hostPortText.visibility = View.VISIBLE
                binding!!.hostButton.visibility = View.INVISIBLE
                SynchronizationCredentials.hosturl = binding!!.hostAddressText.text.toString()
                portNum = binding!!.hostPortText.text.toString().toInt()
                isGuest = true
                SynchronizationCredentials.hostport = portNum
            }
            binding!!.hostButton.setOnClickListener {
                binding!!.hostAddressText.visibility = View.VISIBLE
                binding!!.hostPortText.visibility = View.VISIBLE
                binding!!.guestButton.visibility = View.INVISIBLE
                val wifiManager = requireContext().applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val ipAddress = wifiManager.connectionInfo.ipAddress
                val ipString = String.format(Locale.US, "%d.%d.%d.%d", ipAddress and 0xff, ipAddress shr 8 and 0xff, ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff)
                binding!!.hostAddressText.setText(ipString)
                binding!!.hostAddressText.isEnabled = false
                portNum = binding!!.hostPortText.text.toString().toInt()
                isGuest = false
                SynchronizationCredentials.hostport = portNum
            }
            procFlowEvents()
            return dialog.create()
        }
        override fun onDestroy() {
            cancelFlowEvents()
            super.onDestroy()
        }
         override fun onResume() {
            super.onResume()
            val d = dialog as? AlertDialog
            if (d != null) {
                val confirmButton = d.getButton(Dialog.BUTTON_POSITIVE) as Button
                confirmButton.setOnClickListener {
                    Logd(TAG, "confirm button pressed")
                    if (isGuest == null) {
                        Toast.makeText(requireContext(), R.string.host_or_guest, Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    binding!!.progressContainer.visibility = View.VISIBLE
                    confirmButton.visibility = View.INVISIBLE
                    val cancelButton = d.getButton(Dialog.BUTTON_NEGATIVE) as Button
                    cancelButton.visibility = View.INVISIBLE
                    portNum = binding!!.hostPortText.text.toString().toInt()
                    setWifiSyncEnabled(true)
                    startInstantSync(requireContext(), portNum, binding!!.hostAddressText.text.toString(), isGuest!!)
                }
            }
        }

        private var eventSink: Job?     = null
        private fun cancelFlowEvents() {
            eventSink?.cancel()
            eventSink = null
        }
        private fun procFlowEvents() {
            if (eventSink != null) return
            eventSink = lifecycleScope.launch {
                EventFlow.events.collectLatest { event ->
                    Logd(TAG, "Received event: ${event.TAG}")
                    when (event) {
                        is FlowEvent.SyncServiceEvent -> syncStatusChanged(event)
                        else -> {}
                    }
                }
            }
        }
        fun syncStatusChanged(event: FlowEvent.SyncServiceEvent) {
            when (event.messageResId) {
                R.string.sync_status_error -> {
                    Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                    dialog?.dismiss()
                }
                R.string.sync_status_success -> {
                    Toast.makeText(requireContext(), R.string.sync_status_success, Toast.LENGTH_LONG).show()
                    dialog?.dismiss()
                }
                R.string.sync_status_in_progress -> {
                    binding!!.progressBar.progress = event.message.toInt()
                }
                else -> {
                    Logd(TAG, "Sync result unknow ${event.messageResId}")
//                Toast.makeText(context, "Sync result unknow ${event.messageResId}", Toast.LENGTH_LONG).show()
                }
            }
        }

        companion object {
            val TAG = WifiAuthenticationFragment::class.simpleName ?: "Anonymous"
        }
    }

    @Suppress("EnumEntryName")
    private enum class Prefs {
        preference_instant_sync,
        preference_synchronization_description,
        pref_gpodnet_setlogin_information,
        pref_synchronization_sync,
        pref_synchronization_force_full_sync,
        pref_synchronization_logout,
    }
}
