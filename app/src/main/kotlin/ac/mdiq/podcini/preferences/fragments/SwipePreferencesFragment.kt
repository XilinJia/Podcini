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

        findPreference<Preference>(Prefs.prefSwipeQueue.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), QueuesFragment.TAG).show(object : SwipeActionsDialog.Callback {
                override fun onCall() {}
            })
            true
        }
        findPreference<Preference>(Prefs.prefSwipeEpisodes.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), AllEpisodesFragment.TAG).show (object : SwipeActionsDialog.Callback {
                override fun onCall() {}
            })
            true
        }
        findPreference<Preference>(Prefs.prefSwipeDownloads.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), DownloadsFragment.TAG).show (object : SwipeActionsDialog.Callback {
                override fun onCall() {}
            })
            true
        }
        findPreference<Preference>(Prefs.prefSwipeFeed.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), FeedEpisodesFragment.TAG).show (object : SwipeActionsDialog.Callback {
                override fun onCall() {}
            })
            true
        }
        findPreference<Preference>(Prefs.prefSwipeHistory.name)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
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

    private enum class Prefs {
        prefSwipeQueue,
//        prefSwipeStatistics,
        prefSwipeEpisodes,
        prefSwipeDownloads,
        prefSwipeFeed,
        prefSwipeHistory
    }
}
