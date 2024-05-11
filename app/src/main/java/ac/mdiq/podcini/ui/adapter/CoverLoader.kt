package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.MainActivity
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import coil.ImageLoader
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.target.Target
import java.lang.ref.WeakReference

class CoverLoader(private val activity: MainActivity) {
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

//        val coverTarget = CoverTarget(fallbackTitle, imgvCover!!, textAndImageCombined)
        val coverTargetCoil = CoilCoverTarget(fallbackTitle, imgvCover!!, textAndImageCombined)

        if (resource != 0) {
//            Glide.with(imgvCover!!).clear(coverTarget)
            val imageLoader = ImageLoader.Builder(activity).build()
            imageLoader.enqueue(ImageRequest.Builder(activity).data(null).target(coverTargetCoil).build())
            imgvCover!!.setImageResource(resource)
//            CoverTarget.setTitleVisibility(fallbackTitle, textAndImageCombined)
            CoilCoverTarget.setTitleVisibility(fallbackTitle, textAndImageCombined)
            return
        }

//        val options: RequestOptions = RequestOptions()
//            .fitCenter()
//            .dontAnimate()
//
//        var builder: RequestBuilder<Drawable?> = Glide.with(imgvCover!!)
//            .`as`(Drawable::class.java)
//            .load(uri)
//            .apply(options)
//
//        if (!fallbackUri.isNullOrBlank()) {
//            builder = builder.error(Glide.with(imgvCover!!)
//                .`as`(Drawable::class.java)
//                .load(fallbackUri)
//                .apply(options))
//        }
//
//        builder.into<CoverTarget>(coverTarget)

        val request = ImageRequest.Builder(activity)
            .data(uri)
            .listener(object : ImageRequest.Listener {
                override fun onError(request: ImageRequest, throwable: ErrorResult) {
                    val fallbackImageRequest = ImageRequest.Builder(activity)
                        .data(fallbackUri)
                        .error(R.mipmap.ic_launcher)
                        .target(coverTargetCoil)
                        .build()
                    activity.imageLoader.enqueue(fallbackImageRequest)
                }
            })
            .target(coverTargetCoil)
            .build()
        activity.imageLoader.enqueue(request)

    }

//    internal class CoverTarget(fallbackTitle: TextView?, coverImage: ImageView, private val textAndImageCombined: Boolean)
//        : CustomViewTarget<ImageView, Drawable>(coverImage) {
//
//        private val fallbackTitle: WeakReference<TextView?> = WeakReference<TextView?>(fallbackTitle)
//        private val cover: WeakReference<ImageView> = WeakReference(coverImage)
//
//        override fun onLoadFailed(errorDrawable: Drawable?) {
//            setTitleVisibility(fallbackTitle.get(), true)
//        }
//
//        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable?>?) {
//            val ivCover = cover.get()
//            ivCover!!.setImageDrawable(resource)
//            setTitleVisibility(fallbackTitle.get(), textAndImageCombined)
//        }
//
//        override fun onResourceCleared(placeholder: Drawable?) {
//            val ivCover = cover.get()
//            ivCover!!.setImageDrawable(placeholder)
//            setTitleVisibility(fallbackTitle.get(), textAndImageCombined)
//        }
//
//        companion object {
//            fun setTitleVisibility(fallbackTitle: TextView?, textAndImageCombined: Boolean) {
//                fallbackTitle?.visibility = if (textAndImageCombined) View.VISIBLE else View.GONE
//            }
//        }
//    }

    internal class CoilCoverTarget(fallbackTitle: TextView?, coverImage: ImageView, private val textAndImageCombined: Boolean) : Target {

        private val fallbackTitle: WeakReference<TextView?> = WeakReference<TextView?>(fallbackTitle)
        private val cover: WeakReference<ImageView> = WeakReference(coverImage)

        override fun onStart(placeholder: Drawable?) {

        }
        override fun onError(errorDrawable: Drawable?) {
            setTitleVisibility(fallbackTitle.get(), true)
        }

        override fun onSuccess(resource: Drawable) {
            val ivCover = cover.get()
            ivCover!!.setImageDrawable(resource)
            setTitleVisibility(fallbackTitle.get(), textAndImageCombined)
        }

//        override fun onResourceCleared(placeholder: Drawable?) {
//            val ivCover = cover.get()
//            ivCover!!.setImageDrawable(placeholder)
//            setTitleVisibility(fallbackTitle.get(), textAndImageCombined)
//        }

        companion object {
            fun setTitleVisibility(fallbackTitle: TextView?, textAndImageCombined: Boolean) {
                fallbackTitle?.visibility = if (textAndImageCombined) View.VISIBLE else View.GONE
            }
        }
    }

}