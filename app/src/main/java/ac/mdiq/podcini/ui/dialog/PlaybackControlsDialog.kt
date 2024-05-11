package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AudioControlsBinding
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.playback.PlaybackController.Companion.audioTracks
import ac.mdiq.podcini.playback.PlaybackController.Companion.selectedAudioTrack
import ac.mdiq.podcini.playback.PlaybackController.Companion.setAudioTrack
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PlaybackControlsDialog : DialogFragment() {
    private lateinit var dialog: AlertDialog
    private var _binding: AudioControlsBinding? = null
    private val binding get() = _binding!!

    private var controller: PlaybackController? = null

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
        _binding = AudioControlsBinding.inflate(layoutInflater)
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.audio_controls)
            .setView(R.layout.audio_controls)
            .setPositiveButton(R.string.close_label, null).create()
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @UnstableApi private fun setupAudioTracks() {
        val butAudioTracks = binding.audioTracks
        if (audioTracks.size < 2 || selectedAudioTrack < 0) {
            butAudioTracks.visibility = View.GONE
            return
        }

        butAudioTracks.visibility = View.VISIBLE
        butAudioTracks.text = audioTracks[selectedAudioTrack]
        butAudioTracks.setOnClickListener {
            setAudioTrack((selectedAudioTrack + 1) % audioTracks.size)
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
