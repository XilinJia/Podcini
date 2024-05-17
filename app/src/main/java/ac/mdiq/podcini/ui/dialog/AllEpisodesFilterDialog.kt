package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Bundle

class AllEpisodesFilterDialog : ItemFilterDialog() {
    override fun onFilterChanged(newFilterValues: Set<String>) {
        EventFlow.postEvent(FlowEvent.AllEpisodesFilterChangedEvent(newFilterValues))
    }

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
