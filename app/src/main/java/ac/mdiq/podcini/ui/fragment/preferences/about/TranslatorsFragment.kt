package ac.mdiq.podcini.ui.fragment.preferences.about

import android.R.color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.ListFragment
import ac.mdiq.podcini.ui.adapter.SimpleIconListAdapter
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.InputStreamReader

class TranslatorsFragment : ListFragment() {
    private var translatorsLoader: Disposable? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.divider = null
        listView.setSelector(color.transparent)

        translatorsLoader =
            Single.create { emitter: SingleEmitter<ArrayList<SimpleIconListAdapter.ListItem>?> ->
                val translators = ArrayList<SimpleIconListAdapter.ListItem>()
                val reader = BufferedReader(InputStreamReader(
                    requireContext().assets.open("translators.csv"), "UTF-8"))
                var line: String
                while ((reader.readLine().also { line = it }) != null) {
                    val info = line.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    translators.add(SimpleIconListAdapter.ListItem(info[0], info[1], ""))
                }
                emitter.onSuccess(translators)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { translators: ArrayList<SimpleIconListAdapter.ListItem>? ->
                        if (translators != null) listAdapter = SimpleIconListAdapter(requireContext(), translators)
                    },
                    { error: Throwable -> Toast.makeText(context, error.message, Toast.LENGTH_LONG).show() }
                )
    }

    override fun onStop() {
        super.onStop()
        if (translatorsLoader != null) {
            translatorsLoader!!.dispose()
        }
    }
}
