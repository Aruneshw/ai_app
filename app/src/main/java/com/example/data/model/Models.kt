package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String, // "exam", "hackathon", "college", "personal", "other"
    val startTime: Long, // timestamp
    val endTime: Long, // timestamp
    val deadline: Long?, // deadline timestamp if any
    val priorityWeight: Float = 1.0f,
    val status: String = "active", // "active", "completed", "ignored"
    val energyRequired: Int = 3, // 1 (low) to 5 (high)
    val url: String? = null,
    val teammates: String? = null,
    val notes: String? = null
)

@Entity(tableName = "routine_blocks")
data class RoutineBlock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val dayOfWeek: Int, // 1 = Monday, 7 = Sunday (Calendar.MONDAY etc.)
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)

@Entity(tableName = "suggestion_logs")
data class SuggestionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val suggestedTaskTitle: String,
    val reasoning: String,
    val scoreBreakdown: String, // e.g. "urgency: 0.7, importance: 0.8"
    val status: String = "pending" // "pending", "accepted", "ignored", "rescheduled"
)

@Entity(tableName = "unplanned_activities")
data class UnplannedActivity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val durationMinutes: Int,
    val category: String, // "rest", "social", "study", "work", "college", "other"
    val timestamp: Long,
    val timeOfDayHour: Int,
    val energyLevel: Int // 1 to 5
)

@Entity(tableName = "teammate_milestones")
data class TeammateMilestone(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val teammateName: String,
    val taskTitle: String,
    val deadline: Long,
    val status: String = "pending" // "pending", "completed"
)

@Entity(tableName = "user_settings")
data class UserSetting(
    @PrimaryKey val id: Int = 1, // Single row, always id = 1
    val weeklyCapacityHours: Int = 40,
    val notificationEnabled: Boolean = true,
    val energyTrackingEnabled: Boolean = true,
    val urgencyWeight: Float = 0.5f,
    val importanceWeight: Float = 0.3f,
    val effortWeight: Float = 0.2f,
    val stressIndicator: Boolean = false // If true, recommends lighter loads
)

data class ManualMilestone(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val dueDate: Long,
    val durationHrs: Float = 2.0f,
    val priorityWeight: Float = 1.0f,
    val energyRequired: Int = 3
)

data class AlignmentReport(
    val status: String, // "Good", "Balanced", "Conflict", "Overload", "Optimal"
    val score: Int, // 0 to 100
    val totalHoursAnalyzed: Double,
    val issuesFoundCount: Int,
    val conflictsList: List<String> = emptyList(),
    val dynamicRecommendations: List<String> = emptyList(),
    val modelSignature: String = ""
)

