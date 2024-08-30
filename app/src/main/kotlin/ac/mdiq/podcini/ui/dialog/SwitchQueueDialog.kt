package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SelectQueueDialogBinding
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.app.Activity
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.RadioButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference

class SwitchQueueDialog(activity: Activity) {
    private val activityRef: WeakReference<Activity> = WeakReference(activity)

    fun show() {
        val activity = activityRef.get() ?: return
        val binding = SelectQueueDialogBinding.inflate(LayoutInflater.from(activity))
        val queues = realm.query(PlayQueue::class).find()
        var curQueue_: PlayQueue = curQueue
        for (i in queues.indices) {
            val radioButton = RadioButton(activity)
            radioButton.text = queues[i].name
            radioButton.textSize = 20f
            radioButton.tag = i
            binding.radioGroup.addView(radioButton)
            if (queues[i].id == curQueue.id) binding.radioGroup.check(radioButton.id)
        }
        binding.radioGroup.setOnCheckedChangeListener { group, checkedId ->
            binding.radioGroup.check(checkedId)
            val radioButton = group.findViewById<RadioButton>(checkedId)
            val selectedIndex = radioButton.tag as Int
            curQueue_ = queues[selectedIndex]
        }
        MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setTitle(R.string.switch_queue_label)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                if (curQueue_.id != curQueue.id) {
                    val items = mutableListOf<Episode>()
                    items.addAll(curQueue.episodes)
                    items.addAll(curQueue_.episodes)
//                    unmanaged(curQueue_)
                    curQueue = upsertBlk(curQueue_) {
                        it.update()
                    }
                    EventFlow.postEvent(FlowEvent.QueueEvent.switchQueue(items))
                }
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }
}
