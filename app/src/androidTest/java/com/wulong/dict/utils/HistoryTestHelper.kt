package com.wulong.dict.utils

import android.util.Log
import com.wulong.dict.data.local.AppDatabase
import com.wulong.dict.data.local.SearchHistoryDao
import com.wulong.dict.data.repository.HistoryRepositoryImpl
import com.wulong.dict.domain.usecase.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Debug helper that runs a complete CRUD smoke test on the search history table.
 *
 * Call this from Application.onCreate() or a debug menu:
 *   HistoryTestHelper.runTest(context)
 *
 * Then filter logcat:  adb logcat -s HistoryTest:D
 */
object HistoryTestHelper {

    private const val TAG = "HistoryTest"

    fun runTest(db: AppDatabase) {
        Log.d(TAG, "========== History CRUD Test Start ==========")

        runBlocking {
            try {
                val dao: SearchHistoryDao = db.searchHistoryDao()
                val repo = HistoryRepositoryImpl(dao)
                val save = SaveSearchWordUseCase(repo)
                val getAll = GetSearchHistoryUseCase(repo)
                val delete = DeleteHistoryItemUseCase(repo)
                val clear = ClearAllHistoryUseCase(repo)

                // 1. Clean slate
                clear()
                Log.d(TAG, "[1/5] Cleared all history — OK")

                // 2. Save 3 words
                save("serendipity")
                Thread.sleep(5)
                save("ephemeral")
                Thread.sleep(5)
                save("serendipity") // duplicate: should update timestamp
                Log.d(TAG, "[2/5] Saved 3 words (including duplicate) — OK")

                // 3. Read back
                val history = getAll().first()
                assert(history.size == 2) { "Expected 2 unique entries, got ${history.size}" }
                assert(history[0].searchWord == "serendipity") { "Most recent should be 'serendipity'" }
                assert(history[1].searchWord == "ephemeral") { "Second should be 'ephemeral'" }
                Log.d(TAG, "[3/5] Read back: ${history.size} entries, most recent='${history[0].searchWord}' — OK")

                // 4. Delete one
                delete(history[0].id)
                val afterDelete = getAll().first()
                assert(afterDelete.size == 1) { "Expected 1 entry after delete" }
                assert(afterDelete[0].searchWord == "ephemeral") { "Remaining should be 'ephemeral'" }
                Log.d(TAG, "[4/5] Deleted 'serendipity', remaining: '${afterDelete[0].searchWord}' — OK")

                // 5. Clear all
                clear()
                val afterClear = getAll().first()
                assert(afterClear.isEmpty()) { "Expected empty after clear" }
                Log.d(TAG, "[5/5] Cleared all — OK")

                Log.d(TAG, "========== History CRUD Test PASSED ==========")
            } catch (e: AssertionError) {
                Log.e(TAG, "ASSERTION FAILED: ${e.message}", e)
                Log.e(TAG, "========== History CRUD Test FAILED ==========")
            } catch (e: Exception) {
                Log.e(TAG, "Test crashed: ${e.message}", e)
                Log.e(TAG, "========== History CRUD Test FAILED ==========")
            }
        }
    }
}
