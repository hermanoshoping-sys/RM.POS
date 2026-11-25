package com.example.ui.components

import android.graphics.Rect as AndroidRect
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Lifecycle-safe Compose [TextToolbar] implementation that prevents duplicate or stale
 * `ActionMode.finish()` calls on `DecorView` (`TYPE_FLOATING`), eliminating:
 * `E/DecorView: Destroying unexpected ActionMode instance of TYPE_FLOATING ... Expected null`.
 */
class SafeComposeTextToolbar(private val view: View) : TextToolbar {

    companion object {
        private const val TAG = "SafeTextToolbar"
        private const val MENU_ITEM_COPY = 1
        private const val MENU_ITEM_PASTE = 2
        private const val MENU_ITEM_CUT = 3
        private const val MENU_ITEM_SELECT_ALL = 4
    }

    private var activeActionMode: ActionMode? = null
    private var contentRect: Rect = Rect.Zero
    private var onCopyRequested: (() -> Unit)? = null
    private var onPasteRequested: (() -> Unit)? = null
    private var onCutRequested: (() -> Unit)? = null
    private var onSelectAllRequested: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    private val actionModeCallback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            populateMenu(menu)
            return menu.size() > 0
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.clear()
            populateMenu(menu)
            return menu.size() > 0
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val handled = when (item.itemId) {
                MENU_ITEM_COPY -> {
                    onCopyRequested?.invoke()
                    true
                }
                MENU_ITEM_PASTE -> {
                    onPasteRequested?.invoke()
                    true
                }
                MENU_ITEM_CUT -> {
                    onCutRequested?.invoke()
                    true
                }
                MENU_ITEM_SELECT_ALL -> {
                    onSelectAllRequested?.invoke()
                    true
                }
                else -> false
            }
            if (handled) {
                hide()
            }
            return handled
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            if (activeActionMode === mode) {
                activeActionMode = null
                status = TextToolbarStatus.Hidden
            }
        }

        override fun onGetContentRect(mode: ActionMode, view: View?, outRect: AndroidRect) {
            outRect.set(
                contentRect.left.toInt(),
                contentRect.top.toInt(),
                contentRect.right.toInt(),
                contentRect.bottom.toInt()
            )
        }
    }

    private fun populateMenu(menu: Menu) {
        if (onCopyRequested != null) {
            menu.add(0, MENU_ITEM_COPY, 0, android.R.string.copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        if (onPasteRequested != null) {
            menu.add(0, MENU_ITEM_PASTE, 1, android.R.string.paste)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        if (onCutRequested != null) {
            menu.add(0, MENU_ITEM_CUT, 2, android.R.string.cut)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        if (onSelectAllRequested != null) {
            menu.add(0, MENU_ITEM_SELECT_ALL, 3, android.R.string.selectAll)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        this.contentRect = rect
        this.onCopyRequested = onCopyRequested
        this.onPasteRequested = onPasteRequested
        this.onCutRequested = onCutRequested
        this.onSelectAllRequested = onSelectAllRequested

        if (!view.isAttachedToWindow || !view.hasWindowFocus()) {
            return
        }

        val existing = activeActionMode
        if (existing != null) {
            try {
                existing.invalidate()
                existing.invalidateContentRect()
                status = TextToolbarStatus.Shown
                return
            } catch (t: Throwable) {
                Log.w(TAG, "Refreshing existing FloatingActionMode failed; recreating cleanly", t)
                activeActionMode = null
                status = TextToolbarStatus.Hidden
            }
        }

        try {
            val started = view.startActionMode(actionModeCallback, ActionMode.TYPE_FLOATING)
            activeActionMode = started
            status = if (started != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to start FloatingActionMode on view", t)
            activeActionMode = null
            status = TextToolbarStatus.Hidden
        }
    }

    override fun hide() {
        val modeToFinish = activeActionMode
        activeActionMode = null
        status = TextToolbarStatus.Hidden
        if (modeToFinish != null) {
            try {
                modeToFinish.finish()
            } catch (t: Throwable) {
                Log.w(TAG, "Safe finish of FloatingActionMode ignored error", t)
            }
        }
    }
}

@Composable
fun rememberSafeComposeTextToolbar(): SafeComposeTextToolbar {
    val view = LocalView.current
    val toolbar = remember(view) { SafeComposeTextToolbar(view) }
    DisposableEffect(toolbar) {
        onDispose {
            toolbar.hide()
        }
    }
    return toolbar
}
