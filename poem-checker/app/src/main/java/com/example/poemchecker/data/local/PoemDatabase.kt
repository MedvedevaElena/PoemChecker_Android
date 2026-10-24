package com.example.poemchecker.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavedPoem::class, AttemptEntity::class], version = 1, exportSchema = true)
abstract class PoemDatabase : RoomDatabase() {
    abstract fun poemDao(): PoemDao
    abstract fun attemptDao(): AttemptDao

    companion object {
        @Volatile private var instance: PoemDatabase? = null

        fun getInstance(application: Application): PoemDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(application, PoemDatabase::class.java, "poem_checker_v9.db").build().also { instance = it }
            }
        }
    }
}

