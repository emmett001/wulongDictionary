package com.wulong.dict.data.local

import android.util.Log
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.Adler32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Pure-Kotlin MDX binary format parser.
 *
 * MDX file layout (modern v2, GeneratedByEngineVersion >= 2.0):
 *   [Header: key="val"\r\n pairs, ending with \r\n\r\n]
 *   [Adler32 checksum of header: 4 bytes]
 *   [num_blocks: 8 bytes LE]
 *   [num_entries: 8 bytes LE]
 *   [Keyword blocks (zlib-compressed)]  → each entry: [varint id][null-term keyword]
 *   [Record offset table]               → per entry:  [8 bytes LE offset][4 bytes LE size]
 *   [Record data blocks]                → at each offset: [4B comp_size][4B decomp_size][data]
 */
class MdxParser(private val tag: String = "MdxParser") {

    data class KeywordIndex(
        val keyword: String,
        val recordOffset: Long,
        val recordSize: Int
    )

    /**
     * Parse the MDX header from a RandomAccessFile. Returns header metadata and
     * positions the file pointer at the start of the keyword index section.
     */
    fun parseHeader(raf: RandomAccessFile): MdxHeader {
        // Read all bytes until we find the end-of-header marker
        val headerBytes = mutableListOf<Byte>()
        var prevByte: Byte = 0
        var prevPrevByte: Byte = 0

        while (true) {
            val b = raf.readByte()
            headerBytes.add(b)
            // Header ends with \n\n or \r\n\r\n — detect two consecutive newlines
            if (b == '\n'.code.toByte() && prevByte == '\n'.code.toByte()) break
            if (b == '\n'.code.toByte() && prevByte == '\r'.code.toByte()
                && prevPrevByte == '\n'.code.toByte()
            ) break
            prevPrevByte = prevByte
            prevByte = b
        }

        val headerText = String(headerBytes.toByteArray(), Charsets.UTF_8)
        return parseHeaderText(headerText)
    }

    private fun parseHeaderText(text: String): MdxHeader {
        val map = mutableMapOf<String, String>()
        text.lines().forEach { line ->
            val eqIdx = line.indexOf('=')
            if (eqIdx > 0) {
                var key = line.substring(0, eqIdx).trim()
                var value = line.substring(eqIdx + 1).trim()
                // Strip quotes
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length - 1)
                }
                // Normalize key (some headers use different casing)
                key = key.lowercase().replace("_", "")
                map[key] = value
            }
        }

        return MdxHeader(
            encoding = map["encoding"] ?: "UTF-8",
            title = map["title"] ?: "",
            description = map["description"] ?: "",
            generatedByEngineVersion = map["generatedbyengineversion"] ?: "2.0",
            keyCaseSensitive = map["keycasesensitive"]?.equals("Yes", ignoreCase = true) ?: false,
            encrypted = map["encrypted"]?.equals("Yes", ignoreCase = true) ?: false,
            compact = map["compact"]?.equals("Yes", ignoreCase = true) ?: false,
            stripKey = map["stripkey"]?.equals("Yes", ignoreCase = true) ?: false,
        )
    }

    /**
     * Verify Adler32 checksum and skip the 4-byte checksum field.
     * The checksum covers the header text up to (but not including) the trailing newline sequences.
     */
    fun verifyAdler32(raf: RandomAccessFile, headerLength: Int): Boolean {
        raf.seek(0)
        val headerBytes = ByteArray(headerLength - 2) // Exclude trailing \n\n
        raf.readFully(headerBytes)

        val expected = Adler32().apply { update(headerBytes) }.value

        // Read the stored checksum (4 bytes, big-endian? or little-endian?)
        // Different implementations use different endianness, try big-endian first
        val storedBE = ((raf.readUnsignedByte().toLong() shl 24)
                or (raf.readUnsignedByte().toLong() shl 16)
                or (raf.readUnsignedByte().toLong() shl 8)
                or raf.readUnsignedByte().toLong())

        return expected == storedBE
    }

    /**
     * Parse the keyword index section.
     * Returns a list of KeywordIndex entries sorted by keyword.
     */
    fun parseKeywordIndex(raf: RandomAccessFile): List<KeywordIndex> {
        // Read num_blocks (8 bytes, little-endian)
        val numBlocks = readInt64LE(raf)
        // Read num_entries (8 bytes, little-endian)
        val numEntries = readInt64LE(raf)

        Log.d(tag, "Keyword index: $numBlocks blocks, $numEntries entries")

        // Phase 1: Collect all keywords with their sequential IDs from keyword blocks
        data class RawEntry(val id: Long, val keyword: String)

        val allEntries = mutableListOf<RawEntry>()
        var expectedId = 0L

        for (blockIdx in 0 until numBlocks) {
            val decompSize = readVarint(raf)
            val compSize = readVarint(raf)

            val blockData = ByteArray(compSize.toInt())
            raf.readFully(blockData)

            val decompressed = decompressZlib(blockData, decompSize.toInt())

            val buf = ByteBuffer.wrap(decompressed)
            while (buf.hasRemaining()) {
                val keyId = readVarint(buf)
                val keyword = readNullTerminatedString(buf, Charsets.UTF_8)

                if (keyword.isNotEmpty()) {
                    if (keyId != expectedId && expectedId == 0L) {
                        // keyId might be record offsets (v1 format), handle below
                    }
                    allEntries.add(RawEntry(keyId, keyword))
                    expectedId = keyId + 1
                }
            }
        }

        Log.d(tag, "Parsed ${allEntries.size} keywords from blocks")

        // Phase 2: Read record offset table (after all keyword blocks)
        // Format: for each entry in order: [8 bytes LE: record_offset][4 bytes LE: record_size]
        val recordTable = mutableListOf<Pair<Long, Int>>()
        for (i in 0 until numEntries) {
            val offset = readInt64LE(raf)
            val size = readInt32LE(raf)
            recordTable.add(offset to size)
        }

        Log.d(tag, "Read ${recordTable.size} record offsets")

        // Phase 3: Determine if format is v1 (key_id = offset) or v2 (key_id = index into record table)
        val result = if (recordTable.isNotEmpty() && allEntries.isNotEmpty()) {
            val firstKeyId = allEntries.first().id
            val firstRecordOffset = recordTable.first().first

            // If first key_id looks like a file offset (large number, > num_entries),
            // it's likely v1 format where key_id IS the record offset
            if (firstKeyId > numEntries * 2 || firstKeyId > 100000) {
                // v1 format: key_id IS the record offset
                Log.d(tag, "Detected v1 format (key_id = record offset)")
                allEntries.map { entry ->
                    // Find corresponding record_size (next entry's offset minus this offset, or 64KB default)
                    val size = 65536 // fallback
                    KeywordIndex(entry.keyword, entry.id, size)
                }
            } else {
                // v2 format: key_id indexes into record table
                Log.d(tag, "Detected v2 format (key_id = table index)")
                allEntries.mapNotNull { entry ->
                    val idx = entry.id.toInt()
                    if (idx in recordTable.indices) {
                        val (offset, size) = recordTable[idx]
                        KeywordIndex(entry.keyword, offset, size)
                    } else {
                        null
                    }
                }
            }
        } else {
            emptyList()
        }

        return result.sortedBy { it.keyword.lowercase() }
    }

    /**
     * Read and decompress a definition record at the given file offset.
     */
    fun readRecord(raf: RandomAccessFile, offset: Long): ByteArray {
        raf.seek(offset)

        // Record header: [4 bytes BE: compressed_size][4 bytes BE: decompressed_size][data]
        val compSize = readInt32BE(raf)
        val decompSize = readInt32BE(raf)

        // If compSize == 0, data is uncompressed and size is decompSize
        val actualDataSize = if (compSize > 0) compSize else decompSize
        val data = ByteArray(actualDataSize)
        raf.readFully(data)

        return if (compSize > 0 && compSize != decompSize) {
            decompressZlib(data, decompSize)
        } else {
            data
        }
    }

    // ─── Utility read methods ───────────────────────────────────────────

    private fun readInt64LE(raf: RandomAccessFile): Long {
        val buf = ByteArray(8)
        raf.readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readInt32LE(raf: RandomAccessFile): Int {
        val buf = ByteArray(4)
        raf.readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readInt32BE(raf: RandomAccessFile): Int {
        val buf = ByteArray(4)
        raf.readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun readVarint(raf: RandomAccessFile): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = raf.readUnsignedByte()
            result = result or ((b.toLong() and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result
    }

    private fun readVarint(buf: ByteBuffer): Long {
        var result = 0L
        var shift = 0
        while (buf.hasRemaining()) {
            val b = buf.get().toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result
    }

    private fun readNullTerminatedString(buf: ByteBuffer, charset: Charset): String {
        val bytes = mutableListOf<Byte>()
        while (buf.hasRemaining()) {
            val b = buf.get()
            if (b == 0.toByte()) break
            bytes.add(b)
        }
        return String(bytes.toByteArray(), charset)
    }

    private fun decompressZlib(data: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(data)
            val output = ByteArray(expectedSize.coerceAtLeast(data.size * 2))
            val actualSize = inflater.inflate(output)
            inflater.end()
            if (actualSize == expectedSize) {
                output.copyOf(actualSize)
            } else {
                // Size mismatch, try alternative decompression
                tryDecompressRaw(data, expectedSize)
            }
        } catch (e: DataFormatException) {
            inflater.end()
            tryDecompressRaw(data, expectedSize)
        }
    }

    private fun tryDecompressRaw(data: ByteArray, expectedSize: Int): ByteArray {
        // Try without zlib header (raw deflate)
        val inflater = Inflater(false)
        return try {
            inflater.setInput(data)
            val output = ByteArray(expectedSize.coerceAtLeast(data.size * 2))
            val actualSize = inflater.inflate(output)
            inflater.end()
            output.copyOf(actualSize)
        } catch (e: DataFormatException) {
            inflater.end()
            // Return raw data as fallback
            data
        }
    }
}
