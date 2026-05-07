package com.wulong.dict.domain.model

/**
 * Domain model representing a dictionary lookup result.
 */
data class DictionaryEntry(
    val keyword: String,             // The matched keyword
    val htmlContent: String,         // Decoded HTML definition
    val dictionaryId: Int,           // Source dictionary ID (0=Oxford, 1=Collins, 2=Webster)
    val dictionaryLabel: String,     // Human-readable dictionary name
)

/**
 * Lightweight suggestion item for prefix-based autocomplete.
 */
data class Suggestion(
    val keyword: String,
    val dictionaryId: Int,
    val dictionaryLabel: String,
)
