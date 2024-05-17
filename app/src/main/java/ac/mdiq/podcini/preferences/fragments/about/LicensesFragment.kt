package ac.mdiq.podcini.preferences.fragments.about

import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.adapter.SimpleIconListAdapter
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.ListFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.xml.parsers.DocumentBuilderFactory

class LicensesFragment : ListFragment() {
    private val licenses = ArrayList<LicenseItem>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.divider = null

        lifecycleScope.launch(Dispatchers.IO) {
            licenses.clear()
            val stream = requireContext().assets.open("licenses.xml")
            val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val libraryList = docBuilder.parse(stream).getElementsByTagName("library")
            for (i in 0 until libraryList.length) {
                val lib = libraryList.item(i).attributes
                licenses.add(LicenseItem(lib.getNamedItem("name").textContent,
                    String.format("By %s, %s license", lib.getNamedItem("author").textContent, lib.getNamedItem("license").textContent),
                    "", lib.getNamedItem("website").textContent, lib.getNamedItem("licenseText").textContent))
            }
            withContext(Dispatchers.Main) {
                listAdapter = SimpleIconListAdapter(requireContext(), licenses)
            }
        }.invokeOnCompletion { throwable ->
            if (throwable!= null) {
                Toast.makeText(context, throwable.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private class LicenseItem(title: String, subtitle: String, imageUrl: String, val licenseUrl: String, val licenseTextFile: String)
        : SimpleIconListAdapter.ListItem(title, subtitle, imageUrl)

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)

        val item = licenses[position]
        val items = arrayOf<CharSequence>("View website", "View license")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title)
            .setItems(items) { _: DialogInterface?, which: Int ->
                when (which) {
                    0 -> openInBrowser(requireContext(), item.licenseUrl)
                    1 -> showLicenseText(item.licenseTextFile)
                }
            }.show()
    }

    private fun showLicenseText(licenseTextFile: String) {
        try {
            val reader = BufferedReader(InputStreamReader(requireContext().assets.open(licenseTextFile), "UTF-8"))
            val licenseText = StringBuilder()
            var line = ""
            while ((reader.readLine()?.also { line = it }) != null) {
                licenseText.append(line).append("\n")
            }

            MaterialAlertDialogBuilder(requireContext())
                .setMessage(licenseText)
                .show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.licenses)
    }
}
