package ac.mdiq.podcini.ui.glide

import android.graphics.*
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import ac.mdiq.podcini.storage.model.feed.Feed
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

class GenerativePlaceholderImageModelLoader : ModelLoader<String, InputStream> {
    class Factory : ModelLoaderFactory<String, InputStream> {
        override fun build(unused: MultiModelLoaderFactory): ModelLoader<String, InputStream> {
            return GenerativePlaceholderImageModelLoader()
        }

        override fun teardown() {
            // Do nothing.
        }
    }

    override fun buildLoadData(model: String,
                               width: Int,
                               height: Int,
                               options: Options
    ): ModelLoader.LoadData<InputStream?> {
        return ModelLoader.LoadData(ObjectKey(model), EmbeddedImageFetcher(model, width, height))
    }

    override fun handles(model: String): Boolean {
        return model.startsWith(Feed.PREFIX_GENERATIVE_COVER)
    }

    internal class EmbeddedImageFetcher(private val model: String, private val width: Int, private val height: Int) :
        DataFetcher<InputStream?> {
        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream?>) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val generator = Random(model.hashCode().toLong())
            val lineGridSteps = 4 + generator.nextInt(4)
            val slope = width / 4
            val shadowWidth = width * 0.01f
            val lineDistance = (width.toFloat() / (lineGridSteps - 2))
            val baseColor = PALETTES[generator.nextInt(PALETTES.size)]

            val paint = Paint()
            var color = randomShadeOfGrey(generator)
            paint.color = color
            paint.strokeWidth = lineDistance
            paint.setColorFilter(PorterDuffColorFilter(baseColor, PorterDuff.Mode.MULTIPLY))
            val paintShadow = Paint()
            paintShadow.color = -0x1000000
            paintShadow.strokeWidth = lineDistance

            val forcedColorChange = 1 + generator.nextInt(lineGridSteps - 2)
            for (i in lineGridSteps - 1 downTo 0) {
                val linePos = (i - 0.5f) * lineDistance
                val switchColor = generator.nextFloat() < 0.3f || i == forcedColorChange
                if (switchColor) {
                    var newColor = color
                    while (newColor == color) {
                        newColor = randomShadeOfGrey(generator)
                    }
                    color = newColor
                    paint.color = newColor
                    canvas.drawLine(linePos + slope + shadowWidth, -slope.toFloat(),
                        linePos - slope + shadowWidth, (height + slope).toFloat(), paintShadow)
                }
                canvas.drawLine(linePos + slope, -slope.toFloat(),
                    linePos - slope, (height + slope).toFloat(), paint)
            }

            val gradientPaint = Paint()
            paint.isDither = true
            gradientPaint.setShader(LinearGradient(0f,
                0f,
                0f,
                height.toFloat(),
                0x00000000,
                0x55000000,
                Shader.TileMode.CLAMP))
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val `is`: InputStream = ByteArrayInputStream(baos.toByteArray())
            callback.onDataReady(`is`)
        }

        override fun cleanup() {
            // nothing to clean up
        }

        override fun cancel() {
            // cannot cancel
        }

        override fun getDataClass(): Class<InputStream?> {
            return InputStream::class.java as Class<InputStream?>
        }

        override fun getDataSource(): DataSource {
            return DataSource.LOCAL
        }

        companion object {
            private val PALETTES = intArrayOf(-0x876f64, -0x9100, -0xc771c4,
                -0xff7c71, -0x84e05e, -0x48e3e4, -0xde690d)

            private fun randomShadeOfGrey(generator: Random): Int {
                return -0x888889 + 0x222222 * generator.nextInt(5)
            }
        }
    }
}
