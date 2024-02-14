package de.danoeh.antennapod.fragment

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.core.util.gui.ShownotesCleaner
import de.danoeh.antennapod.core.util.playback.PlaybackController
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.view.ShownotesWebView
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

/**
 * Displays the description of a Playable object in a Webview.
 */
@UnstableApi
class ItemDescriptionFragment : Fragment() {
    private var webvDescription: ShownotesWebView? = null
    private var webViewLoader: Disposable? = null
    private var controller: PlaybackController? = null

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "Creating view")
        val root = inflater.inflate(R.layout.item_description_fragment, container, false)
        webvDescription = root.findViewById(R.id.webview)
        webvDescription?.setTimecodeSelectedListener { time: Int? ->
            if (controller != null) {
                controller!!.seekTo(time!!)
            }
        }
        webvDescription?.setPageFinishedListener {
            // Restoring the scroll position might not always work
            webvDescription!!.postDelayed({ this@ItemDescriptionFragment.restoreFromPreference() }, 50)
        }

        root.addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int,
                                        bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                if (root.measuredHeight != webvDescription?.minimumHeight) {
                    webvDescription?.setMinimumHeight(root.measuredHeight)
                }
                root.removeOnLayoutChangeListener(this)
            }
        })
        if (webvDescription != null) registerForContextMenu(webvDescription!!)
        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Fragment destroyed")
        if (webvDescription != null) {
            webvDescription!!.removeAllViews()
            webvDescription!!.destroy()
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return webvDescription!!.onContextItemSelected(item)
    }

    @UnstableApi private fun load() {
        Log.d(TAG, "load()")
        if (webViewLoader != null) {
            webViewLoader!!.dispose()
        }
        val context = context ?: return
        webViewLoader = Maybe.create { emitter: MaybeEmitter<String?> ->
            val media = controller!!.getMedia()
            if (media == null) {
                emitter.onComplete()
                return@create
            }
            if (media is FeedMedia) {
                val feedMedia = media
                if (feedMedia.getItem() == null) {
                    feedMedia.setItem(DBReader.getFeedItem(feedMedia.itemId))
                }
                if (feedMedia.getItem() != null) DBReader.loadDescriptionOfFeedItem(feedMedia.getItem()!!)
            }
            val shownotesCleaner = ShownotesCleaner(context, media.getDescription()?:"", media.getDuration())
            emitter.onSuccess(shownotesCleaner.processShownotes())
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ data: String? ->
                webvDescription!!.loadDataWithBaseURL("https://127.0.0.1", data!!, "text/html",
                    "utf-8", "about:blank")
                Log.d(TAG, "Webview loaded")
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    @UnstableApi override fun onPause() {
        super.onPause()
        savePreference()
    }

    @UnstableApi private fun savePreference() {
        Log.d(TAG, "Saving preferences")
        val prefs = requireActivity().getSharedPreferences(PREF, Activity.MODE_PRIVATE)
        val editor = prefs.edit()
        if (controller != null && controller!!.getMedia() != null && webvDescription != null) {
            Log.d(TAG, "Saving scroll position: " + webvDescription!!.scrollY)
            editor.putInt(PREF_SCROLL_Y, webvDescription!!.scrollY)
            editor.putString(PREF_PLAYABLE_ID, controller!!.getMedia()!!.getIdentifier()
                .toString())
        } else {
            Log.d(TAG, "savePreferences was called while media or webview was null")
            editor.putInt(PREF_SCROLL_Y, -1)
            editor.putString(PREF_PLAYABLE_ID, "")
        }
        editor.apply()
    }

    @UnstableApi private fun restoreFromPreference(): Boolean {
        Log.d(TAG, "Restoring from preferences")
        val activity: Activity? = activity
        if (activity != null) {
            val prefs = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE)
            val id = prefs.getString(PREF_PLAYABLE_ID, "")
            val scrollY = prefs.getInt(PREF_SCROLL_Y, -1)
            if (controller != null && scrollY != -1 && controller!!.getMedia() != null && id == controller!!.getMedia()!!.getIdentifier().toString() && webvDescription != null) {
                Log.d(TAG, "Restored scroll Position: $scrollY")
                webvDescription!!.scrollTo(webvDescription!!.scrollX, scrollY)
                return true
            }
        }
        return false
    }

    fun scrollToTop() {
        webvDescription!!.scrollTo(0, 0)
        savePreference()
    }

    override fun onStart() {
        super.onStart()
        controller = object : PlaybackController(activity) {
            override fun loadMediaInfo() {
                load()
            }
        }
        controller?.init()
        load()
    }

    override fun onStop() {
        super.onStop()

        if (webViewLoader != null) {
            webViewLoader!!.dispose()
        }
        controller!!.release()
        controller = null
    }

    companion object {
        private const val TAG = "ItemDescriptionFragment"

        private const val PREF = "ItemDescriptionFragmentPrefs"
        private const val PREF_SCROLL_Y = "prefScrollY"
        private const val PREF_PLAYABLE_ID = "prefPlayableId"
    }
}
