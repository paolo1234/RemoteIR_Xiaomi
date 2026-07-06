package com.irxiaomi.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

@Database(
    entities = [
        IrCodeEntity::class,
        RemoteLayoutEntity::class,
        LearnedCodeEntity::class,
        BrandEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun irCodeDao(): IrCodeDao
    abstract fun brandDao(): BrandDao

    companion object {
        private const val DB_NAME = "irxiaomi_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /** Per test / reset */
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}
