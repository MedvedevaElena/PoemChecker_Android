package com.example.poemchecker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.poemchecker.data.local.PoemDatabase
import com.example.poemchecker.data.repository.RoomPoemRepository
import com.example.poemchecker.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPoemRepositoryTest {
    private lateinit var database: PoemDatabase
    private lateinit var repository: RoomPoemRepository
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, PoemDatabase::class.java).build()
        repository = RoomPoemRepository(database)
    }
    @After fun tearDown() { database.close() }

    @Test fun recordsProgressAndAttemptAndDeletesBoth() = runBlocking {
        val id = repository.savePoem(Poem(text = "первая строка\nвторая строка"))
        val progress = TrainingProgress(firstWordsPassed = true, history = listOf(90), bestScore = 90)
        repository.recordAttempt(Attempt(poemId = id, mode = "FIRST_WORDS", scorePercent = 90, createdAt = 1, recognizedPreview = "первая"), progress)
        assertEquals(progress, repository.getPoem(id)!!.progress)
        assertEquals(1, repository.observeAttempts(id).first().size)
        repository.deletePoem(id)
        assertNull(repository.getPoem(id))
        assertTrue(repository.observeAttempts(id).first().isEmpty())
    }

    @Test fun failedAttemptInsertRollsBackProgressUpdate() = runBlocking {
        val id = repository.savePoem(Poem(text = "стих"))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_attempt BEFORE INSERT ON attempts BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        var failed = false
        try {
            repository.recordAttempt(
                Attempt(poemId = id, mode = "NO_HINTS", scorePercent = 100, createdAt = 1, recognizedPreview = "стих"),
                TrainingProgress(finalPassed = true, bestScore = 100)
            )
        } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(0, repository.getPoem(id)!!.progress.bestScore)
        assertFalse(repository.getPoem(id)!!.progress.finalPassed)
        assertTrue(repository.observeAttempts(id).first().isEmpty())
    }

    @Test fun recordsOlderThan20RemainStoredButQueryShowsLatest20() = runBlocking {
        val id = repository.savePoem(Poem(text = "стих"))
        repeat(25) { index ->
            repository.recordAttempt(Attempt(poemId = id, mode = "NO_HINTS", scorePercent = index, createdAt = index.toLong(), recognizedPreview = ""), TrainingProgress())
        }
        val visible = repository.observeAttempts(id).first()
        assertEquals(20, visible.size)
        assertEquals(24, visible.first().scorePercent)
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM attempts").use { cursor ->
            cursor.moveToFirst()
            assertEquals(25, cursor.getInt(0))
        }
    }

    @Test fun savedDataSurvivesDatabaseReopening() = runBlocking {
        val name = "repository-reopen-test.db"
        context.deleteDatabase(name)
        try {
            val id: Long
            val disk = Room.databaseBuilder(context, PoemDatabase::class.java, name).build()
            try {
                id = RoomPoemRepository(disk).savePoem(Poem(text = "сохранённый стих"))
            } finally { disk.close() }
            val reopened = Room.databaseBuilder(context, PoemDatabase::class.java, name).build()
            try {
                assertEquals("сохранённый стих", RoomPoemRepository(reopened).getPoem(id)!!.text)
            } finally { reopened.close() }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun opensOriginalV1DatabaseWithoutLosingPoemsOrAttempts() = runBlocking {
        val name = "legacy-v1-test.db"
        context.deleteDatabase(name)
        try {
            val sql = InstrumentationRegistry.getInstrumentation().context.assets.open("legacy-v1.sql")
                .bufferedReader().use { reader -> reader.readLines().filterNot { it.startsWith("--") }.joinToString("\n") }
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacy ->
                sql.split(';').filter { it.isNotBlank() }.forEach { legacy.execSQL(it.trim()) }
            }
            val reopened = Room.databaseBuilder(context, PoemDatabase::class.java, name).build()
            try {
                val legacyRepository = RoomPoemRepository(reopened)
                val poem = legacyRepository.getPoem(7)!!
                assertEquals("Сохранённый текст", poem.text)
                assertTrue(poem.progress.firstWordsPassed)
                assertEquals(listOf(85), poem.progress.history)
                assertEquals(85, poem.progress.bestScore)
                assertEquals("FIRST_WORDS", legacyRepository.observeAttempts(7).first().single().mode)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
