package com.wulong.dict.data.local

/**
 * Parsed metadata from the MDX file header section.
 */
data class MdxHeader(
    val encoding: String = "UTF-8",
    val title: String = "",
    val description: String = "",
    val generatedByEngineVersion: String = "2.0",
    val keyCaseSensitive: Boolean = false,
    val encrypted: Boolean = false,
    val compact: Boolean = false,
    val stripKey: Boolean = false,
)
