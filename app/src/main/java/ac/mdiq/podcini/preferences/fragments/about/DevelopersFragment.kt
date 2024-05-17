package ac.mdiq.podcini.preferences.fragments.about

import ac.mdiq.podcini.ui.adapter.SimpleIconListAdapter
import android.R.color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.ListFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class DevelopersFragment : ListFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.divider = null
        listView.setSelector(color.transparent)

        lifecycleScope.launch(Dispatchers.IO) {
            val developers = ArrayList<SimpleIconListAdapter.ListItem>()
            val reader = BufferedReader(InputStreamReader(requireContext().assets.open("developers.csv"), "UTF-8"))
            var line = ""
            while ((reader.readLine()?.also { line = it }) != null) {
                val info = line.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                developers.add(SimpleIconListAdapter.ListItem(info[0], info[2], "https://avatars2.githubusercontent.com/u/" + info[1] + "?s=60&v=4"))
            }
            withContext(Dispatchers.Main) {
                listAdapter = SimpleIconListAdapter(requireContext(), developers)            }
        }.invokeOnCompletion { throwable ->
            if (throwable != null) Toast.makeText(context, throwable.message, Toast.LENGTH_LONG).show()
        }
    }
}
