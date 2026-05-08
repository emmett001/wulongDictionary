package com.wulong.dict.ui.screens

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wulong.dict.domain.model.DictionaryEntry
import com.wulong.dict.ui.pool.WebViewPool
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

private data class DictTab(
    val id: Int,
    val shortName: String,
    val fullName: String,
)

private val DICT_TABS = listOf(
    DictTab(0, "牛津", "牛津高阶双解词典"),
    DictTab(1, "柯林斯", "柯林斯高阶双解词典"),
    DictTab(2, "韦氏大学", "韦氏大学词典"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EntryScreen(
    word: String,
    results: List<DictionaryEntry>,
    activeDictId: Int,
    onNavigateBack: () -> Unit,
    onSearchWordClick: () -> Unit,
    webViewPool: WebViewPool,
    dictDirs: Map<Int, File>,
) {
    val pagerState = rememberPagerState(pageCount = { 3 }, initialPage = activeDictId)
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = word,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onSearchWordClick)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // ── Tab row: equal-width, 16sp+, accent indicator ──────────
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {}
            ) {
                DICT_TABS.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    Tab(
                        selected = selected,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = tab.shortName,
                                fontSize = 17.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (selected) 1f else 0.6f
                                )
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // ── Pager: swipeable dictionary pages ──────────────────────
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(rememberGestureFilter())
            ) { pageIndex ->
                val tab = DICT_TABS[pageIndex]
                val entry = results.firstOrNull { it.dictionaryId == tab.id }
                val dictDir = dictDirs[tab.id]

                DictPage(
                    entry = entry,
                    dictId = tab.id,
                    dictDir = dictDir,
                    webViewPool = webViewPool,
                    isCurrentPage = pagerState.currentPage == pageIndex,
                )
            }
        }
    }
}

// ─── Per-dictionary page ──────────────────────────────────────────────────

@Composable
private fun DictPage(
    entry: DictionaryEntry?,
    dictId: Int,
    dictDir: File?,
    webViewPool: WebViewPool,
    isCurrentPage: Boolean,
) {
    if (entry == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "未收录此词",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val webView = remember { webViewPool.acquire() }
    var isLoading by remember { mutableStateOf(true) }
    var hasLoaded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webViewPool.release(webView)
        }
    }

    // Wrap WebViewClient to track page load state while preserving
    // the pool's request-interception logic (CDN blocking, missing-image fallback).
    DisposableEffect(webView) {
        val originalClient = webView.webViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                isLoading = true
                originalClient.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isLoading = false
                originalClient.onPageFinished(view, url)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                return originalClient.shouldInterceptRequest(view, request)
            }
        }
        onDispose {
            // Restore original client so pool gets back a clean WebView
            webView.webViewClient = originalClient
        }
    }

    // Load HTML when this tab becomes visible (or pre-load adjacent tabs).
    LaunchedEffect(isCurrentPage) {
        if (!hasLoaded) {
            if (isCurrentPage) {
                loadEntryHtml(webView, entry, dictDir)
                hasLoaded = true
            } else {
                // Pre-load adjacent tab after a short delay so the current
                // tab's rendering isn't competing for resources.
                kotlinx.coroutines.delay(600)
                if (!hasLoaded) {
                    loadEntryHtml(webView, entry, dictDir)
                    hasLoaded = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInterop())
        )
        if (isLoading && isCurrentPage) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
    }
}

private fun loadEntryHtml(webView: WebView, entry: DictionaryEntry, dictDir: File?) {
    val baseUrl = if (dictDir != null) "file://${dictDir.absolutePath}/" else null
    webView.loadDataWithBaseURL(
        baseUrl,
        buildHtml(entry, dictDir),
        "text/html",
        "UTF-8",
        null
    )
}

// ─── Nested-scroll interop: bridges WebView native scroll → Compose ─────────

/**
 * Manual nested-scroll bridge between the WebView (native Android View) and
 * Compose's nested-scroll system.
 *
 * The WebView handles vertical scrolling internally via Android's native touch
 * dispatch, outside of Compose's nested-scroll protocol.  Without this bridge
 * the system sees every vertical scroll as "unconsumed," which (on rapid
 * successive gestures) can confuse the parent [HorizontalPager] into
 * intercepting the next scroll — the page then appears stuck.
 *
 * This connection consumes the leftover vertical delta / velocity in
 * [onPostScroll] / [onPostFling] so the parent chain knows the child already
 * handled it.
 */
@Composable
private fun rememberNestedScrollInterop(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset(0f, available.y)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity = Velocity(0f, available.y)
        }
    }
}

// ─── Gesture filter: only purely horizontal swipes can trigger tab switch ─────

/**
 * A [NestedScrollConnection] that filters both scroll deltas (drag phase)
 * and fling velocities (inertial phase) before they reach [HorizontalPager].
 *
 * Only swipes with effectively zero vertical component qualify as intentional
 * tab switches.  Every other drag is treated as vertical scrolling — the
 * horizontal component is consumed here so the pager never sees it, and the
 * vertical component passes through to the WebView intact.
 *
 * Separate thresholds for delta (pixels/frame) vs velocity (pixels/second)
 * because their value ranges differ by orders of magnitude.
 *
 * All four nested-scroll methods are overridden because the HorizontalPager
 * can hijack horizontal leftovers at any stage:
 * - [onPreScroll]  – blocks horizontal delta during active drag
 * - [onPostScroll] – blocks horizontal leftovers the WebView didn't consume
 * - [onPreFling]   – blocks horizontal velocity before the pager starts a
 *                    page-change animation (would otherwise consume the
 *                    entire fling, killing WebView momentum)
 * - [onPostFling]  – blocks horizontal velocity leftovers
 */
@Composable
private fun rememberGestureFilter(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {

            // ── Drag phase (pixels per frame) ─────────────────────────────

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (abs(available.y) < 1f) return Offset.Zero
                return Offset(available.x, 0f)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (abs(available.y) < 1f) return Offset.Zero
                return Offset(available.x, 0f)
            }

            // ── Fling phase (pixels per second) ───────────────────────────

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (abs(available.y) < abs(available.x) * 0.02f) return Velocity.Zero
                return Velocity(available.x, 0f)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                if (abs(available.y) < abs(available.x) * 0.02f) return Velocity.Zero
                return Velocity(available.x, 0f)
            }
        }
    }
}

// ─── HTML builder ─────────────────────────────────────────────────────────

private fun buildHtml(entry: DictionaryEntry, dictDir: File?): String {
    val (cssLinks, jsScripts) = if (dictDir != null && dictDir.isDirectory) {
        val files = dictDir.listFiles() ?: emptyArray()
        val css = files.filter { it.name.endsWith(".css", ignoreCase = true) }
            .joinToString("\n") { """<link rel="stylesheet" href="${it.name}">""" }
        val js = files.filter { it.name.endsWith(".js", ignoreCase = true) }
            .joinToString("\n") { """<script src="${it.name}"></script>""" }
        css to js
    } else {
        "" to ""
    }

    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
$cssLinks
<style>
  body {
    font-family: -apple-system, "Noto Sans SC", "PingFang SC", system-ui, sans-serif;
    font-size: 16px;
    line-height: 1.7;
    padding: 12px 16px;
    color: #1a1a1a;
    background: #fff;
    word-wrap: break-word;
  }
  img { max-width: 100%; height: auto; }
</style>
</head>
<body>
${entry.htmlContent}
</body>
$jsScripts
</html>
    """.trimIndent()
}
