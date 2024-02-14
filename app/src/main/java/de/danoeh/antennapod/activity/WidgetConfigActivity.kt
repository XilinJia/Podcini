package de.danoeh.antennapod.activity

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.preferences.ThemeSwitcher.getTheme
import de.danoeh.antennapod.core.receiver.PlayerWidget
import de.danoeh.antennapod.core.widget.WidgetUpdaterWorker

class WidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var opacitySeekBar: SeekBar? = null
    private var opacityTextView: TextView? = null
    private var widgetPreview: View? = null
    private var ckPlaybackSpeed: CheckBox? = null
    private var ckRewind: CheckBox? = null
    private var ckFastForward: CheckBox? = null
    private var ckSkip: CheckBox? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTheme(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        val configIntent = intent
        val extras = configIntent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID)
        }

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultValue)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
        }

        opacityTextView = findViewById(R.id.widget_opacity_textView)
        opacitySeekBar = findViewById(R.id.widget_opacity_seekBar)
        widgetPreview = findViewById(R.id.widgetLayout)
        findViewById<View>(R.id.butConfirm).setOnClickListener { v: View? -> confirmCreateWidget() }
        if (opacitySeekBar != null) {
            opacitySeekBar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                    opacityTextView?.text = seekBar.progress.toString() + "%"
                    val color = getColorWithAlpha(PlayerWidget.DEFAULT_COLOR, opacitySeekBar!!.progress)
                    widgetPreview?.setBackgroundColor(color)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                }
            })
        }
        if (widgetPreview != null) {
            widgetPreview!!.findViewById<View>(R.id.txtNoPlaying).visibility = View.GONE
            val title = widgetPreview!!.findViewById<TextView>(R.id.txtvTitle)
            title.visibility = View.VISIBLE
            title.setText(R.string.app_name)
            val progress = widgetPreview!!.findViewById<TextView>(R.id.txtvProgress)
            progress.visibility = View.VISIBLE
            progress.setText(R.string.position_default_label)
        }
        ckPlaybackSpeed = findViewById(R.id.ckPlaybackSpeed)
        ckPlaybackSpeed?.setOnClickListener { v: View? -> displayPreviewPanel() }
        ckRewind = findViewById(R.id.ckRewind)
        ckRewind?.setOnClickListener { v: View? -> displayPreviewPanel() }
        ckFastForward = findViewById(R.id.ckFastForward)
        ckFastForward?.setOnClickListener { v: View? -> displayPreviewPanel() }
        ckSkip = findViewById(R.id.ckSkip)
        ckSkip?.setOnClickListener { v: View? -> displayPreviewPanel() }

        setInitialState()
    }

    private fun setInitialState() {
        val prefs = getSharedPreferences(PlayerWidget.PREFS_NAME, MODE_PRIVATE)
        ckPlaybackSpeed!!.isChecked = prefs.getBoolean(PlayerWidget.KEY_WIDGET_PLAYBACK_SPEED + appWidgetId, false)
        ckRewind!!.isChecked = prefs.getBoolean(PlayerWidget.KEY_WIDGET_REWIND + appWidgetId, false)
        ckFastForward!!.isChecked = prefs.getBoolean(PlayerWidget.KEY_WIDGET_FAST_FORWARD + appWidgetId, false)
        ckSkip!!.isChecked = prefs.getBoolean(PlayerWidget.KEY_WIDGET_SKIP + appWidgetId, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val color = prefs.getInt(PlayerWidget.KEY_WIDGET_COLOR + appWidgetId, PlayerWidget.DEFAULT_COLOR)
            val opacity = Color.alpha(color) * 100 / 0xFF

            opacitySeekBar!!.setProgress(opacity, false)
        }
        displayPreviewPanel()
    }

    private fun displayPreviewPanel() {
        val showExtendedPreview =
            ckPlaybackSpeed!!.isChecked || ckRewind!!.isChecked || ckFastForward!!.isChecked || ckSkip!!.isChecked
        widgetPreview!!.findViewById<View>(R.id.extendedButtonsContainer).visibility =
            if (showExtendedPreview) View.VISIBLE else View.GONE
        widgetPreview!!.findViewById<View>(R.id.butPlay).visibility =
            if (showExtendedPreview) View.GONE else View.VISIBLE
        widgetPreview!!.findViewById<View>(R.id.butPlaybackSpeed).visibility =
            if (ckPlaybackSpeed!!.isChecked) View.VISIBLE else View.GONE
        widgetPreview!!.findViewById<View>(R.id.butFastForward).visibility =
            if (ckFastForward!!.isChecked) View.VISIBLE else View.GONE
        widgetPreview!!.findViewById<View>(R.id.butSkip).visibility =
            if (ckSkip!!.isChecked) View.VISIBLE else View.GONE
        widgetPreview!!.findViewById<View>(R.id.butRew).visibility =
            if (ckRewind!!.isChecked) View.VISIBLE else View.GONE
    }

    private fun confirmCreateWidget() {
        val backgroundColor = getColorWithAlpha(PlayerWidget.DEFAULT_COLOR, opacitySeekBar!!.progress)

        val prefs = getSharedPreferences(PlayerWidget.PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt(PlayerWidget.KEY_WIDGET_COLOR + appWidgetId, backgroundColor)
        editor.putBoolean(PlayerWidget.KEY_WIDGET_PLAYBACK_SPEED + appWidgetId, ckPlaybackSpeed!!.isChecked)
        editor.putBoolean(PlayerWidget.KEY_WIDGET_SKIP + appWidgetId, ckSkip!!.isChecked)
        editor.putBoolean(PlayerWidget.KEY_WIDGET_REWIND + appWidgetId, ckRewind!!.isChecked)
        editor.putBoolean(PlayerWidget.KEY_WIDGET_FAST_FORWARD + appWidgetId, ckFastForward!!.isChecked)
        editor.apply()

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
        WidgetUpdaterWorker.enqueueWork(this)
    }

    private fun getColorWithAlpha(color: Int, opacity: Int): Int {
        return Math.round(0xFF * (0.01 * opacity)).toInt() * 0x1000000 + (color and 0xffffff)
    }
}
