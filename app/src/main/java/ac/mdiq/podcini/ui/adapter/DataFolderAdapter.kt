package ac.mdiq.podcini.ui.adapter

import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.util.Consumer
import androidx.recyclerview.widget.RecyclerView
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ChooseDataFolderDialogEntryBinding
import ac.mdiq.podcini.util.StorageUtils.getFreeSpaceAvailable
import ac.mdiq.podcini.util.StorageUtils.getTotalSpaceAvailable
import ac.mdiq.podcini.preferences.UserPreferences.getDataFolder
import java.io.File

class DataFolderAdapter(context: Context, selectionHandler: Consumer<String>) : RecyclerView.Adapter<DataFolderAdapter.ViewHolder?>() {

    private val selectionHandler: Consumer<String>
    private val currentPath: String?
    private val entries: List<StoragePath>
    private val freeSpaceString: String

    init {
        this.entries = getStorageEntries(context)
        this.currentPath = getCurrentPath()
        this.selectionHandler = selectionHandler
        this.freeSpaceString = context.getString(R.string.choose_data_directory_available_space)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val entryView = inflater.inflate(R.layout.choose_data_folder_dialog_entry, parent, false)
        return ViewHolder(entryView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val storagePath = entries[position]
        val context = holder.root.context
        val freeSpace = Formatter.formatShortFileSize(context, storagePath.availableSpace)
        val totalSpace = Formatter.formatShortFileSize(context, storagePath.totalSpace)

        holder.path.text = storagePath.shortPath
        holder.size.text = String.format(freeSpaceString, freeSpace, totalSpace)
        holder.progressBar.progress = storagePath.usagePercentage
        val selectListener = View.OnClickListener { _: View? ->
            selectionHandler.accept(storagePath.fullPath)
        }
        holder.root.setOnClickListener(selectListener)
        holder.radioButton.setOnClickListener(selectListener)

        if (storagePath.fullPath == currentPath) holder.radioButton.toggle()
    }

    override fun getItemCount(): Int {
        return entries.size
    }

    private fun getCurrentPath(): String? {
        val dataFolder = getDataFolder(null)
        return dataFolder?.absolutePath
    }

    private fun getStorageEntries(context: Context): List<StoragePath> {
        val mediaDirs = context.getExternalFilesDirs(null)
        val entries: MutableList<StoragePath> = ArrayList(mediaDirs.size)
        for (dir in mediaDirs) {
            if (!isWritable(dir)) continue
            entries.add(StoragePath(dir.absolutePath))
        }
        if (entries.isEmpty() && isWritable(context.filesDir)) entries.add(StoragePath(context.filesDir.absolutePath))
        return entries
    }

    private fun isWritable(dir: File?): Boolean {
        return dir != null && dir.exists() && dir.canRead() && dir.canWrite()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding = ChooseDataFolderDialogEntryBinding.bind(itemView)
        val root: View = binding.root
        val path: TextView = binding.path
        val size: TextView = binding.size
        val radioButton: RadioButton = binding.radioButton
        val progressBar: ProgressBar = binding.usedSpace
    }

    internal class StoragePath(val fullPath: String) {
        val shortPath: String
            get() {
                val prefixIndex = fullPath.indexOf("Android")
                return if ((prefixIndex > 0)) fullPath.substring(0, prefixIndex) else fullPath
            }

        val availableSpace: Long
            get() = getFreeSpaceAvailable(fullPath)

        val totalSpace: Long
            get() = getTotalSpaceAvailable(fullPath)

        val usagePercentage: Int
            get() = 100 - (100 * availableSpace / totalSpace.toFloat()).toInt()
    }
}