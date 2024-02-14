package ac.mdiq.podvinci.ui.home.sections

import ac.mdiq.podvinci.activity.MainActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.storage.DBReader
import ac.mdiq.podvinci.databinding.HomeSectionEchoBinding
import ac.mdiq.podvinci.ui.echo.EchoActivity
import ac.mdiq.podvinci.ui.home.HomeFragment
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*

class EchoSection : Fragment() {
    private var viewBinding: HomeSectionEchoBinding? = null
    private var disposable: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        viewBinding = HomeSectionEchoBinding.inflate(inflater)
        viewBinding!!.titleLabel.text = getString(R.string.podvinci_echo_year, EchoActivity.RELEASE_YEAR)
        viewBinding!!.echoButton.setOnClickListener { v: View? ->
            startActivity(Intent(context,
                EchoActivity::class.java))
        }
        viewBinding!!.closeButton.setOnClickListener { v: View? -> hideThisYear() }
        updateVisibility()
        return viewBinding!!.root
    }

    private fun jan1(): Long {
        val date = Calendar.getInstance()
        date[Calendar.HOUR_OF_DAY] = 0
        date[Calendar.MINUTE] = 0
        date[Calendar.SECOND] = 0
        date[Calendar.MILLISECOND] = 0
        date[Calendar.DAY_OF_MONTH] = 1
        date[Calendar.MONTH] = 0
        date[Calendar.YEAR] = EchoActivity.RELEASE_YEAR
        return date.timeInMillis
    }

    private fun updateVisibility() {
        disposable?.dispose()

        disposable = Observable.fromCallable {
            val statisticsResult: DBReader.StatisticsResult = DBReader.getStatistics(false, jan1(), Long.MAX_VALUE)
            var totalTime: Long = 0
            for (feedTime in statisticsResult.feedTime) {
                totalTime += feedTime.timePlayed
            }
            totalTime
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ totalTime: Long ->
                val shouldShow = (totalTime >= 3600 * 10)
                viewBinding!!.root.visibility = if (shouldShow) View.VISIBLE else View.GONE
                if (!shouldShow) {
                    hideThisYear()
                }
            }, { obj: Throwable -> obj.printStackTrace() })
    }

    fun hideThisYear() {
        requireContext().getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(HomeFragment.PREF_HIDE_ECHO, EchoActivity.RELEASE_YEAR).apply()
        (activity as MainActivity).loadFragment(HomeFragment.TAG, null)
    }
}
