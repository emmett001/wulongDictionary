package com.wulong.dict.data.repository

import com.wulong.dict.data.local.SearchHistoryDao
import com.wulong.dict.data.local.SearchHistoryEntity
import com.wulong.dict.domain.model.SearchHistory
import com.wulong.dict.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepositoryImpl(
    private val dao: SearchHistoryDao
) : HistoryRepository {

    override fun getHistoryFlow(): Flow<List<SearchHistory>> {
        return dao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getHistory(): List<SearchHistory> {
        return dao.getAll().map { it.toDomain() }
    }

    override suspend fun saveSearchWord(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        // Upsert: insert new or update timestamp if already exists
        val existing = dao.getByWord(trimmed)
        val entity = if (existing != null) {
            existing.copy(searchTime = System.currentTimeMillis())
        } else {
            SearchHistoryEntity(searchWord = trimmed, searchTime = System.currentTimeMillis())
        }
        dao.upsert(entity)
    }

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }

    private fun SearchHistoryEntity.toDomain() = SearchHistory(
        id = id,
        searchWord = searchWord,
        searchTime = searchTime,
    )
}
