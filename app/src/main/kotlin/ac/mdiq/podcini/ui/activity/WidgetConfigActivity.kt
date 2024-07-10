package ac.mdiq.podcini.ui.activity

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
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ActivityWidgetConfigBinding
import ac.mdiq.podcini.databinding.PlayerWidgetBinding
import ac.mdiq.podcini.preferences.ThemeSwitcher.getTheme
import ac.mdiq.podcini.receiver.PlayerWidget
import ac.mdiq.podcini.receiver.PlayerWidget.Companion.prefs
import ac.mdiq.podcini.ui.widget.WidgetUpdaterWorker
import ac.mdiq.podcini.util.Logd

class WidgetConfigActivity : AppCompatActivity() {

    private var _binding: ActivityWidgetConfigBinding? = null
    private val binding get() = _binding!!
    private var _wpBinding: PlayerWidgetBinding? = null
    private val wpBinding get() = _wpBinding!!

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var widgetPreview: View
    private lateinit var opacitySeekBar: SeekBar
    private lateinit var opacityTextView: TextView
    private lateinit var ckPlaybackSpeed: CheckBox
    private lateinit var ckRewind: CheckBox
    private lateinit var ckFastForward: CheckBox
    private lateinit var ckSkip: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getTheme(this))
        super.onCreate(savedInstanceState)
        _binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val configIntent = intent
        val extras = configIntent.extras
        if (extras != null) appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultValue)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) finish()

        opacityTextView = binding.widgetOpacityTextView
        opacitySeekBar = binding.widgetOpacitySeekBar
        widgetPreview = binding.widgetConfigPreview.playerWidget
        _wpBinding = PlayerWidgetBinding.bind(widgetPreview)

        binding.butConfirm.setOnClickListener{ confirmCreateWidget() }
        opacitySeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                opacityTextView.text = seekBar.progress.toString() + "%"
                val color = getColorWithAlpha(PlayerWidget.DEFAULT_COLOR, opacitySeekBar.progress)
                widgetPreview.setBackgroundColor(color)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        wpBinding.txtNoPlaying.visibility = View.GONE
        val title = wpBinding.txtvTitle
        title.visibility = View.VISIBLE
        title.setText(R.string.app_name)
        val progress = wpBinding.txtvProgress
        progress.visibility = View.VISIBLE
        progress.setText(R.string.position_default_label)

        ckPlaybackSpeed = binding.ckPlaybackSpeed
        ckPlaybackSpeed.setOnClickListener { displayPreviewPanel() }
        ckRewind = binding.ckRewind
        ckRewind.setOnClickListener { displayPreviewPanel() }
        ckFastForward = binding.ckFastForward
        ckFastForward.setOnClickListener { displayPreviewPanel() }
        ckSkip = binding.ckSkip
        ckSkip.setOnClickListener { displayPreviewPanel() }

        setInitialState()
    }

    override fun onDestroy() {
        _binding = null
        _wpBinding = null
        super.onDestroy()
    }

    private fun setInitialState() {
        PlayerWidget.getSharedPrefs(this)

//        val prefs = getSharedPreferences(PlayerWidget.PREFS_NAME, MODE_PRIVATE)
        ckPlaybackSpeed.isChecked = prefs!!.getBoolean(PlayerWidget.KEY_WIDGET_PLAYBACK_SPEED + appWidgetId, true)
        ckRewind.isChecked = prefs!!.getBoolean(PlayerWidget.KEY_WIDGET_REWIND + appWidgetId, true)
        ckFastForward.isChecked = prefs!!.getBoolean(PlayerWidget.KEY_WIDGET_FAST_FORWARD + appWidgetId, true)
        ckSkip.isChecked = prefs!!.getBoolean(PlayerWidget.KEY_WIDGET_SKIP + appWidgetId, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val color = prefs!!.getInt(PlayerWidget.KEY_WIDGET_COLOR + appWidgetId, PlayerWidget.DEFAULT_COLOR)
            val opacity = Color.alpha(color) * 100 / 0xFF

            opacitySeekBar.setProgress(opacity, false)
        }
        displayPreviewPanel()
    }

    private fun displayPreviewPanel() {
        val showExtendedPreview = ckPlaybackSpeed.isChecked || ckRewind.isChecked || ckFastForward.isChecked || ckSkip.isChecked
        wpBinding.extendedButtonsContainer.visibility = if (showExtendedPreview) View.VISIBLE else View.GONE
        wpBinding.butPlay.visibility = if (showExtendedPreview) View.GONE else View.VISIBLE
        wpBinding.butPlaybackSpeed.visibility = if (ckPlaybackSpeed.isChecked) View.VISIBLE else View.GONE
        wpBinding.butFastForward.visibility = if (ckFastForward.isChecked) View.VISIBLE else View.GONE
        wpBinding.butSkip.visibility = if (ckSkip.isChecked) View.VISIBLE else View.GONE
        wpBinding.butRew.visibility = if (ckRewind.isChecked) View.VISIBLE else View.GONE
    }

    private fun confirmCreateWidget() {
        val backgroundColor = getColorWithAlpha(PlayerWidget.DEFAULT_COLOR, opacitySeekBar.progress)

        Logd("WidgetConfigActivity", "confirmCreateWidget appWidgetId $appWidgetId")
//        val prefs = getSharedPreferences(PlayerWidget.PREFS_NAME, MODE_PRIVATE)
        val editor = prefs!!.edit()
        editor.putInt(PlayerWidget.KEY_WIDGET_COLOR + appWidgetId, backgroundColor)
        editor.putBoolean(PlayerWidget.KEY_WIDGET_PLAYBACK_SPEED + appWidgetId, ckPlaybackSpeed.isChecked)
        editor.putBoolean(PlayerWidget.KEY_WIDGET_SKIP + appWidgetId, ckSkip.isChecked)
        editor.putBoolean(PlayerWidget.KEY_WIDGET_REWIND + appWidgetId, ckRewind.isChecked)
        editor.putBoolean(PlayerWidget.KEY_WIDGET_FAST_FORWARD + appWidgetId, ckFastForward.isChecked)
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
