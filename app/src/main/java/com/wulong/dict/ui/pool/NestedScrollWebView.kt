package com.wulong.dict.ui.pool

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

/**
 * A [WebView] subclass that arbitrates the gesture conflict between
 * the WebView's internal vertical scrolling and a Compose
 * [HorizontalPager] parent.
 *
 * ## How it works
 *
 * On every [ACTION_MOVE] the cumulative displacement from the down
 * point is compared against the system touch slop.  Once the slop is
 * crossed in either axis the gesture direction is locked:
 *
 * - **Horizontal** (`dx > dy`): the WebView calls
 *   `parent.requestDisallowInterceptTouchEvent(false)`, releasing
 *   its claim on the touch stream and letting the Compose pager
 *   intercept the swipe for a tab change.
 *
 * - **Vertical** (`dy > dx`): the WebView calls
 *   `parent.requestDisallowInterceptTouchEvent(true)`, holding the
 *   touch stream so it can scroll the dictionary entry content.
 *
 * The direction is locked for the remainder of the gesture.  It resets
 * on [ACTION_UP] or [ACTION_CANCEL].
 *
 * ## Why this approach
 *
 * `requestDisallowInterceptTouchEvent` is the only mechanism that
 * bridges the native Android View touch model and Compose's pointer
 * system through [androidx.compose.ui.viewinterop.AndroidViewHolder].
 * Attempting to gate [HorizontalPager.userScrollEnabled] mid-gesture
 * breaks the Compose touch-event stream; filtering via
 * [NestedScrollConnection] runs too late (the pager is already
 * tracking).  Native arbitration at the [WebView.onTouchEvent] level
 * avoids both problems.
 */
class NestedScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : WebView(context, attrs, defStyleAttr) {

    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    private var downX = 0f
    private var downY = 0f
    private var directionLocked = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                directionLocked = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!directionLocked) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)

                    if (dx > touchSlop && dx > dy) {
                        // Horizontal swipe detected — release claim so the
                        // Compose pager can intercept and switch tabs.
                        directionLocked = true
                        parent?.requestDisallowInterceptTouchEvent(false)
                    } else if (dy > touchSlop && dy > dx) {
                        // Vertical scroll detected — hold the touch stream
                        // so the WebView can scroll the entry content.
                        directionLocked = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                // Reset for the next gesture.
                directionLocked = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return super.onTouchEvent(event)
    }
}
