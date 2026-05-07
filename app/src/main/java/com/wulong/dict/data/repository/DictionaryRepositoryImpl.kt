package com.wulong.dict.data.repository

import android.util.Log
import com.wulong.dict.data.local.MdxEngine
import com.wulong.dict.data.local.TrieIndex
import com.wulong.dict.domain.model.DictionaryEntry
import com.wulong.dict.domain.model.Suggestion
import com.wulong.dict.domain.repository.DictionaryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class DictionaryRepositoryImpl(
    private val mdxEngine: MdxEngine,
    private val trieIndex: TrieIndex,
    private val ioDispatcher: CoroutineDispatcher
) : DictionaryRepository {

    companion object {
        private const val TAG = "DictRepo"
    }

    override suspend fun initialize() {
        mdxEngine.initialize(trieIndex)
    }

    override suspend fun searchWord(word: String): List<DictionaryEntry> =
        withContext(ioDispatcher) {
            val hits = trieIndex.search(word)
            if (hits.isEmpty()) return@withContext emptyList()

            Log.d(TAG, "searchWord '$word': ${hits.size} hit(s)")

            // Fetch definitions for all matching entries in parallel
            hits.map { hit ->
                async {
                    try {
                        val rawBytes = mdxEngine.lookupDefinition(hit.dictionaryId, hit.recordOffset)
                        val html = String(rawBytes, Charsets.UTF_8)
                        val config = MdxEngine.DICTIONARIES.firstOrNull { it.id == hit.dictionaryId }
                        DictionaryEntry(
                            keyword = hit.keyword,
                            htmlContent = html,
                            dictionaryId = hit.dictionaryId,
                            dictionaryLabel = config?.label ?: "Unknown"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Lookup failed: ${hit.keyword} (dict=${hit.dictionaryId})", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

    override suspend fun getSuggestions(prefix: String, limit: Int): List<Suggestion> =
        withContext(ioDispatcher) {
            val hits = trieIndex.suggest(prefix, limit)
            hits.map { hit ->
                val config = MdxEngine.DICTIONARIES.firstOrNull { it.id == hit.dictionaryId }
                Suggestion(
                    keyword = hit.keyword,
                    dictionaryId = hit.dictionaryId,
                    dictionaryLabel = config?.label ?: "Unknown"
                )
            }.distinctBy { it.keyword.lowercase() }
        }
}
