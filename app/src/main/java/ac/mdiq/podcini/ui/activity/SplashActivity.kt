package ac.mdiq.podcini.ui.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.util.error.CrashReportWriter
import ac.mdiq.podcini.storage.database.PodDBAdapter
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Shows the Podcini logo while waiting for the main activity to start.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : Activity() {
    @UnstableApi override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener { false } // Keep splash screen active

        Completable.create { subscriber: CompletableEmitter ->
            // Trigger schema updates
            PodDBAdapter.getInstance().open()
            PodDBAdapter.getInstance().close()
            subscriber.onComplete()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                }, { error: Throwable ->
                    error.printStackTrace()
                    CrashReportWriter.write(error)
                    Toast.makeText(this, error.localizedMessage, Toast.LENGTH_LONG).show()
                    finish()
                })
    }
}
