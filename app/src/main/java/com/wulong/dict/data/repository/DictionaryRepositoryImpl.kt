package com.wulong.dict.data.repository

import android.util.Log
import com.wulong.dict.data.local.SqliteDictEngine
import com.wulong.dict.domain.model.DictionaryEntry
import com.wulong.dict.domain.model.Suggestion
import com.wulong.dict.domain.repository.DictionaryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class DictionaryRepositoryImpl(
    private val dictEngine: SqliteDictEngine,
    private val ioDispatcher: CoroutineDispatcher
) : DictionaryRepository {

    companion object {
        private const val TAG = "DictRepo"
    }

    override suspend fun initialize() = withContext(ioDispatcher) {
        dictEngine.open()
    }

    override suspend fun searchWord(word: String): List<DictionaryEntry> =
        withContext(ioDispatcher) {
            dictEngine.configs.map { config ->
                async {
                    try {
                        val html = dictEngine.search(word, config.id)
                        if (html != null) {
                            DictionaryEntry(
                                keyword = word,
                                htmlContent = html,
                                dictionaryId = config.id,
                                dictionaryLabel = config.fullName
                            )
                        } else null
                    } catch (e: Exception) {
                        Log.e(TAG, "Search failed: $word (dict=${config.id})", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

    override suspend fun getSuggestions(prefix: String, limit: Int): List<Suggestion> =
        withContext(ioDispatcher) {
            dictEngine.suggest(prefix, limit)
        }
}
