package com.wulong.dict.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    /** Insert a new search word or update its timestamp if it already exists. */
    @Upsert
    suspend fun upsert(entity: SearchHistoryEntity)

    /** Get all history entries ordered by most recent first. */
    @Query("SELECT * FROM search_history ORDER BY search_time DESC")
    fun getAllFlow(): Flow<List<SearchHistoryEntity>>

    /** Get all history entries as a snapshot (for non-observable use). */
    @Query("SELECT * FROM search_history ORDER BY search_time DESC")
    suspend fun getAll(): List<SearchHistoryEntity>

    /** Delete a single history entry by ID. */
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Delete a history entry by word. */
    @Query("DELETE FROM search_history WHERE search_word = :word")
    suspend fun deleteByWord(word: String)

    /** Clear all history. */
    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    /** Get the entry for a specific word, if it exists. */
    @Query("SELECT * FROM search_history WHERE search_word = :word LIMIT 1")
    suspend fun getByWord(word: String): SearchHistoryEntity?
}
