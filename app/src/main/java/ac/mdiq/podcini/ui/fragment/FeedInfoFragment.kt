package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FeedinfoBinding
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBTasks
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedFunding
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.dialog.EditUrlSettingsDialog
import ac.mdiq.podcini.ui.glide.FastBlurTransformation
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.ui.statistics.feed.FeedStatisticsFragment
import ac.mdiq.podcini.ui.view.ToolbarIconTintManager
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.ShareUtils
import ac.mdiq.podcini.util.syndication.HtmlToPlainText
import android.R.string
import android.app.Activity
import android.content.*
import android.content.res.Configuration
import android.graphics.LightingColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils

/**
 * Displays information about a feed.
 */
@UnstableApi
class FeedInfoFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private val addLocalFolderLauncher = registerForActivityResult<Uri?, Uri>(AddLocalFolder()
    ) { uri: Uri? -> this.addLocalFolderResult(uri) }

    private var feed: Feed? = null
    private var disposable: Disposable? = null
    
    private lateinit var imgvCover: ImageView
    private lateinit var txtvTitle: TextView
    private lateinit var txtvDescription: TextView
    private lateinit var txtvFundingUrl: TextView
    private lateinit var lblSupport: TextView
    private lateinit var txtvUrl: TextView
    private lateinit var txtvAuthorHeader: TextView
    private lateinit var imgvBackground: ImageView
    private lateinit var infoContainer: View
    private lateinit var header: View
    private lateinit var toolbar: MaterialToolbar

    private val copyUrlToClipboard = View.OnClickListener {
        if (feed != null && feed!!.download_url != null) {
            val url: String = feed!!.download_url!!
            val clipData: ClipData = ClipData.newPlainText(url, url)
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clipData)
            if (Build.VERSION.SDK_INT <= 32) {
                (activity as MainActivity).showSnackbarAbovePlayer(R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val viewBinding = FeedinfoBinding.inflate(inflater)
//        val root: View = inflater.inflate(R.layout.feedinfo, null)

        Log.d(TAG, "fragment onCreateView")
        toolbar = viewBinding.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.feedinfo)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.setOnMenuItemClickListener(this)
        refreshToolbarState()

        val appBar: AppBarLayout = viewBinding.appBar
        val collapsingToolbar: CollapsingToolbarLayout = viewBinding.collapsingToolbar
        val iconTintManager: ToolbarIconTintManager =
            object : ToolbarIconTintManager(requireContext(), toolbar, collapsingToolbar) {
                override fun doTint(themedContext: Context) {
                    toolbar.menu.findItem(R.id.visit_website_item)
                        .setIcon(AppCompatResources.getDrawable(themedContext, R.drawable.ic_web))
                    toolbar.menu.findItem(R.id.share_item)
                        .setIcon(AppCompatResources.getDrawable(themedContext, R.drawable.ic_share))
                }
            }
        iconTintManager.updateTint()
        appBar.addOnOffsetChangedListener(iconTintManager)

        imgvCover = viewBinding.header.imgvCover
        txtvTitle = viewBinding.header.txtvTitle
        txtvAuthorHeader = viewBinding.header.txtvAuthor
        imgvBackground = viewBinding.imgvBackground
        header = viewBinding.header.root
        infoContainer = viewBinding.infoContainer
        viewBinding.header.butShowInfo.visibility = View.INVISIBLE
        viewBinding.header.butShowSettings.visibility = View.INVISIBLE
        viewBinding.header.butFilter.visibility = View.INVISIBLE
        // https://github.com/bumptech/glide/issues/529
        imgvBackground.colorFilter = LightingColorFilter(-0x7d7d7e, 0x000000)

        txtvDescription = viewBinding.txtvDescription
        txtvUrl = viewBinding.txtvUrl
        lblSupport = viewBinding.lblSupport
        txtvFundingUrl = viewBinding.txtvFundingUrl

        txtvUrl.setOnClickListener(copyUrlToClipboard)

        val feedId = requireArguments().getLong(EXTRA_FEED_ID)
        parentFragmentManager.beginTransaction().replace(R.id.statisticsFragmentContainer,
            FeedStatisticsFragment.newInstance(feedId, false), "feed_statistics_fragment")
            .commitAllowingStateLoss()

        viewBinding.btnvOpenStatistics.setOnClickListener {
            val fragment = StatisticsFragment()
            (activity as MainActivity).loadChildFragment(fragment, TransitionEffect.SLIDE)
        }

        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val feedId = requireArguments().getLong(EXTRA_FEED_ID)
        disposable = Maybe.create { emitter: MaybeEmitter<Feed?> ->
            val feed: Feed? = DBReader.getFeed(feedId)
            if (feed != null) {
                emitter.onSuccess(feed)
            } else {
                emitter.onComplete()
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: Feed? ->
                feed = result
                showFeed()
            }, { error: Throwable? -> Log.d(TAG, Log.getStackTraceString(error)) }, {})
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val horizontalSpacing = resources.getDimension(R.dimen.additional_horizontal_spacing).toInt()
        header.setPadding(horizontalSpacing, header.paddingTop, horizontalSpacing, header.paddingBottom)
        infoContainer.setPadding(horizontalSpacing, infoContainer.paddingTop,
            horizontalSpacing, infoContainer.paddingBottom)
    }

    private fun showFeed() {
        if (feed == null) return
        Log.d(TAG, "Language is " + feed!!.language)
        Log.d(TAG, "Author is " + feed!!.author)
        Log.d(TAG, "URL is " + feed!!.download_url)
        Glide.with(this)
            .load(feed!!.imageUrl)
            .apply(RequestOptions()
                .placeholder(R.color.light_gray)
                .error(R.color.light_gray)
                .fitCenter()
                .dontAnimate())
            .into(imgvCover)
        Glide.with(this)
            .load(feed!!.imageUrl)
            .apply(RequestOptions()
                .placeholder(R.color.image_readability_tint)
                .error(R.color.image_readability_tint)
                .transform(FastBlurTransformation())
                .dontAnimate())
            .into(imgvBackground)

        txtvTitle.text = feed!!.title
        txtvTitle.setMaxLines(6)

        val description: String = HtmlToPlainText.getPlainText(feed!!.description?:"")

        txtvDescription.text = description

        if (!feed!!.author.isNullOrEmpty()) {
            txtvAuthorHeader.text = feed!!.author
        }

        txtvUrl.text = feed!!.download_url
        txtvUrl.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_paperclip, 0)

        if (feed!!.paymentLinks.isEmpty()) {
            lblSupport.visibility = View.GONE
            txtvFundingUrl.visibility = View.GONE
        } else {
            lblSupport.visibility = View.VISIBLE
            val fundingList: ArrayList<FeedFunding> = feed!!.paymentLinks

            // Filter for duplicates, but keep items in the order that they have in the feed.
            val i: MutableIterator<FeedFunding> = fundingList.iterator()
            while (i.hasNext()) {
                val funding: FeedFunding = i.next()
                for (other in fundingList) {
                    if (TextUtils.equals(other.url, funding.url)) {
                        if (other.content != null && funding.content != null && other.content!!.length > funding.content!!.length) {
                            i.remove()
                            break
                        }
                    }
                }
            }

            var str = StringBuilder()
            for (funding in fundingList) {
                str.append(if (funding.content == null || funding.content!!.isEmpty()) requireContext().resources.getString(R.string.support_podcast)
                else funding.content).append(" ").append(funding.url)
                str.append("\n")
            }
            str = StringBuilder(StringUtils.trim(str.toString()))
            txtvFundingUrl.text = str.toString()
        }

        refreshToolbarState()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
    }

    private fun refreshToolbarState() {
        toolbar.menu?.findItem(R.id.reconnect_local_folder)?.setVisible(feed != null && feed!!.isLocalFeed)
        toolbar.menu?.findItem(R.id.share_item)?.setVisible(feed != null && !feed!!.isLocalFeed)
        toolbar.menu?.findItem(R.id.visit_website_item)
            ?.setVisible(feed != null && feed!!.link != null && IntentUtils.isCallable(requireContext(),
                Intent(Intent.ACTION_VIEW, Uri.parse(feed!!.link))))
        toolbar.menu?.findItem(R.id.edit_feed_url_item)?.setVisible(feed != null && !feed!!.isLocalFeed)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (feed == null) {
            (activity as MainActivity).showSnackbarAbovePlayer(
                R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return false
        }
        when (item.itemId) {
            R.id.visit_website_item -> {
                if (feed!!.link != null) IntentUtils.openInBrowser(requireContext(), feed!!.link!!)
            }
            R.id.share_item -> {
                ShareUtils.shareFeedLink(requireContext(), feed!!)
            }
            R.id.reconnect_local_folder -> {
                val alert = MaterialAlertDialogBuilder(requireContext())
                alert.setMessage(R.string.reconnect_local_folder_warning)
                alert.setPositiveButton(string.ok
                ) { _: DialogInterface?, _: Int ->
                    try {
                        addLocalFolderLauncher.launch(null)
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "No activity found. Should never happen...")
                    }
                }
                alert.setNegativeButton(string.cancel, null)
                alert.show()
            }
            R.id.edit_feed_url_item -> {
                object : EditUrlSettingsDialog(activity as Activity, feed!!) {
                    override fun setUrl(url: String?) {
                        feed!!.download_url = url
                        txtvUrl.text = feed!!.download_url
                        txtvUrl.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_paperclip, 0)
                    }
                }.show()
            }
            else -> {
                return false
            }
        }
        return true
    }

    @UnstableApi private fun addLocalFolderResult(uri: Uri?) {
        if (uri == null) {
            return
        }
        reconnectLocalFolder(uri)
    }

    @UnstableApi private fun reconnectLocalFolder(uri: Uri) {
        if (feed == null) return

        Completable.fromAction {
            requireActivity().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val documentFile = DocumentFile.fromTreeUri(requireContext(), uri)
            requireNotNull(documentFile) { "Unable to retrieve document tree" }
            feed!!.download_url = Feed.PREFIX_LOCAL_FOLDER + uri.toString()
            DBTasks.updateFeed(requireContext(), feed!!, true)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    (activity as MainActivity).showSnackbarAbovePlayer(string.ok, Snackbar.LENGTH_SHORT)
                },
                { error: Throwable ->
                    (activity as MainActivity).showSnackbarAbovePlayer(error.localizedMessage, Snackbar.LENGTH_LONG)
                })
    }

    private class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return super.createIntent(context, input)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        private const val EXTRA_FEED_ID = "ac.mdiq.podcini.extra.feedId"
        private const val TAG = "FeedInfoActivity"
        fun newInstance(feed: Feed): FeedInfoFragment {
            val fragment = FeedInfoFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_FEED_ID, feed.id)
            fragment.arguments = arguments
            return fragment
        }
    }
}
