package com.wulong.dict.ui.pool

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import java.util.Stack

/**
 * Global WebView object pool for instant dictionary page rendering.
 *
 * Pre-warms 2 WebView instances during [android.app.Application.onCreate] so the
 * Chromium rendering engine is already initialized when EntryScreen needs a WebView,
 * avoiding the typical 200-400ms cold-start penalty.
 */
class WebViewPool(private val appContext: Context, private val maxPoolSize: Int = 2) {

    private val available = Stack<WebView>()
    private val lock = Any()

    /**
     * Pre-initialize [count] WebView instances on the main thread.
     * Call once from Application.onCreate().
     */
    fun preWarm(count: Int = maxPoolSize) {
        repeat(count) {
            available.push(createWebView())
        }
    }

    /**
     * Acquire a WebView from the pool. If the pool is empty, a new one is created.
     * Must be called on the main thread.
     */
    fun acquire(): WebView {
        synchronized(lock) {
            return if (available.isNotEmpty()) {
                available.pop()
            } else {
                createWebView()
            }
        }
    }

    /**
     * Return a WebView to the pool. Its content is cleared and it's detached
     * from any parent. If the pool is full, the WebView is destroyed.
     */
    fun release(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.loadUrl("about:blank")
        synchronized(lock) {
            if (available.size < maxPoolSize) {
                available.push(webView)
            } else {
                webView.destroy()
            }
        }
    }

    private fun createWebView(): WebView {
        return WebView(appContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                allowFileAccess = true
            }
        }
    }
}
