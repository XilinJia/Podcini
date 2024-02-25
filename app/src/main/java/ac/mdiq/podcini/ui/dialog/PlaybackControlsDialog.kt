package ac.mdiq.podcini.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.PlaybackController

class PlaybackControlsDialog : DialogFragment() {
    private var controller: PlaybackController? = null
    private var dialog: AlertDialog? = null

    @UnstableApi override fun onStart() {
        super.onStart()
        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                setupAudioTracks()
            }
        }
        controller?.init()
    }

    @UnstableApi override fun onStop() {
        super.onStop()
        controller?.release()
        controller = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.audio_controls)
            .setView(R.layout.audio_controls)
            .setPositiveButton(R.string.close_label, null).create()
        return dialog!!
    }

    @UnstableApi private fun setupAudioTracks() {
        val audioTracks = controller!!.audioTracks
        val selectedAudioTrack = controller!!.selectedAudioTrack
        val butAudioTracks = dialog!!.findViewById<Button>(R.id.audio_tracks)
        if (audioTracks.size < 2 || selectedAudioTrack < 0) {
            butAudioTracks!!.visibility = View.GONE
            return
        }

        butAudioTracks!!.visibility = View.VISIBLE
        butAudioTracks.text = audioTracks[selectedAudioTrack]
        butAudioTracks.setOnClickListener {
            controller!!.setAudioTrack((selectedAudioTrack + 1) % audioTracks.size)
            Handler(Looper.getMainLooper()).postDelayed({ this.setupAudioTracks() }, 500)
        }
    }

    companion object {
        fun newInstance(): PlaybackControlsDialog {
            val arguments = Bundle()
            val dialog = PlaybackControlsDialog()
            dialog.arguments = arguments
            return dialog
        }
    }
}
