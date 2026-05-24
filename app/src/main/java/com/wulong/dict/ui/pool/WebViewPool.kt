package com.wulong.dict.ui.pool

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Stack

/**
 * Global WebView object pool for instant dictionary page rendering.
 *
 * Pre-warms 2 WebView instances during [android.app.Application.onCreate] so the
 * Chromium rendering engine is already initialized when EntryScreen needs a WebView,
 * avoiding the typical 200-400ms cold-start penalty.
 */
class WebViewPool(
    private val appContext: Context,
    private val maxPoolSize: Int = 2
) {
    private val available = Stack<WebView>()
    private val lock = Any()

    /** Empty SVG — served as fallback for missing images so they collapse to zero size. */
    private val emptyPlaceholder = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()

    private fun imageMime(path: String): String = when {
        path.endsWith(".png", ignoreCase = true) -> "image/png"
        path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        path.endsWith(".gif", ignoreCase = true) -> "image/gif"
        path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
        path.endsWith(".webp", ignoreCase = true) -> "image/webp"
        else -> "application/octet-stream"
    }

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
        return PagerAwareWebView(appContext).apply {
            // Match content background so unrendered area blends in (no white flash)
            setBackgroundColor(Color.WHITE)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                allowFileAccess = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            // Kill overscroll glow to reduce rendering overhead during scroll
            overScrollMode = View.OVER_SCROLL_NEVER
            // Dedicated hardware layer avoids re-compositing with Compose on every frame
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url ?: return null

                    // Block CDN requests — return empty immediately to prevent
                    // 4+ second network timeout while dict JS tries CDN fallback
                    if (url.scheme == "https" || url.scheme == "http") {
                        val host = url.host ?: ""
                        if ("cdnjs.cloudflare.com" in host || "cdn.jsdelivr.net" in host) {
                            return WebResourceResponse(
                                "text/javascript", "UTF-8",
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                        return null // allow other HTTP requests
                    }

                    if (url.scheme != "file") return null

                    val path = Uri.decode(url.path ?: return null)

                    // Only intercept image/icon requests
                    if (!imageMime(path).startsWith("image/")) return null

                    // If the file exists on disk, let it load normally
                    if (File(path).exists()) return null

                    // File not on disk — return empty SVG so it collapses to zero size
                    return WebResourceResponse(
                        "image/svg+xml", "UTF-8",
                        ByteArrayInputStream(emptyPlaceholder)
                    )
                }
            }
        }
    }
}

