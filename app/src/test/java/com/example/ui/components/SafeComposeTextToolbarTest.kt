package com.example.ui.components

import android.app.Activity
import android.view.ActionMode
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbarStatus
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafeComposeTextToolbarTest {

    @Test
    fun testSafeTextToolbarNeverDoubleFinishesFloatingActionMode() {
        val finishCalls = AtomicInteger(0)
        var capturedCallback: ActionMode.Callback? = null
        var fakeMode: ActionMode? = null

        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()
        val testView = object : FrameLayout(activity) {
            override fun isAttachedToWindow(): Boolean = true
            override fun hasWindowFocus(): Boolean = true
            override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
                capturedCallback = callback
                val created = object : ActionMode() {
                    override fun setTitle(title: CharSequence?) {}
                    override fun setTitle(resId: Int) {}
                    override fun setSubtitle(subtitle: CharSequence?) {}
                    override fun setSubtitle(resId: Int) {}
                    override fun setCustomView(view: View?) {}
                    override fun invalidate() {}
                    override fun finish() {
                        finishCalls.incrementAndGet()
                        capturedCallback?.onDestroyActionMode(this)
                    }
                    override fun getMenu() = android.widget.PopupMenu(activity, FrameLayout(activity)).menu
                    override fun getTitle(): CharSequence? = null
                    override fun getSubtitle(): CharSequence? = null
                    override fun getCustomView(): View? = null
                    override fun getMenuInflater() = activity.menuInflater
                }
                fakeMode = created
                return created
            }
        }
        activity.setContentView(testView)

        val toolbar = SafeComposeTextToolbar(testView)
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)

        // Show menu once
        toolbar.showMenu(Rect(0f, 0f, 100f, 40f), onPasteRequested = {})
        assertEquals(TextToolbarStatus.Shown, toolbar.status)

        // Calling showMenu again while already shown refreshes in-place instead of starting a second ActionMode
        toolbar.showMenu(Rect(10f, 10f, 110f, 50f), onPasteRequested = {}, onCopyRequested = {})
        assertEquals(0, finishCalls.get())

        // Simulate DecorView natively destroying the FloatingActionMode first (e.g. user tapped Paste or outside)
        capturedCallback?.onDestroyActionMode(fakeMode)
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)

        // Subsequent Compose hide() calls MUST NOT call finish() on the already-destroyed ActionMode!
        toolbar.hide()
        toolbar.hide()
        assertEquals("Expected zero redundant finish() calls after native onDestroyActionMode", 0, finishCalls.get())

        // Show again and explicitly hide() -> finish() called once, and repeated hide() does not double-finish
        toolbar.showMenu(Rect(0f, 0f, 100f, 40f), onCopyRequested = {})
        toolbar.hide()
        toolbar.hide()
        assertEquals(1, finishCalls.get())
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
    }
}
