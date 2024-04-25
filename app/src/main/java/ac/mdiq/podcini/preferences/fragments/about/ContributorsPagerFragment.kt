package ac.mdiq.podcini.preferences.fragments.about

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.PagerFragmentBinding
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Displays the 'about->Contributors' pager screen.
 */
class ContributorsPagerFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        setHasOptionsMenu(true)
        val binding = PagerFragmentBinding.inflate(inflater)
        val viewPager = binding.viewpager
        viewPager.adapter = StatisticsPagerAdapter(this)
        // Give the TabLayout the ViewPager
        val tabLayout = binding.slidingTabs
        TabLayoutMediator(tabLayout, viewPager) { tab: TabLayout.Tab, position: Int ->
            when (position) {
                POS_DEVELOPERS -> tab.setText(R.string.developers)
                POS_TRANSLATORS -> tab.setText(R.string.translators)
                POS_SPECIAL_THANKS -> tab.setText(R.string.special_thanks)
                else -> {}
            }
        }.attach()

        binding.toolbar.visibility = View.GONE

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.contributors)
    }

    class StatisticsPagerAdapter internal constructor(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                POS_TRANSLATORS -> TranslatorsFragment()
                POS_SPECIAL_THANKS -> SpecialThanksFragment()
                POS_DEVELOPERS -> DevelopersFragment()
                else -> DevelopersFragment()
            }
        }

        override fun getItemCount(): Int {
            return TOTAL_COUNT
        }
    }

    companion object {
        private const val POS_DEVELOPERS = 0
        private const val POS_TRANSLATORS = 1
        private const val POS_SPECIAL_THANKS = 2
        private const val TOTAL_COUNT = 3
    }
}
