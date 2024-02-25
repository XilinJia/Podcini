package ac.mdiq.podcini.ui.fragment

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
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.adapter.ChaptersListAdapter
import ac.mdiq.podcini.util.ChapterUtils.getCurrentChapterIndex
import ac.mdiq.podcini.util.ChapterUtils.loadChapters
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.playback.event.PlaybackPositionEvent
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.playback.base.PlayerStatus
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@UnstableApi
class ChaptersFragment : AppCompatDialogFragment() {
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ChaptersListAdapter

    private var controller: PlaybackController? = null
    private var disposable: Disposable? = null
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
        val root = inflater.inflate(R.layout.simple_list_fragment, null, false)
        root.findViewById<View>(R.id.toolbar).visibility = View.GONE

        Log.d(TAG, "fragment onCreateView")
        val recyclerView = root.findViewById<RecyclerView>(R.id.recyclerView)
        progressBar = root.findViewById(R.id.progLoading)
        layoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(DividerItemDecoration(recyclerView.context, layoutManager.orientation))

        adapter = ChaptersListAdapter(requireContext(), object : ChaptersListAdapter.Callback {
            override fun onPlayChapterButtonClicked(pos: Int) {
                if (controller?.status != PlayerStatus.PLAYING) {
                    controller!!.playPause()
                }
                val chapter = adapter.getItem(pos)
                if (chapter != null) controller!!.seekTo(chapter.start.toInt())
                updateChapterSelection(pos, true)
            }
        })
        recyclerView.adapter = adapter

        progressBar.visibility = View.VISIBLE

        val wrapHeight = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT)
        recyclerView.layoutParams = wrapHeight

        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                this@ChaptersFragment.loadMediaInfo(false)
            }
        }
        controller?.init()
        EventBus.getDefault().register(this)
        loadMediaInfo(false)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controller?.release()
        controller = null
        EventBus.getDefault().unregister(this)
    }

    override fun onStop() {
        super.onStop()
        disposable?.dispose()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        updateChapterSelection(getCurrentChapter(media), false)
        adapter.notifyTimeChanged(event.position.toLong())
    }

    private fun getCurrentChapter(media: Playable?): Int {
        if (controller == null) return -1

        return getCurrentChapterIndex(media, controller!!.position)
    }

    private fun loadMediaInfo(forceRefresh: Boolean) {
        disposable?.dispose()

        disposable = Maybe.create { emitter: MaybeEmitter<Any> ->
            val media = controller!!.getMedia()
            if (media != null) {
                loadChapters(media, requireContext(), forceRefresh)
                emitter.onSuccess(media)
            } else {
                emitter.onComplete()
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ media: Any -> onMediaChanged(media as Playable) },
                { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun onMediaChanged(media: Playable) {
        this.media = media
        focusedChapter = -1

        if (media.getChapters().isEmpty()) {
            dismiss()
            Toast.makeText(context, R.string.no_chapters_label, Toast.LENGTH_LONG).show()
        } else {
            progressBar.visibility = View.GONE
        }
        adapter.setMedia(media)
        (dialog as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL).visibility = View.INVISIBLE
        if ((media is FeedMedia) && !media.getItem()?.podcastIndexChapterUrl.isNullOrEmpty()) {
            (dialog as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL).visibility = View.VISIBLE
        }
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
