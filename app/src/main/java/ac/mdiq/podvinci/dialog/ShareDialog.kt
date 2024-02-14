package ac.mdiq.podvinci.dialog

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ac.mdiq.podvinci.core.util.ShareUtils.shareFeedItemFile
import ac.mdiq.podvinci.core.util.ShareUtils.shareFeedItemLinkWithDownloadLink
import ac.mdiq.podvinci.core.util.ShareUtils.shareMediaDownloadLink
import ac.mdiq.podvinci.databinding.ShareEpisodeDialogBinding
import ac.mdiq.podvinci.model.feed.FeedItem

class ShareDialog : BottomSheetDialogFragment() {
    private var ctx: Context? = null
    private var item: FeedItem? = null
    private var prefs: SharedPreferences? = null

    private var viewBinding: ShareEpisodeDialogBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View {
        if (arguments != null) {
            ctx = activity
            item = requireArguments().getSerializable(ARGUMENT_FEED_ITEM) as FeedItem?
            prefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }

        viewBinding = ShareEpisodeDialogBinding.inflate(inflater)
        viewBinding!!.shareDialogRadioGroup.setOnCheckedChangeListener { group: RadioGroup?, checkedId: Int ->
            viewBinding!!.sharePositionCheckbox.isEnabled = checkedId == viewBinding!!.shareSocialRadio.id
        }

        setupOptions()

        viewBinding!!.shareButton.setOnClickListener { v: View? ->
            val includePlaybackPosition = viewBinding!!.sharePositionCheckbox.isChecked
            val position: Int
            if (viewBinding!!.shareSocialRadio.isChecked) {
                shareFeedItemLinkWithDownloadLink(ctx!!, item!!, includePlaybackPosition)
                position = 1
            } else if (viewBinding!!.shareMediaReceiverRadio.isChecked) {
                shareMediaDownloadLink(ctx!!, item!!.media!!)
                position = 2
            } else if (viewBinding!!.shareMediaFileRadio.isChecked) {
                shareFeedItemFile(ctx!!, item!!.media!!)
                position = 3
            } else {
                throw IllegalStateException("Unknown share method")
            }
            prefs!!.edit()
                .putBoolean(PREF_SHARE_EPISODE_START_AT, includePlaybackPosition)
                .putInt(PREF_SHARE_EPISODE_TYPE, position)
                .apply()
            dismiss()
        }
        return viewBinding!!.root
    }

    private fun setupOptions() {
        val hasMedia = item!!.media != null
        val downloaded = hasMedia && item!!.media!!.isDownloaded()
        viewBinding!!.shareMediaFileRadio.visibility = if (downloaded) View.VISIBLE else View.GONE

        val hasDownloadUrl = hasMedia && item!!.media!!.download_url != null
        if (!hasDownloadUrl) {
            viewBinding!!.shareMediaReceiverRadio.visibility = View.GONE
        }
        var type = prefs!!.getInt(PREF_SHARE_EPISODE_TYPE, 1)
        if ((type == 2 && !hasDownloadUrl) || (type == 3 && !downloaded)) {
            type = 1
        }
        viewBinding!!.shareSocialRadio.isChecked = type == 1
        viewBinding!!.shareMediaReceiverRadio.isChecked = type == 2
        viewBinding!!.shareMediaFileRadio.isChecked = type == 3

        val switchIsChecked = prefs!!.getBoolean(PREF_SHARE_EPISODE_START_AT, false)
        viewBinding!!.sharePositionCheckbox.isChecked = switchIsChecked
    }

    companion object {
        private const val ARGUMENT_FEED_ITEM = "feedItem"
        private const val PREF_NAME = "ShareDialog"
        private const val PREF_SHARE_EPISODE_START_AT = "prefShareEpisodeStartAt"
        private const val PREF_SHARE_EPISODE_TYPE = "prefShareEpisodeType"

        fun newInstance(item: FeedItem?): ShareDialog {
            val arguments = Bundle()
            arguments.putSerializable(ARGUMENT_FEED_ITEM, item)
            val dialog = ShareDialog()
            dialog.arguments = arguments
            return dialog
        }
    }
}
