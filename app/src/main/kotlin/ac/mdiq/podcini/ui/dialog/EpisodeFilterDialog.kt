package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.FeedFilter
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.Companion.TAG
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.FeedFilterDialog.FeedFilterGroup.ItemProperties
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

abstract class EpisodeFilterDialog : BottomSheetDialogFragment() {

    var filter: EpisodeFilter? = null
    val filtersDisabled: MutableSet<FeedItemFilterGroup> = mutableSetOf()
    private val filterValues: MutableSet<String> = mutableSetOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    MainView()
                }
            }
        }
        return composeView
    }

    @Composable
    fun MainView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        Column {
            for (item in FeedItemFilterGroup.entries) {
                if (item in filtersDisabled) continue
                Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    var selectedIndex by remember { mutableStateOf(-1) }
                    LaunchedEffect(Unit) {
                        if (filter != null) {
                            if (item.values[0].filterId in filter!!.values) selectedIndex = 0
                            else if (item.values[1].filterId in filter!!.values) selectedIndex = 1
                        }
                    }
                    OutlinedButton(modifier = Modifier.padding(2.dp), border = BorderStroke(2.dp, if (selectedIndex != 0) textColor else Color.Green),
                        onClick = {
                            if (selectedIndex != 0) {
                                selectedIndex = 0
                                filterValues.add(item.values[0].filterId)
                                filterValues.remove(item.values[1].filterId)
                            } else {
                                selectedIndex = -1
                                filterValues.remove(item.values[0].filterId)
                            }
                            onFilterChanged(filterValues)
                        },
                    ) {
                        Text(text = stringResource(item.values[0].displayName), color = textColor)
                    }
                    Spacer(Modifier.width(5.dp))
                    OutlinedButton(modifier = Modifier.padding(2.dp), border = BorderStroke(2.dp, if (selectedIndex != 1) textColor else Color.Green),
                        onClick = {
                            if (selectedIndex != 1) {
                                selectedIndex = 1
                                filterValues.add(item.values[1].filterId)
                                filterValues.remove(item.values[0].filterId)
                            } else {
                                selectedIndex = -1
                                filterValues.remove(item.values[1].filterId)
                            }
                            onFilterChanged(filterValues)
                        },
                    ) {
                        Text(text = stringResource(item.values[1].displayName), color = textColor)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        super.onDestroyView()
    }

    abstract fun onFilterChanged(newFilterValues: Set<String>)

    enum class FeedItemFilterGroup(vararg values: ItemProperties) {
        PLAYED(ItemProperties(R.string.hide_played_episodes_label, EpisodeFilter.States.played.name), ItemProperties(R.string.not_played, EpisodeFilter.States.unplayed.name)),
        PAUSED(ItemProperties(R.string.hide_paused_episodes_label, EpisodeFilter.States.paused.name), ItemProperties(R.string.not_paused, EpisodeFilter.States.not_paused.name)),
        FAVORITE(ItemProperties(R.string.hide_is_favorite_label, EpisodeFilter.States.is_favorite.name), ItemProperties(R.string.not_favorite, EpisodeFilter.States.not_favorite.name)),
        MEDIA(ItemProperties(R.string.has_media, EpisodeFilter.States.has_media.name), ItemProperties(R.string.no_media, EpisodeFilter.States.no_media.name)),
        OPINION(ItemProperties(R.string.has_comments, EpisodeFilter.States.has_comments.name), ItemProperties(R.string.no_comments, EpisodeFilter.States.no_comments.name)),
        QUEUED(ItemProperties(R.string.queued_label, EpisodeFilter.States.queued.name), ItemProperties(R.string.not_queued_label, EpisodeFilter.States.not_queued.name)),
        DOWNLOADED(ItemProperties(R.string.downloaded_label, EpisodeFilter.States.downloaded.name), ItemProperties(R.string.not_downloaded_label, EpisodeFilter.States.not_downloaded.name)),
        AUTO_DOWNLOADABLE(ItemProperties(R.string.auto_downloadable_label, EpisodeFilter.States.auto_downloadable.name), ItemProperties(R.string.not_auto_downloadable_label, EpisodeFilter.States.not_auto_downloadable.name));

        @JvmField
        val values: Array<ItemProperties> = arrayOf(*values)

        class ItemProperties(@JvmField val displayName: Int, @JvmField val filterId: String)
    }
}
