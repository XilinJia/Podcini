package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FeedPrefSkipDialogBinding
import android.content.Context
import android.content.DialogInterface
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Displays a dialog with a username and password text field and an optional checkbox to save username and preferences.
 */
abstract class FeedPreferenceSkipDialog(context: Context, skipIntroInitialValue: Int, skipEndInitialValue: Int)
    : MaterialAlertDialogBuilder(context) {

    init {
        setTitle(R.string.pref_feed_skip)
        val binding = FeedPrefSkipDialogBinding.bind(View.inflate(context, R.layout.feed_pref_skip_dialog, null))
        setView(binding.root)

        val etxtSkipIntro = binding.etxtSkipIntro
        val etxtSkipEnd = binding.etxtSkipEnd

        etxtSkipIntro.setText(skipIntroInitialValue.toString())
        etxtSkipEnd.setText(skipEndInitialValue.toString())

        setNegativeButton(R.string.cancel_label, null)
        setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
            val skipIntro = try {
                etxtSkipIntro.text.toString().toInt()
            } catch (e: NumberFormatException) {
                0
            }
            val skipEnding = try {
                etxtSkipEnd.text.toString().toInt()
            } catch (e: NumberFormatException) {
                0
            }
            onConfirmed(skipIntro, skipEnding)
        }
    }

    protected abstract fun onConfirmed(skipIntro: Int, skipEndig: Int)
}
