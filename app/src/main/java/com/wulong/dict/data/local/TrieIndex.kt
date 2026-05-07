package com.wulong.dict.data.local

import java.util.PriorityQueue

/**
 * Compact Trie (prefix tree) for millisecond-level keyword lookup and prefix suggestions.
 *
 * Memory profile for ~500K English words: roughly 10-30 MB depending on key length.
 * Each node stores children as a sorted ArrayMap-like structure to balance speed and memory.
 */
class TrieIndex {

    data class Hit(
        val keyword: String,
        val recordOffset: Long,
        val recordSize: Int,
        val dictionaryId: Int
    )

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var hits: MutableList<Hit>? = null // Non-null only for terminal nodes (word boundaries)
    }

    private val root = TrieNode()
    var entryCount: Int = 0
        private set

    /** Insert a keyword with its record location and dictionary id. */
    fun insert(keyword: String, recordOffset: Long, recordSize: Int, dictionaryId: Int) {
        var node = root
        val lower = keyword.lowercase()
        for (ch in lower) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        if (node.hits == null) node.hits = mutableListOf()
        node.hits!!.add(Hit(keyword, recordOffset, recordSize, dictionaryId))
        entryCount++
    }

    /** Exact-match lookup (case-insensitive). Returns all matching dictionary entries. */
    fun search(word: String): List<Hit> {
        var node = root
        val lower = word.lowercase()
        for (ch in lower) {
            node = node.children[ch] ?: return emptyList()
        }
        return node.hits ?: emptyList()
    }

    /**
     * Prefix-based suggestions. Returns up to [limit] matches whose keys start with [prefix].
     * Results are sorted by keyword (ascending).
     */
    fun suggest(prefix: String, limit: Int = 20): List<Hit> {
        var node = root
        val lower = prefix.lowercase()
        for (ch in lower) {
            node = node.children[ch] ?: return emptyList()
        }

        val results = mutableListOf<Hit>()
        collectHits(node, results, limit)
        return results.sortedBy { it.keyword.lowercase() }
    }

    private fun collectHits(node: TrieNode, results: MutableList<Hit>, limit: Int) {
        node.hits?.let { results.addAll(it) }
        if (results.size >= limit) return

        for ((_, child) in node.children.toSortedMap()) {
            collectHits(child, results, limit)
            if (results.size >= limit) return
        }
    }

    /** Clear all entries (for re-indexing). */
    fun clear() {
        root.children.clear()
        root.hits = null
        entryCount = 0
    }
}
