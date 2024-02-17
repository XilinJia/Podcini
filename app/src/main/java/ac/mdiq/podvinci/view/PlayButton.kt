package ac.mdiq.podvinci.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import ac.mdiq.podvinci.R

class PlayButton : AppCompatImageButton {
    private var isShowPlay = true
    private var isVideoScreen = false

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setIsVideoScreen(isVideoScreen: Boolean) {
        this.isVideoScreen = isVideoScreen
    }

    fun setIsShowPlay(showPlay: Boolean) {
        if (this.isShowPlay != showPlay) {
            this.isShowPlay = showPlay
            contentDescription = context.getString(if (showPlay) R.string.play_label else R.string.pause_label)
            when {
                isVideoScreen -> {
                    setImageResource(if (showPlay) R.drawable.ic_play_video_white else R.drawable.ic_pause_video_white)
                }
                !isShown -> {
                    setImageResource(if (showPlay) R.drawable.ic_play_48dp else R.drawable.ic_pause)
                }
                showPlay -> {
                    val drawable = AnimatedVectorDrawableCompat.create(context, R.drawable.ic_animate_pause_play)
                    setImageDrawable(drawable)
                    drawable?.start()
                }
                else -> {
                    val drawable = AnimatedVectorDrawableCompat.create(context, R.drawable.ic_animate_play_pause)
                    setImageDrawable(drawable)
                    drawable?.start()
                }
            }
        }
    }
}
