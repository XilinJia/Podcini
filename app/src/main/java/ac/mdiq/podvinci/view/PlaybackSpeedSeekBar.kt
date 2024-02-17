package ac.mdiq.podvinci.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.util.Consumer
import ac.mdiq.podvinci.R

class PlaybackSpeedSeekBar : FrameLayout {
    private lateinit var seekBar: SeekBar
    private var progressChangedListener: Consumer<Float>? = null

    constructor(context: Context) : super(context) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setup()
    }

    private fun setup() {
        inflate(context, R.layout.playback_speed_seek_bar, this)
        seekBar = findViewById(R.id.playback_speed)
        findViewById<View>(R.id.butDecSpeed).setOnClickListener { v: View? -> seekBar.progress -= 2 }
        findViewById<View>(R.id.butIncSpeed).setOnClickListener { v: View? -> seekBar.progress += 2 }

        seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val playbackSpeed = (progress + 10) / 20.0f
                if (progressChangedListener != null) {
                    progressChangedListener!!.accept(playbackSpeed)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })
    }

    fun updateSpeed(speedMultiplier: Float) {
        seekBar.progress = Math.round((20 * speedMultiplier) - 10)
    }

    fun setProgressChangedListener(progressChangedListener: Consumer<Float>?) {
        this.progressChangedListener = progressChangedListener
    }

    val currentSpeed: Float
        get() = (seekBar.progress + 10) / 20.0f

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        seekBar.isEnabled = enabled
        findViewById<View>(R.id.butDecSpeed).isEnabled = enabled
        findViewById<View>(R.id.butIncSpeed).isEnabled = enabled
    }
}
