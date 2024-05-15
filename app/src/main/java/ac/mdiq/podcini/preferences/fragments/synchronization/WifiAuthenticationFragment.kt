package ac.mdiq.podcini.preferences.fragments.synchronization

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.WifiSyncDialogBinding
import ac.mdiq.podcini.net.sync.SynchronizationCredentials
import ac.mdiq.podcini.net.sync.SynchronizationSettings.setWifiSyncEnabled
import ac.mdiq.podcini.net.sync.wifi.WifiSyncService.Companion.hostPort
import ac.mdiq.podcini.net.sync.wifi.WifiSyncService.Companion.startInstantSync
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.SyncServiceEvent
import android.app.Dialog
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

@OptIn(UnstableApi::class) class WifiAuthenticationFragment : DialogFragment() {
    private var binding: WifiSyncDialogBinding? = null
    private var portNum = 0
    private var isGuest: Boolean? = null

    @OptIn(UnstableApi::class) override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
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
        EventBus.getDefault().register(this)
        return dialog.create()
    }

    @OptIn(UnstableApi::class) override fun onResume() {
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun syncStatusChanged(event: SyncServiceEvent) {
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
        const val TAG: String = "WifiAuthenticationFragment"
    }
}
