package ac.mdiq.podcini.ui.statistics.subscriptions


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.StatisticsFilterDialogBinding
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.util.event.StatisticsEvent
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import androidx.core.util.Pair
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.EventBus
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class StatisticsFilterDialog(private val context: Context, oldestDate: Long) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(StatisticsFragment.PREF_NAME, Context.MODE_PRIVATE)
    private var includeMarkedAsPlayed: Boolean
    private var timeFilterFrom: Long
    private var timeFilterTo: Long
    private val filterDatesFrom: Pair<Array<String>, Array<Long>>
    private val filterDatesTo: Pair<Array<String>, Array<Long>>

    init {
        includeMarkedAsPlayed = prefs.getBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, false)
        timeFilterFrom = prefs.getLong(StatisticsFragment.PREF_FILTER_FROM, 0)
        timeFilterTo = prefs.getLong(StatisticsFragment.PREF_FILTER_TO, Long.MAX_VALUE)
        filterDatesFrom = makeMonthlyList(oldestDate, false)
        filterDatesTo = makeMonthlyList(oldestDate, true)
    }

    fun show() {
        val dialogBinding = StatisticsFilterDialogBinding.inflate(
            LayoutInflater.from(context))
        val builder = MaterialAlertDialogBuilder(context)
        builder.setView(dialogBinding.root)
        builder.setTitle(R.string.filter)
        dialogBinding.includeMarkedCheckbox.setOnCheckedChangeListener { compoundButton: CompoundButton?, checked: Boolean ->
            dialogBinding.timeToSpinner.isEnabled = !checked
            dialogBinding.timeFromSpinner.isEnabled = !checked
            dialogBinding.pastYearButton.isEnabled = !checked
            dialogBinding.allTimeButton.isEnabled = !checked
            dialogBinding.dateSelectionContainer.alpha = if (checked) 0.5f else 1f
        }
        dialogBinding.includeMarkedCheckbox.isChecked = includeMarkedAsPlayed


        val adapterFrom = ArrayAdapter(context,
            android.R.layout.simple_spinner_item, filterDatesFrom.first)
        adapterFrom.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.timeFromSpinner.adapter = adapterFrom
        for (i in filterDatesFrom.second.indices) {
            if (filterDatesFrom.second[i] >= timeFilterFrom) {
                dialogBinding.timeFromSpinner.setSelection(i)
                break
            }
        }

        val adapterTo = ArrayAdapter(context,
            android.R.layout.simple_spinner_item, filterDatesTo.first)
        adapterTo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.timeToSpinner.adapter = adapterTo
        for (i in filterDatesTo.second.indices) {
            if (filterDatesTo.second[i] >= timeFilterTo) {
                dialogBinding.timeToSpinner.setSelection(i)
                break
            }
        }

        dialogBinding.allTimeButton.setOnClickListener { v: View? ->
            dialogBinding.timeFromSpinner.setSelection(0)
            dialogBinding.timeToSpinner.setSelection(filterDatesTo.first.size - 1)
        }
        dialogBinding.pastYearButton.setOnClickListener { v: View? ->
            dialogBinding.timeFromSpinner.setSelection(max(0.0, (filterDatesFrom.first.size - 12).toDouble())
                .toInt())
            dialogBinding.timeToSpinner.setSelection(filterDatesTo.first.size - 2)
        }

        builder.setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int ->
            includeMarkedAsPlayed = dialogBinding.includeMarkedCheckbox.isChecked
            if (includeMarkedAsPlayed) {
                // We do not know the date at which something was marked as played, so filtering does not make sense
                timeFilterFrom = 0
                timeFilterTo = Long.MAX_VALUE
            } else {
                timeFilterFrom = filterDatesFrom.second[dialogBinding.timeFromSpinner.selectedItemPosition]
                timeFilterTo = filterDatesTo.second[dialogBinding.timeToSpinner.selectedItemPosition]
            }
            prefs.edit()
                .putBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, includeMarkedAsPlayed)
                .putLong(StatisticsFragment.PREF_FILTER_FROM, timeFilterFrom)
                .putLong(StatisticsFragment.PREF_FILTER_TO, timeFilterTo)
                .apply()
            EventBus.getDefault().post(StatisticsEvent())
        }
        builder.show()
    }

    private fun makeMonthlyList(oldestDate: Long, inclusive: Boolean): Pair<Array<String>, Array<Long>> {
        val date = Calendar.getInstance()
        date.timeInMillis = oldestDate
        date[Calendar.HOUR_OF_DAY] = 0
        date[Calendar.MINUTE] = 0
        date[Calendar.SECOND] = 0
        date[Calendar.MILLISECOND] = 0
        date[Calendar.DAY_OF_MONTH] = 1
        val names = ArrayList<String>()
        val timestamps = ArrayList<Long>()
        val skeleton = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMM yyyy")
        val dateFormat = SimpleDateFormat(skeleton, Locale.getDefault())
        while (date.timeInMillis < System.currentTimeMillis()) {
            names.add(dateFormat.format(Date(date.timeInMillis)))
            if (!inclusive) {
                timestamps.add(date.timeInMillis)
            }
            if (date[Calendar.MONTH] == Calendar.DECEMBER) {
                date[Calendar.MONTH] = Calendar.JANUARY
                date[Calendar.YEAR] = date[Calendar.YEAR] + 1
            } else {
                date[Calendar.MONTH] = date[Calendar.MONTH] + 1
            }
            if (inclusive) {
                timestamps.add(date.timeInMillis)
            }
        }
        if (inclusive) {
            names.add(context.getString(R.string.statistics_today))
            timestamps.add(Long.MAX_VALUE)
        }
        return Pair(names.toTypedArray<String>(), timestamps.toTypedArray<Long>())
    }
}
