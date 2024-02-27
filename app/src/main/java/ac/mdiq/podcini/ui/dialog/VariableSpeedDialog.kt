package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SpeedSelectDialogBinding
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.playback.event.SpeedChangedEvent
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.UserPreferences.playbackSpeedArray
import ac.mdiq.podcini.ui.view.ItemOffsetDecoration
import ac.mdiq.podcini.ui.view.PlaybackSpeedSeekBar
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
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

    private var controller: PlaybackController? = null
    private val selectedSpeeds: MutableList<Float>

    init {
        val format = DecimalFormatSymbols(Locale.US)
        format.decimalSeparator = '.'
        selectedSpeeds = ArrayList(playbackSpeedArray)
    }

    @UnstableApi override fun onStart() {
        super.onStart()
        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                updateSpeed(SpeedChangedEvent(controller!!.currentPlaybackSpeedMultiplier))
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
        val binding = SpeedSelectDialogBinding.inflate(inflater)
//        val root = View.inflate(context, R.layout.speed_select_dialog, null)
        speedSeekBar = binding.speedSeekBar
        speedSeekBar.setProgressChangedListener { multiplier: Float? ->
            controller?.setPlaybackSpeed(multiplier!!)
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
            controller!!.setSkipSilence(isChecked)
        }
        return binding.root
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
            holder.chip.setOnClickListener {
                Handler(Looper.getMainLooper()).postDelayed(
                    {
                        if (controller != null) {
                            dismiss()
                            controller!!.setPlaybackSpeed(speed)
                        }
                    }, 200)
            }
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
}
