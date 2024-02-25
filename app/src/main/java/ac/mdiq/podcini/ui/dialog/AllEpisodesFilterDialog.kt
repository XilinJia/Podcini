package ac.mdiq.podcini.ui.dialog

import android.os.Bundle
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import org.greenrobot.eventbus.EventBus

class AllEpisodesFilterDialog : ItemFilterDialog() {
    override fun onFilterChanged(newFilterValues: Set<String>) {
        EventBus.getDefault().post(AllEpisodesFilterChangedEvent(newFilterValues))
    }

    class AllEpisodesFilterChangedEvent(val filterValues: Set<String?>?)
    companion object {
        fun newInstance(filter: FeedItemFilter?): AllEpisodesFilterDialog {
            val dialog = AllEpisodesFilterDialog()
            val arguments = Bundle()
            arguments.putSerializable(ARGUMENT_FILTER, filter)
            dialog.arguments = arguments
            return dialog
        }
    }
}
