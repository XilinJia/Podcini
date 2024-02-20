package ac.mdiq.podcini.fragment.preferences.about

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.ListFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.activity.PreferenceActivity
import ac.mdiq.podcini.adapter.SimpleIconListAdapter
import ac.mdiq.podcini.core.util.IntentUtils.openInBrowser
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.xml.parsers.DocumentBuilderFactory

class LicensesFragment : ListFragment() {
    private var licensesLoader: Disposable? = null
    private val licenses = ArrayList<LicenseItem>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.divider = null

        licensesLoader = Single.create { emitter: SingleEmitter<ArrayList<LicenseItem>?> ->
            licenses.clear()
            val stream = requireContext().assets.open("licenses.xml")
            val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val libraryList = docBuilder.parse(stream).getElementsByTagName("library")
            for (i in 0 until libraryList.length) {
                val lib = libraryList.item(i).attributes
                licenses.add(LicenseItem(
                    lib.getNamedItem("name").textContent,
                    String.format("By %s, %s license",
                        lib.getNamedItem("author").textContent,
                        lib.getNamedItem("license").textContent),
                    "",
                    lib.getNamedItem("website").textContent,
                    lib.getNamedItem("licenseText").textContent))
            }
            emitter.onSuccess(licenses)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { developers: ArrayList<LicenseItem>? -> if (developers != null) listAdapter = SimpleIconListAdapter(requireContext(), developers) },
                { error: Throwable -> Toast.makeText(context, error.message, Toast.LENGTH_LONG).show() }
            )
    }

    private class LicenseItem(title: String,
                              subtitle: String,
                              imageUrl: String,
                              val licenseUrl: String,
                              val licenseTextFile: String
    ) : SimpleIconListAdapter.ListItem(title, subtitle, imageUrl)

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)

        val item = licenses[position]
        val items = arrayOf<CharSequence>("View website", "View license")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title)
            .setItems(items) { dialog: DialogInterface?, which: Int ->
                if (which == 0) {
                    openInBrowser(requireContext(), item.licenseUrl)
                } else if (which == 1) {
                    showLicenseText(item.licenseTextFile)
                }
            }.show()
    }

    private fun showLicenseText(licenseTextFile: String) {
        try {
            val reader = BufferedReader(InputStreamReader(
                requireContext().assets.open(licenseTextFile), "UTF-8"))
            val licenseText = StringBuilder()
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                licenseText.append(line).append("\n")
            }

            MaterialAlertDialogBuilder(requireContext())
                .setMessage(licenseText)
                .show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        licensesLoader?.dispose()
    }

    override fun onStart() {
        super.onStart()
        (activity as PreferenceActivity).supportActionBar!!.setTitle(R.string.licenses)
    }
}
