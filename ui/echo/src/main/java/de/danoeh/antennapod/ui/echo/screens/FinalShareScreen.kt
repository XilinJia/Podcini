package de.danoeh.antennapod.ui.echo.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import de.danoeh.antennapod.ui.echo.EchoActivity
import de.danoeh.antennapod.ui.echo.R

class FinalShareScreen(context: Context,
                       private val favoritePodNames: ArrayList<String>,
                       private val favoritePodImages: ArrayList<Drawable>
) : BubbleScreen(context) {
    private val paintTextMain = Paint()
    private val paintCoverBorder: Paint
    private val heading = context.getString(R.string.echo_share_heading)
    private val year = EchoActivity.RELEASE_YEAR.toString()
    private val logo = AppCompatResources.getDrawable(context, R.drawable.echo)
    private val typefaceNormal = ResourcesCompat.getFont(context, R.font.sarabun_regular)
    private val typefaceBold = ResourcesCompat.getFont(context, R.font.sarabun_semi_bold)

    init {
        paintTextMain.color = -0x1
        paintTextMain.flags = Paint.ANTI_ALIAS_FLAG
        paintTextMain.style = Paint.Style.FILL
        paintCoverBorder = Paint()
        paintCoverBorder.color = -0x1
        paintCoverBorder.flags = Paint.ANTI_ALIAS_FLAG
        paintCoverBorder.style = Paint.Style.FILL
        paintCoverBorder.alpha = 70
    }

    override fun drawInner(canvas: Canvas?, innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float) {
        paintTextMain.textAlign = Paint.Align.CENTER
        paintTextMain.setTypeface(typefaceBold)
        val headingSize = innerBoxSize / 14
        paintTextMain.textSize = headingSize
        canvas!!.drawText(heading, innerBoxX + 0.5f * innerBoxSize, innerBoxY + headingSize, paintTextMain)
        paintTextMain.textSize = 0.12f * innerBoxSize
        canvas.drawText(year, innerBoxX + 0.8f * innerBoxSize, innerBoxY + 0.25f * innerBoxSize, paintTextMain)

        var fontSizePods = innerBoxSize / 18 // First one only
        var textY = innerBoxY + 0.62f * innerBoxSize
        for (i in favoritePodNames.indices) {
            val coverSize = if ((i == 0)) (0.4f * innerBoxSize) else (0.2f * innerBoxSize)
            val coverX = COVER_POSITIONS[i][0]
            val coverY = COVER_POSITIONS[i][1]
            val logo1Pos = RectF(innerBoxX + coverX * innerBoxSize,
                innerBoxY + (coverY + 0.12f) * innerBoxSize,
                innerBoxX + coverX * innerBoxSize + coverSize,
                innerBoxY + (coverY + 0.12f) * innerBoxSize + coverSize)
            logo1Pos.inset((0.01f * innerBoxSize).toInt().toFloat(),
                (0.01f * innerBoxSize).toInt().toFloat())
            val radius = if ((i == 0)) (coverSize / 16) else (coverSize / 8)
            canvas.drawRoundRect(logo1Pos, radius, radius, paintCoverBorder)
            logo1Pos.inset((0.003f * innerBoxSize).toInt().toFloat(),
                (0.003f * innerBoxSize).toInt().toFloat())
            val pos = Rect()
            logo1Pos.round(pos)
            if (favoritePodImages.size > i) {
                favoritePodImages[i].bounds = pos
                favoritePodImages[i].draw(canvas)
            } else {
                canvas.drawText(" ...", pos.left.toFloat(), pos.centerY().toFloat(), paintTextMain)
            }

            paintTextMain.textAlign = Paint.Align.CENTER
            paintTextMain.textSize = fontSizePods
            val numberWidth = 0.06f * innerBoxSize
            canvas.drawText((i + 1).toString() + ".", innerBoxX + numberWidth / 2, textY, paintTextMain)
            paintTextMain.textAlign = Paint.Align.LEFT
            val ellipsizedTitle = ellipsize(favoritePodNames[i], paintTextMain, innerBoxSize - numberWidth)
            canvas.drawText(ellipsizedTitle, innerBoxX + numberWidth, textY, paintTextMain)
            fontSizePods = innerBoxSize / 24 // Starting with second text is smaller
            textY += 1.3f * fontSizePods
            paintTextMain.setTypeface(typefaceNormal)
        }

        val ratio = (1.0 * logo!!.intrinsicHeight) / logo.intrinsicWidth
        logo.setBounds((innerBoxX + 0.1 * innerBoxSize).toInt(),
            (innerBoxY + innerBoxSize - 0.8 * innerBoxSize * ratio).toInt(),
            (innerBoxX + 0.9 * innerBoxSize).toInt(),
            (innerBoxY + innerBoxSize).toInt())
        logo.draw(canvas)
    }

    fun ellipsize(string: String, paint: Paint, maxWidth: Float): String {
        var string = string
        if (paint.measureText(string) <= maxWidth) {
            return string
        }
        while (paint.measureText("$string…") > maxWidth || string.endsWith(" ")) {
            string = string.substring(0, string.length - 1)
        }
        return "$string…"
    }

    companion object {
        private val COVER_POSITIONS = arrayOf(floatArrayOf(0.0f, 0.0f),
            floatArrayOf(0.4f, 0.0f), floatArrayOf(0.4f, 0.2f), floatArrayOf(0.6f, 0.2f), floatArrayOf(0.8f, 0.2f))
    }
}
