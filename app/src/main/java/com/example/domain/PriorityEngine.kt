package com.example.domain

import com.example.data.model.Event
import com.example.data.model.UnplannedActivity
import com.example.data.model.UserSetting
import kotlin.math.max

data class FreeSlot(
    val startTime: Long,
    val endTime: Long
) {
    val durationMinutes: Long
        get() = (endTime - startTime) / (1000 * 60)
}

data class Suggestion(
    val event: Event,
    val score: Float,
    val reasoning: String,
    val scoreBreakdown: String,
    val recommendedSlot: FreeSlot? = null
)

object PriorityEngine {

    // Importance weights by event type
    private fun getImportanceScore(type: String): Float {
        return when (type.lowercase()) {
            "exam" -> 1.0f
            "hackathon" -> 0.8f
            "college" -> 0.6f
            "personal" -> 0.4f
            else -> 0.2f
        }
    }

    // Urgency calculation: 1.0 / (days remaining)
    fun getUrgencyScore(deadline: Long?, currentTime: Long): Float {
        if (deadline == null) return 0.1f
        val diffMillis = deadline - currentTime
        if (diffMillis <= 0) return 1.0f // Overdue
        val diffDays = diffMillis.toFloat() / (1000 * 60 * 60 * 24)
        return if (diffDays < 1.0f) {
            1.0f // Less than a day remaining -> max urgency
        } else {
            1.0f / diffDays
        }
    }

    // Effort score: mapped 1-5 to 0.2-1.0
    private fun getEffortScore(energyRequired: Int): Float {
        return max(1, energyRequired).toFloat() / 5.0f
    }

    // The priority formula: priority = w1 * urgency + w2 * importance + w3 * effort
    fun calculatePriorityScore(
        event: Event,
        settings: UserSetting,
        currentTime: Long,
        slotHour: Int? = null,
        historicalEnergyByHour: Map<Int, Float> = emptyMap()
    ): Pair<Float, String> {
        val urgency = getUrgencyScore(event.deadline, currentTime)
        val importance = getImportanceScore(event.type)
        val effort = getEffortScore(event.energyRequired)

        var score = (settings.urgencyWeight * urgency) +
                (settings.importanceWeight * importance) +
                (settings.effortWeight * effort)

        // Adjust score based on stress level (recommends lighter loads)
        if (settings.stressIndicator && event.energyRequired > 3) {
            score *= 0.7f // Penalize heavy tasks when stressed
        }

        // Energy-aware slotting boost
        var energyBoostApplied = false
        if (slotHour != null && historicalEnergyByHour.isNotEmpty()) {
            val avgEnergy = historicalEnergyByHour[slotHour] ?: 3.0f
            val isDeepFocusTask = event.energyRequired >= 4
            val isLightTask = event.energyRequired <= 2

            if (avgEnergy >= 3.8f && isDeepFocusTask) {
                score += 0.2f // Boost high-energy tasks in high-energy hours
                energyBoostApplied = true
            } else if (avgEnergy < 2.5f && isLightTask) {
                score += 0.15f // Boost low-energy tasks in low-energy hours
                energyBoostApplied = true
            }
        }

        val breakdown = "urgency: ${"%.2f".format(urgency)}, importance: ${"%.2f".format(importance)}, effort: ${"%.2f".format(effort)}" +
                (if (energyBoostApplied) ", energy_boost: +0.2" else "") +
                (if (settings.stressIndicator) ", stress_penalty: -30%" else "")

        val reasoning = buildString {
            if (event.type.equals("exam", ignoreCase = true)) {
                append("Critical Exam task. ")
            } else if (event.type.equals("hackathon", ignoreCase = true)) {
                append("Hackathon milestone. ")
            }
            if (event.deadline != null && event.deadline > currentTime) {
                val days = (event.deadline - currentTime) / (1000 * 60 * 60 * 24)
                append("Deadline in $days days. ")
            }
            if (energyBoostApplied) {
                append("Matches your hourly energy pattern. ")
            }
            if (settings.stressIndicator && event.energyRequired > 3) {
                append("De-prioritized high-energy task due to recent stress signals.")
            }
        }

        return Pair(score * event.priorityWeight, reasoning.trim())
    }

    // Earliest Deadline First (EDF) scheduler for a given slot
    fun getSuggestionsForFreeSlots(
        events: List<Event>,
        settings: UserSetting,
        freeSlots: List<FreeSlot>,
        currentTime: Long,
        unplannedLogs: List<UnplannedActivity> = emptyList()
    ): List<Suggestion> {
        val activeEvents = events.filter { it.status == "active" }
        if (activeEvents.isEmpty() || freeSlots.isEmpty()) return emptyList()

        // Calculate historical hourly energy average
        val historicalEnergyByHour = unplannedLogs
            .groupBy { it.timeOfDayHour }
            .mapValues { (_, logs) -> logs.map { it.energyLevel }.average().toFloat() }

        val suggestions = mutableListOf<Suggestion>()

        for (slot in freeSlots) {
            // Find a slot hour (convert slot start time to local hour of day)
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = slot.startTime }
            val slotHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)

            val bestEventForSlot = activeEvents
                .map { event ->
                    val (score, reasoning) = calculatePriorityScore(
                        event = event,
                        settings = settings,
                        currentTime = currentTime,
                        slotHour = slotHour,
                        historicalEnergyByHour = historicalEnergyByHour
                    )
                    Suggestion(
                        event = event,
                        score = score,
                        reasoning = reasoning,
                        scoreBreakdown = "score: ${"%.2f".format(score)} ($reasoning)",
                        recommendedSlot = slot
                    )
                }
                // Order primarily by priority score descending, and within that, by Earliest Deadline First (EDF)
                .sortedWith(compareByDescending<Suggestion> { it.score }
                    .thenBy { it.event.deadline ?: Long.MAX_VALUE })
                .firstOrNull()

            if (bestEventForSlot != null && !suggestions.any { it.event.id == bestEventForSlot.event.id }) {
                suggestions.add(bestEventForSlot)
            }
        }

        return suggestions.sortedByDescending { it.score }
    }

    // Deadline risk flag: simple burn-rate projection
    fun checkDeadlineRisk(event: Event, currentTime: Long, estimatedHoursRemaining: Float): Boolean {
        if (event.status != "active" || event.deadline == null) return false
        val timeRemainingMillis = event.deadline - currentTime
        if (timeRemainingMillis <= 0) return true // Already past deadline and active -> high risk!

        val hoursRemaining = timeRemainingMillis.toFloat() / (1000 * 60 * 60)
        // If remaining hours in calendar are less than required effort hours, it's at risk!
        return hoursRemaining < estimatedHoursRemaining
    }

    // Commitment overload detector
    fun checkCommitmentOverload(
        events: List<Event>,
        settings: UserSetting,
        upcomingWeekStart: Long
    ): Pair<Boolean, Float> {
        val oneWeekMillis = 7 * 24 * 60 * 60 * 1000L
        val upcomingWeekEnd = upcomingWeekStart + oneWeekMillis

        // Sum up total hours of active events scheduled in the upcoming week
        val totalMillis = events
            .filter { it.status == "active" && it.startTime >= upcomingWeekStart && it.startTime < upcomingWeekEnd }
            .sumOf { it.endTime - it.startTime }

        val totalHours = totalMillis.toFloat() / (1000 * 60 * 60)
        val overloaded = totalHours > settings.weeklyCapacityHours

        return Pair(overloaded, totalHours)
    }
}
