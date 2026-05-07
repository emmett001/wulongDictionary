package com.wulong.dict.domain.repository

import com.wulong.dict.domain.model.SearchHistory
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getHistoryFlow(): Flow<List<SearchHistory>>
    suspend fun getHistory(): List<SearchHistory>
    suspend fun saveSearchWord(word: String)
    suspend fun deleteById(id: Long)
    suspend fun clearAll()
}
