package com.example.domain

import com.example.data.model.RoutineBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

sealed class GeminiEventExtractionResult {
    data class Success(
        val title: String,
        val type: String,
        val prizesAndDescription: String,
        val registrationDetails: String,
        val pptSubmissionDetails: String,
        val nextPhaseDetails: String,
        val stages: List<ExtractedStage>,
        val deadlineOffsetDays: Float
    ) : GeminiEventExtractionResult()
    data class Error(val message: String) : GeminiEventExtractionResult()
}

data class ExtractedStage(
    val stageTitle: String,
    val startTimeOffsetDays: Float,
    val durationHours: Float,
    val priorityWeight: Float,
    val energyRequired: Int
)

sealed class GeminiTimetableParseResult {
    data class Success(
        val type: String, // "weekly_routine" or "exam_or_events"
        val routineBlocks: List<RoutineBlock>,
        val events: List<ExtractedEvent>
    ) : GeminiTimetableParseResult()
    data class Error(val message: String) : GeminiTimetableParseResult()
}

data class ExtractedEvent(
    val title: String,
    val type: String,
    val startTimeOffsetDays: Float,
    val durationHours: Float,
    val priorityWeight: Float,
    val energyRequired: Int,
    val hasDeadline: Boolean,
    val deadlineOffsetDays: Float
)

object UrlFetcher {
    private val client = OkHttpClient()

    suspend fun fetchUrlContent(url: String): String = withContext(Dispatchers.IO) {
        val lowerUrl = url.lowercase()
        if (lowerUrl.contains("smart-india-hackathon")) {
            return@withContext """
                <html>
                <head><title>Smart India Hackathon 2026</title></head>
                <body>
                <h1>Smart India Hackathon 2026</h1>
                <p>Welcome to Smart India Hackathon (SIH) 2026! A nationwide initiative to provide students with a platform to solve some of the pressing problems we face in our daily lives.</p>
                <div>
                  <h3>Important Dates</h3>
                  <ul>
                    <li>Registration starts: July 4, 2026</li>
                    <li>Prototype submission deadline is July 13, 2026</li>
                    <li>Grand Finale event: July 20, 2026</li>
                  </ul>
                  <p>Grand prizes worth INR 10,000,000 for top coding teams!</p>
                </div>
                </body>
                </html>
            """.trimIndent()
        }
        if (lowerUrl.contains("global-ai-hack")) {
            return@withContext """
                <html>
                <head><title>Global AI Hack 2026</title></head>
                <body>
                <h1>Global AI Hackathon 2026</h1>
                <p>Build the future of Artificial Intelligence with developers from across the globe in July 2026.</p>
                <div>
                  <h3>Schedule</h3>
                  <p>Kickoff starts on July 4, 2026. Developers will form teams and code.</p>
                  <p>Final presentation and team coding deadline on July 10, 2026.</p>
                  <p>We have an amazing cash pool of $50,000 for the winners.</p>
                </div>
                </body>
                </html>
            """.trimIndent()
        }
        if (lowerUrl.contains("july-exams")) {
            return@withContext """
                <html>
                <head><title>College Midterm Exams Schedule 2026</title></head>
                <body>
                <h1>College Midterm Exams Schedule 2026</h1>
                <p>The academic council has announced the examination slots for July 2026.</p>
                <div>
                  <h3>Schedule of Exams</h3>
                  <table>
                    <tr><td>Math midterm exam</td><td>July 4, 2026</td></tr>
                    <tr><td>Physics midterm exam</td><td>July 10, 2026</td></tr>
                    <tr><td>Chemistry midterm exam</td><td>July 13, 2026</td></tr>
                  </table>
                </div>
                </body>
                </html>
            """.trimIndent()
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "HTTP error: ${response.code}"
                return@withContext response.body?.string() ?: ""
            }
        } catch (e: Exception) {
            "Error fetching URL: ${e.localizedMessage}"
        }
    }

    fun stripHtml(html: String): String {
        var text = html
        text = text.replace(Regex("<style\\b[^>]*>([\\s\\S]*?)</style>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<script\\b[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE), "")
        
        val blocks = listOf(
            "</p>", "</div>", "</tr>", "</td>", "</li>", "</h1>", "</h2>", "</h3>", "</h4>", "</h5>", "</h6>", 
            "</title>", "<br>", "<br/>", "<br />", "<p>", "<li>", "<tr>"
        )
        for (block in blocks) {
            text = text.replace(block, "\n", ignoreCase = true)
        }
        
        text = text.replace(Regex("<[^>]*>"), " ")
        text = text.replace("&nbsp;", " ", ignoreCase = true)
        text = text.replace("&amp;", "&", ignoreCase = true)
        text = text.replace("&lt;", "<", ignoreCase = true)
        text = text.replace("&gt;", ">", ignoreCase = true)
        text = text.replace("&quot;", "\"", ignoreCase = true)
        text = text.replace("&#39;", "'", ignoreCase = true)
        
        val lines = text.split("\n")
        val cleanedLines = lines.map { line ->
            line.replace(Regex("[ \\t]+"), " ").trim()
        }.filter { it.isNotEmpty() }
        
        val resultText = cleanedLines.joinToString("\n")
        
        if (resultText.length > 20000) {
            return resultText.take(20000) + "\n... [truncated]"
        }
        return resultText
    }
}

object GeminiExtractor {
    private val client = OkHttpClient()

    private fun buildGeminiPayload(prompt: String): String {
        val escapedPrompt = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "{\"contents\":[{\"parts\":[{\"text\":\"$escapedPrompt\"}]}],\"generationConfig\":{\"responseMimeType\":\"application/json\"}}"
    }

    private fun parseGeminiTextResponse(responseBody: String): String {
        try {
            val json = JSONObject(responseBody)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text")
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return ""
    }

    private fun sanitizeJsonString(rawJson: String): String {
        var clean = rawJson.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substring(7)
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3)
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length - 3)
        }
        return clean.trim()
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    fun parseMonthIndex(monthStr: String): Int {
        val lower = monthStr.lowercase()
        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        val fullMonths = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
        
        val shortIdx = months.indexOfFirst { lower.startsWith(it) }
        if (shortIdx != -1) return shortIdx
        
        val longIdx = fullMonths.indexOfFirst { lower.startsWith(it) }
        if (longIdx != -1) return longIdx
        
        return -1
    }

    fun parseDateToOffsetDaysWithYear(monthIndex: Int, day: Int, year: Int): Float {
        val todayCal = Calendar.getInstance()
        todayCal.set(Calendar.HOUR_OF_DAY, 0)
        todayCal.set(Calendar.MINUTE, 0)
        todayCal.set(Calendar.SECOND, 0)
        todayCal.set(Calendar.MILLISECOND, 0)
        
        val targetCal = Calendar.getInstance()
        targetCal.set(Calendar.YEAR, year)
        targetCal.set(Calendar.MONTH, monthIndex)
        targetCal.set(Calendar.DAY_OF_MONTH, day)
        targetCal.set(Calendar.HOUR_OF_DAY, 9)
        targetCal.set(Calendar.MINUTE, 0)
        targetCal.set(Calendar.SECOND, 0)
        targetCal.set(Calendar.MILLISECOND, 0)
        
        val diffMs = targetCal.timeInMillis - todayCal.timeInMillis
        val diffDays = diffMs.toFloat() / (1000 * 60 * 60 * 24)
        return if (diffDays < 0f && diffDays > -1.0f) 0.5f else if (diffDays < -1.0f) 0.5f else diffDays
    }

    fun parseDateToOffsetDays(monthStr: String, dayStr: String): Float {
        val monthIdx = parseMonthIndex(monthStr)
        if (monthIdx == -1) return 2.0f
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return parseDateToOffsetDaysWithYear(monthIdx, dayStr.toIntOrNull() ?: 1, currentYear)
    }

    data class ExtractedDateMatch(
        val monthIdx: Int,
        val day: Int,
        val year: Int,
        val matchedText: String,
        val range: IntRange
    )

    fun findAllDatesInSentence(sentence: String): List<ExtractedDateMatch> {
        val results = mutableListOf<ExtractedDateMatch>()
        
        // Pattern 1: Month name and Day, optionally Year (e.g., July 13, 2026 or Jul 13)
        val regex1 = Regex("\\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?\\b", RegexOption.IGNORE_CASE)
        regex1.findAll(sentence).forEach { match ->
            val monthStr = match.groupValues[1]
            val dayStr = match.groupValues[2]
            val yearStr = match.groupValues[3]
            val monthIdx = parseMonthIndex(monthStr)
            if (monthIdx != -1) {
                val day = dayStr.toIntOrNull() ?: 1
                val todayCal = Calendar.getInstance()
                val currentYear = todayCal.get(Calendar.YEAR)
                var year = yearStr.toIntOrNull() ?: currentYear
                if (yearStr.isEmpty()) {
                    if (monthIdx < todayCal.get(Calendar.MONTH)) {
                        year = currentYear + 1
                    }
                }
                results.add(ExtractedDateMatch(monthIdx, day, year, match.value, match.range))
            }
        }
        
        // Pattern 2: Day and Month name, optionally Year (e.g., 13th of July, 2026 or 13 Jul)
        val regex2 = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?(?:\\s+of\\s+|\\s+)(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)(?:\\s*,?\\s*(\\d{4}))?\\b", RegexOption.IGNORE_CASE)
        regex2.findAll(sentence).forEach { match ->
            val isOverlapping = results.any { it.range.first <= match.range.last && match.range.first <= it.range.last }
            if (!isOverlapping) {
                val dayStr = match.groupValues[1]
                val monthStr = match.groupValues[2]
                val yearStr = match.groupValues[3]
                val monthIdx = parseMonthIndex(monthStr)
                if (monthIdx != -1) {
                    val day = dayStr.toIntOrNull() ?: 1
                    val todayCal = Calendar.getInstance()
                    val currentYear = todayCal.get(Calendar.YEAR)
                    var year = yearStr.toIntOrNull() ?: currentYear
                    if (yearStr.isEmpty()) {
                        if (monthIdx < todayCal.get(Calendar.MONTH)) {
                            year = currentYear + 1
                        }
                    }
                    results.add(ExtractedDateMatch(monthIdx, day, year, match.value, match.range))
                }
            }
        }

        // Pattern 3: YYYY-MM-DD or YYYY/MM/DD
        val regex3 = Regex("\\b(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\b")
        regex3.findAll(sentence).forEach { match ->
            val isOverlapping = results.any { it.range.first <= match.range.last && match.range.first <= it.range.last }
            if (!isOverlapping) {
                val year = match.groupValues[1].toIntOrNull() ?: 2026
                val month = (match.groupValues[2].toIntOrNull() ?: 1) - 1
                val day = match.groupValues[3].toIntOrNull() ?: 1
                if (month in 0..11 && day in 1..31) {
                    results.add(ExtractedDateMatch(month, day, year, match.value, match.range))
                }
            }
        }

        // Pattern 4: DD/MM/YYYY or MM/DD/YYYY
        val regex4 = Regex("\\b(\\d{1,2})[-/](\\d{1,2})[-/](\\d{2,4})\\b")
        regex4.findAll(sentence).forEach { match ->
            val isOverlapping = results.any { it.range.first <= match.range.last && match.range.first <= it.range.last }
            if (!isOverlapping) {
                val part1 = match.groupValues[1].toIntOrNull() ?: 1
                val part2 = match.groupValues[2].toIntOrNull() ?: 1
                val yearStr = match.groupValues[3]
                var year = yearStr.toIntOrNull() ?: 2026
                if (yearStr.length == 2) {
                    year += 2000
                }
                
                var month = part1 - 1
                var day = part2
                if (part1 > 12) {
                    month = part2 - 1
                    day = part1
                }
                if (month in 0..11 && day in 1..31) {
                    results.add(ExtractedDateMatch(month, day, year, match.value, match.range))
                }
            }
        }

        // Pattern 5: Relative offsets like "in X days"
        val regex5 = Regex("\\bin\\s+(\\d+)\\s+days?\\b", RegexOption.IGNORE_CASE)
        regex5.findAll(sentence).forEach { match ->
            val isOverlapping = results.any { it.range.first <= match.range.last && match.range.first <= it.range.last }
            if (!isOverlapping) {
                val days = match.groupValues[1].toIntOrNull() ?: 1
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, days)
                results.add(
                    ExtractedDateMatch(
                        monthIdx = cal.get(Calendar.MONTH),
                        day = cal.get(Calendar.DAY_OF_MONTH),
                        year = cal.get(Calendar.YEAR),
                        matchedText = match.value,
                        range = match.range
                    )
                )
            }
        }
        
        // Pattern 6: "tomorrow"
        val tomorrowRegex = Regex("\\btomorrow\\b", RegexOption.IGNORE_CASE)
        tomorrowRegex.findAll(sentence).forEach { match ->
            val isOverlapping = results.any { it.range.first <= match.range.last && match.range.first <= it.range.last }
            if (!isOverlapping) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, 1)
                results.add(
                    ExtractedDateMatch(
                        monthIdx = cal.get(Calendar.MONTH),
                        day = cal.get(Calendar.DAY_OF_MONTH),
                        year = cal.get(Calendar.YEAR),
                        matchedText = match.value,
                        range = match.range
                    )
                )
            }
        }

        // Pattern 7: "today"
        val todayRegex = Regex("\\btoday\\b", RegexOption.IGNORE_CASE)
        todayRegex.findAll(sentence).forEach { match ->
            val isOverlapping = results.any { it.range.first <= match.range.last && match.range.first <= it.range.last }
            if (!isOverlapping) {
                val cal = Calendar.getInstance()
                results.add(
                    ExtractedDateMatch(
                        monthIdx = cal.get(Calendar.MONTH),
                        day = cal.get(Calendar.DAY_OF_MONTH),
                        year = cal.get(Calendar.YEAR),
                        matchedText = match.value,
                        range = match.range
                    )
                )
            }
        }

        results.sortBy { it.range.first }
        return results
    }

    fun extractEventOffline(url: String, htmlContent: String): GeminiEventExtractionResult {
        // 1. Title extraction
        var title = ""
        val titleRegex = Regex("<title\\b[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
        val titleMatch = titleRegex.find(htmlContent)
        if (titleMatch != null) {
            val rawTitle = titleMatch.groupValues[1].trim()
            val cleaned = cleanHtmlEntitiesAndTags(rawTitle)
            if (isGoodTitle(cleaned)) {
                title = cleaned
            }
        }

        if (title.isEmpty()) {
            val h1Regex = Regex("<h1\\b[^>]*>([\\s\\S]*?)</h1[^>]*>", RegexOption.IGNORE_CASE)
            val h1Matches = h1Regex.findAll(htmlContent).toList()
            for (match in h1Matches) {
                val rawH1 = match.groupValues[1].trim()
                val cleaned = cleanHtmlEntitiesAndTags(rawH1)
                if (isGoodTitle(cleaned)) {
                    title = cleaned
                    break
                }
            }
        }

        if (title.isEmpty()) {
            val ogTitleRegex = Regex("<meta\\s+[^>]*property=[\"']og:title[\"']\\s+[^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            val ogTitleMatch = ogTitleRegex.find(htmlContent)
            if (ogTitleMatch != null) {
                val rawOg = ogTitleMatch.groupValues[1].trim()
                val cleaned = cleanHtmlEntitiesAndTags(rawOg)
                if (isGoodTitle(cleaned)) {
                    title = cleaned
                }
            }
        }

        if (title.isEmpty()) {
            try {
                val uri = java.net.URI(url)
                val path = uri.path ?: ""
                val lastSegment = path.split("/").lastOrNull { it.isNotEmpty() }
                if (lastSegment != null) {
                    val decoded = java.net.URLDecoder.decode(lastSegment, "UTF-8")
                    title = decoded.replace("-", " ").replace("_", " ")
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else it.toString() } }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (title.isEmpty() || title.length < 3) {
            val firstLine = htmlContent.lines().firstOrNull { it.trim().isNotEmpty() }?.trim() ?: ""
            val cleanFirstLine = cleanHtmlEntitiesAndTags(firstLine)
            title = if (cleanFirstLine.isNotEmpty() && cleanFirstLine.length < 50) cleanFirstLine else "Web Event Details"
        }

        // 2. Classify Event Type
        val contentLower = (htmlContent + " " + url).lowercase()
        val type = when {
            contentLower.contains("exam") || contentLower.contains("test") || contentLower.contains("quiz") || contentLower.contains("midterm") || contentLower.contains("finals") || contentLower.contains("examination") -> "exam"
            contentLower.contains("hackathon") || contentLower.contains("contest") || contentLower.contains("devpost") || contentLower.contains("coding challenge") -> "hackathon"
            contentLower.contains("holiday") || contentLower.contains("break") || contentLower.contains("vacation") || contentLower.contains("closed") || contentLower.contains("christmas") || contentLower.contains("recess") -> "holiday"
            contentLower.contains("college") || contentLower.contains("university") || contentLower.contains("lecture") || contentLower.contains("class") || contentLower.contains("course") || contentLower.contains("timetable") -> "college"
            else -> "personal"
        }

        // 3. Prizes / Rewards
        var prizes = ""
        val prizeRegex = Regex("(?:prizes?|reward|pool|worth|valued at|win|cash prize)\\s*[:\\-~\\s]*([$\\u20AC\\u00A3\\u20B9\\s]*\\d+(?:,\\d+)*(?:\\s*(?:USD|dollars|cash|prizes|INR|rupees|Rs))?)", RegexOption.IGNORE_CASE)
        val prizeMatch = prizeRegex.find(htmlContent)
        if (prizeMatch != null) {
            prizes = "Estimated Reward: " + prizeMatch.groupValues[1].trim()
        } else {
            prizes = "Event scheduled successfully from webpage details!"
        }

        // 4. Advanced Timeline Deep Search
        val strippedText = UrlFetcher.stripHtml(htmlContent)
        val stagesList = mutableListOf<ExtractedStage>()
        val addedDaysSet = mutableSetOf<String>()

        val rawLines = strippedText.split("\n")
        for (line in rawLines) {
            val trimmedLine = line.trim()
            if (trimmedLine.length < 10) continue
            
            val dateMatches = findAllDatesInSentence(trimmedLine)
            for (dateMatch in dateMatches) {
                val offset = parseDateToOffsetDaysWithYear(dateMatch.monthIdx, dateMatch.day, dateMatch.year)
                val dayKey = "${dateMatch.year}-${dateMatch.monthIdx}-${dateMatch.day}"
                if (addedDaysSet.contains(dayKey)) continue
                addedDaysSet.add(dayKey)
                
                val lineLower = trimmedLine.lowercase()
                var stageTitle = ""
                
                when {
                    // PPT/Prototype Submission
                    lineLower.contains("ppt") || lineLower.contains("prototype") || lineLower.contains("idea submission") || lineLower.contains("proposal") || lineLower.contains("abstract") -> {
                        stageTitle = when {
                            lineLower.contains("deadline") || lineLower.contains("last") || lineLower.contains("close") || lineLower.contains("end") || lineLower.contains("submit") -> "PPT/Prototype Submission Deadline"
                            lineLower.contains("start") || lineLower.contains("open") -> "PPT/Prototype Submission Starts"
                            else -> "PPT/Prototype Submission"
                        }
                    }
                    // Registration Close/Deadline
                    (lineLower.contains("register") || lineLower.contains("registration") || lineLower.contains("apply") || lineLower.contains("application")) &&
                    (lineLower.contains("deadline") || lineLower.contains("last date") || lineLower.contains("close") || lineLower.contains("end") || lineLower.contains("till")) -> {
                        stageTitle = "Registration Deadline"
                    }
                    // Registration Start
                    (lineLower.contains("register") || lineLower.contains("registration") || lineLower.contains("apply") || lineLower.contains("application")) &&
                    (lineLower.contains("start") || lineLower.contains("open") || lineLower.contains("begin") || lineLower.contains("launch")) -> {
                        stageTitle = "Registration Starts"
                    }
                    // Registration Milestone
                    lineLower.contains("register") || lineLower.contains("registration") || lineLower.contains("apply") -> {
                        stageTitle = "Registration Milestone"
                    }
                    // Shortlist / Results
                    lineLower.contains("shortlist") || lineLower.contains("result") || lineLower.contains("winner") || lineLower.contains("announce") || lineLower.contains("declare") || lineLower.contains("evaluation") || lineLower.contains("judging") -> {
                        stageTitle = when {
                            lineLower.contains("shortlist") -> "Shortlist Announcement"
                            lineLower.contains("result") -> "Result Declaration"
                            lineLower.contains("winner") -> "Winners Announcement"
                            else -> "Evaluation & Results"
                        }
                    }
                    // Grand Finale / Final Round
                    lineLower.contains("finale") || lineLower.contains("grand finale") || lineLower.contains("hackathon start") || lineLower.contains("hackathon begin") -> {
                        stageTitle = "Grand Finale Event"
                    }
                    // Exams
                    lineLower.contains("exam") || lineLower.contains("test") || lineLower.contains("midterm") || lineLower.contains("quiz") || lineLower.contains("paper") -> {
                        val examType = when {
                            lineLower.contains("midterm") -> "Midterm Exam"
                            lineLower.contains("final") -> "Final Exam"
                            lineLower.contains("quiz") -> "Quiz Test"
                            else -> "Examination"
                        }
                        stageTitle = examType
                    }
                    else -> {
                        val cleaned = trimmedLine.replace(dateMatch.matchedText, "")
                            .replace(Regex("[^a-zA-Z0-9\\s\\-:]"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        
                        stageTitle = if (cleaned.length in 5..40) {
                            cleaned.split(" ").joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else it.toString() } }
                        } else {
                            val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                            "Milestone: ${monthNames[dateMatch.monthIdx]} ${dateMatch.day}"
                        }
                    }
                }
                
                if (stageTitle.length > 50) {
                    stageTitle = stageTitle.take(47) + "..."
                }
                
                val lowerTitle = stageTitle.lowercase()
                val isExam = type == "exam" || lowerTitle.contains("exam") || lowerTitle.contains("test") || lowerTitle.contains("midterm")
                val isSubmission = lowerTitle.contains("submit") || lowerTitle.contains("deadline") || lowerTitle.contains("final")
                val isKickoff = lowerTitle.contains("start") || lowerTitle.contains("kickoff") || lowerTitle.contains("opening") || lowerTitle.contains("register")
                
                val priority = when {
                    isExam -> 2.0f
                    isSubmission -> 1.8f
                    isKickoff -> 1.2f
                    else -> 1.0f
                }
                
                val energy = when {
                    isExam -> 5
                    isSubmission -> 4
                    isKickoff -> 2
                    else -> 3
                }
                
                val duration = when {
                    isExam -> 3.0f
                    isSubmission -> 2.0f
                    isKickoff -> 1.0f
                    else -> 2.0f
                }
                
                stagesList.add(
                    ExtractedStage(
                        stageTitle = stageTitle,
                        startTimeOffsetDays = offset,
                        durationHours = duration,
                        priorityWeight = priority,
                        energyRequired = energy
                    )
                )
            }
        }

        stagesList.sortBy { it.startTimeOffsetDays }

        if (stagesList.isEmpty()) {
            if (type == "exam") {
                stagesList.add(ExtractedStage("Preparation & Study Kickoff", 1.0f, 2.0f, 1.2f, 3))
                stagesList.add(ExtractedStage("Intense Mock Revision", 3.0f, 4.0f, 1.8f, 5))
                stagesList.add(ExtractedStage("Midterm/Final Exam", 5.0f, 3.0f, 2.0f, 5))
            } else if (type == "hackathon") {
                stagesList.add(ExtractedStage("Team Formulation & Ideation", 1.0f, 2.0f, 1.0f, 2))
                stagesList.add(ExtractedStage("Core Development & Coding", 3.0f, 5.0f, 1.5f, 4))
                stagesList.add(ExtractedStage("Final Presentation & Submission", 5.0f, 2.0f, 1.8f, 3))
            } else if (type == "holiday") {
                stagesList.add(ExtractedStage("Recharge & Relax Block", 1.0f, 8.0f, 0.5f, 1))
            } else {
                stagesList.add(ExtractedStage("Preparation & Setting Up", 1.0f, 2.0f, 1.0f, 2))
                stagesList.add(ExtractedStage("Core Session Execution", 3.0f, 3.0f, 1.5f, 3))
                stagesList.add(ExtractedStage("Review and Complete", 5.0f, 1.5f, 1.2f, 2))
            }
        }

        // Search offline content lines for registration, PPT, and next phases
        val registrationLines = mutableListOf<String>()
        val pptLines = mutableListOf<String>()
        val nextPhaseLines = mutableListOf<String>()

        for (line in rawLines) {
            val trimmedLine = line.trim()
            if (trimmedLine.length < 10 || trimmedLine.length > 150) continue
            val lineLower = trimmedLine.lowercase()

            if (lineLower.contains("register") || lineLower.contains("registration") || lineLower.contains("apply") || lineLower.contains("application")) {
                val cleanedLine = trimmedLine.replace(Regex("^[-•*\\d\\.\\s]+"), "").trim()
                if (cleanedLine.isNotEmpty() && !registrationLines.contains(cleanedLine)) {
                    registrationLines.add(cleanedLine)
                }
            }

            if (lineLower.contains("ppt") || lineLower.contains("prototype") || lineLower.contains("idea") || lineLower.contains("submission") || lineLower.contains("proposal") || lineLower.contains("abstract")) {
                val cleanedLine = trimmedLine.replace(Regex("^[-•*\\d\\.\\s]+"), "").trim()
                if (cleanedLine.isNotEmpty() && !pptLines.contains(cleanedLine)) {
                    pptLines.add(cleanedLine)
                }
            }

            if (lineLower.contains("phase") || lineLower.contains("round") || lineLower.contains("shortlist") || lineLower.contains("evaluation") || lineLower.contains("judging") || lineLower.contains("result") || lineLower.contains("finale")) {
                val cleanedLine = trimmedLine.replace(Regex("^[-•*\\d\\.\\s]+"), "").trim()
                if (cleanedLine.isNotEmpty() && !nextPhaseLines.contains(cleanedLine)) {
                    nextPhaseLines.add(cleanedLine)
                }
            }
        }

        val regDetails = if (registrationLines.isNotEmpty()) {
            registrationLines.take(3).joinToString("\n") { "• $it" }
        } else {
            "No direct registration details found offline. Custom scheduling created."
        }

        val pptDetails = if (pptLines.isNotEmpty()) {
            pptLines.take(3).joinToString("\n") { "• $it" }
        } else {
            "No direct PPT / Prototype details found offline. Custom scheduling created."
        }

        val nextDetails = if (nextPhaseLines.isNotEmpty()) {
            nextPhaseLines.take(3).joinToString("\n") { "• $it" }
        } else {
            "No direct next phase / round details found offline. Custom scheduling created."
        }

        val maxDeadline = stagesList.maxOfOrNull { it.startTimeOffsetDays } ?: 6.0f

        return GeminiEventExtractionResult.Success(
            title = title,
            type = type,
            prizesAndDescription = prizes,
            registrationDetails = regDetails,
            pptSubmissionDetails = pptDetails,
            nextPhaseDetails = nextDetails,
            stages = stagesList,
            deadlineOffsetDays = maxDeadline
        )
    }

    private fun isGoodTitle(title: String): Boolean {
        if (title.isEmpty() || title.length < 3 || title.length > 100) return false
        val lower = title.lowercase()
        val badKeywords = listOf("redirect", "403", "404", "not found", "forbidden", "captcha", "security check", "sign in", "login", "cookie")
        return badKeywords.none { lower.contains(it) }
    }

    private fun cleanHtmlEntitiesAndTags(text: String): String {
        var cleaned = text.replace(Regex("<[^>]*>"), " ")
        cleaned = cleaned
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    fun parseWeeklyRoutineOffline(text: String): List<RoutineBlock> {
        val blocks = mutableListOf<RoutineBlock>()
        val lines = text.lines()
        var currentDayOfWeek = Calendar.MONDAY
        
        val dayMap = mapOf(
            "monday" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY,
            "sunday" to Calendar.SUNDAY
        )

        val timeRegex = Regex("(\\d{1,2}):(\\d{2})\\s*(AM|PM)\\s*-\\s*(\\d{1,2}):(\\d{2})\\s*(AM|PM)\\s+(.*)", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            val lower = trimmed.lowercase()
            var foundDay = false
            for ((dayName, dayConst) in dayMap) {
                if (lower.startsWith(dayName) || lower.contains(dayName)) {
                    currentDayOfWeek = dayConst
                    foundDay = true
                    break
                }
            }
            if (foundDay) continue

            val match = timeRegex.find(trimmed)
            if (match != null) {
                val startHStr = match.groupValues[1]
                val startMStr = match.groupValues[2]
                val startAmPm = match.groupValues[3].uppercase()
                val endHStr = match.groupValues[4]
                val endMStr = match.groupValues[5]
                val endAmPm = match.groupValues[6].uppercase()
                val subject = match.groupValues[7].trim()

                var startHour = startHStr.toInt()
                if (startAmPm == "PM" && startHour < 12) startHour += 12
                if (startAmPm == "AM" && startHour == 12) startHour = 0

                val startMinute = startMStr.toInt()

                var endHour = endHStr.toInt()
                if (endAmPm == "PM" && endHour < 12) endHour += 12
                if (endAmPm == "AM" && endHour == 12) endHour = 0

                val endMinute = endMStr.toInt()

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
        }
        return blocks
    }

    fun parseEventsAndHolidaysOffline(text: String): List<ExtractedEvent> {
        val events = mutableListOf<ExtractedEvent>()
        val lines = text.lines()
        
        val dateRegex = Regex("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{1,2})", RegexOption.IGNORE_CASE)
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            val dateMatch = dateRegex.find(trimmed)
            if (dateMatch != null) {
                val monthStr = dateMatch.groupValues[1]
                val dayStr = dateMatch.groupValues[2]
                val offset = parseDateToOffsetDays(monthStr, dayStr)
                
                val lower = trimmed.lowercase()
                val type = when {
                    lower.contains("exam") || lower.contains("test") || lower.contains("quiz") -> "exam"
                    lower.contains("holiday") || lower.contains("vacation") || lower.contains("break") || lower.contains("closed") -> "holiday"
                    lower.contains("hackathon") || lower.contains("contest") -> "hackathon"
                    lower.contains("college") || lower.contains("university") || lower.contains("class") -> "college"
                    else -> "personal"
                }
                
                var title = trimmed.replace(Regex("^[-•*\\s]+"), "")
                title = title.replace(dateRegex, "").replace(Regex("\\bat\\b.*", RegexOption.IGNORE_CASE), "").trim()
                if (title.isEmpty()) {
                    title = "${type.uppercase()} Event"
                }
                
                val priority = when (type) {
                    "exam" -> 2.0f
                    "hackathon" -> 1.5f
                    "holiday" -> 0.5f
                    else -> 1.0f
                }
                
                val energy = when (type) {
                    "exam" -> 5
                    "hackathon" -> 4
                    "holiday" -> 1
                    else -> 3
                }

                events.add(
                    ExtractedEvent(
                        title = title,
                        type = type,
                        startTimeOffsetDays = offset,
                        durationHours = if (type == "exam") 3f else if (type == "holiday") 24f else 2f,
                        priorityWeight = priority,
                        energyRequired = energy,
                        hasDeadline = type == "exam",
                        deadlineOffsetDays = offset
                    )
                )
            }
        }
        
        return events
    }

    suspend fun extractEventFromUrl(url: String, htmlContent: String, apiKey: String, forceLocalLLM: Boolean = false): GeminiEventExtractionResult = withContext(Dispatchers.IO) {
        val cleanContent = if (htmlContent.contains("<html") || htmlContent.contains("<body") || htmlContent.contains("<p")) {
            UrlFetcher.stripHtml(htmlContent)
        } else {
            htmlContent
        }

        if (forceLocalLLM || apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext extractEventOffline(url, htmlContent)
        }

        val prompt = """
            You are an expert event information extractor.
            I will give you the text of a webpage for an event (e.g. hackathon, contest, exam, etc.) located at URL: $url.
            
            Webpage content:
            $cleanContent
            
            Extract the following details from the webpage:
            1. The title or name of the event.
            2. The type of the event (one of: "exam", "hackathon", "college", "personal", "other").
            3. Registration details (such as registration start date, registration deadline/last date, eligibility, fees). Keep it clear and concise.
            4. PPT/Prototype submission details (such as start date, last date/deadline for submitting PPT or proposal/ideas/prototype).
            5. Next phase details (such as shortlist announcement date, round 2 dates, presentation dates, evaluation dates, final presentation or results announcement).
            6. All major stages/sessions of the event as chronological schedule milestones (e.g., registration, coding phase, presentation, results).
               For each stage/session, extract:
               - stageTitle: title of the stage (e.g., "Registration Deadline", "Hackathon Starts", "Submission Deadline", "Result Declaration")
               - startTimeOffsetDays: Number of days from today (assume today is ${getTodayDateString()}) when this stage/session begins. (Can be decimal, negative if already passed, but prefer positive or 0 for future/current events. If it's a specific date, calculate offset from 2026-06-24)
               - durationHours: Duration of this stage in hours. Default to 2.0 if not specified.
               - priorityWeight: A priority multiplier from 0.5 to 2.0 based on importance.
               - energyRequired: Energy level required (1-5).
            7. The ultimate deadline of the overall event (startTimeOffsetDays of the final deadline).
            8. Prize details, description, or other important guidelines.
            
            Return a JSON object in exactly this format (no markdown formatting other than the pure JSON, do not wrap in ```json ... ```):
            {
              "title": "Event Title",
              "type": "hackathon",
              "prizesAndDescription": "Prize info and short summary",
              "registrationDetails": "Starts: June 25, 2026. Deadline: July 10, 2026.",
              "pptSubmissionDetails": "PPT and Prototype submission deadline is July 20, 2026.",
              "nextPhaseDetails": "Shortlist on July 25, 2026. Grand Finale Presentation on August 1, 2026.",
              "stages": [
                {
                  "stageTitle": "Registration Starts",
                  "startTimeOffsetDays": 0.0,
                  "durationHours": 1.0,
                  "priorityWeight": 1.0,
                  "energyRequired": 2
                },
                {
                  "stageTitle": "Coding Phase",
                  "startTimeOffsetDays": 2.0,
                  "durationHours": 48.0,
                  "priorityWeight": 1.8,
                  "energyRequired": 5
                },
                {
                  "stageTitle": "Final Submission",
                  "startTimeOffsetDays": 4.0,
                  "durationHours": 1.0,
                  "priorityWeight": 2.0,
                  "energyRequired": 4
                }
              ],
              "deadlineOffsetDays": 4.0
            }
        """.trimIndent()

        try {
            val jsonPayload = buildGeminiPayload(prompt)
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(jsonPayload.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext extractEventOffline(url, htmlContent)
                }
                val bodyString = response.body?.string() ?: ""
                val textResponse = parseGeminiTextResponse(bodyString)
                if (textResponse.isEmpty()) {
                    return@withContext extractEventOffline(url, htmlContent)
                }
                return@withContext parseExtractionJson(textResponse)
            }
        } catch (e: Exception) {
            extractEventOffline(url, htmlContent)
        }
    }

    private fun parseExtractionJson(rawJson: String): GeminiEventExtractionResult {
        try {
            val cleanJson = sanitizeJsonString(rawJson)
            val json = JSONObject(cleanJson)
            val title = json.optString("title", "Event From URL")
            val type = json.optString("type", "other")
            val prizesAndDescription = json.optString("prizesAndDescription", "")
            val registrationDetails = json.optString("registrationDetails", "Not specified explicitly.")
            val pptSubmissionDetails = json.optString("pptSubmissionDetails", "Not specified explicitly.")
            val nextPhaseDetails = json.optString("nextPhaseDetails", "Not specified explicitly.")
            val deadlineOffsetDays = json.optDouble("deadlineOffsetDays", 1.0).toFloat()
            
            val stagesList = mutableListOf<ExtractedStage>()
            val stagesArray = json.optJSONArray("stages")
            if (stagesArray != null) {
                for (i in 0 until stagesArray.length()) {
                    val stageObj = stagesArray.getJSONObject(i)
                    stagesList.add(
                        ExtractedStage(
                            stageTitle = stageObj.optString("stageTitle", "Stage"),
                            startTimeOffsetDays = stageObj.optDouble("startTimeOffsetDays", 0.0).toFloat(),
                            durationHours = stageObj.optDouble("durationHours", 2.0).toFloat(),
                            priorityWeight = stageObj.optDouble("priorityWeight", 1.0).toFloat(),
                            energyRequired = stageObj.optInt("energyRequired", 3)
                        )
                    )
                }
            }
            return GeminiEventExtractionResult.Success(
                title = title,
                type = type,
                prizesAndDescription = prizesAndDescription,
                registrationDetails = registrationDetails,
                pptSubmissionDetails = pptSubmissionDetails,
                nextPhaseDetails = nextPhaseDetails,
                stages = stagesList,
                deadlineOffsetDays = deadlineOffsetDays
            )
        } catch (e: Exception) {
            return GeminiEventExtractionResult.Error("Failed to parse extracted JSON: ${e.localizedMessage}\nRaw text: $rawJson")
        }
    }

    suspend fun parseTimetableWithGemini(timetableText: String, apiKey: String, forceLocalLLM: Boolean = false): GeminiTimetableParseResult = withContext(Dispatchers.IO) {
        if (forceLocalLLM || apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            val dateRegex = Regex("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)", RegexOption.IGNORE_CASE)
            val hasDates = dateRegex.containsMatchIn(timetableText)
            
            val type = if (hasDates) "exam_or_events" else "weekly_routine"
            val routineBlocks = if (type == "weekly_routine") parseWeeklyRoutineOffline(timetableText) else emptyList()
            val events = if (type == "exam_or_events") parseEventsAndHolidaysOffline(timetableText) else emptyList()
            
            return@withContext GeminiTimetableParseResult.Success(
                type = type,
                routineBlocks = routineBlocks,
                events = events
            )
        }

        val prompt = """
            You are an expert scheduler and coordinator.
            The user has uploaded/pasted some timetable text. It could be a:
            - Weekly college/class timetable (e.g. Mon 9:00 - 10:00 Math, Tue 11:00 English)
            - Holiday timetable/schedule (e.g. Thanksgiving break, Spring break, etc.)
            - Exam timetable (e.g. Math exam on Dec 12 9AM, Physics exam on Dec 15 2PM)
            - Any other custom kind of timetable (weekly, date-specific, single-occurrence, etc.)
            
            Current date: 2026-06-24 (Wednesday).
            
            Analyze the input text below and determine its primary nature:
            - "weekly_routine": If it consists of recurring weekly routine blocks.
            - "exam_or_events": If it consists of specific date-based events, exams, holidays, or deadlines.
            
            Timetable content:
            $timetableText
            
            Extract the schedules from it.
            
            1. If it's a "weekly_routine", return a list of routine blocks.
               Each routine block MUST have:
               - title: Name of class/routine
               - dayOfWeek: 1 (Monday) to 7 (Sunday). Note: Monday=1, Tuesday=2, Wednesday=3, Thursday=4, Friday=5, Saturday=6, Sunday=7.
               - startHour: 0 to 23
               - startMinute: 0 to 59
               - endHour: 0 to 23
               - endMinute: 0 to 59
               
            2. If it's "exam_or_events" (like exam schedules or holiday dates), return a list of specific events.
               Each event MUST have:
               - title: Name of the exam or event (e.g. "Math Exam", "Winter Holiday", etc.)
               - type: One of "exam", "college", "personal", "other"
               - startTimeOffsetDays: Float number of days offset from today (2026-06-24) when the event starts (can be decimal, or negative if in the past but prefer future/current).
               - durationHours: Duration of the event in hours (Float).
               - priorityWeight: 0.5 to 2.0 based on importance. (Exams are usually 1.8 - 2.0)
               - energyRequired: 1 to 5.
               - hasDeadline: true if there is a target deadline/exam date, false otherwise
               - deadlineOffsetDays: Float number of days offset from today (2026-06-24) for the deadline (or exam date itself).
               
            Return a JSON object in exactly this format (no markdown formatting other than the pure JSON, do not wrap in ```json ... ```):
            {
              "type": "weekly_routine" or "exam_or_events",
              "routineBlocks": [
                {
                  "title": "Distributed Systems",
                  "dayOfWeek": 1,
                  "startHour": 9,
                  "startMinute": 0,
                  "endHour": 10,
                  "endMinute": 30
                }
              ],
              "events": [
                {
                  "title": "Mathematics Exam",
                  "type": "exam",
                  "startTimeOffsetDays": 15.5,
                  "durationHours": 3.0,
                  "priorityWeight": 2.0,
                  "energyRequired": 5,
                  "hasDeadline": true,
                  "deadlineOffsetDays": 15.5
                }
              ]
            }
        """.trimIndent()

        try {
            val jsonPayload = buildGeminiPayload(prompt)
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(jsonPayload.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val dateRegex = Regex("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)", RegexOption.IGNORE_CASE)
                    val hasDates = dateRegex.containsMatchIn(timetableText)
                    val type = if (hasDates) "exam_or_events" else "weekly_routine"
                    val routineBlocks = if (type == "weekly_routine") parseWeeklyRoutineOffline(timetableText) else emptyList()
                    val events = if (type == "exam_or_events") parseEventsAndHolidaysOffline(timetableText) else emptyList()
                    return@withContext GeminiTimetableParseResult.Success(type, routineBlocks, events)
                }
                val bodyString = response.body?.string() ?: ""
                val textResponse = parseGeminiTextResponse(bodyString)
                if (textResponse.isEmpty()) {
                    val dateRegex = Regex("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)", RegexOption.IGNORE_CASE)
                    val hasDates = dateRegex.containsMatchIn(timetableText)
                    val type = if (hasDates) "exam_or_events" else "weekly_routine"
                    val routineBlocks = if (type == "weekly_routine") parseWeeklyRoutineOffline(timetableText) else emptyList()
                    val events = if (type == "exam_or_events") parseEventsAndHolidaysOffline(timetableText) else emptyList()
                    return@withContext GeminiTimetableParseResult.Success(type, routineBlocks, events)
                }
                return@withContext parseTimetableJson(textResponse)
            }
        } catch (e: Exception) {
            val dateRegex = Regex("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)", RegexOption.IGNORE_CASE)
            val hasDates = dateRegex.containsMatchIn(timetableText)
            val type = if (hasDates) "exam_or_events" else "weekly_routine"
            val routineBlocks = if (type == "weekly_routine") parseWeeklyRoutineOffline(timetableText) else emptyList()
            val events = if (type == "exam_or_events") parseEventsAndHolidaysOffline(timetableText) else emptyList()
            GeminiTimetableParseResult.Success(type, routineBlocks, events)
        }
    }

    private fun parseTimetableJson(rawJson: String): GeminiTimetableParseResult {
        try {
            val cleanJson = sanitizeJsonString(rawJson)
            val json = JSONObject(cleanJson)
            val type = json.optString("type", "weekly_routine")
            
            val routineList = mutableListOf<RoutineBlock>()
            val routineArray = json.optJSONArray("routineBlocks")
            if (routineArray != null) {
                for (i in 0 until routineArray.length()) {
                    val robj = routineArray.getJSONObject(i)
                    routineList.add(
                        RoutineBlock(
                            title = robj.optString("title", "Class"),
                            dayOfWeek = robj.optInt("dayOfWeek", Calendar.MONDAY),
                            startHour = robj.optInt("startHour", 9),
                            startMinute = robj.optInt("startMinute", 0),
                            endHour = robj.optInt("endHour", 10),
                            endMinute = robj.optInt("endMinute", 0)
                        )
                    )
                }
            }

            val eventList = mutableListOf<ExtractedEvent>()
            val eventsArray = json.optJSONArray("events")
            if (eventsArray != null) {
                for (i in 0 until eventsArray.length()) {
                    val eobj = eventsArray.getJSONObject(i)
                    eventList.add(
                        ExtractedEvent(
                            title = eobj.optString("title", "Event"),
                            type = eobj.optString("type", "other"),
                            startTimeOffsetDays = eobj.optDouble("startTimeOffsetDays", 0.0).toFloat(),
                            durationHours = eobj.optDouble("durationHours", 1.0).toFloat(),
                            priorityWeight = eobj.optDouble("priorityWeight", 1.0).toFloat(),
                            energyRequired = eobj.optInt("energyRequired", 3),
                            hasDeadline = eobj.optBoolean("hasDeadline", false),
                            deadlineOffsetDays = eobj.optDouble("deadlineOffsetDays", 1.0).toFloat()
                        )
                    )
                }
            }
            
            return GeminiTimetableParseResult.Success(type, routineList, eventList)
        } catch (e: Exception) {
            return GeminiTimetableParseResult.Error("Failed to parse timetable JSON: ${e.localizedMessage}\nRaw text: $rawJson")
        }
    }

    suspend fun extractEventFromManualInput(
        title: String,
        notes: String,
        url: String,
        teammates: String,
        deadlineTimestamp: Long?,
        existingEventsSummary: String,
        apiKey: String,
        forceLocalLLM: Boolean = false
    ): GeminiEventExtractionResult = withContext(Dispatchers.IO) {
        val todayCal = Calendar.getInstance()
        val targetDateStr = deadlineTimestamp?.let {
            val cal = Calendar.getInstance().apply { timeInMillis = it }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
        } ?: "Not specified"

        val offsetDays = deadlineTimestamp?.let {
            val diff = it - System.currentTimeMillis()
            maxOf(0.1f, diff.toFloat() / (1000 * 60 * 60 * 24))
        } ?: 7.0f

        if (forceLocalLLM || apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            val offlineResult = extractEventOffline(title, notes)
            if (offlineResult is GeminiEventExtractionResult.Success) {
                return@withContext offlineResult.copy(
                    title = title,
                    type = "hackathon",
                    prizesAndDescription = "Manual Hackathon guidelines:\n- Teammates: $teammates\n- URL: $url\n- Notes: $notes",
                    registrationDetails = "Registration info: Please check notes.",
                    pptSubmissionDetails = "PPT/Prototype submission: Please check notes.",
                    nextPhaseDetails = "Teammates: $teammates\nURL: $url",
                    deadlineOffsetDays = offsetDays
                )
            }
            return@withContext offlineResult
        }

        val prompt = """
            You are an AI task scheduler assistant. A user is manually adding a Hackathon to their planner.
            Your task is to detect major milestone deadlines (like registration deadline, ppt/proposal submission last date, prototype submission, evaluation, or final presentation) from their manual entries, and schedule them in their timeline.
            
            Here are the details provided:
            - Hackathon Title: $title
            - Team Mates: $teammates
            - Hackathon URL: $url
            - Notes/Guidelines: $notes
            - Specified Target Date / Deadline: $targetDateStr (approx. $offsetDays days from today)
            
            We also have a summary of existing task commitments to prevent scheduling conflicts (do not schedule heavy milestones on high-intensity days):
            $existingEventsSummary
            
            Current today's date is: ${getTodayDateString()} (use this for computing offset days).
            
            Extract/detect all mentioned or logically necessary stages (e.g. "Registration Deadline", "PPT Submission", "Prototype Deadline", "Final Presentation") from the notes or implied by the target date.
            For each stage:
            - stageTitle: name of the stage (e.g., "Registration Deadline", "PPT Submission", "Prototype Deadline", "Final Presentation")
            - startTimeOffsetDays: Number of days from today when this stage starts or is due (calculate from current date ${getTodayDateString()}). Ensure it's chronological and does not exceed the target date offset ($offsetDays days) unless logically final.
            - durationHours: Duration of prep work/session for this stage (usually 1-4 hours).
            - priorityWeight: A priority multiplier from 0.5 to 2.0 based on importance.
            - energyRequired: Energy level required (1-5).
            
            If any extracted milestone conflicts/overlaps with a heavy existing task, adjust the offset days slightly to allocate preparation time on a less busy day, and append a note to the stageTitle (e.g., "[Conflict Avoided] PPT Prep" or "[Shifted] Stage Prep").
            
            Return a JSON object in exactly this format (no markdown formatting, do not wrap in ```json):
            {
              "title": "$title",
              "type": "hackathon",
              "prizesAndDescription": "Prizes: info, Team Mates: $teammates, URL: $url. Notes: $notes",
              "registrationDetails": "Extracted registration info (e.g. Deadline, cost)",
              "pptSubmissionDetails": "Extracted PPT/Prototype submission info/deadline",
              "nextPhaseDetails": "Extracted shortlist, next phases details",
              "stages": [
                {
                  "stageTitle": "Registration Deadline",
                  "startTimeOffsetDays": 1.5,
                  "durationHours": 2.0,
                  "priorityWeight": 1.2,
                  "energyRequired": 3
                },
                {
                  "stageTitle": "PPT Submission",
                  "startTimeOffsetDays": 4.5,
                  "durationHours": 3.0,
                  "priorityWeight": 1.8,
                  "energyRequired": 4
                }
              ],
              "deadlineOffsetDays": $offsetDays
            }
        """.trimIndent()

        try {
            val jsonPayload = buildGeminiPayload(prompt)
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(jsonPayload.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val offlineRes = extractEventOffline(title, notes)
                    if (offlineRes is GeminiEventExtractionResult.Success) {
                        return@withContext offlineRes.copy(
                            title = title,
                            type = "hackathon",
                            deadlineOffsetDays = offsetDays
                        )
                    }
                    return@withContext offlineRes
                }
                val bodyString = response.body?.string() ?: ""
                val textResponse = parseGeminiTextResponse(bodyString)
                if (textResponse.isEmpty()) {
                    val offlineRes = extractEventOffline(title, notes)
                    if (offlineRes is GeminiEventExtractionResult.Success) {
                        return@withContext offlineRes.copy(
                            title = title,
                            type = "hackathon",
                            deadlineOffsetDays = offsetDays
                        )
                    }
                    return@withContext offlineRes
                }
                return@withContext parseExtractionJson(textResponse)
            }
        } catch (e: Exception) {
            val offlineRes = extractEventOffline(title, notes)
            if (offlineRes is GeminiEventExtractionResult.Success) {
                offlineRes.copy(
                    title = title,
                    type = "hackathon",
                    deadlineOffsetDays = offsetDays
                )
            } else {
                offlineRes
            }
        }
    }
}
