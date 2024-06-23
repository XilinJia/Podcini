package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SwitchQueueDialogBinding
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Activity
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference

class SwitchQueueDialog(activity: Activity) {
    private val activityRef: WeakReference<Activity> = WeakReference(activity)

    fun show() {
        val activity = activityRef.get() ?: return
        val binding = SwitchQueueDialogBinding.inflate(LayoutInflater.from(activity))
        val queues = realm.query(PlayQueue::class).find()
        val queueNames = queues.map { it.name }.toTypedArray()
        val adaptor = ArrayAdapter(activity, android.R.layout.simple_spinner_item, queueNames)
        adaptor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val catSpinner = binding.queueSpinner
        catSpinner.setAdapter(adaptor)
        catSpinner.setSelection(adaptor.getPosition(curQueue.name))
        var curQueue_: PlayQueue = curQueue
        catSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                curQueue_ = queues[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setTitle(R.string.switch_queue_label)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                val items = mutableListOf<Episode>()
                items.addAll(curQueue.episodes)
                items.addAll(curQueue_.episodes)
                curQueue = realm.copyFromRealm(curQueue_)
                curQueue.update()
                upsertBlk(curQueue) {}
                EventFlow.postEvent(FlowEvent.QueueEvent.switchQueue(items))
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }
}
