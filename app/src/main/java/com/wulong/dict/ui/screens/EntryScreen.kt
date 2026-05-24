package com.wulong.dict.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wulong.dict.data.local.SqliteDictEngine
import com.wulong.dict.domain.model.DictionaryEntry
import com.wulong.dict.ui.pool.WebViewPool
import com.wulong.dict.ui.theme.WulongColors
import com.wulong.dict.ui.theme.WulongFonts
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EntryScreen(
    word: String,
    results: List<DictionaryEntry>,
    activeDictId: Int,
    onNavigateBack: () -> Unit,
    onSearchWordClick: () -> Unit,
    onWordClick: (String, Int) -> Unit,
    webViewPool: WebViewPool,
    dictDirs: Map<Int, File>,
    dictConfigs: List<SqliteDictEngine.DictConfig>,
) {
    val pagerState = rememberPagerState(
        pageCount = { dictConfigs.size },
        initialPage = activeDictId.coerceIn(0, maxOf(dictConfigs.size - 1, 0))
    )
    val coroutineScope = rememberCoroutineScope()
    var isTopAreaTouch by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = WulongColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = word,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onSearchWordClick),
                        fontFamily = WulongFonts.PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = WulongColors.BodyText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = WulongColors.BodyText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WulongColors.Background,
                    titleContentColor = WulongColors.BodyText
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // ── Tab row: equal-width, 16sp+, warm accent ──────────────
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = WulongColors.Background,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                .width(24.dp)
                                .height(3.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                },
                divider = {}
            ) {
                dictConfigs.forEachIndexed { index, config ->
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
                                text = config.shortName,
                                fontSize = 17.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) WulongColors.BodyText
                                        else WulongColors.Placeholder
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = WulongColors.Placeholder
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = WulongColors.SearchFill)

            // ── Pager: swipeable dictionary pages ──────────────────────
            // Area-restricted scroll: only top 55% of the screen allows
            // horizontal swipes to switch tabs. Bottom 45% is reserved
            // for vertical WebView scrolling.
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 1,
                userScrollEnabled = isTopAreaTouch,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press) {
                                    val y = event.changes.first().position.y
                                    isTopAreaTouch = y < size.height * 0.55f
                                }
                            }
                        }
                    }
            ) { pageIndex ->
                val config = dictConfigs[pageIndex]
                val entry = results.firstOrNull { it.dictionaryId == config.id }
                val dictDir = dictDirs[config.id]

                DictPage(
                    entry = entry,
                    dictId = config.id,
                    dictDir = dictDir,
                    webViewPool = webViewPool,
                    isCurrentPage = pagerState.currentPage == pageIndex,
                    onInternalLinkClick = { target -> onWordClick(target, config.id) },
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
    onInternalLinkClick: (String) -> Unit = {},
) {
    if (entry == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "未收录此词",
                style = MaterialTheme.typography.bodyLarge,
                color = WulongColors.Placeholder
            )
        }
        return
    }

    val webView = remember { webViewPool.acquire() }
    var isLoading by remember { mutableStateOf(true) }
    var hasLoaded by remember(entry.keyword) { mutableStateOf(false) }

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

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return super.shouldOverrideUrlLoading(view, request)
                val scheme = url.scheme ?: return super.shouldOverrideUrlLoading(view, request)

                // Private MDX link schemes — extract target word and trigger search
                if (scheme == "entry" || scheme == "bword") {
                    val target = Uri.decode(url.schemeSpecificPart.trimStart('/'))
                    if (target.isNotBlank()) {
                        Log.d("EntryScreen", "Internal link: $scheme → $target (dict=$dictId)")
                        onInternalLinkClick(target)
                        return true
                    }
                }

                // Plain-text relative link like href="word" — treat as internal lookup
                if (url.scheme == null && url.host == null && url.path != null) {
                    val target = Uri.decode(url.path!!).trim()
                    if (target.isNotBlank() && !target.startsWith("/")) {
                        Log.d("EntryScreen", "Plain link: $target (dict=$dictId)")
                        onInternalLinkClick(target)
                        return true
                    }
                }

                // External HTTP links → open in browser
                if (scheme == "http" || scheme == "https") {
                    try {
                        view?.context?.startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (_: Exception) { }
                    return true
                }

                return super.shouldOverrideUrlLoading(view, request)
            }
        }
        onDispose {
            // Restore original client so pool gets back a clean WebView
            webView.webViewClient = originalClient
        }
    }

    // Load HTML when this tab becomes visible, or when the entry changes
    // (e.g. internal cross-reference link clicked from within the WebView).
    val loadKey = entry?.keyword
    LaunchedEffect(isCurrentPage, loadKey) {
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
    val baseUrl = if (dictDir != null) "file://" + Uri.encode(dictDir.absolutePath, "/") + "/" else null
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

// ─── HTML builder ─────────────────────────────────────────────────────────

private val GENDER_CSS = """
    font.hw-m b { background: #1976d2; color: #fff; padding: 2px 10px; border-radius: 4px; }
    font.hw-f b { background: #e91e63; color: #fff; padding: 2px 10px; border-radius: 4px; }
    font.hw-n b { background: #4caf50; color: #fff; padding: 2px 10px; border-radius: 4px; }
""".trimIndent()

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
  $GENDER_CSS
  img { max-width: 100%; height: auto; border: 0 !important; min-width: 0 !important; min-height: 0 !important; }
  #ox-enlarge .ox-enlarge-label { display: none !important; }
  #ox-enlarge .topic { pointer-events: none !important; }
</style>
</head>
<body>
${entry.htmlContent}
</body>
$jsScripts
</html>
    """.trimIndent()
}
