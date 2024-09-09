package ac.mdiq.podcini.ui.dialog

//import ac.mdiq.podcini.preferences.UserPreferences.videoPlaybackSpeed
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SpeedSelectDialogBinding
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curSpeedFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.ui.utils.ItemOffsetDecoration
import ac.mdiq.podcini.ui.view.PlaybackSpeedSeekBar
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
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
import org.json.JSONArray
import org.json.JSONException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

@OptIn(UnstableApi::class)
open class VariableSpeedDialog : BottomSheetDialogFragment() {
    private var _binding: SpeedSelectDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SpeedSelectionAdapter
    private lateinit var speedSeekBar: PlaybackSpeedSeekBar
    private lateinit var addCurrentSpeedChip: Chip
    private lateinit var settingCode: BooleanArray
    private val selectedSpeeds: MutableList<Float>

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
        updateSpeed(FlowEvent.SpeedChangedEvent(curSpeedFB))
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
        val indexDefault = arguments?.getInt(INDEX_DEFAULT)

        when (indexDefault) {
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
            playbackService?.mPlayer?.setPlaybackParams(playbackService!!.curSpeed, isChecked)
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
        Logd(TAG, "onDestroyView")
        _binding = null
        super.onDestroyView()
    }

    private fun addCurrentSpeed() {
        val newSpeed = speedSeekBar.currentSpeed
        if (selectedSpeeds.contains(newSpeed)) Snackbar.make(addCurrentSpeedChip, getString(R.string.preset_already_exists, newSpeed), Snackbar.LENGTH_LONG).show()
        else {
            selectedSpeeds.add(newSpeed)
            selectedSpeeds.sort()
            playbackSpeedArray = selectedSpeeds
            adapter.notifyDataSetChanged()
        }
    }

    var playbackSpeedArray: List<Float>
        get() = readPlaybackSpeedArray(appPrefs.getString(UserPreferences.Prefs.prefPlaybackSpeedArray.name, null))
        set(speeds) {
            val format = DecimalFormatSymbols(Locale.US)
            format.decimalSeparator = '.'
            val speedFormat = DecimalFormat("0.00", format)
            val jsonArray = JSONArray()
            for (speed in speeds) {
                jsonArray.put(speedFormat.format(speed.toDouble()))
            }
            appPrefs.edit().putString(UserPreferences.Prefs.prefPlaybackSpeedArray.name, jsonArray.toString()).apply()
        }

    private fun readPlaybackSpeedArray(valueFromPrefs: String?): List<Float> {
        if (valueFromPrefs != null) {
            try {
                val jsonArray = JSONArray(valueFromPrefs)
                val selectedSpeeds: MutableList<Float> = ArrayList()
                for (i in 0 until jsonArray.length()) {
                    selectedSpeeds.add(jsonArray.getDouble(i).toFloat())
                }
                return selectedSpeeds
            } catch (e: JSONException) {
                Log.e(TAG, "Got JSON error when trying to get speeds from JSONArray")
                e.printStackTrace()
            }
        }
        // If this preference hasn't been set yet, return the default options
        return mutableListOf(1.0f, 1.25f, 1.5f)
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

                if (codeArray != null && codeArray.size == 3) {
                    Logd(TAG, "setSpeed codeArray: ${codeArray[0]} ${codeArray[1]} ${codeArray[2]}")
                    if (codeArray[2]) UserPreferences.setPlaybackSpeed(speed)
                    if (codeArray[1]) {
                        val episode = (curMedia as? EpisodeMedia)?.episodeOrFetch() ?: curEpisode
                        if (episode?.feed?.preferences != null) upsertBlk(episode.feed!!) { it.preferences!!.playSpeed = speed }
                    }
                    if (codeArray[0]) {
                        setCurTempSpeed(speed)
                        playbackService!!.mPlayer?.setPlaybackParams(speed, isSkipSilence)
                    }
                } else {
                    setCurTempSpeed(speed)
                    playbackService!!.mPlayer?.setPlaybackParams(speed, isSkipSilence)
                }
            }
            else {
                UserPreferences.setPlaybackSpeed(speed)
                EventFlow.postEvent(FlowEvent.SpeedChangedEvent(speed))
            }
        }
        private fun setCurTempSpeed(speed: Float) {
            curState = upsertBlk(curState) { it.curTempSpeed = speed }
        }
        inner class ViewHolder internal constructor(var chip: Chip) : RecyclerView.ViewHolder(chip)
    }

    companion object {
        private val TAG: String = VariableSpeedDialog::class.simpleName ?: "Anonymous"
        private const val INDEX_DEFAULT = "index_default"

        /**
         *  @param settingCode_ array at input indicate which categories can be set, at output which categories are changed
        * @param indexDefault indicates which category is checked by default
         */
        fun newInstance(settingCode_: BooleanArray? = null, indexDefault: Int? = null): VariableSpeedDialog? {
            val settingCode = settingCode_ ?: BooleanArray(3){true}
            Logd("VariableSpeedDialog", "newInstance settingCode: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
            if (settingCode.size != 3) {
                Log.e("VariableSpeedDialog", "wrong settingCode dimension")
                return null
            }
            val dialog = VariableSpeedDialog()
            val args = Bundle()
            args.putBooleanArray("settingCode", settingCode)
            if (indexDefault != null) args.putInt(INDEX_DEFAULT, indexDefault)
            dialog.arguments = args
            return dialog
        }
    }
}
