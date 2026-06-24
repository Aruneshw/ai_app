package com.example.domain

import com.example.data.model.RoutineBlock
import java.util.Calendar

object TimetableParser {

    private val dayKeywords = mapOf(
        "monday" to Calendar.MONDAY,
        "mon" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "tue" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "wed" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "thu" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "fri" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY,
        "sat" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY,
        "sun" to Calendar.SUNDAY
    )

    // Regex to match hour and minute, optional AM/PM, optionally with a second time range
    // E.g., "09:00 - 10:00" or "9:30 AM"
    private val timeRegex = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM|am|pm)?""")

    fun parseOCRText(text: String): List<RoutineBlock> {
        val blocks = mutableListOf<RoutineBlock>()
        val lines = text.split("\n")
        var currentDayOfWeek = Calendar.MONDAY // default, carries over

        for (line in lines) {
            val lowerLine = line.lowercase().trim()
            if (lowerLine.isEmpty()) continue

            // 1. Identify Day of Week
            for ((key, value) in dayKeywords) {
                if (lowerLine.contains(key)) {
                    currentDayOfWeek = value
                    break
                }
            }

            // 2. Extract Times
            val timeMatches = timeRegex.findAll(line).toList()
            if (timeMatches.isEmpty()) continue

            // Parse start time
            val startMatch = timeMatches[0]
            var startHour = startMatch.groupValues[1].toInt()
            val startMinute = startMatch.groupValues[2].toInt()
            val startAmPm = startMatch.groupValues[3]

            if (startAmPm.equals("pm", ignoreCase = true) && startHour < 12) {
                startHour += 12
            } else if (startAmPm.equals("am", ignoreCase = true) && startHour == 12) {
                startHour = 0
            }

            // Default end time to 1 hour later
            var endHour = (startHour + 1) % 24
            var endMinute = startMinute

            // If a second time match is found in the same line, use it as end time
            if (timeMatches.size > 1) {
                val endMatch = timeMatches[1]
                endHour = endMatch.groupValues[1].toInt()
                endMinute = endMatch.groupValues[2].toInt()
                val endAmPm = endMatch.groupValues[3]

                if (endAmPm.equals("pm", ignoreCase = true) && endHour < 12) {
                    endHour += 12
                } else if (endAmPm.equals("am", ignoreCase = true) && endHour == 12) {
                    endHour = 0
                }
            }

            // 3. Extract Subject Name (everything that isn't the day keyword or times)
            var subject = line
            // Strip times
            for (match in timeMatches) {
                subject = subject.replace(match.value, "")
            }
            // Strip day keywords
            for (dayKey in dayKeywords.keys) {
                // Remove day keyword, case insensitive
                subject = subject.replace(Regex("(?i)\\b$dayKey\\b"), "")
            }
            
            // Clean up remaining characters (dashes, slashes, extra spaces)
            subject = subject.replace(Regex("""[-–/\\:;,]"""), " ")
            subject = subject.replace(Regex("""\s+"""), " ").trim()

            if (subject.isEmpty()) {
                subject = "Class commit"
            }

            blocks.add(
                RoutineBlock(
                    title = subject,
                    dayOfWeek = currentDayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute
                )
            )
        }

        return blocks
    }
}
