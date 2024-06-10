package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.storage.database.PodDBAdapter
import ac.mdiq.podcini.util.error.CrashReportWriter
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows the Podcini logo while waiting for the main activity to start.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : Activity() {
    @UnstableApi override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener { false } // Keep splash screen active

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch(Dispatchers.IO) {
            try {
                PodDBAdapter.getInstance().open()
                PodDBAdapter.getInstance().close()
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                CrashReportWriter.write(e)
                Toast.makeText(this@SplashActivity, e.localizedMessage, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    }
}
