package de.danoeh.antennapod.dialog

import android.content.Context
import android.view.View
import androidx.core.util.Consumer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.adapter.DataFolderAdapter

object ChooseDataFolderDialog {
    fun showDialog(context: Context?, handlerFunc: Consumer<String?>) {
        val content = View.inflate(context, R.layout.choose_data_folder_dialog, null)
        val dialog = MaterialAlertDialogBuilder(context!!)
            .setView(content)
            .setTitle(R.string.choose_data_directory)
            .setMessage(R.string.choose_data_directory_message)
            .setNegativeButton(R.string.cancel_label, null)
            .create()
        val recyclerView = content.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        val adapter = DataFolderAdapter(context) { path: String? ->
            dialog.dismiss()
            handlerFunc.accept(path)
        }
        recyclerView.adapter = adapter

        if (adapter.itemCount != 0) {
            dialog.show()
        }
    }
}
