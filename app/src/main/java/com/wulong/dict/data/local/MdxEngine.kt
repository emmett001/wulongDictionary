package com.wulong.dict.data.local

import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Central engine for managing multiple MDX dictionaries.
 *
 * Responsibilities:
 *  - Copy dictionary assets (mdx/mdd/css/js) to internal storage on first launch
 *  - Parse MDX headers and keyword indices
 *  - Build a unified Trie-based index for millisecond lookup
 *  - Provide word definition retrieval
 */
class MdxEngine(
    private val assets: AssetManager,
    private val storageDir: File
) {
    companion object {
        private const val TAG = "MdxEngine"
        private const val ASSET_DICT_DIR = "dictionaries"

        // Known dictionary subdirectories inside assets/dictionaries/
        // fileStem is used to match against the directory name
        val DICTIONARIES = listOf(
            DictConfig("牛津高阶双解词典", "牛津高阶", 0),
            DictConfig("柯林斯高阶双解词典", "柯林斯高阶", 1),
            DictConfig("韦氏大学词典", "韦氏大学", 2),
        )
    }

    data class DictConfig(
        val label: String,       // Human-readable name
        val fileStem: String,    // filename without extension
        val id: Int              // unique dictionary ID
    )

    data class DictFile(
        val config: DictConfig,
        val mdxFile: File,
        val mddFile: File?  // Nullable — some dictionaries may lack MDD resource files
    )

    private val parser = MdxParser(TAG)

    /** All discovered dictionary files ready for parsing. */
    var dictFiles: List<DictFile> = emptyList()
        private set

    /** Whether the engine has been initialized (files copied + indexed). */
    var isInitialized: Boolean = false
        private set

    /**
     * Full initialization: copy assets → parse headers → build trie.
     * Call once on cold start.
     */
    suspend fun initialize(trieIndex: TrieIndex) = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        storageDir.mkdirs()

        // Step 1: Copy dictionary files from assets to internal storage
        val dictDirs = assets.list(ASSET_DICT_DIR) ?: emptyArray()
        Log.d(TAG, "Found dictionary dirs in assets: ${dictDirs.joinToString()}")

        val readyFiles = mutableListOf<DictFile>()

        for (dirName in dictDirs) {
            val config = DICTIONARIES.find { dirName.contains(it.fileStem) }
                ?: continue

            val srcPath = "$ASSET_DICT_DIR/$dirName"
            val destDir = File(storageDir, dirName)
            destDir.mkdirs()

            // Copy all files recursively (handles nested subdirectories like 中文例句释义反查/)
            var mdxFile: File? = null
            var mddFile: File? = null

            fun copyAssetsRecursively(srcDirPath: String, destDirFile: File, isTopLevel: Boolean = true) {
                val entries = assets.list(srcDirPath) ?: return
                for (entry in entries) {
                    val entryPath = "$srcDirPath/$entry"
                    // Check if this is a file or directory by trying to list it
                    val subEntries = assets.list(entryPath)
                    if (subEntries != null && subEntries.isNotEmpty()) {
                        // It's a directory — recurse
                        val subDir = File(destDirFile, entry)
                        subDir.mkdirs()
                        copyAssetsRecursively(entryPath, subDir, isTopLevel = false)
                    } else {
                        // It's a file
                        val destFile = File(destDirFile, entry)
                        if (!destFile.exists()) {
                            Log.d(TAG, "Copying $entry (${config.label})...")
                            try {
                                assets.open(entryPath).use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to copy $entry: ${e.message}")
                                continue
                            }
                        }
                        // Only track top-level MDX/MDD files — subdirectory files (e.g. 中文例句释义反查/)
                        // are separate dictionaries and must not overwrite the main dictionary reference.
                        if (isTopLevel) {
                            when {
                                entry.endsWith(".mdx", ignoreCase = true) -> mdxFile = destFile
                                entry.endsWith(".mdd", ignoreCase = true) -> mddFile = destFile
                            }
                        }
                    }
                }
            }

            copyAssetsRecursively(srcPath, destDir)

            if (mdxFile != null) {
                readyFiles.add(DictFile(config, mdxFile!!, mddFile))
                Log.d(TAG, "Ready: ${config.label} → ${mdxFile!!.name}")
            }
        }

        dictFiles = readyFiles
        Log.d(TAG, "Total dictionaries ready: ${dictFiles.size}")

        // Step 2: Build unified Trie index from all dictionaries
        trieIndex.clear()

        for ((idx, dictFile) in dictFiles.withIndex()) {
            Log.d(TAG, "Indexing ${dictFile.config.label} (${idx + 1}/${dictFiles.size})...")
            try {
                buildIndexForDict(dictFile, trieIndex)
                Log.d(TAG, "Indexed ${dictFile.config.label} — trie now has ${trieIndex.entryCount} entries")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to index ${dictFile.config.label}: ${e.message}", e)
                // Continue with remaining dictionaries — don't fail everything for one bad file
            }
        }

        if (trieIndex.entryCount == 0) {
            throw IllegalStateException("所有词典索引均构建失败，请检查词典文件是否完整")
        }

        isInitialized = true
        Log.d(TAG, "Engine initialized. Total entries indexed: ${trieIndex.entryCount}")
    }

    private fun buildIndexForDict(dictFile: DictFile, trieIndex: TrieIndex) {
        RandomAccessFile(dictFile.mdxFile, "r").use { raf ->
            // Parse header (positions file pointer after the final \n of the header section)
            val header = parser.parseHeader(raf)

            // Some MDX files have \0\0 between the header text and adler32.
            // Peek: if the next two bytes are both 0, skip them first.
            val peek1 = raf.readUnsignedByte()
            val peek2 = raf.readUnsignedByte()
            if (!(peek1 == 0 && peek2 == 0)) {
                // No null terminator: rewind 2 bytes so we're at the adler32 start
                raf.seek(raf.filePointer - 2)
            }
            // else: null bytes detected, file pointer is now correctly at adler32

            // Skip the 4-byte Adler32 checksum
            raf.skipBytes(4)

            Log.d(TAG, "  Header: encoding=${header.encoding}, version=${header.generatedByEngineVersion}, title=${header.title}")

            // Parse keyword index
            val keywords = parser.parseKeywordIndex(raf)

            Log.d(TAG, "  Keywords parsed: ${keywords.size}")

            // Insert into trie
            for (kw in keywords) {
                trieIndex.insert(
                    keyword = kw.keyword,
                    recordOffset = kw.recordOffset,
                    recordSize = kw.recordSize,
                    dictionaryId = dictFile.config.id
                )
            }
        }
    }

    /**
     * Look up a word's definition from a specific dictionary.
     * Returns the raw record bytes (usually HTML).
     */
    suspend fun lookupDefinition(dictId: Int, recordOffset: Long): ByteArray =
        withContext(Dispatchers.IO) {
            val dictFile = dictFiles.firstOrNull { it.config.id == dictId }
                ?: throw IllegalArgumentException("Dictionary not found: $dictId")

            RandomAccessFile(dictFile.mdxFile, "r").use { raf ->
                parser.readRecord(raf, recordOffset)
            }
        }

    /**
     * Get the local file path for MDD resource lookup (CSS, JS, images).
     */
    fun getMddPath(dictId: Int): String? {
        return dictFiles.firstOrNull { it.config.id == dictId }?.mddFile?.absolutePath
    }

    /**
     * Get the local directory for a dictionary (contains CSS, JS, PNG files alongside MDX/MDD).
     */
    fun getDictDir(dictId: Int): File? {
        return dictFiles.firstOrNull { it.config.id == dictId }?.mdxFile?.parentFile
    }
}
