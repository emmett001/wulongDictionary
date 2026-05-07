package com.wulong.dict.data.local

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.wulong.dict.domain.model.Suggestion
import java.io.File

/**
 * Read-only SQLite dictionary engine.
 * Each dictionary is a .sqlite3 file with two tables:
 *   search  (lower_key TEXT, original_key TEXT)
 *   entries (key TEXT, content BLOB)
 */
class SqliteDictEngine(private val dictRootDir: File) {

    companion object {
        private const val TAG = "SqliteDictEngine"

        val DICTIONARIES = listOf(
            DictConfig("牛津高阶双解词典", 0, "oaldpe/oaldpe.sqlite3"),
            DictConfig("柯林斯高阶双解词典", 1, "柯林斯高阶双解/柯林斯高阶双解.sqlite3"),
            DictConfig("韦氏大学词典",    2, "Merriam-Webster's Collegiate Dictionary 11th Edtion/Merriam-Webster's Collegiate Dictionary 11th Edtion.sqlite3"),
        )
    }

    data class DictConfig(
        val label: String,
        val id: Int,
        val dbRelPath: String   // relative path inside dictRootDir
    )

    private fun resolveDbFile(config: DictConfig): File = File(dictRootDir, config.dbRelPath)

    private val databases = mutableMapOf<Int, SQLiteDatabase>()

    fun open() {
        for (config in DICTIONARIES) {
            val dbFile = resolveDbFile(config)
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            databases[config.id] = db
            Log.d(TAG, "Opened ${config.label} — ${dbFile.absolutePath}")
        }
    }

    fun close() {
        databases.values.forEach { it.close() }
        databases.clear()
    }

    /** Look up a word in a specific dictionary. Returns HTML content or null. */
    fun search(keyword: String, dictId: Int): String? {
        val db = databases[dictId] ?: return null
        val lower = keyword.lowercase()

        // Step 1: find original_key from search table
        var originalKey: String? = null
        db.rawQuery("SELECT original_key FROM search WHERE lower_key = ?", arrayOf(lower)).use { cursor ->
            if (cursor.moveToFirst()) {
                originalKey = cursor.getString(0)
            }
        }

        if (originalKey == null) return null

        // Step 2: fetch HTML content from entries table
        db.rawQuery("SELECT content FROM entries WHERE key = ?", arrayOf(originalKey)).use { cursor ->
            if (cursor.moveToFirst()) {
                val blob = cursor.getBlob(0)
                return String(blob, Charsets.UTF_8)
            }
        }
        return null
    }

    /** Prefix-based autocomplete across all dictionaries. */
    fun suggest(prefix: String, limit: Int = 20): List<Suggestion> {
        val lower = prefix.lowercase()
        val results = mutableListOf<Suggestion>()

        for (config in DICTIONARIES) {
            val db = databases[config.id] ?: continue
            db.rawQuery(
                "SELECT original_key FROM search WHERE lower_key LIKE ? LIMIT ?",
                arrayOf("$lower%", limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(
                        Suggestion(
                            keyword = cursor.getString(0),
                            dictionaryId = config.id,
                            dictionaryLabel = config.label
                        )
                    )
                }
            }
        }
        return results
    }

    /** Get the directory containing CSS/JS resources for a dictionary. */
    fun getResourceDir(dictId: Int): File? {
        val config = DICTIONARIES.firstOrNull { it.id == dictId } ?: return null
        return resolveDbFile(config).parentFile
    }
}
