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
    var configs: List<DictConfig> = scanDictionaries()
        private set

    /** Reorder configs to match the saved preference. Unknown entries are appended at the end.
     * Must be called after [open] — re-keys the internal database map to match new IDs. */
    fun applyOrder(order: List<String>) {
        // Map old DBs by directory name for stable re-keying
        val dbByDir = databases.entries.associate { (oldId, db) ->
            val dirName = configs.firstOrNull { it.id == oldId }
                ?.let { resolveDbFile(it).parentFile?.name } ?: ""
            dirName to db
        }

        val byName = configs.associateBy { resolveDbFile(it).parentFile?.name ?: "" }
        val ordered = mutableListOf<DictConfig>()
        for (dirName in order) {
            byName[dirName]?.let { ordered.add(it) }
        }
        for (config in configs) {
            val dirName = resolveDbFile(config).parentFile?.name ?: ""
            if (dirName !in order) ordered.add(config)
        }

        configs = ordered.mapIndexed { index, config ->
            config.copy(id = index)
        }

        databases.clear()
        for (config in configs) {
            val dirName = resolveDbFile(config).parentFile?.name ?: ""
            dbByDir[dirName]?.let { databases[config.id] = it }
        }
    }

    companion object {
        private const val TAG = "SqliteDictEngine"

        /** Matches a &lt;table&gt; grammar bar containing m./f./n. (German noun gender). */
        private val GENDER_RE = Regex("""<table[^>]*>.*?\b([mfn])\.\s.*?</table>""", RegexOption.DOT_MATCHES_ALL)

        /** Matches the headword &lt;font&gt; tag (may have extra attrs like size=+0). */
        private val GENDER_TAG_RE = Regex("""<font color="black"(?:\s[^>]*)?>""")
    }

    fun resolveDbFile(config: DictConfig): File = File(dictRootDir, config.dbRelPath)

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
    fun search(keyword: String, dictId: Int): String? = searchWithRedirect(keyword, dictId, maxDepth = 3)

    /**
     * Internal search that follows @@@LINK= redirect chains.
     *
     * MDX datasets use soft-redirect entries whose content is the literal string
     * `@@@LINK=<target>`. This method collects ALL matching entries for the
     * keyword (words like 白土 may have multiple redirect entries pointing to
     * different definitions) and concatenates resolved HTMLs with a separator.
     */
    private fun searchWithRedirect(keyword: String, dictId: Int, maxDepth: Int): String? {
        val db = databases[dictId] ?: return null
        val lower = keyword.lowercase()

        // Step 1: collect ALL distinct original_keys from search table
        val originalKeys = mutableListOf<String>()
        db.rawQuery("SELECT original_key FROM search WHERE lower_key = ?", arrayOf(lower)).use { cursor ->
            while (cursor.moveToNext()) {
                originalKeys.add(cursor.getString(0))
            }
        }

        // Step 2: for each original_key, fetch ALL content rows from entries
        val lookupKeys = if (originalKeys.isEmpty()) listOf(keyword) else originalKeys.distinct()
        val rawContents = mutableListOf<String>()
        for (lk in lookupKeys) {
            db.rawQuery("SELECT content FROM entries WHERE key = ?", arrayOf(lk)).use { cursor ->
                while (cursor.moveToNext()) {
                    rawContents.add(String(cursor.getBlob(0), Charsets.UTF_8))
                }
            }
            // Fallback: try "@" prefix when search table missed and no entries found
            if (rawContents.isEmpty() && originalKeys.isEmpty()) {
                db.rawQuery("SELECT content FROM entries WHERE key = ?", arrayOf("@$keyword")).use { cursor ->
                    while (cursor.moveToNext()) {
                        rawContents.add(String(cursor.getBlob(0), Charsets.UTF_8))
                    }
                }
            }
        }
        if (rawContents.isEmpty()) return null

        // Step 3: resolve each content — follow @@@LINK= redirects, inject gender class
        val resolved = rawContents.map { raw ->
            val resolved = resolveEntry(raw, keyword, dictId, maxDepth) ?: return@map null
            injectGenderClass(resolved)
        }.filterNotNull()

        return if (resolved.isEmpty()) null
        else if (resolved.size == 1) resolved[0]
        else resolved.joinToString("\n<hr style='border:0;border-top:1px solid #ddd;margin:16px 0'>\n")
    }

    /** Follow a single @@@LINK= redirect. Returns resolved HTML or null. */
    private fun resolveEntry(raw: String, keyword: String, dictId: Int, maxDepth: Int): String? {
        val trimmed = raw.trimStart()
        if (!trimmed.startsWith("@@@LINK=")) return raw
        val target = trimmed.removePrefix("@@@LINK=").trim().lines().firstOrNull()?.trim() ?: return null
        if (maxDepth <= 1) {
            Log.w(TAG, "Redirect depth exceeded: $keyword -> $target (dict=$dictId)")
            return "<p style='color:#999'>跳转次数过多</p>"
        }
        if (target.equals(keyword, ignoreCase = true)) {
            Log.w(TAG, "Self-redirect loop: $keyword (dict=$dictId)")
            return "<p style='color:#999'>词条循环引用</p>"
        }
        Log.d(TAG, "Redirect: $keyword -> $target (dict=$dictId, depth=${4 - maxDepth})")
        return searchWithRedirect(target, dictId, maxDepth - 1)
            ?.let { injectGenderClass(it) }
    }

    /** Inject CSS class into the headword &lt;font&gt; tag for German gender colour coding. */
    private fun injectGenderClass(html: String): String {
        val m = GENDER_RE.find(html) ?: return html
        val gender = m.groupValues[1]
        return html.replaceFirst(GENDER_TAG_RE, """<font color="black" class="hw-$gender">""")
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
