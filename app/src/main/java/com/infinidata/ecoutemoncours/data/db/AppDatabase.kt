package com.infinidata.ecoutemoncours.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DocumentEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ecoute_mon_cours.db"
            )
                // Les cours scannes sont la seule copie : on ne detruit jamais la base
                // en cas de mise a jour. Une vraie migration sera ecrite le moment venu.
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build().also { instance = it }
        }
    }
}
