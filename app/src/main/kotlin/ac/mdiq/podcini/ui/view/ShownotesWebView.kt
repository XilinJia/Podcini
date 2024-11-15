package ac.mdiq.podcini.ui.view

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.util.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer

import com.google.android.material.snackbar.Snackbar
import kotlin.math.max

class ShownotesWebView : WebView, View.OnLongClickListener {
    /**
     * URL that was selected via long-press.
     */
    private var selectedUrl: String? = null
    private var timecodeSelectedListener: Consumer<Int>? = null
    private var pageFinishedListener: Runnable? = null

    constructor(context: Context) : super(context) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setup()
    }

    private fun setup() {
        setBackgroundColor(Color.TRANSPARENT)
        // Use cached resources, even if they have expired
        if (!NetworkUtils.networkAvailable()) getSettings().cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

        getSettings().mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        getSettings().useWideViewPort = false
        getSettings().loadWithOverviewMode = true
        setOnLongClickListener(this)

        setWebViewClient(object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (ShownotesCleaner.isTimecodeLink(url) && timecodeSelectedListener != null)
                    timecodeSelectedListener!!.accept(ShownotesCleaner.getTimecodeLinkTime(url))
                else IntentUtils.openInBrowser(context, url)
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Logd(TAG, "Page finished")
                pageFinishedListener?.run()
            }
        })
    }

     override fun onLongClick(v: View): Boolean {
        val r: HitTestResult = getHitTestResult()
        when (r.type) {
            HitTestResult.SRC_ANCHOR_TYPE -> {
                Logd(TAG, "Link of webview was long-pressed. Extra: " + r.extra)
                selectedUrl = r.extra
                showContextMenu()
                return true
            }
            HitTestResult.EMAIL_TYPE -> {
                Logd(TAG, "E-Mail of webview was long-pressed. Extra: " + r.extra)
                ContextCompat.getSystemService(context, ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Podcini", r.extra))
                if (Build.VERSION.SDK_INT <= 32 && this.context is MainActivity)
                    (this.context as MainActivity).showSnackbarAbovePlayer(resources.getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT)
                return true
            }
            else -> {
                selectedUrl = null
                return false
            }
        }
    }

    fun onContextItemSelected(item: MenuItem): Boolean {
        if (selectedUrl == null) return false
        val itemId = item.itemId
        when (itemId) {
            R.id.open_in_browser_item -> if (selectedUrl != null) IntentUtils.openInBrowser(context, selectedUrl!!)
            R.id.share_url_item -> if (selectedUrl != null) ShareUtils.shareLink(context, selectedUrl!!)
            R.id.copy_url_item -> {
                val clipData: ClipData = ClipData.newPlainText(selectedUrl, selectedUrl)
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(clipData)
                if (Build.VERSION.SDK_INT < 32) {
                    val s: Snackbar = Snackbar.make(this, R.string.copied_to_clipboard, Snackbar.LENGTH_LONG)
                    s.view.elevation = 100f
                    s.show()
                }
            }
            R.id.go_to_position_item -> {
                if (ShownotesCleaner.isTimecodeLink(selectedUrl) && timecodeSelectedListener != null)
                    timecodeSelectedListener!!.accept(ShownotesCleaner.getTimecodeLinkTime(selectedUrl))
                else Log.e(TAG, "Selected go_to_position_item, but URL was no timecode link: $selectedUrl")
            }
            else -> {
                selectedUrl = null
                return false
            }
        }
        selectedUrl = null
        return true
    }

    override fun onCreateContextMenu(menu: ContextMenu) {
        super.onCreateContextMenu(menu)
        if (selectedUrl == null) return
        if (ShownotesCleaner.isTimecodeLink(selectedUrl)) {
            menu.add(Menu.NONE, R.id.go_to_position_item, Menu.NONE, R.string.go_to_position_label)
            menu.setHeaderTitle(DurationConverter.getDurationStringLong(ShownotesCleaner.getTimecodeLinkTime(selectedUrl)))
        } else {
            val uri = Uri.parse(selectedUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (IntentUtils.isCallable(context, intent)) menu.add(Menu.NONE, R.id.open_in_browser_item, Menu.NONE, R.string.open_in_browser_label)
            menu.add(Menu.NONE, R.id.copy_url_item, Menu.NONE, R.string.copy_url_label)
            menu.add(Menu.NONE, R.id.share_url_item, Menu.NONE, R.string.share_url_label)
            menu.setHeaderTitle(selectedUrl)
        }
        setOnClickListeners(menu) { item: MenuItem -> this.onContextItemSelected(item) }
    }

    /**
     * When pressing a context menu item, Android calls onContextItemSelected
     * for ALL fragments in arbitrary order, not just for the fragment that the
     * context menu was created from. This assigns the listener to every menu item,
     * so that the correct fragment is always called first and can consume the click.
     *
     * Note that Android still calls the onContextItemSelected methods of all fragments
     * when the passed listener returns false.
     */
    fun setOnClickListeners(menu: Menu?, listener: MenuItem.OnMenuItemClickListener?) {
        for (i in 0 until menu!!.size()) {
            if (menu.getItem(i).subMenu != null) setOnClickListeners(menu.getItem(i).subMenu, listener)
            menu.getItem(i).setOnMenuItemClickListener(listener)
        }
    }

    fun setTimecodeSelectedListener(timecodeSelectedListener: Consumer<Int>?) {
        this.timecodeSelectedListener = timecodeSelectedListener
    }

    fun setPageFinishedListener(pageFinishedListener: Runnable?) {
        this.pageFinishedListener = pageFinishedListener
    }

    @Deprecated("Deprecated in Java")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(max(measuredWidth, minimumWidth), max(measuredHeight, minimumHeight))
    }

    companion object {
        private val TAG: String = ShownotesWebView::class.simpleName ?: "Anonymous"
    }
}
