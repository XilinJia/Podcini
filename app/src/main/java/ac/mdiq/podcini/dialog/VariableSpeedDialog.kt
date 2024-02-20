package ac.mdiq.podcini.dialog

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.util.playback.PlaybackController
import ac.mdiq.podcini.event.playback.SpeedChangedEvent
import ac.mdiq.podcini.storage.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.storage.preferences.UserPreferences.playbackSpeedArray
import ac.mdiq.podcini.view.ItemOffsetDecoration
import ac.mdiq.podcini.view.PlaybackSpeedSeekBar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DecimalFormatSymbols
import java.util.*

open class VariableSpeedDialog : BottomSheetDialogFragment() {
    private var adapter: SpeedSelectionAdapter? = null
    private var controller: PlaybackController? = null
    private val selectedSpeeds: MutableList<Float>
    private var speedSeekBar: PlaybackSpeedSeekBar? = null
    private var addCurrentSpeedChip: Chip? = null

    init {
        val format = DecimalFormatSymbols(Locale.US)
        format.decimalSeparator = '.'
        selectedSpeeds = ArrayList(playbackSpeedArray)
    }

    @UnstableApi override fun onStart() {
        super.onStart()
        controller = object : PlaybackController(activity) {
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
        speedSeekBar!!.updateSpeed(event.newSpeed)
        addCurrentSpeedChip!!.text = String.format(Locale.getDefault(), "%1$.2f", event.newSpeed)
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                           savedInstanceState: Bundle?
    ): View? {
        val root = View.inflate(context, R.layout.speed_select_dialog, null)
        speedSeekBar = root.findViewById(R.id.speed_seek_bar)
        speedSeekBar?.setProgressChangedListener { multiplier: Float? ->
            controller?.setPlaybackSpeed(multiplier!!)
        }
        val selectedSpeedsGrid = root.findViewById<RecyclerView>(R.id.selected_speeds_grid)
        selectedSpeedsGrid.layoutManager = GridLayoutManager(context, 3)
        selectedSpeedsGrid.addItemDecoration(ItemOffsetDecoration(requireContext(), 4))
        adapter = SpeedSelectionAdapter()
        adapter!!.setHasStableIds(true)
        selectedSpeedsGrid.adapter = adapter

        addCurrentSpeedChip = root.findViewById(R.id.add_current_speed_chip)
        addCurrentSpeedChip?.isCloseIconVisible = true
        addCurrentSpeedChip?.setCloseIconResource(R.drawable.ic_add)
        addCurrentSpeedChip?.setOnCloseIconClickListener { v: View? -> addCurrentSpeed() }
        addCurrentSpeedChip?.closeIconContentDescription = getString(R.string.add_preset)
        addCurrentSpeedChip?.setOnClickListener { v: View? -> addCurrentSpeed() }

        val skipSilence = root.findViewById<CheckBox>(R.id.skipSilence)
        skipSilence.isChecked = isSkipSilence
        skipSilence.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            isSkipSilence = isChecked
            controller!!.setSkipSilence(isChecked)
        }
        return root
    }

    private fun addCurrentSpeed() {
        val newSpeed = speedSeekBar!!.currentSpeed
        if (selectedSpeeds.contains(newSpeed)) {
            Snackbar.make(addCurrentSpeedChip!!,
                getString(R.string.preset_already_exists, newSpeed), Snackbar.LENGTH_LONG).show()
        } else {
            selectedSpeeds.add(newSpeed)
            selectedSpeeds.sort()
            playbackSpeedArray = selectedSpeeds
            adapter!!.notifyDataSetChanged()
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
            holder.chip.setOnLongClickListener { v: View? ->
                selectedSpeeds.remove(speed)
                playbackSpeedArray = selectedSpeeds
                notifyDataSetChanged()
                true
            }
            holder.chip.setOnClickListener { v: View? ->
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
