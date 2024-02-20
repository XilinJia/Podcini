package ac.mdiq.podcini.fragment.preferences.synchronization

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.service.download.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.core.sync.SyncService
import ac.mdiq.podcini.core.sync.SynchronizationCredentials
import ac.mdiq.podcini.core.sync.SynchronizationProviderViewData
import ac.mdiq.podcini.core.sync.SynchronizationSettings
import ac.mdiq.podcini.databinding.NextcloudAuthDialogBinding
import ac.mdiq.podcini.net.sync.nextcloud.NextcloudLoginFlow

/**
 * Guides the user through the authentication process.
 */
class NextcloudAuthenticationFragment : DialogFragment(), NextcloudLoginFlow.AuthenticationCallback {
    private var viewBinding: NextcloudAuthDialogBinding? = null
    private var nextcloudLoginFlow: NextcloudLoginFlow? = null
    private var shouldDismiss = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setTitle(R.string.gpodnetauth_login_butLabel)
        dialog.setNegativeButton(R.string.cancel_label, null)
        dialog.setCancelable(false)
        this.isCancelable = false

        viewBinding = NextcloudAuthDialogBinding.inflate(layoutInflater)
        dialog.setView(viewBinding!!.root)

        viewBinding!!.chooseHostButton.setOnClickListener { v: View? ->
            nextcloudLoginFlow = NextcloudLoginFlow(getHttpClient(),
                viewBinding!!.serverUrlText.text.toString(), requireContext(), this)
            startLoginFlow()
        }
        if (savedInstanceState?.getStringArrayList(EXTRA_LOGIN_FLOW) != null) {
            nextcloudLoginFlow = NextcloudLoginFlow.fromInstanceState(getHttpClient(),
                requireContext(), this, savedInstanceState.getStringArrayList(EXTRA_LOGIN_FLOW)!!)
            startLoginFlow()
        }
        return dialog.create()
    }

    private fun startLoginFlow() {
        viewBinding!!.errorText.visibility = View.GONE
        viewBinding!!.chooseHostButton.visibility = View.GONE
        viewBinding!!.loginProgressContainer.visibility = View.VISIBLE
        viewBinding!!.serverUrlText.isEnabled = false
        nextcloudLoginFlow!!.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (nextcloudLoginFlow != null) {
            outState.putStringArrayList(EXTRA_LOGIN_FLOW, nextcloudLoginFlow!!.saveInstanceState())
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (nextcloudLoginFlow != null) {
            nextcloudLoginFlow!!.cancel()
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldDismiss) {
            dismiss()
        }
    }

    override fun onNextcloudAuthenticated(server: String, username: String, password: String) {
        SynchronizationSettings.setSelectedSyncProvider(SynchronizationProviderViewData.NEXTCLOUD_GPODDER)
        SynchronizationCredentials.clear(requireContext())
        SynchronizationCredentials.password = password
        SynchronizationCredentials.hosturl = server
        SynchronizationCredentials.username = username
        SyncService.fullSync(context)
        if (isResumed) {
            dismiss()
        } else {
            shouldDismiss = true
        }
    }

    override fun onNextcloudAuthError(errorMessage: String?) {
        viewBinding!!.loginProgressContainer.visibility = View.GONE
        viewBinding!!.errorText.visibility = View.VISIBLE
        viewBinding!!.errorText.text = errorMessage
        viewBinding!!.chooseHostButton.visibility = View.VISIBLE
        viewBinding!!.serverUrlText.isEnabled = true
    }

    companion object {
        const val TAG: String = "NextcloudAuthenticationFragment"
        private const val EXTRA_LOGIN_FLOW = "LoginFlow"
    }
}
