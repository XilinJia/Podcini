package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ChooseDataFolderDialogBinding
import ac.mdiq.podcini.ui.adapter.DataFolderAdapter
import android.content.Context
import android.view.View
import androidx.core.util.Consumer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ChooseDataFolderDialog {
    fun showDialog(context: Context, handlerFunc: Consumer<String?>) {
        val content = View.inflate(context, R.layout.choose_data_folder_dialog, null)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(content)
            .setTitle(R.string.choose_data_directory)
            .setMessage(R.string.choose_data_directory_message)
            .setNegativeButton(R.string.cancel_label, null)
            .create()
        val binding = ChooseDataFolderDialogBinding.bind(content)
        val recyclerView = binding.recyclerView
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
