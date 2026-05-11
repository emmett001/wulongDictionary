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
 *
 * Dictionary discovery: at construction time, scans [dictRootDir] for
 * subdirectories containing .sqlite3 files. Each directory may include an
 * optional `dict.json` file:
 *   { "shortName": "牛津", "fullName": "牛津高阶双解词典" }
 * If absent, the directory name is used for both fields.
 */
class SqliteDictEngine(private val dictRootDir: File) {

    data class DictConfig(
        val shortName: String,
        val fullName: String,
        val id: Int,
        val dbRelPath: String   // relative path inside dictRootDir
    )

    /** Dynamically discovered dictionary configurations. */
    val configs: List<DictConfig> = scanDictionaries()

    companion object {
        private const val TAG = "SqliteDictEngine"
    }

    private fun resolveDbFile(config: DictConfig): File = File(dictRootDir, config.dbRelPath)

    private val databases = mutableMapOf<Int, SQLiteDatabase>()

    private fun scanDictionaries(): List<DictConfig> {
        val result = mutableListOf<DictConfig>()
        val dirs = dictRootDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }
            ?: return result

        var nextId = 0
        for (dir in dirs) {
            val sqliteFile = dir.listFiles()?.find { it.name.endsWith(".sqlite3") } ?: continue
            val meta = readDictJson(dir)
            val dbRelPath = sqliteFile.relativeTo(dictRootDir).path
            result.add(
                DictConfig(
                    shortName = meta.shortName ?: dir.name,
                    fullName = meta.fullName ?: dir.name,
                    id = nextId,
                    dbRelPath = dbRelPath
                )
            )
            nextId++
        }
        return result
    }

    private data class DictMeta(val shortName: String?, val fullName: String?)

    private fun readDictJson(dir: File): DictMeta {
        val jsonFile = File(dir, "dict.json")
        if (!jsonFile.isFile) return DictMeta(null, null)
        return try {
            val obj = org.json.JSONObject(jsonFile.readText())
            DictMeta(
                shortName = obj.optString("shortName", null),
                fullName = obj.optString("fullName", null)
            )
        } catch (_: Exception) {
            DictMeta(null, null)
        }
    }

    fun open() {
        for (config in configs) {
            val dbFile = resolveDbFile(config)
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            databases[config.id] = db
            Log.d(TAG, "Opened ${config.fullName} — ${dbFile.absolutePath}")
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

        for (config in configs) {
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
                            dictionaryLabel = config.fullName
                        )
                    )
                }
            }
        }
        return results
    }

    /** Get the directory containing CSS/JS resources for a dictionary. */
    fun getResourceDir(dictId: Int): File? {
        val config = configs.firstOrNull { it.id == dictId } ?: return null
        return resolveDbFile(config).parentFile
    }
}
