package ac.mdiq.podcini.glide

import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.bumptech.glide.Priority
import com.bumptech.glide.integration.okhttp3.OkHttpStreamFetcher
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GlideUrl
import okhttp3.Call
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.*
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

class ResizingOkHttpStreamFetcher(client: Call.Factory?, url: GlideUrl?) : OkHttpStreamFetcher(client, url) {
    private var stream: FileInputStream? = null
    private var tempIn: File? = null
    private var tempOut: File? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        super.loadData(priority, object : DataFetcher.DataCallback<InputStream?> {
            override fun onDataReady(data: InputStream?) {
                if (data == null) {
                    callback.onDataReady(null)
                    return
                }
                try {
                    tempIn = File.createTempFile("resize_", null)
                    tempOut = File.createTempFile("resize_", null)
                    val outputStream: OutputStream = FileOutputStream(tempIn)
                    IOUtils.copy(data, outputStream)
                    outputStream.close()
                    IOUtils.closeQuietly(data)

                    if (tempIn != null && tempIn!!.length() <= MAX_FILE_SIZE) {
                        try {
                            stream = FileInputStream(tempIn)
                            callback.onDataReady(stream) // Just deliver the original, non-scaled image
                        } catch (fileNotFoundException: FileNotFoundException) {
                            callback.onLoadFailed(fileNotFoundException)
                        }
                        return
                    }

                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    var inVal = FileInputStream(tempIn)
                    BitmapFactory.decodeStream(inVal, null, options)
                    IOUtils.closeQuietly(inVal)

                    when {
                        options.outWidth == -1 || options.outHeight == -1 -> throw IOException("Not a valid image")
                        max(options.outHeight.toDouble(), options.outWidth.toDouble()) >= MAX_DIMENSIONS -> {
                            val sampleSize = max(options.outHeight.toDouble(), options.outWidth.toDouble()) / MAX_DIMENSIONS
                            options.inSampleSize = 2.0.pow(floor(ln(sampleSize) / ln(2.0))).toInt()
                        }
                    }

                    options.inJustDecodeBounds = false
                    inVal = FileInputStream(tempIn)
                    val bitmap = BitmapFactory.decodeStream(inVal, null, options)
                    IOUtils.closeQuietly(inVal)

                    val format = if (Build.VERSION.SDK_INT < 30) CompressFormat.WEBP else CompressFormat.WEBP_LOSSY

                    var quality = 100
                    if (tempOut != null) while (true) {
                        val out = FileOutputStream(tempOut)
                        bitmap!!.compress(format, quality, out)
                        IOUtils.closeQuietly(out)

                        quality -= when {
                            tempOut!!.length() > 3 * MAX_FILE_SIZE && quality >= 45 -> 40
                            tempOut!!.length() > 2 * MAX_FILE_SIZE && quality >= 25 -> 20
                            tempOut!!.length() > MAX_FILE_SIZE && quality >= 15 -> 10
                            tempOut!!.length() > MAX_FILE_SIZE && quality >= 10 -> 5
                            else -> break
                        }
                    }
                    bitmap?.recycle()

                    stream = FileInputStream(tempOut)
                    callback.onDataReady(stream)
                    if (tempIn != null && tempOut != null)
                        Log.d(TAG, "Compressed image from ${tempIn!!.length() / 1024} to ${tempOut!!.length() / 1024} kB (quality: $quality%)")
                } catch (e: Throwable) {
                    e.printStackTrace()

                    try {
                        stream = FileInputStream(tempIn)
                        callback.onDataReady(stream) // Just deliver the original, non-scaled image
                    } catch (fileNotFoundException: FileNotFoundException) {
                        e.printStackTrace()
                        callback.onLoadFailed(fileNotFoundException)
                    }
                }
            }

            override fun onLoadFailed(e: Exception) {
                callback.onLoadFailed(e)
            }
        })
    }

    override fun cleanup() {
        IOUtils.closeQuietly(stream)
        FileUtils.deleteQuietly(tempIn)
        FileUtils.deleteQuietly(tempOut)
        super.cleanup()
    }

    companion object {
        private const val TAG = "ResizingOkHttpStreamFet"
        private const val MAX_DIMENSIONS = 1500
        private const val MAX_FILE_SIZE = 1024 * 1024 // 1 MB
    }
}
