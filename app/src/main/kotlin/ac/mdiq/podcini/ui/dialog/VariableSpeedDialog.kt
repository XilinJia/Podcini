package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SpeedSelectDialogBinding
import ac.mdiq.podcini.playback.PlaybackController.Companion.curSpeedMultiplier
import ac.mdiq.podcini.playback.PlaybackController.Companion.playbackService
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.currentMediaType
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.UserPreferences.playbackSpeedArray
import ac.mdiq.podcini.preferences.UserPreferences.videoPlaybackSpeed
import ac.mdiq.podcini.storage.database.Feeds.persistFeedPreferences
import ac.mdiq.podcini.storage.database.RealmDB.unmanagedCopy
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.ui.utils.ItemOffsetDecoration
import ac.mdiq.podcini.ui.view.PlaybackSpeedSeekBar
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DecimalFormatSymbols
import java.util.*

@OptIn(UnstableApi::class) open class VariableSpeedDialog : BottomSheetDialogFragment() {

    private lateinit var adapter: SpeedSelectionAdapter
    private lateinit var speedSeekBar: PlaybackSpeedSeekBar
    private lateinit var addCurrentSpeedChip: Chip

    private var _binding: SpeedSelectDialogBinding? = null
    private val binding get() = _binding!!

    private val selectedSpeeds: MutableList<Float>

    private lateinit var settingCode: BooleanArray

    init {
        val format = DecimalFormatSymbols(Locale.US)
        format.decimalSeparator = '.'
        selectedSpeeds = ArrayList(playbackSpeedArray)
    }

    @UnstableApi override fun onStart() {
        super.onStart()
        Logd(TAG, "onStart: playbackService ready: ${playbackService?.isServiceReady()}")

        binding.currentAudio.visibility = View.VISIBLE
        binding.currentPodcast.visibility = View.VISIBLE

        if (!settingCode[0]) binding.currentAudio.visibility = View.INVISIBLE
        if (!settingCode[1]) binding.currentPodcast.visibility = View.INVISIBLE
        if (!settingCode[2]) binding.global.visibility = View.INVISIBLE

        procFlowEvents()
        updateSpeed(FlowEvent.SpeedChangedEvent(curSpeedMultiplier))
    }

    @UnstableApi override fun onStop() {
        super.onStop()
        cancelFlowEvents()
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
                when (event) {
                    is FlowEvent.SpeedChangedEvent -> updateSpeed(event)
                    else -> {}
                }
            }
        }
    }

    private fun updateSpeed(event: FlowEvent.SpeedChangedEvent) {
        speedSeekBar.updateSpeed(event.newSpeed)
        addCurrentSpeedChip.text = String.format(Locale.getDefault(), "%1$.2f", event.newSpeed)
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = SpeedSelectDialogBinding.inflate(inflater)

        settingCode = (arguments?.getBooleanArray("settingCode") ?: BooleanArray(3) {true})
        val index_default = arguments?.getInt("index_default")

        when (index_default) {
            null, 0 -> binding.currentAudio.isChecked = true
            1 -> binding.currentPodcast.isChecked = true
            else -> binding.global.isChecked = true
        }

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
//            setSkipSilence(isChecked)
            playbackService?.mediaPlayer?.setPlaybackParams(playbackService!!.currentPlaybackSpeed, isChecked)
        }

        return binding.root
    }

    @OptIn(UnstableApi::class) override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (playbackService?.isServiceReady() == false) {
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
            Snackbar.make(addCurrentSpeedChip, getString(R.string.preset_already_exists, newSpeed), Snackbar.LENGTH_LONG).show()
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
                Logd("VariableSpeedDialog", "holder.chip settingCode0: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                settingCode[0] = binding.currentAudio.isChecked
                settingCode[1] = binding.currentPodcast.isChecked
                settingCode[2] = binding.global.isChecked
                Logd("VariableSpeedDialog", "holder.chip settingCode: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                dismiss()
                setPlaybackSpeed(speed, settingCode)
            }, 200) }
        }

        override fun getItemCount(): Int {
            return selectedSpeeds.size
        }

        override fun getItemId(position: Int): Long {
            return selectedSpeeds[position].hashCode().toLong()
        }

        private fun setPlaybackSpeed(speed: Float, codeArray: BooleanArray? = null) {
            if (playbackService != null) {
//                playbackService!!.setSpeed(speed, codeArray)
                playbackService!!.isSpeedForward = false
                playbackService!!.isFallbackSpeed = false

                if (currentMediaType == MediaType.VIDEO) {
                    curState.curTempSpeed = speed
                    videoPlaybackSpeed = speed
                    playbackService!!.mediaPlayer?.setPlaybackParams(speed, isSkipSilence)
                } else {
                    if (codeArray != null && codeArray.size == 3) {
                        Logd(TAG, "setSpeed codeArray: ${codeArray[0]} ${codeArray[1]} ${codeArray[2]}")
                        if (codeArray[2]) UserPreferences.setPlaybackSpeed(speed)
                        if (codeArray[1]) {
                            val episode = (playbackService!!.playable as? EpisodeMedia)?.episode ?: playbackService!!.currentitem
                            if (episode != null) {
                                var feed = episode.feed
                                if (feed != null) {
                                    feed = unmanagedCopy(feed)
                                    val feedPrefs = feed.preferences
                                    if (feedPrefs != null) {
                                        feedPrefs.playSpeed = speed
                                        persistFeedPreferences(feed)
                                        Logd(TAG, "persisted feed speed ${feed.title} $speed")
//                                EventFlow.postEvent(FlowEvent.FeedPrefsChangeEvent(feedPrefs))
                                    }
                                }
                            }
                        }
                        if (codeArray[0]) {
                            curState.curTempSpeed = speed
                            playbackService!!.mediaPlayer?.setPlaybackParams(speed, isSkipSilence)
                        }
                    } else {
                        curState.curTempSpeed = speed
                        playbackService!!.mediaPlayer?.setPlaybackParams(speed, isSkipSilence)
                    }
                }
            }
            else {
                UserPreferences.setPlaybackSpeed(speed)
                EventFlow.postEvent(FlowEvent.SpeedChangedEvent(speed))
            }
        }

        inner class ViewHolder internal constructor(var chip: Chip) : RecyclerView.ViewHolder(chip)
    }

    companion object {
        private val TAG: String = VariableSpeedDialog::class.simpleName ?: "Anonymous"

        /**
         *  @param settingCode_ array at input indicate which categories can be set, at output which categories are changed
        * @param index_default indicates which category is checked by default
         */
        fun newInstance(settingCode_: BooleanArray? = null, index_default: Int? = null): VariableSpeedDialog? {
            val settingCode = settingCode_ ?: BooleanArray(3){true}
            Logd("VariableSpeedDialog", "newInstance settingCode: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
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
