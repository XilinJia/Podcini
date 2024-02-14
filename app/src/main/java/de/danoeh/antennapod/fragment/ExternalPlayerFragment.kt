package de.danoeh.antennapod.fragment

import de.danoeh.antennapod.activity.MainActivity
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
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.feed.util.ImageResourceUtils.getEpisodeListImageLocation
import de.danoeh.antennapod.core.feed.util.ImageResourceUtils.getFallbackImageLocation
import de.danoeh.antennapod.core.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import de.danoeh.antennapod.core.util.playback.PlaybackController
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.view.PlayButton
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
    private var imgvCover: ImageView? = null
    private var txtvTitle: TextView? = null
    private var butPlay: PlayButton? = null
    private var feedName: TextView? = null
    private var progressBar: ProgressBar? = null
    private var controller: PlaybackController? = null
    private var disposable: Disposable? = null

    @UnstableApi
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.external_player_fragment, container, false)
        imgvCover = root.findViewById(R.id.imgvCover)
        txtvTitle = root.findViewById(R.id.txtvTitle)
        butPlay = root.findViewById(R.id.butPlay)
        feedName = root.findViewById(R.id.txtvAuthor)
        progressBar = root.findViewById(R.id.episodeProgress)

        root.findViewById<View>(R.id.fragmentLayout).setOnClickListener { v: View? ->
            Log.d(TAG, "layoutInfo was clicked")
            if (controller != null && controller!!.getMedia() != null) {
                if (controller!!.getMedia()!!.getMediaType() == MediaType.AUDIO) {
                    (activity as MainActivity).bottomSheet?.setState(BottomSheetBehavior.STATE_EXPANDED)
                } else {
                    val intent = getPlayerActivityIntent(requireContext(), controller!!.getMedia()!!)
                    startActivity(intent)
                }
            }
        }
        return root
    }

    @UnstableApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        butPlay!!.setOnClickListener { v: View? ->
            if (controller == null) {
                return@setOnClickListener
            }
            if (controller!!.getMedia() != null && controller!!.getMedia()!!.getMediaType() == MediaType.VIDEO && controller!!.status != PlayerStatus.PLAYING) {
                controller!!.playPause()
                requireContext().startActivity(getPlayerActivityIntent(requireContext(), controller!!.getMedia()!!))
            } else {
                controller!!.playPause()
            }
        }
        loadMediaInfo()
    }

    @UnstableApi
    private fun setupPlaybackController(): PlaybackController {
        return object : PlaybackController(activity) {
            override fun updatePlayButtonShowsPlay(showPlay: Boolean) {
                butPlay!!.setIsShowPlay(showPlay)
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
    override fun onStart() {
        super.onStart()
        controller = setupPlaybackController()
        controller!!.init()
        loadMediaInfo()
        EventBus.getDefault().register(this)
    }

    @UnstableApi
    override fun onStop() {
        super.onStop()
        if (controller != null) {
            controller!!.release()
            controller = null
        }
        EventBus.getDefault().unregister(this)
    }

    @UnstableApi
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPositionObserverUpdate(event: PlaybackPositionEvent?) {
        if (controller == null) {
            return
        } else if (controller!!.position == Playable.INVALID_TIME
                || controller!!.duration == Playable.INVALID_TIME) {
            return
        }
        progressBar!!.progress = (controller!!.position.toDouble() / controller!!.duration * 100).toInt()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackServiceChanged(event: PlaybackServiceEvent) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) {
            (activity as MainActivity).setPlayerVisible(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Fragment is about to be destroyed")
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    @UnstableApi
    override fun onPause() {
        super.onPause()
        if (controller != null) {
            controller!!.pause()
        }
    }

    @UnstableApi
    private fun loadMediaInfo() {
        Log.d(TAG, "Loading media info")
        if (controller == null) {
            Log.w(TAG, "loadMediaInfo was called while PlaybackController was null!")
            return
        }

        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Maybe.fromCallable<Playable?> { controller!!.getMedia() }
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
        txtvTitle!!.text = media.getEpisodeTitle()
        feedName!!.text = media.getFeedTitle()
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
            .into(imgvCover!!)

        if (controller != null && controller!!.isPlayingVideoLocally) {
            (activity as MainActivity).bottomSheet?.setLocked(true)
            (activity as MainActivity).bottomSheet?.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else {
            butPlay!!.visibility = View.VISIBLE
            (activity as MainActivity).bottomSheet?.setLocked(false)
        }
    }

    companion object {
        const val TAG: String = "ExternalPlayerFragment"
    }
}
