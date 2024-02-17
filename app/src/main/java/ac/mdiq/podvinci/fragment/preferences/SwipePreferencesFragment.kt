package ac.mdiq.podvinci.fragment.preferences

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.activity.PreferenceActivity
import ac.mdiq.podvinci.dialog.SwipeActionsDialog
import ac.mdiq.podvinci.fragment.*

class SwipePreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_swipe)

        findPreference<Preference>(PREF_SWIPE_QUEUE)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                SwipeActionsDialog(requireContext(), QueueFragment.TAG).show(object : SwipeActionsDialog.Callback {
                    override fun onCall() {}
                })
                true
            }
        findPreference<Preference>(PREF_SWIPE_INBOX)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                SwipeActionsDialog(requireContext(), InboxFragment.TAG).show (object : SwipeActionsDialog.Callback {
                    override fun onCall() {}
                })
                true
            }
        findPreference<Preference>(PREF_SWIPE_EPISODES)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                SwipeActionsDialog(requireContext(), AllEpisodesFragment.TAG).show (object : SwipeActionsDialog.Callback {
                    override fun onCall() {}
                })
                true
            }
        findPreference<Preference>(PREF_SWIPE_DOWNLOADS)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                SwipeActionsDialog(requireContext(), CompletedDownloadsFragment.TAG).show (object : SwipeActionsDialog.Callback {
                    override fun onCall() {}
                })
                true
            }
        findPreference<Preference>(PREF_SWIPE_FEED)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                SwipeActionsDialog(requireContext(), FeedItemlistFragment.TAG).show (object : SwipeActionsDialog.Callback {
                    override fun onCall() {}
                })
                true
            }
        findPreference<Preference>(PREF_SWIPE_HISTORY)!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                SwipeActionsDialog(requireContext(), PlaybackHistoryFragment.TAG).show (object : SwipeActionsDialog.Callback {
                    override fun onCall() {}
                })
                true
            }
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity?)!!.supportActionBar!!.setTitle(R.string.swipeactions_label)
    }

    companion object {
        private const val PREF_SWIPE_QUEUE = "prefSwipeQueue"
        private const val PREF_SWIPE_INBOX = "prefSwipeInbox"
        private const val PREF_SWIPE_EPISODES = "prefSwipeEpisodes"
        private const val PREF_SWIPE_DOWNLOADS = "prefSwipeDownloads"
        private const val PREF_SWIPE_FEED = "prefSwipeFeed"
        private const val PREF_SWIPE_HISTORY = "prefSwipeHistory"
    }
}