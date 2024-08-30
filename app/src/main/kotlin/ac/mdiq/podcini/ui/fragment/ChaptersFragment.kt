package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SimpleListFragmentBinding
import ac.mdiq.podcini.databinding.SimplechapterItemBinding
import ac.mdiq.podcini.playback.ServiceStatusHandler
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.utils.ChapterUtils.getCurrentChapterIndex
import ac.mdiq.podcini.storage.utils.ChapterUtils.loadChapters
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curPositionFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playPause
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.storage.model.EmbeddedChapterImage
import ac.mdiq.podcini.ui.view.CircularProgressBar
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLocalized
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLong
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max
import kotlin.math.min

@UnstableApi
class ChaptersFragment : AppCompatDialogFragment() {

    private var _binding: SimpleListFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ChaptersListAdapter

    private var controller: ServiceStatusHandler? = null
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
                if (MediaPlayerBase.status != PlayerStatus.PLAYING) playPause()

                val chapter = adapter.getItem(pos)
                if (chapter != null) seekTo(chapter.start.toInt())
                updateChapterSelection(pos, true)
            }
        })
        recyclerView.adapter = adapter

        progressBar.visibility = View.VISIBLE

        val wrapHeight = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT)
        recyclerView.layoutParams = wrapHeight

        controller = object : ServiceStatusHandler(requireActivity()) {
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

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        controller?.release()
        controller = null
        super.onDestroyView()
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
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.PlaybackPositionEvent -> onEventMainThread(event)
                    else -> {}
                }
            }
        }
    }

    fun onEventMainThread(event: FlowEvent.PlaybackPositionEvent) {
        if (event.media?.getIdentifier() != media?.getIdentifier()) return
        updateChapterSelection(getCurrentChapter(media), false)
        adapter.notifyTimeChanged(event.position.toLong())
    }

    private fun getCurrentChapter(media: Playable?): Int {
        if (controller == null) return -1

        return getCurrentChapterIndex(media, curPositionFB)
    }

    private fun loadMediaInfo(forceRefresh: Boolean) {
        lifecycleScope.launch {
            val media = withContext(Dispatchers.IO) {
                val media_ = curMedia
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
        if ((media is EpisodeMedia) && !media.episodeOrFetch()?.podcastIndexChapterUrl.isNullOrEmpty())
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

    class ChaptersListAdapter(private val context: Context, private val callback: Callback?) : RecyclerView.Adapter<ChaptersListAdapter.ChapterHolder>() {

        private var media: Playable? = null
        private var currentChapterIndex = -1
        private var currentChapterPosition: Long = -1
        private var hasImages = false

        fun setMedia(media: Playable) {
            this.media = media
            hasImages = false
            for (chapter in media.getChapters()) {
                if (!chapter.imageUrl.isNullOrEmpty()) hasImages = true
            }
            notifyDataSetChanged()
        }

        override fun onBindViewHolder(holder: ChapterHolder, position: Int) {
            val sc = getItem(position)?: return
            holder.title.text = sc.title
            holder.start.text = getDurationStringLong(sc.start.toInt())
            val duration = if (position + 1 < itemCount) media!!.getChapters()[position + 1].start - sc.start
            else (media?.getDuration()?:0) - sc.start

            holder.duration.text = context.getString(R.string.chapter_duration,
                getDurationStringLocalized(context, duration.toInt().toLong()))

            if (sc.link.isNullOrEmpty()) {
                holder.link.visibility = View.GONE
            } else {
                holder.link.visibility = View.VISIBLE
                holder.link.text = sc.link
                holder.link.setOnClickListener {
                    if (sc.link!=null) openInBrowser(context, sc.link!!)
                }
            }
            holder.secondaryActionIcon.setImageResource(R.drawable.ic_play_48dp)
            holder.secondaryActionButton.contentDescription = context.getString(R.string.play_chapter)
            holder.secondaryActionButton.setOnClickListener {
                callback?.onPlayChapterButtonClicked(position)
            }

            if (position == currentChapterIndex) {
                val density = context.resources.displayMetrics.density
                holder.itemView.setBackgroundColor(SurfaceColors.getColorForElevation(context, 32 * density))
                var progress = ((currentChapterPosition - sc.start).toFloat()) / duration
                progress = max(progress.toDouble(), CircularProgressBar.MINIMUM_PERCENTAGE.toDouble()).toFloat()
                progress = min(progress.toDouble(), CircularProgressBar.MAXIMUM_PERCENTAGE.toDouble()).toFloat()
                holder.progressBar.setPercentage(progress, position)
                holder.secondaryActionIcon.setImageResource(R.drawable.ic_replay)
            } else {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                holder.progressBar.setPercentage(0f, null)
            }

            if (hasImages) {
                holder.image.visibility = View.VISIBLE
                if (sc.imageUrl.isNullOrEmpty()) {
//                Glide.with(context).clear(holder.image)
                    val imageLoader = ImageLoader.Builder(context).build()
                    imageLoader.enqueue(ImageRequest.Builder(context).data(null).target(holder.image).build())
                } else {
                    if (media != null) {
                        val imgUrl = EmbeddedChapterImage.getModelFor(media!!,position)
                        holder.image.load(imgUrl)
                    }
                }
            } else holder.image.visibility = View.GONE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterHolder {
            val inflater = LayoutInflater.from(context)
            return ChapterHolder(inflater.inflate(R.layout.simplechapter_item, parent, false))
        }

        override fun getItemCount(): Int {
            return media?.getChapters()?.size?:0
        }

        class ChapterHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val binding: SimplechapterItemBinding = SimplechapterItemBinding.bind(itemView)
            val title: TextView = binding.txtvTitle
            val start: TextView = binding.txtvStart
            val link: TextView = binding.txtvLink
            val duration: TextView = binding.txtvDuration
            val image: ImageView = binding.imgvCover
            val secondaryActionButton: View = binding.secondaryActionLayout.secondaryAction
            val secondaryActionIcon: ImageView = binding.secondaryActionLayout.secondaryActionIcon
            val progressBar: CircularProgressBar = binding.secondaryActionLayout.secondaryActionProgress
        }

        fun notifyChapterChanged(newChapterIndex: Int) {
            currentChapterIndex = newChapterIndex
            currentChapterPosition = getItem(newChapterIndex)?.start?:0
            notifyDataSetChanged()
        }

        fun notifyTimeChanged(timeMs: Long) {
            currentChapterPosition = timeMs
            // Passing an argument prevents flickering.
            // See EpisodeItemListAdapter.notifyItemChangedCompat.
            notifyItemChanged(currentChapterIndex, "foo")
        }

        fun getItem(position: Int): Chapter? {
            val chapters = media?.getChapters()?: return null
            if (position < 0 || position >= chapters.size) return null
            return chapters[position]
        }

        interface Callback {
            fun onPlayChapterButtonClicked(position: Int)
        }
    }

    companion object {
        val TAG = ChaptersFragment::class.simpleName ?: "Anonymous"
    }
}
