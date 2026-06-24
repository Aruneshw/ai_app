package com.example.data.repository

import com.example.data.db.*
import com.example.data.model.*
import com.example.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class PlannerRepository(private val db: AppDatabase) {

    private val eventDao = db.eventDao()
    private val routineBlockDao = db.routineBlockDao()
    private val suggestionLogDao = db.suggestionLogDao()
    private val unplannedActivityDao = db.unplannedActivityDao()
    private val teammateMilestoneDao = db.teammateMilestoneDao()
    private val userSettingDao = db.userSettingDao()

    // Events
    val allEvents: Flow<List<Event>> = eventDao.getAllEvents()
    suspend fun insertEvent(event: Event) = eventDao.insertEvent(event)
    suspend fun updateEvent(event: Event) = eventDao.updateEvent(event)
    suspend fun deleteEvent(event: Event) = eventDao.deleteEvent(event)
    suspend fun deleteEventById(id: Int) = eventDao.deleteEventById(id)

    // Routine Blocks
    val allRoutineBlocks: Flow<List<RoutineBlock>> = routineBlockDao.getAllRoutineBlocks()
    suspend fun insertRoutineBlock(block: RoutineBlock) = routineBlockDao.insertRoutineBlock(block)
    suspend fun insertRoutineBlocks(blocks: List<RoutineBlock>) = routineBlockDao.insertRoutineBlocks(blocks)
    suspend fun deleteRoutineBlock(block: RoutineBlock) = routineBlockDao.deleteRoutineBlock(block)
    suspend fun clearAllRoutineBlocks() = routineBlockDao.clearAll()

    // Suggestion Logs
    val allSuggestionLogs: Flow<List<SuggestionLog>> = suggestionLogDao.getAllSuggestionLogs()
    suspend fun insertSuggestionLog(log: SuggestionLog) = suggestionLogDao.insertSuggestionLog(log)
    suspend fun updateSuggestionStatus(id: Int, status: String) = suggestionLogDao.updateSuggestionStatus(id, status)

    // Unplanned Activities
    val allUnplannedActivities: Flow<List<UnplannedActivity>> = unplannedActivityDao.getAllUnplannedActivities()
    suspend fun insertUnplannedActivity(activity: UnplannedActivity) = unplannedActivityDao.insertUnplannedActivity(activity)
    suspend fun deleteUnplannedActivityById(id: Int) = unplannedActivityDao.deleteUnplannedActivityById(id)
    suspend fun clearUnplannedBuffer() = unplannedActivityDao.clearBuffer()

    // Teammate Milestones
    val allTeammateMilestones: Flow<List<TeammateMilestone>> = teammateMilestoneDao.getAllTeammateMilestones()
    suspend fun insertTeammateMilestone(milestone: TeammateMilestone) = teammateMilestoneDao.insertTeammateMilestone(milestone)
    suspend fun updateTeammateMilestone(milestone: TeammateMilestone) = teammateMilestoneDao.updateTeammateMilestone(milestone)
    suspend fun deleteTeammateMilestone(milestone: TeammateMilestone) = teammateMilestoneDao.deleteTeammateMilestone(milestone)

    // User Settings
    val userSettings: Flow<UserSetting?> = userSettingDao.getUserSettings()
    suspend fun saveUserSetting(setting: UserSetting) = userSettingDao.insertUserSetting(setting)

    // Trigger on-device deferred learning analysis
    suspend fun triggerDeferredLearning(): String {
        val currentSettings = userSettings.firstOrNull() ?: UserSetting()
        val buffer = unplannedActivityDao.getAllUnplannedActivities().firstOrNull() ?: emptyList()

        if (buffer.size < 3) {
            return "Analysis deferred. Buffer needs at least 3 unplanned activities to run learning models (currently has ${buffer.size})."
        }

        val result = DeferredLearning.analyzeBuffer(buffer, currentSettings)
        
        // 1. Save updated settings
        saveUserSetting(result.updatedSettings)

        // 2. Insert promoted routine blocks
        if (result.promotedRoutineBlocks.isNotEmpty()) {
            insertRoutineBlocks(result.promotedRoutineBlocks)
        }

        // 3. Clear buffer since we have committed the analysis
        clearUnplannedBuffer()

        return result.analysisLog
    }

    // JSON export and encryption
    suspend fun exportAndEncryptBackup(password: String): String {
        val events = eventDao.getAllEvents().firstOrNull() ?: emptyList()
        val settings = userSettings.firstOrNull() ?: UserSetting()
        val routines = routineBlockDao.getAllRoutineBlocks().firstOrNull() ?: emptyList()
        val teammates = teammateMilestoneDao.getAllTeammateMilestones().firstOrNull() ?: emptyList()

        // Create a simple custom JSON string serialization manually to avoid complex serializer setup
        val json = buildString {
            append("{")
            append("\"settings\":{\"weeklyCapacityHours\":${settings.weeklyCapacityHours},\"urgencyWeight\":${settings.urgencyWeight},\"importanceWeight\":${settings.importanceWeight},\"effortWeight\":${settings.effortWeight}},")
            append("\"events\":[")
            events.forEachIndexed { i, e ->
                append("{\"title\":\"${e.title}\",\"type\":\"${e.type}\",\"startTime\":${e.startTime},\"endTime\":${e.endTime},\"deadline\":${e.deadline ?: "null"},\"priorityWeight\":${e.priorityWeight},\"status\":\"${e.status}\",\"energyRequired\":${e.energyRequired}}")
                if (i < events.size - 1) append(",")
            }
            append("],")
            append("\"routines\":[")
            routines.forEachIndexed { i, r ->
                append("{\"title\":\"${r.title}\",\"dayOfWeek\":${r.dayOfWeek},\"startHour\":${r.startHour},\"startMinute\":${r.startMinute},\"endHour\":${r.endHour},\"endMinute\":${r.endMinute}}")
                if (i < routines.size - 1) append(",")
            }
            append("],")
            append("\"teammates\":[")
            teammates.forEachIndexed { i, t ->
                append("{\"teammateName\":\"${t.teammateName}\",\"taskTitle\":\"${t.taskTitle}\",\"deadline\":${t.deadline},\"status\":\"${t.status}\"}")
                if (i < teammates.size - 1) append(",")
            }
            append("]")
            append("}")
        }

        return BackupEncryptor.encrypt(json, password)
    }

    // Decrypt and import backup
    suspend fun decryptAndImportBackup(encryptedBase64: String, password: String): String {
        try {
            val decryptedJson = BackupEncryptor.decrypt(encryptedBase64, password)
            
            // Simple manual parser of the custom JSON format to avoid depending on moshi or kotlin-serialization configuration errors
            // Parse and save UserSetting
            val capHoursMatch = Regex("\"weeklyCapacityHours\":(\\d+)").find(decryptedJson)
            val capHours = capHoursMatch?.groupValues?.get(1)?.toInt() ?: 40
            
            val uWeightMatch = Regex("\"urgencyWeight\":([\\d.]+)").find(decryptedJson)
            val uWeight = uWeightMatch?.groupValues?.get(1)?.toFloat() ?: 0.5f
            
            val iWeightMatch = Regex("\"importanceWeight\":([\\d.]+)").find(decryptedJson)
            val iWeight = iWeightMatch?.groupValues?.get(1)?.toFloat() ?: 0.3f
            
            val eWeightMatch = Regex("\"effortWeight\":([\\d.]+)").find(decryptedJson)
            val eWeight = eWeightMatch?.groupValues?.get(1)?.toFloat() ?: 0.2f

            saveUserSetting(UserSetting(
                weeklyCapacityHours = capHours,
                urgencyWeight = uWeight,
                importanceWeight = iWeight,
                effortWeight = eWeight
            ))

            // Parse Events
            val eventMatches = Regex("\\{\"title\":\"(.*?)\",\"type\":\"(.*?)\",\"startTime\":(\\d+),\"endTime\":(\\d+),\"deadline\":(null|\\d+),\"priorityWeight\":([\\d.]+),\"status\":\"(.*?)\",\"energyRequired\":(\\d+)\\}").findAll(decryptedJson)
            for (match in eventMatches) {
                val values = match.groupValues
                val deadlineVal = if (values[5] == "null") null else values[5].toLong()
                insertEvent(Event(
                    title = values[1],
                    type = values[2],
                    startTime = values[3].toLong(),
                    endTime = values[4].toLong(),
                    deadline = deadlineVal,
                    priorityWeight = values[6].toFloat(),
                    status = values[7],
                    energyRequired = values[8].toInt()
                ))
            }

            // Parse Routines
            val routineMatches = Regex("\\{\"title\":\"(.*?)\",\"dayOfWeek\":(\\d+),\"startHour\":(\\d+),\"startMinute\":(\\d+),\"endHour\":(\\d+),\"endMinute\":(\\d+)\\}").findAll(decryptedJson)
            for (match in routineMatches) {
                val values = match.groupValues
                insertRoutineBlock(RoutineBlock(
                    title = values[1],
                    dayOfWeek = values[2].toInt(),
                    startHour = values[3].toInt(),
                    startMinute = values[4].toInt(),
                    endHour = values[5].toInt(),
                    endMinute = values[6].toInt()
                ))
            }

            return "Backup imported successfully! Loaded: ${eventMatches.count()} events and ${routineMatches.count()} routine blocks."
        } catch (e: Exception) {
            return "Decryption failed: ${e.localizedMessage}"
        }
    }
}
