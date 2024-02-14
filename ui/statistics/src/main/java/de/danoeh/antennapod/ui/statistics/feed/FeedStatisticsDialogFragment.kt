package de.danoeh.antennapod.ui.statistics.feed

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.statistics.R

class FeedStatisticsDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setPositiveButton(android.R.string.ok, null)
        dialog.setNeutralButton(R.string.open_podcast) { dialogInterface: DialogInterface?, i: Int ->
            val feedId = requireArguments().getLong(EXTRA_FEED_ID)
            MainActivityStarter(requireContext()).withOpenFeed(feedId).withAddToBackStack().start()
        }
        dialog.setTitle(requireArguments().getString(EXTRA_FEED_TITLE))
        dialog.setView(R.layout.feed_statistics_dialog)
        return dialog.create()
    }

    override fun onStart() {
        super.onStart()
        val feedId = requireArguments().getLong(EXTRA_FEED_ID)
        childFragmentManager.beginTransaction().replace(R.id.statisticsContainer,
            FeedStatisticsFragment.newInstance(feedId, true), "feed_statistics_fragment")
            .commitAllowingStateLoss()
    }

    companion object {
        private const val EXTRA_FEED_ID = "de.danoeh.antennapod.extra.feedId"
        private const val EXTRA_FEED_TITLE = "de.danoeh.antennapod.extra.feedTitle"

        @JvmStatic
        fun newInstance(feedId: Long, feedTitle: String?): FeedStatisticsDialogFragment {
            val fragment = FeedStatisticsDialogFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_FEED_ID, feedId)
            arguments.putString(EXTRA_FEED_TITLE, feedTitle)
            fragment.arguments = arguments
            return fragment
        }
    }
}
