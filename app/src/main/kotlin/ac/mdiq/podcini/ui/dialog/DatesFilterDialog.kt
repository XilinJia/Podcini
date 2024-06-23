package ac.mdiq.podcini.ui.dialog


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.StatisticsFilterDialogBinding
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

abstract class DatesFilterDialog(private val context: Context, oldestDate: Long) {

    protected var prefs: SharedPreferences? = null
    protected var includeMarkedAsPlayed: Boolean = false
    protected var timeFilterFrom: Long = 0L
    protected var timeFilterTo: Long = Date().time
    protected var showMarkPlayed = true

    protected val filterDatesFrom: Pair<Array<String>, Array<Long>>
    protected val filterDatesTo: Pair<Array<String>, Array<Long>>

    init {
        initParams()
        filterDatesFrom = makeMonthlyList(oldestDate, false)
        filterDatesTo = makeMonthlyList(oldestDate, true)
    }

//    set prefs, includeMarkedAsPlayed, timeFilterFrom, timeFilterTo
    abstract fun initParams()
    abstract fun callback(timeFilterFrom: Long, timeFilterTo: Long, includeMarkedAsPlayed: Boolean = true)

    fun show() {
        val binding = StatisticsFilterDialogBinding.inflate(LayoutInflater.from(context))
        val builder = MaterialAlertDialogBuilder(context)
        builder.setView(binding.root)
        builder.setTitle(R.string.filter)
        binding.includeMarkedCheckbox.setOnCheckedChangeListener { compoundButton: CompoundButton?, checked: Boolean ->
            binding.timeToSpinner.isEnabled = !checked
            binding.timeFromSpinner.isEnabled = !checked
            binding.pastYearButton.isEnabled = !checked
            binding.allTimeButton.isEnabled = !checked
            binding.dateSelectionContainer.alpha = if (checked) 0.5f else 1f
        }
        if (showMarkPlayed) {
            binding.includeMarkedCheckbox.isChecked = includeMarkedAsPlayed
        } else {
            binding.includeMarkedCheckbox.visibility = View.GONE
            binding.noticeMessage.visibility = View.GONE
        }

        val adapterFrom = ArrayAdapter(context, android.R.layout.simple_spinner_item, filterDatesFrom.first)
        adapterFrom.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.timeFromSpinner.adapter = adapterFrom
        for (i in filterDatesFrom.second.indices) {
            if (filterDatesFrom.second[i] >= timeFilterFrom) {
                binding.timeFromSpinner.setSelection(i)
                break
            }
        }

        val adapterTo = ArrayAdapter(context, android.R.layout.simple_spinner_item, filterDatesTo.first)
        adapterTo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.timeToSpinner.adapter = adapterTo
        for (i in filterDatesTo.second.indices) {
            if (filterDatesTo.second[i] >= timeFilterTo) {
                binding.timeToSpinner.setSelection(i)
                break
            }
        }

        binding.allTimeButton.setOnClickListener { v: View? ->
            binding.timeFromSpinner.setSelection(0)
            binding.timeToSpinner.setSelection(filterDatesTo.first.size - 1)
        }
        binding.pastYearButton.setOnClickListener { v: View? ->
            binding.timeFromSpinner.setSelection(max(0.0, (filterDatesFrom.first.size - 12).toDouble()).toInt())
            binding.timeToSpinner.setSelection(filterDatesTo.first.size - 2)
        }

        builder.setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int ->
            includeMarkedAsPlayed = binding.includeMarkedCheckbox.isChecked
            if (includeMarkedAsPlayed) {
                // We do not know the date at which something was marked as played, so filtering does not make sense
                timeFilterFrom = 0
                timeFilterTo = Long.MAX_VALUE
            } else {
                timeFilterFrom = filterDatesFrom.second[binding.timeFromSpinner.selectedItemPosition]
                timeFilterTo = filterDatesTo.second[binding.timeToSpinner.selectedItemPosition]
            }
            callback(timeFilterFrom, timeFilterTo, includeMarkedAsPlayed)
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
            if (!inclusive) timestamps.add(date.timeInMillis)

            if (date[Calendar.MONTH] == Calendar.DECEMBER) {
                date[Calendar.MONTH] = Calendar.JANUARY
                date[Calendar.YEAR] = date[Calendar.YEAR] + 1
            } else date[Calendar.MONTH] = date[Calendar.MONTH] + 1

            if (inclusive) timestamps.add(date.timeInMillis)
        }
        if (inclusive) {
            names.add(context.getString(R.string.statistics_today))
            timestamps.add(Long.MAX_VALUE)
        }
        return Pair(names.toTypedArray<String>(), timestamps.toTypedArray<Long>())
    }
}
