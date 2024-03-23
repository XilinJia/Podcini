package ac.mdiq.podcini.ui.dialog

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ac.mdiq.podcini.util.ShareUtils.shareFeedItemFile
import ac.mdiq.podcini.util.ShareUtils.shareFeedItemLinkWithDownloadLink
import ac.mdiq.podcini.util.ShareUtils.shareMediaDownloadLink
import ac.mdiq.podcini.databinding.ShareEpisodeDialogBinding
import ac.mdiq.podcini.storage.model.feed.FeedItem

class ShareDialog : BottomSheetDialogFragment() {
    private lateinit var ctx: Context
    private lateinit var prefs: SharedPreferences
    private var _binding: ShareEpisodeDialogBinding? = null
    private val binding get() = _binding!!

    private var item: FeedItem? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        ctx = requireContext()
        item = requireArguments().getSerializable(ARGUMENT_FEED_ITEM) as FeedItem?
        prefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        _binding = ShareEpisodeDialogBinding.inflate(inflater)
        binding.shareDialogRadioGroup.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            binding.sharePositionCheckbox.isEnabled = checkedId == binding.shareSocialRadio.id
        }

        setupOptions()

        binding.shareButton.setOnClickListener {
            val includePlaybackPosition = binding.sharePositionCheckbox.isChecked
            val position: Int
            if (binding.shareSocialRadio.isChecked) {
                shareFeedItemLinkWithDownloadLink(ctx, item!!, includePlaybackPosition)
                position = 1
            } else if (binding.shareMediaReceiverRadio.isChecked) {
                shareMediaDownloadLink(ctx, item!!.media!!)
                position = 2
            } else if (binding.shareMediaFileRadio.isChecked) {
                shareFeedItemFile(ctx, item!!.media!!)
                position = 3
            } else {
                throw IllegalStateException("Unknown share method")
            }
            prefs.edit()
                .putBoolean(PREF_SHARE_EPISODE_START_AT, includePlaybackPosition)
                .putInt(PREF_SHARE_EPISODE_TYPE, position)
                .apply()
            dismiss()
        }
        return binding.root
    }

    private fun setupOptions() {
        val hasMedia = item!!.media != null
        val downloaded = hasMedia && item!!.media!!.isDownloaded()
        binding.shareMediaFileRadio.visibility = if (downloaded) View.VISIBLE else View.GONE

        val hasDownloadUrl = hasMedia && item!!.media!!.download_url != null
        if (!hasDownloadUrl) {
            binding.shareMediaReceiverRadio.visibility = View.GONE
        }
        var type = prefs.getInt(PREF_SHARE_EPISODE_TYPE, 1)
        if ((type == 2 && !hasDownloadUrl) || (type == 3 && !downloaded)) {
            type = 1
        }
        binding.shareSocialRadio.isChecked = type == 1
        binding.shareMediaReceiverRadio.isChecked = type == 2
        binding.shareMediaFileRadio.isChecked = type == 3

        val switchIsChecked = prefs.getBoolean(PREF_SHARE_EPISODE_START_AT, false)
        binding.sharePositionCheckbox.isChecked = switchIsChecked
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
