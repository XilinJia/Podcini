package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SimpleListFragmentBinding
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.ui.adapter.ChaptersListAdapter
import ac.mdiq.podcini.util.ChapterUtils.getCurrentChapterIndex
import ac.mdiq.podcini.util.ChapterUtils.loadChapters
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class ChaptersFragment : AppCompatDialogFragment() {
    private var _binding: SimpleListFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ChaptersListAdapter

    private var controller: PlaybackController? = null
//    private var disposable: Disposable? = null
    private var focusedChapter = -1
    private var media: Playable? = null
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.chapters_label))
            .setView(onCreateView(layoutInflater))
            .setPositiveButton(getString(R.string.close_label), null) //dismisses
            .setNeutralButton(getString(R.string.refresh_label), null)
            .create()
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).visibility = View.INVISIBLE
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            progressBar.visibility = View.VISIBLE
            loadMediaInfo(true)
        }

        return dialog
    }

    fun onCreateView(inflater: LayoutInflater): View {
        _binding = SimpleListFragmentBinding.inflate(inflater)
        binding.toolbar.visibility = View.GONE

        Logd(TAG, "fragment onCreateView")
        val recyclerView = binding.recyclerView
        progressBar = binding.progLoading
        layoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(DividerItemDecoration(recyclerView.context, layoutManager.orientation))

        adapter = ChaptersListAdapter(requireContext(), object : ChaptersListAdapter.Callback {
            override fun onPlayChapterButtonClicked(pos: Int) {
                if (MediaPlayerBase.status != PlayerStatus.PLAYING) controller!!.playPause()

                val chapter = adapter.getItem(pos)
                if (chapter != null) controller!!.seekTo(chapter.start.toInt())
                updateChapterSelection(pos, true)
            }
        })
        recyclerView.adapter = adapter

        progressBar.visibility = View.VISIBLE

        val wrapHeight = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT)
        recyclerView.layoutParams = wrapHeight

        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                this@ChaptersFragment.loadMediaInfo(false)
            }
        }
        controller?.init()

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
        loadMediaInfo(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        controller?.release()
        controller = null
        
    }

//    override fun onStop() {
//        super.onStop()
////        disposable?.dispose()
//    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                when (event) {
                    is FlowEvent.PlaybackPositionEvent -> onEventMainThread(event)
                    else -> {}
                }
            }
        }
    }

    fun onEventMainThread(event: FlowEvent.PlaybackPositionEvent) {
        updateChapterSelection(getCurrentChapter(media), false)
        adapter.notifyTimeChanged(event.position.toLong())
    }

    private fun getCurrentChapter(media: Playable?): Int {
        if (controller == null) return -1

        return getCurrentChapterIndex(media, controller!!.position)
    }

    private fun loadMediaInfo(forceRefresh: Boolean) {
//        disposable?.dispose()

//        disposable = Maybe.create { emitter: MaybeEmitter<Any> ->
//            val media = controller!!.getMedia()
//            if (media != null) {
//                loadChapters(media, requireContext(), forceRefresh)
//                emitter.onSuccess(media)
//            } else emitter.onComplete()
//        }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe({ media: Any -> onMediaChanged(media as Playable) },
//                { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })

        lifecycleScope.launch {
            val media = withContext(Dispatchers.IO) {
                val media_ = controller!!.getMedia()
                if (media_ != null) loadChapters(media_, requireContext(), forceRefresh)
                media_
            }
            onMediaChanged(media as Playable)
        }.invokeOnCompletion { throwable ->
            if (throwable!= null) Logd(TAG, Log.getStackTraceString(throwable))
        }

    }

    private fun onMediaChanged(media: Playable) {
        this.media = media
        focusedChapter = -1

        if (media.getChapters().isEmpty()) {
            dismiss()
            Toast.makeText(context, R.string.no_chapters_label, Toast.LENGTH_LONG).show()
        } else progressBar.visibility = View.GONE

        adapter.setMedia(media)
        (dialog as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL).visibility = View.INVISIBLE
        if ((media is FeedMedia) && !media.item?.podcastIndexChapterUrl.isNullOrEmpty())
            (dialog as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL).visibility = View.VISIBLE

        val positionOfCurrentChapter = getCurrentChapter(media)
        updateChapterSelection(positionOfCurrentChapter, true)
    }

    private fun updateChapterSelection(position: Int, scrollTo: Boolean) {
        if (position != -1 && focusedChapter != position) {
            focusedChapter = position
            adapter.notifyChapterChanged(focusedChapter)
            if (scrollTo && (layoutManager.findFirstCompletelyVisibleItemPosition() >= position
                            || layoutManager.findLastCompletelyVisibleItemPosition() <= position)) {
                layoutManager.scrollToPositionWithOffset(position, 100)
            }
        }
    }

    companion object {
        const val TAG: String = "ChaptersFragment"
    }
}
