package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.ui.activity.MainActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ExternalPlayerFragmentBinding
import ac.mdiq.podcini.feed.util.ImageResourceUtils.getEpisodeListImageLocation
import ac.mdiq.podcini.feed.util.ImageResourceUtils.getFallbackImageLocation
import ac.mdiq.podcini.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.playback.event.PlaybackPositionEvent
import ac.mdiq.podcini.playback.event.PlaybackServiceEvent
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.ui.view.PlayButton
import androidx.annotation.OptIn
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Fragment which is supposed to be displayed outside of the MediaplayerActivity.
 */
class ExternalPlayerFragment : Fragment() {
    private lateinit var imgvCover: ImageView
    private lateinit var txtvTitle: TextView
    private lateinit var butPlay: PlayButton
    private lateinit var feedName: TextView
    private lateinit var progressBar: ProgressBar

    private var controller: PlaybackController? = null
    private var disposable: Disposable? = null

    @UnstableApi
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View {
        val viewBinding = ExternalPlayerFragmentBinding.inflate(inflater)

        Log.d(TAG, "fragment onCreateView")
        imgvCover = viewBinding.imgvCover
        txtvTitle = viewBinding.txtvTitle
        butPlay = viewBinding.butPlay
        feedName = viewBinding.txtvAuthor
        progressBar = viewBinding.episodeProgress

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

    @UnstableApi
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPositionObserverUpdate(event: PlaybackPositionEvent?) {
        if (controller == null) {
            return
        } else if (controller!!.position == Playable.INVALID_TIME || controller!!.duration == Playable.INVALID_TIME) {
            return
        }
        progressBar.progress = (controller!!.position.toDouble() / controller!!.duration * 100).toInt()
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

    @UnstableApi
    private fun updateUi(media: Playable?) {
        if (media == null) {
            return
        }
        (activity as MainActivity).setPlayerVisible(true)
        txtvTitle.text = media.getEpisodeTitle()
        feedName.text = media.getFeedTitle()
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
