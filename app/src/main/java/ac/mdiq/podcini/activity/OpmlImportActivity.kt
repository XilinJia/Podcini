package ac.mdiq.podcini.activity

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.export.opml.OpmlElement
import ac.mdiq.podcini.core.export.opml.OpmlReader
import ac.mdiq.podcini.core.preferences.ThemeSwitcher.getTheme
import ac.mdiq.podcini.core.storage.DBTasks
import ac.mdiq.podcini.core.util.download.FeedUpdateManager.runOnce
import ac.mdiq.podcini.databinding.OpmlSelectionBinding
import ac.mdiq.podcini.model.feed.Feed
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.apache.commons.io.input.BOMInputStream
import java.io.InputStreamReader
import java.io.Reader

/**
 * Activity for Opml Import.
 */
class OpmlImportActivity : AppCompatActivity() {
    private var uri: Uri? = null
    private lateinit var viewBinding: OpmlSelectionBinding
    private lateinit var selectAll: MenuItem
    private lateinit var deselectAll: MenuItem

    private var listAdapter: ArrayAdapter<String>? = null
    private var readElements: ArrayList<OpmlElement>? = null

    @UnstableApi override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTheme(this))
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        viewBinding = OpmlSelectionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.feedlist.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        viewBinding.feedlist.onItemClickListener =
            OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                val checked = viewBinding.feedlist.checkedItemPositions
                var checkedCount = 0
                for (i in 0 until checked.size()) {
                    if (checked.valueAt(i)) {
                        checkedCount++
                    }
                }
                if (listAdapter != null) {
                    if (checkedCount == listAdapter!!.count) {
                        selectAll.setVisible(false)
                        deselectAll.setVisible(true)
                    } else {
                        deselectAll.setVisible(false)
                        selectAll.setVisible(true)
                    }
                }
            }
        viewBinding.butCancel.setOnClickListener { v: View? ->
            setResult(RESULT_CANCELED)
            finish()
        }
        viewBinding.butConfirm.setOnClickListener { v: View? ->
            viewBinding.progressBar.visibility = View.VISIBLE
            Completable.fromAction {
                val checked = viewBinding.feedlist.checkedItemPositions
                for (i in 0 until checked.size()) {
                    if (!checked.valueAt(i)) {
                        continue
                    }
                    if (!readElements.isNullOrEmpty()) {
                        val element = readElements!![checked.keyAt(i)]
                        val feed = Feed(element.xmlUrl, null,
                            if (element.text != null) element.text else "Unknown podcast")
                        feed.items = mutableListOf()
                        DBTasks.updateFeed(this, feed, false)
                    }
                }
                runOnce(this)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        viewBinding.progressBar.visibility = View.GONE
                        val intent = Intent(this@OpmlImportActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    }, { e: Throwable ->
                        e.printStackTrace()
                        viewBinding.progressBar.visibility = View.GONE
                        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    })
        }

        var uri = intent.data
        if (uri != null && uri.toString().startsWith("/")) {
            uri = Uri.parse("file://$uri")
        } else {
            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (extraText != null) {
                uri = Uri.parse(extraText)
            }
        }
        importUri(uri)
    }

    fun importUri(uri: Uri?) {
        if (uri == null) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.opml_import_error_no_file)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        this.uri = uri
        startImport()
    }

    private val titleList: List<String>
        get() {
            val result: MutableList<String> = ArrayList()
            if (!readElements.isNullOrEmpty()) {
                for (element in readElements!!) {
                    if (element.text != null) result.add(element.text!!)
                }
            }
            return result
        }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.opml_selection_options, menu)
        selectAll = menu.findItem(R.id.select_all_item)
        deselectAll = menu.findItem(R.id.deselect_all_item)
        deselectAll.setVisible(false)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.select_all_item -> {
                selectAll.setVisible(false)
                selectAllItems(true)
                deselectAll.setVisible(true)
                return true
            }
            R.id.deselect_all_item -> {
                deselectAll.setVisible(false)
                selectAllItems(false)
                selectAll.setVisible(true)
                return true
            }
            android.R.id.home -> {
                finish()
            }
        }
        return false
    }

    private fun selectAllItems(b: Boolean) {
        for (i in 0 until viewBinding.feedlist.count) {
            viewBinding.feedlist.setItemChecked(i, b)
        }
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startImport()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.opml_import_ask_read_permission)
                    .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> requestPermission() }
                    .setNegativeButton(R.string.cancel_label) { dialog: DialogInterface?, which: Int -> finish() }
                    .show()
            }
        }

    /** Starts the import process.  */
    private fun startImport() {
        viewBinding.progressBar.visibility = View.VISIBLE

        Observable.fromCallable {
            val opmlFileStream = contentResolver.openInputStream(uri!!)
            val bomInputStream = BOMInputStream(opmlFileStream)
            val bom = bomInputStream.bom
            val charsetName = if (bom == null) "UTF-8" else bom.charsetName
            val reader: Reader = InputStreamReader(bomInputStream, charsetName)
            val opmlReader = OpmlReader()
            val result = opmlReader.readDocument(reader)
            reader.close()
            result
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: ArrayList<OpmlElement>? ->
                    viewBinding.progressBar.visibility = View.GONE
                    Log.d(TAG, "Parsing was successful")
                    readElements = result
                    listAdapter = ArrayAdapter(this@OpmlImportActivity,
                        android.R.layout.simple_list_item_multiple_choice,
                        titleList)
                    viewBinding.feedlist.adapter = listAdapter
                }, { e: Throwable ->
                    Log.d(TAG, Log.getStackTraceString(e))
                    val message = if (e.message == null) "" else e.message!!
                    if (message.lowercase().contains("permission")
                            && Build.VERSION.SDK_INT >= 23) {
                        val permission = ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE)
                        if (permission != PackageManager.PERMISSION_GRANTED) {
                            requestPermission()
                            return@subscribe
                        }
                    }
                    viewBinding.progressBar.visibility = View.GONE
                    val alert = MaterialAlertDialogBuilder(this)
                    alert.setTitle(R.string.error_label)
                    val userReadable = getString(R.string.opml_reader_error)
                    val details = e.message
                    val total = """
                    $userReadable
                    
                    $details
                    """.trimIndent()
                    val errorMessage = SpannableString(total)
                    errorMessage.setSpan(ForegroundColorSpan(-0x77777778),
                        userReadable.length, total.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    alert.setMessage(errorMessage)
                    alert.setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> finish() }
                    alert.show()
                })
    }

    companion object {
        private const val TAG = "OpmlImportBaseActivity"
    }
}
