package io.github.sarraf5757.barebrowser.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat

class NestedScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr), NestedScrollingChild3 {

    private val childHelper = NestedScrollingChildHelper(this)
    private var lastY = 0f
    private val consumed = IntArray(2)
    private val offsetInWindow = IntArray(2)
    private var velocityTracker: android.view.VelocityTracker? = null

    init {
        isNestedScrollingEnabled = true
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return childHelper.isNestedScrollingEnabled
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return childHelper.startNestedScroll(axes, type)
    }

    override fun stopNestedScroll(type: Int) {
        childHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return childHelper.hasNestedScrollingParent(type)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?, type: Int, consumed: IntArray
    ) {
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?, type: Int
    ): Boolean {
        return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)
    }

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int,
        consumed: IntArray?, offsetInWindow: IntArray?, type: Int
    ): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return childHelper.startNestedScroll(axes)
    }

    override fun stopNestedScroll() {
        childHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean {
        return childHelper.hasNestedScrollingParent()
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean {
        return childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)
    }

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int,
        consumed: IntArray?, offsetInWindow: IntArray?
    ): Boolean {
        return childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return childHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        return childHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var result = false
        val y = event.y

        if (velocityTracker == null) {
            velocityTracker = android.view.VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = y
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                result = super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = (lastY - y).toInt()
                lastY = y

                // Dispatch pre-scroll to Compose
                if (dispatchNestedPreScroll(0, deltaY, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH)) {
                    val consumedY = consumed[1]
                    event.offsetLocation(0f, consumedY.toFloat())
                }

                result = super.onTouchEvent(event)

                if (scrollY == 0 && deltaY < 0) {
                    dispatchNestedScroll(0, 0, 0, deltaY, offsetInWindow, ViewCompat.TYPE_TOUCH)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityY = -(velocityTracker?.yVelocity ?: 0f)
                
                if (!dispatchNestedPreFling(0f, velocityY)) {
                    dispatchNestedFling(0f, velocityY, false)
                }
                
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                result = super.onTouchEvent(event)
                
                velocityTracker?.recycle()
                velocityTracker = null
            }
            else -> {
                result = super.onTouchEvent(event)
            }
        }
        return result
    }
}
