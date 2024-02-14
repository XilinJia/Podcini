package de.danoeh.antennapod.dialog

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
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.*
import de.danoeh.antennapod.fragment.*
import de.danoeh.antennapod.fragment.swipeactions.SwipeAction
import de.danoeh.antennapod.fragment.swipeactions.SwipeActions
import de.danoeh.antennapod.fragment.swipeactions.SwipeActions.Companion.getPrefsWithDefaults
import de.danoeh.antennapod.fragment.swipeactions.SwipeActions.Companion.isSwipeActionEnabled
import de.danoeh.antennapod.ui.common.ThemeUtils.getColorFromAttr

class SwipeActionsDialog(private val context: Context, private val tag: String) {
    private var rightAction: SwipeAction? = null
    private var leftAction: SwipeAction? = null
    private var keys: List<SwipeAction>? = null

    fun show(prefsChanged: Callback) {
        val actions = getPrefsWithDefaults(
            context, tag)
        leftAction = actions.left
        rightAction = actions.right

        val builder = MaterialAlertDialogBuilder(context)

        keys = SwipeActions.swipeActions

        var forFragment = ""
        when (tag) {
            InboxFragment.TAG -> {
                forFragment = context.getString(R.string.inbox_label)
                keys = Stream.of(keys!!).filter { a: SwipeAction ->
                    (!a.getId().equals(SwipeAction.TOGGLE_PLAYED)
                            && !a.getId().equals(SwipeAction.DELETE)
                            && !a.getId().equals(SwipeAction.REMOVE_FROM_HISTORY))
                }.toList()
            }
            AllEpisodesFragment.TAG -> {
                forFragment = context.getString(R.string.episodes_label)
                keys = Stream.of(keys!!).filter { a: SwipeAction -> !a.getId().equals(SwipeAction.REMOVE_FROM_HISTORY) }
                    .toList()
            }
            CompletedDownloadsFragment.TAG -> {
                forFragment = context.getString(R.string.downloads_label)
                keys = Stream.of(keys!!).filter { a: SwipeAction ->
                    (!a.getId().equals(SwipeAction.REMOVE_FROM_INBOX)
                            && !a.getId().equals(SwipeAction.REMOVE_FROM_HISTORY)
                            && !a.getId().equals(SwipeAction.START_DOWNLOAD))
                }.toList()
            }
            FeedItemlistFragment.TAG -> {
                forFragment = context.getString(R.string.individual_subscription)
                keys = Stream.of(keys!!).filter { a: SwipeAction -> !a.getId().equals(SwipeAction.REMOVE_FROM_HISTORY) }
                    .toList()
            }
            QueueFragment.TAG -> {
                forFragment = context.getString(R.string.queue_label)
                keys = Stream.of(keys!!).filter { a: SwipeAction ->
                    (!a.getId().equals(SwipeAction.ADD_TO_QUEUE)
                            && !a.getId().equals(SwipeAction.REMOVE_FROM_INBOX)
                            && !a.getId().equals(SwipeAction.REMOVE_FROM_HISTORY))
                }.toList()
            }
            PlaybackHistoryFragment.TAG -> {
                forFragment = context.getString(R.string.playback_history_label)
                keys = Stream.of(keys!!).filter { a: SwipeAction -> !a.getId().equals(SwipeAction.REMOVE_FROM_INBOX) }
                    .toList()
            }
            else -> {}
        }
        if (tag != QueueFragment.TAG) {
            keys = Stream.of(keys!!).filter { a: SwipeAction -> !a.getId().equals(SwipeAction.REMOVE_FROM_QUEUE) }
                .toList()
        }

        builder.setTitle(context.getString(R.string.swipeactions_label) + " - " + forFragment)
        val viewBinding = SwipeactionsDialogBinding.inflate(LayoutInflater.from(
            context))
        builder.setView(viewBinding.root)

        viewBinding.enableSwitch.setOnCheckedChangeListener { compoundButton: CompoundButton?, b: Boolean ->
            viewBinding.actionLeftContainer.root.alpha = if (b) 1.0f else 0.4f
            viewBinding.actionRightContainer.root.alpha = if (b) 1.0f else 0.4f
        }

        viewBinding.enableSwitch.isChecked = isSwipeActionEnabled(context, tag)

        setupSwipeDirectionView(viewBinding.actionLeftContainer, LEFT)
        setupSwipeDirectionView(viewBinding.actionRightContainer, RIGHT)

        builder.setPositiveButton(R.string.confirm_label) { dialog: DialogInterface?, which: Int ->
            savePrefs(tag, rightAction!!.getId(), leftAction!!.getId())
            saveActionsEnabledPrefs(viewBinding.enableSwitch.isChecked)
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

        view.swipeIcon.setImageResource(action.getActionColor())
        view.swipeIcon.setColorFilter(getColorFromAttr(context, action.getActionColor()))

        view.changeButton.setOnClickListener { v: View? -> showPicker(view, direction) }
        view.previewContainer.setOnClickListener { v: View? -> showPicker(view, direction) }
    }

    private fun showPicker(view: SwipeactionsRowBinding, direction: Int) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(if (direction == LEFT) R.string.swipe_left else R.string.swipe_right)

        val picker = SwipeactionsPickerBinding.inflate(LayoutInflater.from(
            context))
        builder.setView(picker.root)
        builder.setNegativeButton(R.string.cancel_label, null)
        val dialog = builder.show()

        for (i in keys!!.indices) {
            val action = keys!![i]
            val item = SwipeactionsPickerItemBinding.inflate(LayoutInflater.from(
                context))
            item.swipeActionLabel.text = action.getTitle(context)

            val icon = DrawableCompat.wrap(AppCompatResources.getDrawable(
                context, action.getActionColor())!!)
            icon.mutate()
            icon.setTintMode(PorterDuff.Mode.SRC_ATOP)
            if ((direction == LEFT && leftAction === action) || (direction == RIGHT && rightAction === action)) {
                icon.setTint(getColorFromAttr(context, action.getActionColor()))
                item.swipeActionLabel.setTextColor(getColorFromAttr(context, action.getActionColor()))
            } else {
                icon.setTint(getColorFromAttr(context, R.attr.action_icon_color))
            }
            item.swipeIcon.setImageDrawable(icon)

            item.root.setOnClickListener { v: View? ->
                if (direction == LEFT) {
                    leftAction = keys!![i]
                } else {
                    rightAction = keys!![i]
                }
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
        picker.pickerGridLayout.rowCount = (keys!!.size + 1) / 2
    }

    private fun populateMockEpisode(view: FeeditemlistItemBinding) {
        view.container.alpha = 0.3f
        view.secondaryActionButton.secondaryActionButton.visibility = View.GONE
        view.dragHandle.visibility = View.GONE
        view.statusInbox.visibility = View.GONE
        view.txtvTitle.text = "███████"
        view.txtvPosition.text = "█████"
    }

    private fun savePrefs(tag: String, right: String?, left: String?) {
        val prefs = context.getSharedPreferences(SwipeActions.PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SwipeActions.KEY_PREFIX_SWIPEACTIONS + tag, "$right,$left").apply()
    }

    private fun saveActionsEnabledPrefs(enabled: Boolean) {
        val prefs = context.getSharedPreferences(SwipeActions.PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(SwipeActions.KEY_PREFIX_NO_ACTION + tag, enabled).apply()
    }

    interface Callback {
        fun onCall()
    }

    companion object {
        private const val LEFT = 1
        private const val RIGHT = 0
    }
}
