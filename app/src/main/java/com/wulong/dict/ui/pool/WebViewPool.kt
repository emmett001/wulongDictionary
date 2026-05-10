package com.wulong.dict.ui.pool

import android.content.Context
import android.graphics.Color
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

    /** 1×1 transparent PNG — served as fallback for missing images. */
    private val transparentPixel = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
        0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(), 0x89.toByte(),
        0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9C.toByte(),
        0x62, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01, 0xE5.toByte(), 0x27.toByte(),
        0xDE.toByte(), 0x1F.toByte(), 0xC5.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60,
        0x82.toByte()
    )

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

                    val path = url.path ?: return null

                    // Only intercept image/icon requests
                    if (!imageMime(path).startsWith("image/")) return null

                    // If the file exists on disk, let it load normally
                    if (File(path).exists()) return null

                    // File not on disk (likely inside .mdd archive):
                    // Return 1×1 transparent PNG to prevent broken-image icon
                    return WebResourceResponse(
                        "image/png", "UTF-8",
                        ByteArrayInputStream(transparentPixel)
                    )
                }
            }
        }
    }
}

