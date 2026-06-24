package com.example.data.db

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startTime ASC")
    fun getAllEvents(): Flow<List<Event>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event)

    @Update
    suspend fun updateEvent(event: Event)

    @Delete
    suspend fun deleteEvent(event: Event)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEventById(id: Int)
}

@Dao
interface RoutineBlockDao {
    @Query("SELECT * FROM routine_blocks ORDER BY dayOfWeek ASC, startHour ASC, startMinute ASC")
    fun getAllRoutineBlocks(): Flow<List<RoutineBlock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineBlock(block: RoutineBlock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineBlocks(blocks: List<RoutineBlock>)

    @Delete
    suspend fun deleteRoutineBlock(block: RoutineBlock)

    @Query("DELETE FROM routine_blocks")
    suspend fun clearAll()
}

@Dao
interface SuggestionLogDao {
    @Query("SELECT * FROM suggestion_logs ORDER BY timestamp DESC")
    fun getAllSuggestionLogs(): Flow<List<SuggestionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestionLog(log: SuggestionLog)

    @Query("UPDATE suggestion_logs SET status = :status WHERE id = :id")
    suspend fun updateSuggestionStatus(id: Int, status: String)
}

@Dao
interface UnplannedActivityDao {
    @Query("SELECT * FROM unplanned_activities ORDER BY timestamp DESC")
    fun getAllUnplannedActivities(): Flow<List<UnplannedActivity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnplannedActivity(activity: UnplannedActivity)

    @Query("DELETE FROM unplanned_activities WHERE id = :id")
    suspend fun deleteUnplannedActivityById(id: Int)

    @Query("DELETE FROM unplanned_activities")
    suspend fun clearBuffer()
}

@Dao
interface TeammateMilestoneDao {
    @Query("SELECT * FROM teammate_milestones ORDER BY deadline ASC")
    fun getAllTeammateMilestones(): Flow<List<TeammateMilestone>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeammateMilestone(milestone: TeammateMilestone)

    @Update
    suspend fun updateTeammateMilestone(milestone: TeammateMilestone)

    @Delete
    suspend fun deleteTeammateMilestone(milestone: TeammateMilestone)
}

@Dao
interface UserSettingDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getUserSettings(): Flow<UserSetting?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserSetting(setting: UserSetting)
}
