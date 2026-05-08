package com.wulong.dict.ui.pool

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

class PagerAwareWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private var startX = 0f
    private var startY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isHorizontalSwipe = false
    private var isVerticalSwipe = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                isHorizontalSwipe = false
                isVerticalSwipe = false
                // Hold events by default — don't let Pager intercept
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - startX)
                val dy = abs(event.y - startY)

                if (!isHorizontalSwipe && !isVerticalSwipe) {
                    if (dx > touchSlop || dy > touchSlop) {
                        // Only when horizontal intent is unambiguous
                        // (dx > dy * 1.5) release to the Compose Pager.
                        // Everything else stays in the WebView.
                        if (dx > dy * 1.5f) {
                            isHorizontalSwipe = true
                            parent?.requestDisallowInterceptTouchEvent(false)
                        } else {
                            isVerticalSwipe = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}
