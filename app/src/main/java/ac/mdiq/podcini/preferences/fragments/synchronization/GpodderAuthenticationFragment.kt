package ac.mdiq.podcini.preferences.fragments.synchronization

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.*
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.SynchronizationCredentials
import ac.mdiq.podcini.net.sync.SynchronizationProviderViewData
import ac.mdiq.podcini.net.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.gpoddernet.GpodnetService
import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetDevice
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.util.FileNameGenerator.generateFileName
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.regex.Pattern
import kotlin.concurrent.Volatile

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
            Completable.fromAction {
                service?.setCredentials(usernameStr, passwordStr)
                service?.login()
                if (service != null) devices = service!!.devices
                this@GpodderAuthenticationFragment.username = usernameStr
                this@GpodderAuthenticationFragment.password = passwordStr
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    login.isEnabled = true
                    progressBar.visibility = View.GONE
                    advance()
                }, { error: Throwable ->
                    login.isEnabled = true
                    progressBar.visibility = View.GONE
                    txtvError.text = error.cause!!.message
                    txtvError.visibility = View.VISIBLE
                })
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

        Observable.fromCallable {
            val deviceId = generateDeviceId(deviceNameStr)
            service!!.configureDevice(deviceId, deviceNameStr, GpodnetDevice.DeviceType.MOBILE)
            GpodnetDevice(deviceId, deviceNameStr, GpodnetDevice.DeviceType.MOBILE.toString(), 0)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ device: GpodnetDevice? ->
                progBarCreateDevice.visibility = View.GONE
                selectedDevice = device
                advance()
            }, { error: Throwable ->
                deviceName.isEnabled = true
                progBarCreateDevice.visibility = View.GONE
                txtvError.text = error.message
                txtvError.visibility = View.VISIBLE
            })
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
                    SynchronizationSettings.setSelectedSyncProvider(SynchronizationProviderViewData.GPODDER_NET)
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
        const val TAG: String = "GpodnetAuthActivity"

        private const val STEP_DEFAULT = -1
        private const val STEP_HOSTNAME = 0
        private const val STEP_LOGIN = 1
        private const val STEP_DEVICE = 2
        private const val STEP_FINISH = 3
    }
}
