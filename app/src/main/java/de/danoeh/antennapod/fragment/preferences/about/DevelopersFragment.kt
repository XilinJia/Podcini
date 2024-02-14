package de.danoeh.antennapod.fragment.preferences.about

import android.R.color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.ListFragment
import de.danoeh.antennapod.adapter.SimpleIconListAdapter
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.InputStreamReader

class DevelopersFragment : ListFragment() {
    private var developersLoader: Disposable? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.divider = null
        listView.setSelector(color.transparent)

        developersLoader =
            Single.create { emitter: SingleEmitter<ArrayList<SimpleIconListAdapter.ListItem>?> ->
                val developers = ArrayList<SimpleIconListAdapter.ListItem>()
                val reader = BufferedReader(InputStreamReader(
                    requireContext().assets.open("developers.csv"), "UTF-8"))
                var line: String
                while ((reader.readLine().also { line = it }) != null) {
                    val info = line.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    developers.add(SimpleIconListAdapter.ListItem(info[0], info[2],
                        "https://avatars2.githubusercontent.com/u/" + info[1] + "?s=60&v=4"))
                }
                emitter.onSuccess(developers)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { developers: ArrayList<SimpleIconListAdapter.ListItem>? ->
                        if (developers != null) listAdapter = SimpleIconListAdapter(requireContext(), developers)
                    },
                    { error: Throwable -> Toast.makeText(context, error.message, Toast.LENGTH_LONG).show() }
                )
    }

    override fun onStop() {
        super.onStop()
        if (developersLoader != null) {
            developersLoader!!.dispose()
        }
    }
}
