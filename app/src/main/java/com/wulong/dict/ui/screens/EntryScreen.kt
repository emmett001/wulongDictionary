package com.wulong.dict.ui.screens

import android.webkit.WebView
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wulong.dict.domain.model.DictionaryEntry
import com.wulong.dict.ui.pool.WebViewPool
import kotlinx.coroutines.launch
import java.io.File

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
    onNavigateBack: () -> Unit,
    onSearchWordClick: () -> Unit,
    webViewPool: WebViewPool,
    dictDirs: Map<Int, File>,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
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
        Column(modifier = Modifier.padding(paddingValues)) {
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
                modifier = Modifier.fillMaxSize()
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

    DisposableEffect(Unit) {
        onDispose { webViewPool.release(webView) }
    }

    // Lazy rendering: only load HTML when this tab becomes visible
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            val baseUrl = if (dictDir != null) "file://${dictDir.absolutePath}/" else null
            webView.loadDataWithBaseURL(
                baseUrl,
                buildHtml(entry, dictDir),
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
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
