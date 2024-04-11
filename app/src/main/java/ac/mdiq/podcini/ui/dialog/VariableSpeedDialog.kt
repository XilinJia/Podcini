package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SpeedSelectDialogBinding
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.util.event.playback.SpeedChangedEvent
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.UserPreferences.playbackSpeedArray
import ac.mdiq.podcini.ui.view.ItemOffsetDecoration
import ac.mdiq.podcini.ui.view.PlaybackSpeedSeekBar
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DecimalFormatSymbols
import java.util.*

open class VariableSpeedDialog : BottomSheetDialogFragment() {

    private lateinit var adapter: SpeedSelectionAdapter
    private lateinit var speedSeekBar: PlaybackSpeedSeekBar
    private lateinit var addCurrentSpeedChip: Chip

    private var _binding: SpeedSelectDialogBinding? = null
    private val binding get() = _binding!!

    private var controller: PlaybackController? = null
    private val selectedSpeeds: MutableList<Float>

    private lateinit var settingCode: BooleanArray

    init {
        val format = DecimalFormatSymbols(Locale.US)
        format.decimalSeparator = '.'
        selectedSpeeds = ArrayList(playbackSpeedArray)
    }

    @UnstableApi override fun onStart() {
        super.onStart()
        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                if (controller != null) updateSpeed(SpeedChangedEvent(controller!!.currentPlaybackSpeedMultiplier))
            }

            override fun onPlaybackServiceConnected() {
                super.onPlaybackServiceConnected()
                binding.currentAudio.visibility = View.VISIBLE
                binding.currentPodcast.visibility = View.VISIBLE

                if (!settingCode[0]) binding.currentAudio.visibility = View.INVISIBLE
                if (!settingCode[1]) binding.currentPodcast.visibility = View.INVISIBLE
                if (!settingCode[2]) binding.global.visibility = View.INVISIBLE
            }
        }
        controller?.init()

        EventBus.getDefault().register(this)
        if (controller != null) updateSpeed(SpeedChangedEvent(controller!!.currentPlaybackSpeedMultiplier))
    }

    @UnstableApi override fun onStop() {
        super.onStop()
        controller?.release()
        controller = null
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updateSpeed(event: SpeedChangedEvent) {
        speedSeekBar.updateSpeed(event.newSpeed)
        addCurrentSpeedChip.text = String.format(Locale.getDefault(), "%1$.2f", event.newSpeed)
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                           savedInstanceState: Bundle?
    ): View? {
        _binding = SpeedSelectDialogBinding.inflate(inflater)

        settingCode = (arguments?.getBooleanArray("settingCode") ?: BooleanArray(3) {true})
        val index_default = arguments?.getInt("index_default")

        when (index_default) {
            null, 0 -> {
                binding.currentAudio.isChecked = true
            }
            1 -> {
                binding.currentPodcast.isChecked = true
            }
            else -> {
                binding.global.isChecked = true
            }
        }
//        if (!settingCode[0]) binding.currentAudio.visibility = View.INVISIBLE
//        if (!settingCode[1]) binding.currentPodcast.visibility = View.INVISIBLE
//        if (!settingCode[2]) binding.global.visibility = View.INVISIBLE

        speedSeekBar = binding.speedSeekBar
        speedSeekBar.setProgressChangedListener { multiplier: Float ->
            addCurrentSpeedChip.text = String.format(Locale.getDefault(), "%1$.2f", multiplier)
//            controller?.setPlaybackSpeed(multiplier)
        }
        val selectedSpeedsGrid = binding.selectedSpeedsGrid
        selectedSpeedsGrid.layoutManager = GridLayoutManager(context, 3)
        selectedSpeedsGrid.addItemDecoration(ItemOffsetDecoration(requireContext(), 4))
        adapter = SpeedSelectionAdapter()
        adapter.setHasStableIds(true)
        selectedSpeedsGrid.adapter = adapter

        addCurrentSpeedChip = binding.addCurrentSpeedChip
        addCurrentSpeedChip.isCloseIconVisible = true
        addCurrentSpeedChip.setCloseIconResource(R.drawable.ic_add)
        addCurrentSpeedChip.setOnCloseIconClickListener { addCurrentSpeed() }
        addCurrentSpeedChip.closeIconContentDescription = getString(R.string.add_preset)
        addCurrentSpeedChip.setOnClickListener { addCurrentSpeed() }

        val skipSilence = binding.skipSilence
        skipSilence.isChecked = isSkipSilence
        skipSilence.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            isSkipSilence = isChecked
            controller?.setSkipSilence(isChecked)
        }

        return binding.root
    }

    @OptIn(UnstableApi::class) override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (controller == null || !controller!!.isPlaybackServiceReady()) {
            binding.currentAudio.visibility = View.INVISIBLE
            binding.currentPodcast.visibility = View.INVISIBLE
        } else {
            binding.currentAudio.visibility = View.VISIBLE
            binding.currentPodcast.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun addCurrentSpeed() {
        val newSpeed = speedSeekBar.currentSpeed
        if (selectedSpeeds.contains(newSpeed)) {
            Snackbar.make(addCurrentSpeedChip,
                getString(R.string.preset_already_exists, newSpeed), Snackbar.LENGTH_LONG).show()
        } else {
            selectedSpeeds.add(newSpeed)
            selectedSpeeds.sort()
            playbackSpeedArray = selectedSpeeds
            adapter.notifyDataSetChanged()
        }
    }

    inner class SpeedSelectionAdapter : RecyclerView.Adapter<SpeedSelectionAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val chip = Chip(context)
            chip.textAlignment = View.TEXT_ALIGNMENT_CENTER
            return ViewHolder(chip)
        }

        @UnstableApi override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val speed = selectedSpeeds[position]

            holder.chip.text = String.format(Locale.getDefault(), "%1$.2f", speed)
            holder.chip.setOnLongClickListener {
                selectedSpeeds.remove(speed)
                playbackSpeedArray = selectedSpeeds
                notifyDataSetChanged()
                true
            }
            holder.chip.setOnClickListener { Handler(Looper.getMainLooper()).postDelayed({
                if (binding.currentAudio.isChecked) settingCode[0] = true
                if (binding.currentPodcast.isChecked) settingCode[1] = true
                if (binding.global.isChecked) settingCode[2] = true

                if (controller != null) {
                    dismiss()
                    controller!!.setPlaybackSpeed(speed, settingCode)
                }
            }, 200) }
        }

        override fun getItemCount(): Int {
            return selectedSpeeds.size
        }

        override fun getItemId(position: Int): Long {
            return selectedSpeeds[position].hashCode().toLong()
        }

        inner class ViewHolder internal constructor(var chip: Chip) : RecyclerView.ViewHolder(
            chip)
    }

    companion object {
        fun newInstance(settingCode_: BooleanArray? = null, index_default: Int? = null): VariableSpeedDialog? {
            val settingCode = settingCode_ ?: BooleanArray(3){false}
            if (settingCode.size != 3) {
                Log.e("VariableSpeedDialog", "wrong settingCode dimension")
                return null
            }
            val dialog = VariableSpeedDialog()
            val args = Bundle()
            args.putBooleanArray("settingCode", settingCode)
            if (index_default != null) args.putInt("index_default", index_default)
            dialog.arguments = args

            return dialog
        }
    }
}
