package com.wulong.dict.domain.repository

import com.wulong.dict.domain.model.DictionaryEntry
import com.wulong.dict.domain.model.Suggestion

interface DictionaryRepository {

    /** One-time initialization: open SQLite dictionary databases. */
    suspend fun initialize()

    /** Exact word lookup across all dictionaries. */
    suspend fun searchWord(word: String): List<DictionaryEntry>

    /** Prefix-based suggestions for autocomplete. */
    suspend fun getSuggestions(prefix: String, limit: Int = 20): List<Suggestion>
}
