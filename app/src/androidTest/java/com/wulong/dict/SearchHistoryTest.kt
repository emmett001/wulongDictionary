package com.wulong.dict

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wulong.dict.data.local.AppDatabase
import com.wulong.dict.data.local.SearchHistoryDao
import com.wulong.dict.data.local.SearchHistoryEntity
import com.wulong.dict.data.repository.HistoryRepositoryImpl
import com.wulong.dict.domain.repository.HistoryRepository
import com.wulong.dict.domain.usecase.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test: verifies CRUD for search history.
 *
 * Run via:  ./gradlew connectedAndroidTest
 * Or in Android Studio: right-click → Run 'SearchHistoryTest'
 */
@RunWith(AndroidJUnit4::class)
class SearchHistoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SearchHistoryDao
    private lateinit var repository: HistoryRepository

    private lateinit var saveUseCase: SaveSearchWordUseCase
    private lateinit var getHistoryUseCase: GetSearchHistoryUseCase
    private lateinit var deleteItemUseCase: DeleteHistoryItemUseCase
    private lateinit var clearAllUseCase: ClearAllHistoryUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.searchHistoryDao()
        repository = HistoryRepositoryImpl(dao)

        saveUseCase = SaveSearchWordUseCase(repository)
        getHistoryUseCase = GetSearchHistoryUseCase(repository)
        deleteItemUseCase = DeleteHistoryItemUseCase(repository)
        clearAllUseCase = ClearAllHistoryUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSaveAndRetrieveHistory() = runBlocking {
        // Save 3 words
        saveUseCase("hello")
        Thread.sleep(10) // ensure distinct timestamps
        saveUseCase("world")
        Thread.sleep(10)
        saveUseCase("hello") // duplicate: should update timestamp, not insert new row

        val history = getHistoryUseCase().first()

        // Should have 2 unique words, most recent first
        assertEquals(2, history.size)
        assertEquals("hello", history[0].searchWord) // "hello" was updated last
        assertEquals("world", history[1].searchWord)
    }

    @Test
    fun testDeleteSingleItem() = runBlocking {
        saveUseCase("test1")
        saveUseCase("test2")
        saveUseCase("test3")

        var history = getHistoryUseCase().first()
        assertEquals(3, history.size)

        deleteItemUseCase(history.first().id) // delete most recent = "test3"

        history = getHistoryUseCase().first()
        assertEquals(2, history.size)
        assertEquals("test2", history[0].searchWord)
        assertEquals("test1", history[1].searchWord)
    }

    @Test
    fun testClearAllHistory() = runBlocking {
        saveUseCase("apple")
        saveUseCase("banana")
        saveUseCase("cherry")

        var history = getHistoryUseCase().first()
        assertEquals(3, history.size)

        clearAllUseCase()

        history = getHistoryUseCase().first()
        assertTrue(history.isEmpty())
    }
}
