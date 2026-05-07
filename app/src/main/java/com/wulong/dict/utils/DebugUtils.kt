package com.wulong.dict.utils

import android.util.Log
import java.io.RandomAccessFile

/**
 * Debug utilities for validating MDX parsing at development time.
 * Call these from a debug-only codepath or via logcat filtering on "MdxDebug".
 */
object DebugUtils {

    private const val TAG = "MdxDebug"

    /** Dump the first N bytes of an MDX file as hex for format analysis. */
    fun dumpHexHeader(filePath: String, bytes: Int = 512) {
        try {
            RandomAccessFile(filePath, "r").use { raf ->
                val data = ByteArray(bytes.coerceAtMost(raf.length().toInt()))
                raf.readFully(data)
                val text = String(data, 0, data.size.coerceAtMost(1024), Charsets.UTF_8)
                Log.d(TAG, "=== Header text preview (first ${data.size}B) ===")
                Log.d(TAG, text.take(1024))
                Log.d(TAG, "=== End preview ===")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file: $filePath", e)
        }
    }

    /** Dump keyword statistics after parsing. */
    fun logKeywordSample(keywords: List<String>, sampleSize: Int = 10) {
        Log.d(TAG, "Total keywords: ${keywords.size}")
        Log.d(TAG, "First $sampleSize keywords:")
        keywords.take(sampleSize).forEachIndexed { i, kw ->
            Log.d(TAG, "  [$i] $kw")
        }
        Log.d(TAG, "Last $sampleSize keywords:")
        keywords.takeLast(sampleSize).forEachIndexed { i, kw ->
            Log.d(TAG, "  [${keywords.size - sampleSize + i}] $kw")
        }
    }
}
