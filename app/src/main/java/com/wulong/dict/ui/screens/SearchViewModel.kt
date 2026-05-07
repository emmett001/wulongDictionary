package com.wulong.dict.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wulong.dict.domain.model.DictionaryEntry
import com.wulong.dict.domain.model.SearchHistory
import com.wulong.dict.domain.model.Suggestion
import com.wulong.dict.domain.usecase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchWord: SearchWordUseCase,
    private val getSuggestions: GetSuggestionsUseCase,
    private val saveSearchWord: SaveSearchWordUseCase,
    getSearchHistory: GetSearchHistoryUseCase,
    private val deleteHistoryItem: DeleteHistoryItemUseCase,
    private val clearAllHistory: ClearAllHistoryUseCase,
    private val initEngine: suspend () -> Unit,
) : ViewModel() {

    // ── UI State ────────────────────────────────────────────────────────

    data class UiState(
        val query: String = "",
        val suggestions: List<Suggestion> = emptyList(),
        val results: List<DictionaryEntry> = emptyList(),
        val history: List<SearchHistory> = emptyList(),
        val isInitializing: Boolean = true,
        val isSearching: Boolean = false,
        val initError: String? = null,
        val searchError: String? = null,
        val showHistory: Boolean = false,
        val shouldNavigateToEntry: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── Internal debounce channel ────────────────────────────────────────

    private val queryFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var suggestionJob: Job? = null
    private var searchJob: Job? = null

    // ── Initialization ───────────────────────────────────────────────────

    init {
        // Observe history from Room (reactive Flow)
        viewModelScope.launch {
            getSearchHistory().catch { /* silently ignore DB errors */ }
                .collect { history ->
                    _uiState.update { it.copy(history = history) }
                }
        }

        // Initialize engine
        viewModelScope.launch {
            try {
                initEngine()
                _uiState.update { it.copy(isInitializing = false) }
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Engine initialization failed", e)
                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        initError = "${e.javaClass.simpleName}: ${e.message ?: "(无详细信息)"}"
                    )
                }
            }
        }
    }

    // ── Public actions ───────────────────────────────────────────────────

    /** Called on every text change in the search field. */
    fun onQueryChange(text: String) {
        _uiState.update {
            it.copy(
                query = text,
                suggestions = if (text.length < 2) emptyList() else it.suggestions,
                results = if (text.isEmpty()) emptyList() else it.results,
                showHistory = text.isEmpty(),
            )
        }

        // Debounce: wait 300ms before firing suggestion query
        suggestionJob?.cancel()
        if (text.length >= 2) {
            suggestionJob = viewModelScope.launch {
                delay(300)
                val trimmed = text.trim()
                if (trimmed.length >= 2) {
                    try {
                        val suggestions = getSuggestions(trimmed)
                        _uiState.update { it.copy(suggestions = suggestions) }
                    } catch (_: Exception) { }
                }
            }
        }
    }

    /** Execute full search (triggered by Enter key or suggestion tap). */
    fun onSearch(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return

        _uiState.update {
            it.copy(
                query = trimmed,
                suggestions = emptyList(),
                isSearching = true,
                searchError = null,
                showHistory = false,
            )
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val results = searchWord(trimmed)
                _uiState.update {
                    it.copy(
                        results = results,
                        isSearching = false,
                        shouldNavigateToEntry = results.isNotEmpty()
                    )
                }
                // Save to history after successful search
                saveSearchWord(trimmed)
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Search failed for '$trimmed'", e)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchError = "${e.javaClass.simpleName}: ${e.message ?: "(无详细信息)"}"
                    )
                }
            }
        }
    }

    /** Clear query text, cancel pending search, reset to idle state. */
    fun onClear() {
        searchJob?.cancel()
        suggestionJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                suggestions = emptyList(),
                showHistory = true,
                isSearching = false,
                shouldNavigateToEntry = false,
            )
        }
    }

    /** Delete a single history entry. */
    fun onDeleteHistory(id: Long) {
        viewModelScope.launch {
            try {
                deleteHistoryItem(id)
            } catch (_: Exception) { }
        }
    }

    /** Clear all history. */
    fun onClearAllHistory() {
        viewModelScope.launch {
            try {
                clearAllHistory()
                _uiState.update { it.copy(history = emptyList()) }
            } catch (_: Exception) { }
        }
    }

    /** Consume the navigation event after EntryScreen is shown. */
    fun onNavigatedToEntry() {
        _uiState.update { it.copy(shouldNavigateToEntry = false) }
    }

    /** Show history panel when search field gets focus. */
    fun onSearchFieldFocused() {
        if (_uiState.value.query.isEmpty()) {
            _uiState.update { it.copy(showHistory = true) }
        }
    }

    /** Hide history panel when focus is lost. */
    fun onSearchFieldBlurred() {
        _uiState.update { it.copy(showHistory = false) }
    }

    /** Dismiss error snackbar. */
    fun onErrorDismissed() {
        _uiState.update { it.copy(searchError = null) }
    }
}
