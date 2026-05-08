package com.wulong.dict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wulong.dict.navigation.NavRoutes
import com.wulong.dict.ui.screens.EntryScreen
import com.wulong.dict.ui.screens.MainScreen
import com.wulong.dict.ui.screens.SearchScreen
import com.wulong.dict.ui.screens.SearchViewModel
import com.wulong.dict.ui.theme.WulongDictTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as WulongDictApp).appContainer

    private val viewModel: SearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(
                    searchWord = container.searchWordUseCase,
                    getSuggestions = container.getSuggestionsUseCase,
                    saveSearchWord = container.saveSearchWordUseCase,
                    getSearchHistory = container.getSearchHistoryUseCase,
                    deleteHistoryItem = container.deleteHistoryItemUseCase,
                    clearAllHistory = container.clearAllHistoryUseCase,
                    initEngine = { container.dictionaryRepository.initialize() }
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            WulongDictTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.MAIN
                ) {
                    composable(NavRoutes.MAIN) {
                        MainScreen(
                            onNavigateToSearch = {
                                viewModel.onEnterSearch()
                                navController.navigate(NavRoutes.SEARCH)
                            }
                        )
                    }
                    composable(NavRoutes.SEARCH) {
                        val state by viewModel.uiState.collectAsState()
                        SearchScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEntry = { word ->
                                navController.navigate(NavRoutes.ENTRY) {
                                    // Keep at most one ENTRY on top of SEARCH —
                                    // prevents back-stack nesting from repeated searches.
                                    popUpTo(NavRoutes.SEARCH)
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(NavRoutes.ENTRY) {
                        val state by viewModel.uiState.collectAsState()
                        EntryScreen(
                            word = state.query,
                            results = state.results,
                            activeDictId = state.activeDictId,
                            onNavigateBack = { navController.popBackStack() },
                            onSearchWordClick = { navController.popBackStack() },
                            webViewPool = container.webViewPool,
                            dictDirs = container.dictDirs
                        )
                    }
                }
            }
        }
    }
}
