package com.wulong.dict.domain.model

data class SearchHistory(
    val id: Long,
    val searchWord: String,
    val searchTime: Long,
)
