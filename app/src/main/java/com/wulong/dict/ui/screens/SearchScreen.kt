package com.wulong.dict.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wulong.dict.domain.model.SearchHistory
import com.wulong.dict.domain.model.Suggestion
import com.wulong.dict.ui.theme.WulongColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEntry: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFieldFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    var showClearAllDialog by remember { mutableStateOf(false) }

    // ── TextFieldValue wrapper for auto-select-all on focus ──────────
    var textFieldValue by remember { mutableStateOf(TextFieldValue(state.query)) }
    var wasFocused by remember { mutableStateOf(false) }

    // Sync external query changes (e.g. onClear, onSearch) into TextFieldValue
    LaunchedEffect(state.query) {
        if (textFieldValue.text != state.query) {
            textFieldValue = TextFieldValue(state.query)
        }
    }

    // Auto-focus the search field when screen appears
    LaunchedEffect(Unit) {
        searchFieldFocusRequester.requestFocus()
    }

    // Navigate to EntryScreen when search results arrive
    LaunchedEffect(state.shouldNavigateToEntry) {
        if (state.shouldNavigateToEntry) {
            keyboardController?.hide()
            onNavigateToEntry(state.query)
            viewModel.onNavigatedToEntry()
        }
    }

    // Show search errors via snackbar
    LaunchedEffect(state.searchError) {
        state.searchError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorDismissed()
        }
    }

    // Clear-all confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("清空搜索历史") },
            text = { Text("确定要清空所有搜索历史吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onClearAllHistory()
                    showClearAllDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WulongColors.Background,
        topBar = {
            TopAppBar(
                title = { },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Immersive cream pill search bar ───────────────────────
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    viewModel.onQueryChange(newValue.text)
                },
                placeholder = {
                    Text(
                        "输入单词以查询…",
                        color = WulongColors.Placeholder,
                        fontSize = 15.sp
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = WulongColors.BodyText,
                    fontSize = 16.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .focusRequester(searchFieldFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            viewModel.onSearchFieldFocused()
                            if (!wasFocused && textFieldValue.text.isNotEmpty()) {
                                textFieldValue = textFieldValue.copy(
                                    selection = TextRange(0, textFieldValue.text.length)
                                )
                            }
                            wasFocused = true
                        } else {
                            viewModel.onSearchFieldBlurred()
                            wasFocused = false
                        }
                    },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = WulongColors.SearchFill,
                    unfocusedContainerColor = WulongColors.SearchFill,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = WulongColors.BodyText,
                ),
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = WulongColors.Placeholder,
                        modifier = Modifier.size(22.dp)
                    )
                },
                trailingIcon = {
                    if (textFieldValue.text.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.onClear()
                            textFieldValue = TextFieldValue("")
                            searchFieldFocusRequester.requestFocus()
                        }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "清除",
                                tint = WulongColors.Placeholder
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.onSearch(textFieldValue.text)
                    }
                )
            )

            // ── Content area ────────────────────────────────────────────
            when {
                state.isInitializing -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "正在加载词典索引…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                state.initError != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "初始化失败: ${state.initError}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                state.showHistory && textFieldValue.text.isEmpty() && state.history.isNotEmpty() -> {
                    HistoryPanel(
                        history = state.history,
                        onItemClick = { word ->
                            keyboardController?.hide()
                            viewModel.onSearch(word)
                        },
                        onDelete = { id -> viewModel.onDeleteHistory(id) },
                        onClearAll = { showClearAllDialog = true },
                        keyboardController = keyboardController
                    )
                }

                state.suggestions.isNotEmpty() && state.results.isEmpty() -> {
                    SuggestionsDropdown(
                        suggestions = state.suggestions,
                        onSuggestionClick = { sug ->
                            keyboardController?.hide()
                            viewModel.onSearch(sug.keyword, sug.dictionaryId)
                        },
                        keyboardController = keyboardController
                    )
                }

                state.isSearching -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                textFieldValue.text.isNotBlank() && !state.isSearching && state.results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "未找到「${textFieldValue.text}」的相关结果",
                            style = MaterialTheme.typography.bodyLarge,
                            color = WulongColors.Placeholder
                        )
                    }
                }

                else -> {
                    // Fallback: empty idle state — nothing to show yet, but prevents whiteout
                }
            }
        }
    }
}

// ─── Sub-composables ────────────────────────────────────────────────────

@Composable
private fun HistoryPanel(
    history: List<SearchHistory>,
    onItemClick: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    keyboardController: SoftwareKeyboardController?,
) {
    val listState = rememberLazyListState()

    // Hide keyboard when the user starts scrolling the history list
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            keyboardController?.hide()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "搜索历史",
                style = MaterialTheme.typography.titleSmall,
                color = WulongColors.Placeholder
            )
            IconButton(onClick = onClearAll) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "清空全部",
                    tint = WulongColors.Placeholder
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(history, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(item.searchWord) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = WulongColors.Placeholder
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.searchWord,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatTime(item.searchTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onDelete(item.id) }) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
            }
        }
    }
}

@Composable
private fun SuggestionsDropdown(
    suggestions: List<Suggestion>,
    onSuggestionClick: (Suggestion) -> Unit,
    keyboardController: SoftwareKeyboardController?,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            keyboardController?.hide()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 300.dp), state = listState) {
            items(suggestions.take(20)) { sug ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(sug) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sug.keyword,
                        style = MaterialTheme.typography.bodyLarge,
                        color = WulongColors.BodyText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = sug.dictionaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = WulongColors.Placeholder
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        else -> {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
