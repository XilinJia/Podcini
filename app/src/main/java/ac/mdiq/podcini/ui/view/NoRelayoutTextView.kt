package ac.mdiq.podcini.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class NoRelayoutTextView : AppCompatTextView {
    private var requestLayoutEnabled = false
    private var maxTextLength = 0f

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun requestLayout() {
        if (requestLayoutEnabled) {
            super.requestLayout()
        }
        requestLayoutEnabled = false
    }

    override fun setText(text: CharSequence, type: BufferType) {
        val textLength = paint.measureText(text.toString())
        if (textLength > maxTextLength) {
            maxTextLength = textLength
            requestLayoutEnabled = true
        }
        super.setText(text, type)
    }
}
