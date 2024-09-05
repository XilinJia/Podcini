package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.OpmlSelectionBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.preferences.ThemeSwitcher.getTheme
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlElement
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlReader
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.input.BOMInputStream
import java.io.InputStreamReader
import java.io.Reader

/**
 * Activity for Opml Import.
 */
class OpmlImportActivity : AppCompatActivity() {
    private var uri: Uri? = null
    private var _binding: OpmlSelectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var selectAll: MenuItem
    private lateinit var deselectAll: MenuItem

    private var listAdapter: ArrayAdapter<String>? = null
    private var readElements: ArrayList<OpmlElement>? = null

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

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) startImport()
        else {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.opml_import_ask_read_permission)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> requestPermission() }
                .setNegativeButton(R.string.cancel_label) { _: DialogInterface?, _: Int -> finish() }
                .show()
        }
    }

    @UnstableApi override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTheme(this))
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        _binding = OpmlSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.feedlist.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        binding.feedlist.onItemClickListener = OnItemClickListener { _: AdapterView<*>?, _: View?, _: Int, _: Long ->
            val checked = binding.feedlist.checkedItemPositions
            var checkedCount = 0
            for (i in 0 until checked.size()) {
                if (checked.valueAt(i)) checkedCount++
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
        binding.butCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        binding.butConfirm.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val checked = binding.feedlist.checkedItemPositions
                        for (i in 0 until checked.size()) {
                            if (!checked.valueAt(i)) continue

                            if (!readElements.isNullOrEmpty()) {
                                val element = readElements!![checked.keyAt(i)]
                                val feed = Feed(element.xmlUrl, null, if (element.text != null) element.text else "Unknown podcast")
                                feed.episodes.clear()
                                updateFeed(this@OpmlImportActivity, feed, false)
                            }
                        }
                        runOnce(this@OpmlImportActivity)
                    }
                    binding.progressBar.visibility = View.GONE
                    val intent = Intent(this@OpmlImportActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                } catch (e: Throwable) {
                    e.printStackTrace()
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@OpmlImportActivity, (e.message ?: "Import error"), Toast.LENGTH_LONG).show()
                }
            }
        }

        var uri = intent.data
        if (uri != null && uri.toString().startsWith("/")) uri = Uri.parse("file://$uri")
        else {
            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (extraText != null) uri = Uri.parse(extraText)
        }
        importUri(uri)
    }

    private fun importUri(uri: Uri?) {
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
            android.R.id.home -> finish()
        }
        return false
    }

    private fun selectAllItems(b: Boolean) {
        for (i in 0 until binding.feedlist.count) {
            binding.feedlist.setItemChecked(i, b)
        }
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Starts the import process.  */
    private fun startImport() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val opmlFileStream = contentResolver.openInputStream(uri!!)
                val bomInputStream = BOMInputStream(opmlFileStream)
                val bom = bomInputStream.bom
                val charsetName = if (bom == null) "UTF-8" else bom.charsetName
                val reader: Reader = InputStreamReader(bomInputStream, charsetName)
                val opmlReader = OpmlReader()
                val result = opmlReader.readDocument(reader)
                reader.close()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Logd(TAG, "Parsing was successful")
                    readElements = result
                    listAdapter = ArrayAdapter(this@OpmlImportActivity, android.R.layout.simple_list_item_multiple_choice, titleList)
                    binding.feedlist.adapter = listAdapter
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Logd(TAG, Log.getStackTraceString(e))
                    val message = if (e.message == null) "" else e.message!!
                    if (message.lowercase().contains("permission")) {
                        val permission = ActivityCompat.checkSelfPermission(this@OpmlImportActivity, Manifest.permission.READ_EXTERNAL_STORAGE)
                        if (permission != PackageManager.PERMISSION_GRANTED) {
                            requestPermission()
                            return@withContext
                        }
                    }
                    binding.progressBar.visibility = View.GONE
                    val alert = MaterialAlertDialogBuilder(this@OpmlImportActivity)
                    alert.setTitle(R.string.error_label)
                    val userReadable = getString(R.string.opml_reader_error)
                    val details = e.message
                    val total = """
                    $userReadable
                    
                    $details
                    """.trimIndent()
                    val errorMessage = SpannableString(total)
                    errorMessage.setSpan(ForegroundColorSpan(-0x77777778), userReadable.length, total.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    alert.setMessage(errorMessage)
                    alert.setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> finish() }
                    alert.show()
                }
            }
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    companion object {
        private val TAG: String = OpmlImportActivity::class.simpleName ?: "Anonymous"
    }
}
