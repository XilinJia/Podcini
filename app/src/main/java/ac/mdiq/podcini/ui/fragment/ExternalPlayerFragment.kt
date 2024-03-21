package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ExternalPlayerFragmentBinding
import ac.mdiq.podcini.feed.util.ImageResourceUtils.getEpisodeListImageLocation
import ac.mdiq.podcini.feed.util.ImageResourceUtils.getFallbackImageLocation
import ac.mdiq.podcini.feed.util.PlaybackSpeedUtils
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.event.PlaybackPositionEvent
import ac.mdiq.podcini.playback.event.PlaybackServiceEvent
import ac.mdiq.podcini.playback.event.SpeedChangedEvent
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.common.PlaybackSpeedIndicatorView
import ac.mdiq.podcini.ui.dialog.SkipPreferenceDialog
import ac.mdiq.podcini.ui.dialog.VariableSpeedDialog
import ac.mdiq.podcini.ui.view.ChapterSeekBar
import ac.mdiq.podcini.ui.view.PlayButton
import ac.mdiq.podcini.util.Converter
import ac.mdiq.podcini.util.TimeSpeedConverter
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlin.math.max

/**
 * Fragment which is supposed to be displayed outside of the MediaplayerActivity.
 */
class ExternalPlayerFragment : Fragment(), SeekBar.OnSeekBarChangeListener {
    private lateinit var imgvCover: ImageView
    private lateinit var butPlay: PlayButton

    lateinit var butPlaybackSpeed: PlaybackSpeedIndicatorView
    lateinit var txtvPlaybackSpeed: TextView

    private lateinit var episodeTitle: TextView
    private lateinit var butRev: ImageButton
    private lateinit var txtvRev: TextView
    private lateinit  var butFF: ImageButton
    private lateinit  var txtvFF: TextView
    private lateinit  var butSkip: ImageButton

    private lateinit var txtvPosition: TextView
    private lateinit var txtvLength: TextView
    private lateinit var sbPosition: ChapterSeekBar

    private var showTimeLeft = false

    private var controller: PlaybackController? = null
    private var disposable: Disposable? = null

    @UnstableApi
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val viewBinding = ExternalPlayerFragmentBinding.inflate(inflater)
        Log.d(TAG, "fragment onCreateView")

        episodeTitle = viewBinding.titleView
        butPlaybackSpeed = viewBinding.butPlaybackSpeed
        txtvPlaybackSpeed = viewBinding.txtvPlaybackSpeed
        imgvCover = viewBinding.imgvCover
        butPlay = viewBinding.butPlay
        butRev = viewBinding.butRev
        txtvRev = viewBinding.txtvRev
        butFF = viewBinding.butFF
        txtvFF = viewBinding.txtvFF
        butSkip = viewBinding.butSkip
        sbPosition = viewBinding.sbPosition
        txtvPosition = viewBinding.txtvPosition
        txtvLength = viewBinding.txtvLength

        setupLengthTextView()
        setupControlButtons()
        butPlaybackSpeed.setOnClickListener {
            VariableSpeedDialog().show(childFragmentManager, null)
        }
        sbPosition.setOnSeekBarChangeListener(this)

        viewBinding.externalPlayerFragment.setOnClickListener {
            Log.d(TAG, "externalPlayerFragment was clicked")
            val media = controller?.getMedia()
            if (media != null) {
                if (media.getMediaType() == MediaType.AUDIO) {
                    (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED)
                } else {
                    val intent = getPlayerActivityIntent(requireContext(), media)
                    startActivity(intent)
                }
            }
        }

        controller = setupPlaybackController()
        controller!!.init()
        loadMediaInfo()
        EventBus.getDefault().register(this)
        return viewBinding.root
    }

    @OptIn(UnstableApi::class) override fun onDestroyView() {
        super.onDestroyView()
        controller?.release()
        controller = null
        EventBus.getDefault().unregister(this)
    }

    @UnstableApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        butPlay.setOnClickListener {
            if (controller == null) {
                return@setOnClickListener
            }
            val media = controller!!.getMedia()

            if (media?.getMediaType() == MediaType.VIDEO && controller!!.status != PlayerStatus.PLAYING) {
                controller!!.playPause()
                requireContext().startActivity(getPlayerActivityIntent(requireContext(), media))
            } else {
                controller!!.playPause()
            }
        }
        loadMediaInfo()
    }

    @OptIn(UnstableApi::class) private fun setupControlButtons() {
        butRev.setOnClickListener {
            if (controller != null) {
                val curr: Int = controller!!.position
                controller!!.seekTo(curr - UserPreferences.rewindSecs * 1000)
            }
        }
        butRev.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(),
                SkipPreferenceDialog.SkipDirection.SKIP_REWIND, txtvRev)
            true
        }
        butPlay.setOnClickListener {
            controller?.init()
            controller?.playPause()
        }
        butFF.setOnClickListener {
            if (controller != null) {
                val curr: Int = controller!!.position
                controller!!.seekTo(curr + UserPreferences.fastForwardSecs * 1000)
            }
        }
        butFF.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(),
                SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, txtvFF)
            false
        }
        butSkip.setOnClickListener {
            activity?.sendBroadcast(
                MediaButtonReceiver.createIntent(requireContext(), KeyEvent.KEYCODE_MEDIA_NEXT))
        }
    }

    @UnstableApi
    private fun setupPlaybackController(): PlaybackController {
        return object : PlaybackController(requireActivity()) {
            override fun updatePlayButtonShowsPlay(showPlay: Boolean) {
                butPlay.setIsShowPlay(showPlay)
            }

            override fun loadMediaInfo() {
                this@ExternalPlayerFragment.loadMediaInfo()
            }

            override fun onPlaybackEnd() {
                (activity as MainActivity).setPlayerVisible(false)
            }
        }
    }

    @OptIn(UnstableApi::class) private fun setupLengthTextView() {
        showTimeLeft = UserPreferences.shouldShowRemainingTime()
        txtvLength.setOnClickListener(View.OnClickListener {
            if (controller == null) {
                return@OnClickListener
            }
            showTimeLeft = !showTimeLeft
            UserPreferences.setShowRemainTimeSetting(showTimeLeft)
            onPositionObserverUpdate(PlaybackPositionEvent(controller!!.position, controller!!.duration))
        })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updatePlaybackSpeedButton(event: SpeedChangedEvent) {
        val speedStr: String = DecimalFormat("0.00").format(event.newSpeed.toDouble())
        txtvPlaybackSpeed.text = speedStr
        butPlaybackSpeed.setSpeed(event.newSpeed)
    }

    @UnstableApi
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPositionObserverUpdate(event: PlaybackPositionEvent) {
        if (controller == null || controller!!.position == Playable.INVALID_TIME || controller!!.duration == Playable.INVALID_TIME) {
            return
        }
        val converter = TimeSpeedConverter(controller!!.currentPlaybackSpeedMultiplier)
        val currentPosition: Int = converter.convert(event.position)
        val duration: Int = converter.convert(event.duration)
        val remainingTime: Int = converter.convert(max((event.duration - event.position).toDouble(), 0.0).toInt())
        if (currentPosition == Playable.INVALID_TIME || duration == Playable.INVALID_TIME) {
            Log.w(AudioPlayerFragment.TAG, "Could not react to position observer update because of invalid time")
            return
        }

        txtvPosition.text = Converter.getDurationStringLong(currentPosition)
        txtvPosition.setContentDescription(getString(R.string.position,
            Converter.getDurationStringLocalized(requireContext(), currentPosition.toLong())))
        val showTimeLeft = UserPreferences.shouldShowRemainingTime()
        if (showTimeLeft) {
            txtvLength.setContentDescription(getString(R.string.remaining_time,
                Converter.getDurationStringLocalized(requireContext(), remainingTime.toLong())))
            txtvLength.text = (if (remainingTime > 0) "-" else "") + Converter.getDurationStringLong(remainingTime)
        } else {
            txtvLength.setContentDescription(getString(R.string.chapter_duration,
                Converter.getDurationStringLocalized(requireContext(), duration.toLong())))
            txtvLength.text = Converter.getDurationStringLong(duration)
        }

        if (!sbPosition.isPressed) {
            val progress: Float = (event.position.toFloat()) / event.duration
            sbPosition.progress = (progress * sbPosition.max).toInt()
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackServiceChanged(event: PlaybackServiceEvent) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) {
            (activity as MainActivity).setPlayerVisible(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Fragment is about to be destroyed")
        disposable?.dispose()
    }

    @OptIn(UnstableApi::class) override fun onStart() {
        super.onStart()
        txtvRev.text = NumberFormat.getInstance().format(UserPreferences.rewindSecs.toLong())
        txtvFF.text = NumberFormat.getInstance().format(UserPreferences.fastForwardSecs.toLong())
        val media = controller?.getMedia() ?: return
        updatePlaybackSpeedButton(SpeedChangedEvent(PlaybackSpeedUtils.getCurrentPlaybackSpeed(media)))
    }

    @UnstableApi
    override fun onPause() {
        super.onPause()
        controller?.pause()
    }

    @UnstableApi
    private fun loadMediaInfo() {
        Log.d(TAG, "Loading media info")
        if (controller == null) {
            Log.w(TAG, "loadMediaInfo was called while PlaybackController was null!")
            return
        }

        disposable?.dispose()
        disposable = Maybe.fromCallable<Playable?> { controller?.getMedia() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ media: Playable? -> this.updateUi(media) },
                { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) },
                { (activity as MainActivity).setPlayerVisible(false) })
    }

    @OptIn(UnstableApi::class) override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (controller == null) return

//        if (fromUser) {
//            val prog: Float = progress / (seekBar.max.toFloat())
//            val converter = TimeSpeedConverter(controller!!.currentPlaybackSpeedMultiplier)
//            var position: Int = converter.convert((prog * controller!!.duration).toInt())
//            val newChapterIndex: Int = ChapterUtils.getCurrentChapterIndex(controller!!.getMedia(), position)
//            if (newChapterIndex > -1) {
//                if (!sbPosition.isPressed && currentChapterIndex != newChapterIndex) {
//                    currentChapterIndex = newChapterIndex
//                    val media = controller!!.getMedia()
//                    position = media?.getChapters()?.get(currentChapterIndex)?.start?.toInt() ?: 0
//                    seekedToChapterStart = true
//                    controller!!.seekTo(position)
//                    updateUi(controller!!.getMedia())
//                    sbPosition.highlightCurrentChapter()
//                }
////                txtvSeek.text = controller!!.getMedia()?.getChapters()?.get(newChapterIndex)?.title ?: (""
////                        + "\n" + Converter.getDurationStringLong(position))
//            } else {
////                txtvSeek.text = Converter.getDurationStringLong(position)
//            }
//        } else if (duration != controller!!.duration) {
//            updateUi(controller!!.getMedia())
//        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        // interrupt position Observer, restart later
//        cardViewSeek.scaleX = .8f
//        cardViewSeek.scaleY = .8f
//        cardViewSeek.animate()
//            ?.setInterpolator(FastOutSlowInInterpolator())
//            ?.alpha(1f)?.scaleX(1f)?.scaleY(1f)
//            ?.setDuration(200)
//            ?.start()
    }

    @OptIn(UnstableApi::class) override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (controller != null) {
            val prog: Float = seekBar.progress / (seekBar.max.toFloat())
            controller!!.seekTo((prog * controller!!.duration).toInt())
        }
//        cardViewSeek.scaleX = 1f
//        cardViewSeek.scaleY = 1f
//        cardViewSeek.animate()
//            ?.setInterpolator(FastOutSlowInInterpolator())
//            ?.alpha(0f)?.scaleX(.8f)?.scaleY(.8f)
//            ?.setDuration(200)
//            ?.start()
    }

    @UnstableApi
    private fun updateUi(media: Playable?) {
        if (media == null) {
            return
        }
        episodeTitle.text = media.getEpisodeTitle()
        (activity as MainActivity).setPlayerVisible(true)
        onPositionObserverUpdate(PlaybackPositionEvent(media.getPosition(), media.getDuration()))

        val options = RequestOptions()
            .placeholder(R.color.light_gray)
            .error(R.color.light_gray)
            .fitCenter()
            .dontAnimate()

        Glide.with(this)
            .load(getEpisodeListImageLocation(media))
            .error(Glide.with(this)
                .load(getFallbackImageLocation(media))
                .apply(options))
            .apply(options)
            .into(imgvCover)

        if (controller?.isPlayingVideoLocally == true) {
            (activity as MainActivity).bottomSheet.setLocked(true)
            (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else {
            butPlay.visibility = View.VISIBLE
            (activity as MainActivity).bottomSheet.setLocked(false)
        }
    }

    companion object {
        const val TAG: String = "ExternalPlayerFragment"
    }
}
