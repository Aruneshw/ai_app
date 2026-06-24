package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.*

@Database(
    entities = [
        Event::class,
        RoutineBlock::class,
        SuggestionLog::class,
        UnplannedActivity::class,
        TeammateMilestone::class,
        UserSetting::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun routineBlockDao(): RoutineBlockDao
    abstract fun suggestionLogDao(): SuggestionLogDao
    abstract fun unplannedActivityDao(): UnplannedActivityDao
    abstract fun teammateMilestoneDao(): TeammateMilestoneDao
    abstract fun userSettingDao(): UserSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "planner_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
