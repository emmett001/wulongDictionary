package com.wulong.dict.domain.model

/**
 * Lightweight suggestion item for prefix-based autocomplete.
 */
data class Suggestion(
    val keyword: String,
    val dictionaryId: Int,
    val dictionaryLabel: String,
)
