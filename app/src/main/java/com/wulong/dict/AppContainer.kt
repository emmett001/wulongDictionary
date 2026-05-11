package com.wulong.dict

import android.content.Context
import com.wulong.dict.data.local.AppDatabase
import com.wulong.dict.data.local.LanguageSettings
import com.wulong.dict.data.local.SqliteDictEngine
import com.wulong.dict.data.repository.DictionaryRepositoryImpl
import com.wulong.dict.data.repository.HistoryRepositoryImpl
import com.wulong.dict.domain.model.Language
import com.wulong.dict.domain.repository.DictionaryRepository
import com.wulong.dict.domain.repository.HistoryRepository
import com.wulong.dict.domain.usecase.*
import com.wulong.dict.ui.pool.WebViewPool
import java.io.File

/**
 * Simple manual DI container — avoids heavyweight DI frameworks for this single-purpose app.
 */
class AppContainer(context: Context, val languageSettings: LanguageSettings, initialLanguageCode: String) {

    private val appContext = context.applicationContext

    var language: Language = Language.fromCode(initialLanguageCode)
        private set

    private fun dictRootFor(lang: Language) =
        appContext.getExternalFilesDir(null)!!.resolve("dicts/${lang.code}")

    // ── Data layer ──────────────────────────────────────────────────────

    /** Exposed for debug/testing purposes. */
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    private val historyDao = database.searchHistoryDao()

    var dictEngine: SqliteDictEngine = SqliteDictEngine(dictRootFor(language))
        private set

    fun switchLanguage(code: String) {
        dictEngine.close()
        language = Language.fromCode(code)
        dictEngine = SqliteDictEngine(dictRootFor(language))
        dictEngine.open()
    }

    // ── WebView pool ──────────────────────────────────────────────────────

    val webViewPool: WebViewPool = WebViewPool(appContext)

    /** Dict ID → local directory containing CSS/JS/PNG resources. */
    val dictDirs: Map<Int, File>
        get() = dictEngine.configs.mapNotNull { config ->
            dictEngine.getResourceDir(config.id)?.let { config.id to it }
        }.toMap()

    // ── Repositories ────────────────────────────────────────────────────

    private val ioDispatcher = kotlinx.coroutines.Dispatchers.IO

    val dictionaryRepository: DictionaryRepository = DictionaryRepositoryImpl(
        dictEngine = dictEngine,
        ioDispatcher = ioDispatcher
    )

    val historyRepository: HistoryRepository = HistoryRepositoryImpl(historyDao)

    // ── Use Cases (Dictionary) ──────────────────────────────────────────

    val searchWordUseCase: SearchWordUseCase = SearchWordUseCase(dictionaryRepository)
    val getSuggestionsUseCase: GetSuggestionsUseCase = GetSuggestionsUseCase(dictionaryRepository)

    // ── Use Cases (History) ─────────────────────────────────────────────

    val saveSearchWordUseCase: SaveSearchWordUseCase = SaveSearchWordUseCase(historyRepository)
    val getSearchHistoryUseCase: GetSearchHistoryUseCase = GetSearchHistoryUseCase(historyRepository)
    val deleteHistoryItemUseCase: DeleteHistoryItemUseCase = DeleteHistoryItemUseCase(historyRepository)
    val clearAllHistoryUseCase: ClearAllHistoryUseCase = ClearAllHistoryUseCase(historyRepository)
}
