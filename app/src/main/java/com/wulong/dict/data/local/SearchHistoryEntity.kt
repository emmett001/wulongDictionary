package com.wulong.dict.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["search_word"], unique = true)]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "search_word")
    val searchWord: String,

    @ColumnInfo(name = "search_time")
    val searchTime: Long = System.currentTimeMillis(),
)
