package ac.mdiq.podcini.preferences.fragments

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.dialog.SwipeActionsDialog
import ac.mdiq.podcini.ui.fragment.*
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

class SwipePreferencesFragment : PreferenceFragmentCompat() {
    @OptIn(UnstableApi::class) override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_swipe)

        findPreference<Preference>(PREF_SWIPE_QUEUE)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), QueueFragment.TAG).show(object : SwipeActionsDialog.Callback {
                override fun onCall() {}
            })
            true
        }
        findPreference<Preference>(PREF_SWIPE_EPISODES)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), AllEpisodesFragment.TAG).show (object : SwipeActionsDialog.Callback {
                override fun onCall() {}
            })
            true
        }
        findPreference<Preference>(PREF_SWIPE_DOWNLOADS)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), DownloadsFragment.TAG).show (object : SwipeActionsDialog.Callback {
                override fun onCall() {}
            })
            true
        }
        findPreference<Preference>(PREF_SWIPE_FEED)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), FeedEpisodesFragment.TAG).show (object : SwipeActionsDialog.Callback {
                override fun onCall() {}
            })
            true
        }
        findPreference<Preference>(PREF_SWIPE_HISTORY)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), HistoryFragment.TAG).show (object : SwipeActionsDialog.Callback {
                override fun onCall() {}
            })
            true
        }
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar?.setTitle(R.string.swipeactions_label)
    }

    companion object {
        private const val PREF_SWIPE_QUEUE = "prefSwipeQueue"
//        private const val PREF_SWIPE_STATISTICS = "prefSwipeStatistics"
        private const val PREF_SWIPE_EPISODES = "prefSwipeEpisodes"
        private const val PREF_SWIPE_DOWNLOADS = "prefSwipeDownloads"
        private const val PREF_SWIPE_FEED = "prefSwipeFeed"
        private const val PREF_SWIPE_HISTORY = "prefSwipeHistory"
    }
}
