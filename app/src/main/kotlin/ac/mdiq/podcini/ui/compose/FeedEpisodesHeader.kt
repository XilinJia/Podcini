package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.FeedSettingsFragment
import ac.mdiq.podcini.ui.utils.TransitionEffect
import ac.mdiq.podcini.util.Logd
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedEpisodesHeader(activity: MainActivity, feed: Feed?, filterButColor: Color, filterClickCB: ()->Unit, filterLongClickCB: ()->Unit) {
    val TAG = "FeedEpisodesHeader"
    val textColor = MaterialTheme.colorScheme.onSurface
    ConstraintLayout(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val (controlRow, image1, image2, imgvCover, taColumn) = createRefs()
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).background(colorResource(id = R.color.image_readability_tint))
            .constrainAs(controlRow) {
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
            }, verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            Image(painter = painterResource(R.drawable.ic_filter_white), colorFilter = ColorFilter.tint(filterButColor), contentDescription = "butFilter",
                modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).combinedClickable(onClick = filterClickCB, onLongClick = filterLongClickCB))
            Spacer(modifier = Modifier.width(15.dp))
            Image(painter = painterResource(R.drawable.ic_settings_white), contentDescription = "butShowSettings",
                Modifier.width(40.dp).height(40.dp).padding(3.dp).clickable(onClick = {
                    if (feed != null) {
                        val fragment = FeedSettingsFragment.newInstance(feed)
                        activity.loadChildFragment(fragment, TransitionEffect.SLIDE)
                    }
                }))
            Spacer(modifier = Modifier.weight(1f))
            Text(feed?.episodes?.size?.toString()?:"", textAlign = TextAlign.Center, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
        Image(painter = painterResource(R.drawable.ic_rounded_corner_left), contentDescription = "left_corner",
            Modifier.width(12.dp).height(12.dp).constrainAs(image1) {
            bottom.linkTo(parent.bottom)
            start.linkTo(parent.start)
        })
        Image(painter = painterResource(R.drawable.ic_rounded_corner_right), contentDescription = "right_corner",
            Modifier.width(12.dp).height(12.dp).constrainAs(image2) {
                bottom.linkTo(parent.bottom)
                end.linkTo(parent.end)
            })
        AsyncImage(model = feed?.imageUrl?:"", contentDescription = "imgvCover",
            Modifier.width(120.dp).height(120.dp).padding(start = 16.dp, end = 16.dp, bottom = 12.dp).constrainAs(imgvCover) {
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
            }.clickable(onClick = {
                Logd(TAG, "icon clicked!")
            }))
        Column(Modifier.constrainAs(taColumn) {
                top.linkTo(imgvCover.top)
                start.linkTo(imgvCover.end) }) {
            Text(feed?.title?:"", color = textColor, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(feed?.author?:"", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}