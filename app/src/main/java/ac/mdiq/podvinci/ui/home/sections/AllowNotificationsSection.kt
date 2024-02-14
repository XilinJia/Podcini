package ac.mdiq.podvinci.ui.home.sections

import ac.mdiq.podvinci.activity.MainActivity
import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.databinding.HomeSectionNotificationBinding
import ac.mdiq.podvinci.ui.home.HomeFragment

class AllowNotificationsSection : Fragment() {
    var viewBinding: HomeSectionNotificationBinding? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                (activity as MainActivity).loadFragment(HomeFragment.TAG, null)
            } else {
                viewBinding!!.openSettingsButton.visibility = View.VISIBLE
                viewBinding!!.allowButton.visibility = View.GONE
                Toast.makeText(context, R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        viewBinding = HomeSectionNotificationBinding.inflate(inflater)
        viewBinding!!.allowButton.setOnClickListener { v: View? ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewBinding!!.openSettingsButton.setOnClickListener { view: View? ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", requireContext().packageName, null)
            intent.setData(uri)
            startActivity(intent)
        }
        viewBinding!!.denyButton.setOnClickListener { v: View? ->
            val builder = MaterialAlertDialogBuilder(requireContext())
            builder.setMessage(R.string.notification_permission_deny_warning)
            builder.setPositiveButton(R.string.deny_label
            ) { dialog: DialogInterface?, which: Int ->
                requireContext().getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(HomeFragment.PREF_DISABLE_NOTIFICATION_PERMISSION_NAG, true).apply()
                (activity as MainActivity).loadFragment(HomeFragment.TAG, null)
            }
            builder.setNegativeButton(R.string.cancel_label, null)
            builder.show()
        }
        return viewBinding!!.root
    }
}
