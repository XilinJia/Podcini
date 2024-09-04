package ac.mdiq.podcini.ui.dialog

import android.content.Context
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.gridlayout.widget.GridLayout
import com.annimon.stream.Stream
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.*
import ac.mdiq.podcini.ui.fragment.*
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeAction
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions.Companion.getPrefsWithDefaults
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions.Companion.getSharedPrefs
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions.Companion.isSwipeActionEnabled
import ac.mdiq.podcini.ui.utils.ThemeUtils.getColorFromAttr
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class SwipeActionsDialog(private val context: Context, private val tag: String) {
    private lateinit var keys: List<SwipeAction>

    private var rightAction: SwipeAction? = null
    private var leftAction: SwipeAction? = null

    fun show(prefsChanged: Callback) {
        val actions = getPrefsWithDefaults(tag)
        leftAction = actions.left
        rightAction = actions.right

        val builder = MaterialAlertDialogBuilder(context)

        keys = SwipeActions.swipeActions

        var forFragment = ""
        when (tag) {
            AllEpisodesFragment.TAG -> {
                forFragment = context.getString(R.string.episodes_label)
                keys = Stream.of(keys).filter { a: SwipeAction -> !a.getId().equals(SwipeAction.REMOVE_FROM_HISTORY) }.toList()
            }
            DownloadsFragment.TAG -> {
                forFragment = context.getString(R.string.downloads_label)
                keys = Stream.of(keys).filter { a: SwipeAction ->
                    (!a.getId().equals(SwipeAction.REMOVE_FROM_HISTORY) && !a.getId().equals(SwipeAction.START_DOWNLOAD)) }.toList()
            }
            FeedEpisodesFragment.TAG -> {
                forFragment = context.getString(R.string.individual_subscription)
                keys = Stream.of(keys).filter { a: SwipeAction -> !a.getId().equals(SwipeAction.REMOVE_FROM_HISTORY) }.toList()
            }
            QueuesFragment.TAG -> {
                forFragment = context.getString(R.string.queue_label)
                keys = Stream.of(keys).filter { a: SwipeAction ->
                    (!a.getId().equals(SwipeAction.ADD_TO_QUEUE) && !a.getId().equals(SwipeAction.REMOVE_FROM_HISTORY)) }.toList()
            }
            HistoryFragment.TAG -> {
                forFragment = context.getString(R.string.playback_history_label)
                keys = Stream.of(keys).toList()
            }
            else -> {}
        }
        if (tag != QueuesFragment.TAG) keys = Stream.of(keys).filter { a: SwipeAction -> !a.getId().equals(SwipeAction.REMOVE_FROM_QUEUE) }.toList()

        builder.setTitle(context.getString(R.string.swipeactions_label) + " - " + forFragment)
        val binding = SwipeactionsDialogBinding.inflate(LayoutInflater.from(context))
        builder.setView(binding.root)

        binding.enableSwitch.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            binding.actionLeftContainer.root.alpha = if (b) 1.0f else 0.4f
            binding.actionRightContainer.root.alpha = if (b) 1.0f else 0.4f
        }

        binding.enableSwitch.isChecked = isSwipeActionEnabled(tag)

        setupSwipeDirectionView(binding.actionLeftContainer, LEFT)
        setupSwipeDirectionView(binding.actionRightContainer, RIGHT)

        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
            savePrefs(tag, rightAction!!.getId(), leftAction!!.getId())
            saveActionsEnabledPrefs(binding.enableSwitch.isChecked)
            prefsChanged.onCall()
        }

        builder.setNegativeButton(R.string.cancel_label, null)
        builder.create().show()
    }

    private fun setupSwipeDirectionView(view: SwipeactionsRowBinding, direction: Int) {
        val action = if (direction == LEFT) leftAction else rightAction

        view.swipeDirectionLabel.setText(if (direction == LEFT) R.string.swipe_left else R.string.swipe_right)
        view.swipeActionLabel.text = action!!.getTitle(context)
        populateMockEpisode(view.mockEpisode)
        if (direction == RIGHT && view.previewContainer.getChildAt(0) !== view.swipeIcon) {
            view.previewContainer.removeView(view.swipeIcon)
            view.previewContainer.addView(view.swipeIcon, 0)
        }

        view.swipeIcon.setImageResource(action.getActionIcon())
        view.swipeIcon.setColorFilter(getColorFromAttr(context, action.getActionColor()))

        view.changeButton.setOnClickListener { showPicker(view, direction) }
        view.previewContainer.setOnClickListener { showPicker(view, direction) }
    }

    private fun showPicker(view: SwipeactionsRowBinding, direction: Int) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(if (direction == LEFT) R.string.swipe_left else R.string.swipe_right)

        val picker = SwipeactionsPickerBinding.inflate(LayoutInflater.from(context))
        builder.setView(picker.root)
        builder.setNegativeButton(R.string.cancel_label, null)
        val dialog = builder.show()

        for (i in keys.indices) {
            val action = keys[i]
            val item = SwipeactionsPickerItemBinding.inflate(LayoutInflater.from(context))
            item.swipeActionLabel.text = action.getTitle(context)

            val icon = DrawableCompat.wrap(AppCompatResources.getDrawable(context, action.getActionIcon())!!)
            icon.mutate()
            icon.setTintMode(PorterDuff.Mode.SRC_ATOP)
            if ((direction == LEFT && leftAction === action) || (direction == RIGHT && rightAction === action)) {
                icon.setTint(getColorFromAttr(context, action.getActionColor()))
                item.swipeActionLabel.setTextColor(getColorFromAttr(context, action.getActionColor()))
            } else icon.setTint(getColorFromAttr(context, R.attr.action_icon_color))

            item.swipeIcon.setImageDrawable(icon)
            item.root.setOnClickListener {
                if (direction == LEFT) leftAction = keys[i]
                else rightAction = keys[i]

                setupSwipeDirectionView(view, direction)
                dialog.dismiss()
            }
            val param = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, GridLayout.BASELINE),
                GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f))
            param.width = 0
            picker.pickerGridLayout.addView(item.root, param)
        }
        picker.pickerGridLayout.columnCount = 2
        picker.pickerGridLayout.rowCount = (keys.size + 1) / 2
    }

    private fun populateMockEpisode(view: FeeditemlistItemBinding) {
        view.container.alpha = 0.3f
        view.secondaryActionButton.secondaryAction.visibility = View.GONE
        view.dragHandle.visibility = View.GONE
        view.txtvTitle.text = "███████"
        view.txtvPosition.text = "█████"
    }

    private fun savePrefs(tag: String, right: String?, left: String?) {
        getSharedPrefs(context)
        SwipeActions.prefs!!.edit().putString(SwipeActions.KEY_PREFIX_SWIPEACTIONS + tag, "$right,$left").apply()
    }

    private fun saveActionsEnabledPrefs(enabled: Boolean) {
        getSharedPrefs(context)
        SwipeActions.prefs!!.edit().putBoolean(SwipeActions.KEY_PREFIX_NO_ACTION + tag, enabled).apply()
    }

    interface Callback {
        fun onCall()
    }

    companion object {
        private const val LEFT = 1
        private const val RIGHT = 0
    }
}
