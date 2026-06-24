package com.example.domain

import com.example.data.model.RoutineBlock
import com.example.data.model.UnplannedActivity
import com.example.data.model.UserSetting
import java.util.Calendar

data class LearningResult(
    val promotedRoutineBlocks: List<RoutineBlock>,
    val noiseActivities: List<UnplannedActivity>,
    val detectedStress: Boolean,
    val updatedSettings: UserSetting,
    val analysisLog: String
)

object DeferredLearning {

    // Threshold for promoting an activity to a recurring routine block (e.g. 3 times at same hour & day-of-week)
    private const val PATTERN_THRESHOLD = 3
    private const val LEARNING_RATE = 0.05f

    fun analyzeBuffer(
        buffer: List<UnplannedActivity>,
        currentSettings: UserSetting
    ): LearningResult {
        if (buffer.isEmpty()) {
            return LearningResult(
                promotedRoutineBlocks = emptyList(),
                noiseActivities = emptyList(),
                detectedStress = false,
                updatedSettings = currentSettings,
                analysisLog = "Buffer is empty. No analysis performed."
            )
        }

        val promotedBlocks = mutableListOf<RoutineBlock>()
        val noiseList = mutableListOf<UnplannedActivity>()
        val logBuilder = StringBuilder()
        logBuilder.append("Starting offline deferred learning analysis on ${buffer.size} logs...\n")

        // 1. Group by category, day-of-week, and approximate time-of-day (hour) to find recurring patterns
        // To find the day of week, we can parse the timestamp of the UnplannedActivity.
        val calendar = Calendar.getInstance()
        val grouped = buffer.groupBy { activity ->
            calendar.timeInMillis = activity.timestamp
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            // Normalize hour to nearest 2-hour window to catch slight timing variations
            val normalizedHour = (activity.timeOfDayHour / 2) * 2
            Triple(activity.title.lowercase().trim(), dayOfWeek, normalizedHour)
        }

        logBuilder.append("Grouping logs to identify recurring patterns...\n")
        for ((triple, activities) in grouped) {
            val (title, dayOfWeek, hour) = triple
            val count = activities.size

            if (count >= PATTERN_THRESHOLD) {
                // Detected a recurring pattern! Let's promote to routine block.
                val displayName = title.replaceFirstChar { it.uppercase() }
                val newBlock = RoutineBlock(
                    title = "$displayName (Auto-learned)",
                    dayOfWeek = dayOfWeek,
                    startHour = hour,
                    startMinute = 0,
                    endHour = (hour + 1) % 24,
                    endMinute = 0
                )
                promotedBlocks.add(newBlock)
                logBuilder.append("✨ PATTERN CONFIRMED: '$displayName' occurred $count times on Day $dayOfWeek around $hour:00. Promoted to Routine!\n")
            } else {
                // Not enough occurrences yet -> keep in noise / history
                noiseList.addAll(activities)
            }
        }

        // 2. Stress/Overload Signal Check:
        // If > 40% of the activities in the buffer are unplanned "rest" or "social" when they should be working,
        // or if average energy is extremely low, flag as stress/overload.
        val restSocialCount = buffer.count { it.category.lowercase() in listOf("rest", "social") }
        val restRatio = restSocialCount.toFloat() / buffer.size
        val avgEnergy = buffer.map { it.energyLevel }.average()

        val detectedStress = restRatio > 0.4f || (avgEnergy < 2.0 && buffer.size >= 3)
        if (detectedStress) {
            logBuilder.append("⚠️ STRESS SIGNAL DETECTED: High proportion of unplanned rest/social (${"%.0f".format(restRatio * 100)}%) or low average energy (${"%.1f".format(avgEnergy)}).\n")
        } else {
            logBuilder.append("✅ Energy levels and task balance appear stable.\n")
        }

        // 3. Confidence-Gated Weight Adjustments (Ramped updates):
        // If stress is detected, we want to decrease urgencyWeight/importanceWeight and increase effortWeight (to prioritize rest and lighter loads).
        // If energy levels are high, we can tune weights to prioritize importance (hackathon/exams).
        var newUrgency = currentSettings.urgencyWeight
        var newImportance = currentSettings.importanceWeight
        var newEffort = currentSettings.effortWeight

        if (detectedStress) {
            // Apply learning rate ramped adjustments
            newUrgency = maxOf(0.1f, newUrgency - LEARNING_RATE)
            newImportance = maxOf(0.1f, newImportance - LEARNING_RATE)
            newEffort = minOf(0.8f, newEffort + LEARNING_RATE)
            logBuilder.append("⚡ Learning Update: Ramped focus to Effort weight (+${LEARNING_RATE}) to prioritize pacing & recovery.\n")
        } else if (avgEnergy > 4.0 && buffer.size >= PATTERN_THRESHOLD) {
            // If user has high energy, boost importance to hit startup/hackathons harder
            newUrgency = minOf(0.8f, newUrgency + LEARNING_RATE)
            newImportance = minOf(0.8f, newImportance + LEARNING_RATE)
            newEffort = maxOf(0.1f, newEffort - LEARNING_RATE)
            logBuilder.append("🚀 Learning Update: High energy detected. Ramped Importance weight (+${LEARNING_RATE}) to prioritize high-value achievements.\n")
        }

        // Normalize weights to sum to 1.0f
        val sum = newUrgency + newImportance + newEffort
        newUrgency /= sum
        newImportance /= sum
        newEffort /= sum

        val updatedSettings = currentSettings.copy(
            urgencyWeight = newUrgency,
            importanceWeight = newImportance,
            effortWeight = newEffort,
            stressIndicator = detectedStress
        )

        logBuilder.append("New Normalized weights -> Urgency: ${"%.2f".format(newUrgency)}, Importance: ${"%.2f".format(newImportance)}, Effort: ${"%.2f".format(newEffort)}\n")
        logBuilder.append("Deferred learning analysis complete.")

        return LearningResult(
            promotedRoutineBlocks = promotedBlocks,
            noiseActivities = noiseList,
            detectedStress = detectedStress,
            updatedSettings = updatedSettings,
            analysisLog = logBuilder.toString()
        )
    }
}
