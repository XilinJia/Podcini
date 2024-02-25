package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.ui.activity.MainActivity
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import java.lang.ref.WeakReference

class CoverLoader(activity: MainActivity) {
    private var resource = 0
    private var uri: String? = null
    private var fallbackUri: String? = null
    private var imgvCover: ImageView? = null
    private var textAndImageCombined = false
    private var fallbackTitle: TextView? = null

    fun withUri(uri: String?): CoverLoader {
        this.uri = uri
        return this
    }

    fun withResource(resource: Int): CoverLoader {
        this.resource = resource
        return this
    }

    fun withFallbackUri(uri: String?): CoverLoader {
        fallbackUri = uri
        return this
    }

    fun withCoverView(coverView: ImageView): CoverLoader {
        imgvCover = coverView
        return this
    }

    fun withPlaceholderView(title: TextView): CoverLoader {
        this.fallbackTitle = title
        return this
    }

    /**
     * Set cover text and if it should be shown even if there is a cover image.
     * @param fallbackTitle Fallback title text
     * @param textAndImageCombined Show cover text even if there is a cover image?
     */
    fun withPlaceholderView(fallbackTitle: TextView?, textAndImageCombined: Boolean): CoverLoader {
        this.fallbackTitle = fallbackTitle
        this.textAndImageCombined = textAndImageCombined
        return this
    }

    fun load() {
        if (imgvCover == null) return

        val coverTarget = CoverTarget(fallbackTitle, imgvCover!!, textAndImageCombined)

        if (resource != 0) {
            Glide.with(imgvCover!!).clear(coverTarget)
            imgvCover!!.setImageResource(resource)
            CoverTarget.setTitleVisibility(fallbackTitle, textAndImageCombined)
            return
        }

        val options: RequestOptions = RequestOptions()
            .fitCenter()
            .dontAnimate()

        var builder: RequestBuilder<Drawable?> = Glide.with(imgvCover!!)
            .`as`(Drawable::class.java)
            .load(uri)
            .apply(options)

        if (fallbackUri != null) {
            builder = builder.error(Glide.with(imgvCover!!)
                .`as`(Drawable::class.java)
                .load(fallbackUri)
                .apply(options))
        }

        builder.into<CoverTarget>(coverTarget)
    }

    internal class CoverTarget(fallbackTitle: TextView?,
                               coverImage: ImageView,
                               private val textAndImageCombined: Boolean
    ) : CustomViewTarget<ImageView, Drawable>(coverImage) {

        private val fallbackTitle: WeakReference<TextView?> = WeakReference<TextView?>(fallbackTitle)
        private val cover: WeakReference<ImageView> = WeakReference(coverImage)

        override fun onLoadFailed(errorDrawable: Drawable?) {
            setTitleVisibility(fallbackTitle.get(), true)
        }

        override fun onResourceReady(resource: Drawable,
                                     transition: Transition<in Drawable?>?
        ) {
            val ivCover = cover.get()
            ivCover!!.setImageDrawable(resource)
            setTitleVisibility(fallbackTitle.get(), textAndImageCombined)
        }

        override fun onResourceCleared(placeholder: Drawable?) {
            val ivCover = cover.get()
            ivCover!!.setImageDrawable(placeholder)
            setTitleVisibility(fallbackTitle.get(), textAndImageCombined)
        }

        companion object {
            fun setTitleVisibility(fallbackTitle: TextView?, textAndImageCombined: Boolean) {
                fallbackTitle?.visibility = if (textAndImageCombined) View.VISIBLE else View.GONE
            }
        }
    }
}