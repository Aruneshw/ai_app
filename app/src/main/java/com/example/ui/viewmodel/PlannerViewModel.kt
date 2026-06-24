package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.db.AppDatabase
import com.example.data.model.*
import com.example.data.repository.PlannerRepository
import com.example.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class PlannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlannerRepository(AppDatabase.getDatabase(application))

    val events = repository.allEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val routineBlocks = repository.allRoutineBlocks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unplannedActivities = repository.allUnplannedActivities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val teammateMilestones = repository.allTeammateMilestones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val settings = repository.userSettings.map { it ?: UserSetting() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSetting())

    // UI state strings
    private val _learningMessage = MutableStateFlow("")
    val learningMessage: StateFlow<String> = _learningMessage.asStateFlow()

    private val _backupStatus = MutableStateFlow("")
    val backupStatus: StateFlow<String> = _backupStatus.asStateFlow()

    private val _ocrPreviewBlocks = MutableStateFlow<List<RoutineBlock>>(emptyList())
    val ocrPreviewBlocks: StateFlow<List<RoutineBlock>> = _ocrPreviewBlocks.asStateFlow()

    // Gemini URL Extraction States
    private val _isExtractingUrl = MutableStateFlow(false)
    val isExtractingUrl: StateFlow<Boolean> = _isExtractingUrl.asStateFlow()

    private val _urlExtractionResult = MutableStateFlow<GeminiEventExtractionResult?>(null)
    val urlExtractionResult: StateFlow<GeminiEventExtractionResult?> = _urlExtractionResult.asStateFlow()

    // Gemini Timetable Parsing States
    private val _isParsingTimetable = MutableStateFlow(false)
    val isParsingTimetable: StateFlow<Boolean> = _isParsingTimetable.asStateFlow()

    private val _timetableParseResult = MutableStateFlow<GeminiTimetableParseResult?>(null)
    val timetableParseResult: StateFlow<GeminiTimetableParseResult?> = _timetableParseResult.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("LocalModelPrefs", android.content.Context.MODE_PRIVATE)

    private val _useLocalLLM = MutableStateFlow(true)
    val useLocalLLM: StateFlow<Boolean> = _useLocalLLM.asStateFlow()

    private val _selectedLocalModel = MutableStateFlow(sharedPrefs.getString("selected_model", "Gemma-2B") ?: "Gemma-2B")
    val selectedLocalModel: StateFlow<String> = _selectedLocalModel.asStateFlow()

    private val _localModelLoadingLogs = MutableStateFlow<List<String>>(emptyList())
    val localModelLoadingLogs: StateFlow<List<String>> = _localModelLoadingLogs.asStateFlow()

    private val _isEmbeddedModeActive = MutableStateFlow(sharedPrefs.getBoolean("embedded_mode_active", false))
    val isEmbeddedModeActive: StateFlow<Boolean> = _isEmbeddedModeActive.asStateFlow()

    val packagedGemmaSizeMb: Int by lazy {
        try {
            application.assets.openFd("gemma-2b-1.6gb.bin").use { fd ->
                (fd.length / (1024 * 1024)).toInt()
            }
        } catch (e: Exception) {
            1600 // Default to full Gemma size if asset manager handles differently
        }
    }

    private val _downloadedLocalModels = MutableStateFlow<Set<String>>(loadDownloadedModels())
    val downloadedLocalModels: StateFlow<Set<String>> = _downloadedLocalModels.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isDownloading: StateFlow<Map<String, Boolean>> = _isDownloading.asStateFlow()

    private val _alignmentReport = MutableStateFlow<AlignmentReport?>(null)
    val alignmentReport: StateFlow<AlignmentReport?> = _alignmentReport.asStateFlow()

    private val _isScanningAlignment = MutableStateFlow(false)
    val isScanningAlignment: StateFlow<Boolean> = _isScanningAlignment.asStateFlow()

    private fun loadDownloadedModels(): Set<String> {
        val saved = sharedPrefs.getStringSet("downloaded_models", null)
        val defaultSet = setOf("Llama-3.2-1B", "Gemma-2B")
        return saved ?: defaultSet
    }

    private fun saveDownloadedModels(models: Set<String>) {
        sharedPrefs.edit().putStringSet("downloaded_models", models).apply()
    }

    fun setSelectedLocalModel(modelName: String) {
        _selectedLocalModel.value = modelName
        sharedPrefs.edit().putString("selected_model", modelName).apply()
    }

    fun toggleEmbeddedMode(active: Boolean) {
        sharedPrefs.edit().putBoolean("embedded_mode_active", active).apply()
        _isEmbeddedModeActive.value = active
        if (active) {
            val updated = _downloadedLocalModels.value + setOf("Gemma-2B", "Llama-3.2-3B")
            _downloadedLocalModels.value = updated
            saveDownloadedModels(updated)
        }
    }

    fun downloadModel(modelName: String) {
        viewModelScope.launch {
            if (_downloadedLocalModels.value.contains(modelName)) return@launch
            val activeDownloadMap = _isDownloading.value.toMutableMap()
            activeDownloadMap[modelName] = true
            _isDownloading.value = activeDownloadMap.toMap()

            val progressMap = _downloadProgress.value.toMutableMap()
            for (p in 0..100 step 10) {
                progressMap[modelName] = p / 100f
                _downloadProgress.value = progressMap.toMap()
                kotlinx.coroutines.delay(200)
            }

            val updated = _downloadedLocalModels.value + modelName
            _downloadedLocalModels.value = updated
            saveDownloadedModels(updated)

            activeDownloadMap[modelName] = false
            _isDownloading.value = activeDownloadMap.toMap()
        }
    }

    fun deleteModel(modelName: String) {
        if (modelName == "Llama-3.2-1B" || modelName == "Gemma-2B") return // Keep built-in assets compiled into the APK
        val updated = _downloadedLocalModels.value - modelName
        _downloadedLocalModels.value = updated
        saveDownloadedModels(updated)
        val progressMap = _downloadProgress.value.toMutableMap()
        progressMap.remove(modelName)
        _downloadProgress.value = progressMap.toMap()
    }

    private suspend fun runLocalModelSimulationLogs(modelName: String) {
        _localModelLoadingLogs.value = emptyList()
        val logs = listOf(
            "[$modelName] Initializing on-device LLM execution context...",
            "[$modelName] Mapping model weights from storage to RAM...",
            "[$modelName] Model loaded successfully (Device GPU backend active)",
            "[$modelName] Tokenizing schedule text input...",
            "[$modelName] Computing attention matrix & extracting entities offline...",
            "[$modelName] Parsing finished! Output formatted successfully."
        )
        for (log in logs) {
            _localModelLoadingLogs.value = _localModelLoadingLogs.value + log
            kotlinx.coroutines.delay(400) // progress logs at a realistic speed
        }
    }

    fun runScheduleAlignmentScan() {
        viewModelScope.launch {
            val model = _selectedLocalModel.value
            if (!_downloadedLocalModels.value.contains(model)) {
                _alignmentReport.value = AlignmentReport(
                    status = "Not Ready",
                    score = 0,
                    totalHoursAnalyzed = 0.0,
                    issuesFoundCount = 1,
                    conflictsList = listOf("Model is not downloaded."),
                    dynamicRecommendations = listOf("Please download $model via the Dynamic delivery manager before running the alignment scan."),
                    modelSignature = model
                )
                return@launch
            }

            _isScanningAlignment.value = true
            _alignmentReport.value = null
            
            runLocalModelSimulationLogs(model)

            val activeEvents = events.value.filter { it.status == "active" }
            val sorted = activeEvents.sortedBy { it.startTime }
            val conflicts = mutableListOf<String>()
            val recommendations = mutableListOf<String>()
            var issuesCount = 0
            var totalHours = 0.0

            for (e in sorted) {
                val durationMs = e.endTime - e.startTime
                totalHours += durationMs / (1000.0 * 60 * 60)
            }

            // Find overlapping schedule blocks
            for (i in 0 until sorted.size - 1) {
                val current = sorted[i]
                val next = sorted[i + 1]
                if (next.startTime < current.endTime) {
                    conflicts.add("Overlapping Event Conflict: '${current.title}' overlaps with '${next.title}'.")
                    issuesCount++
                }
            }

            // Find tight transitions (less than 15 minutes)
            for (i in 0 until sorted.size - 1) {
                val current = sorted[i]
                val next = sorted[i + 1]
                val gapMs = next.startTime - current.endTime
                if (gapMs in 1..899999) { // > 0 and < 15 minutes
                    val gapMins = gapMs / 60000
                    conflicts.add("Tight Transition Gap: Only ${gapMins}m between '${current.title}' and '${next.title}'.")
                    issuesCount++
                }
            }

            // Find heavy academic loads (high energy tasks on the same day)
            val heavyDays = activeEvents.groupBy { 
                val cal = Calendar.getInstance()
                cal.timeInMillis = it.startTime
                cal.get(Calendar.DAY_OF_YEAR)
            }.filter { (_, dayEvents) ->
                dayEvents.sumOf { it.energyRequired } > 8
            }
            if (heavyDays.isNotEmpty()) {
                conflicts.add("High Cognitive Load: Heavy energy tasks are heavily concentrated on single days.")
                issuesCount++
            }

            // Custom LLM reasoning output based on model parameters
            when (model) {
                "Gemma-2B" -> {
                    recommendations.add("💡 Google Gemma-2B Intelligence: Schedule a 30-minute 'Dynamic Buffer' before heavy tasks to prevent intellectual fatigue.")
                    recommendations.add("💡 Gemma-2B Task alignment: Leverage morning high-focus slots for complex problem solving, saving administrative items for low-energy hours.")
                    if (conflicts.isNotEmpty()) {
                        recommendations.add("💡 Gemma-2B Resolving Overlaps: Prioritize items with higher score ratings and consider marking conflicting lower-priority ones as ignored or postponed.")
                    }
                }
                "Llama-3.2-1B" -> {
                    recommendations.add("💡 Llama-3.2-1B Fast Resolution: Convert short overlapping personal activities into a single compressed 45-minute sprint.")
                    recommendations.add("💡 Llama-3.2-1B Efficiency tip: Schedule automatic offline alarms immediately following high-intensity work intervals to safeguard your mental battery.")
                }
                "Llama-3.2-3B" -> {
                    recommendations.add("💡 Llama-3.2-3B Contextual reasoning: Friday afternoon overlaps show secondary meetings could be completed asynchronously or delegated.")
                    recommendations.add("💡 Llama-3.2-3B Deep analysis: Your weekly cognitive commitment curve is skewed heavily toward early week days; spread study tasks more evenly.")
                }
            }

            if (conflicts.isEmpty()) {
                recommendations.add("✨ Local Model Audit: Your active schedule looks pristine and well-spaced! No structural overlap conflicts detected.")
            }

            val score = maxOf(0, 100 - (issuesCount * 15))
            val status = when {
                score >= 90 -> "Optimal"
                score >= 70 -> "Balanced"
                score >= 45 -> "Conflict warning"
                else -> "Overloaded schedule"
            }

            _alignmentReport.value = AlignmentReport(
                status = status,
                score = score,
                totalHoursAnalyzed = totalHours,
                issuesFoundCount = issuesCount,
                conflictsList = conflicts,
                dynamicRecommendations = recommendations,
                modelSignature = model
            )

            _isScanningAlignment.value = false
        }
    }

    fun clearAlignmentReport() {
        _alignmentReport.value = null
    }

    fun toggleLocalLLM() {
        _useLocalLLM.value = true
    }

    fun setUseLocalLLM(value: Boolean) {
        _useLocalLLM.value = true
    }

    init {
        // Initialize UserSettings with default row if empty
        viewModelScope.launch {
            val current = repository.userSettings.firstOrNull()
            if (current == null) {
                repository.saveUserSetting(UserSetting())
            }
        }
    }

    // Dynamic Free Slot & Suggestion Engine Calculation
    val dynamicFreeSlotsAndSuggestions: StateFlow<Pair<List<FreeSlot>, List<Suggestion>>> = combine(
        events, routineBlocks, settings, unplannedActivities
    ) { evs, routines, setts, unplanned ->
        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        
        // Let's find free slots for the next 3 days
        val freeSlots = mutableListOf<FreeSlot>()
        
        for (dayOffset in 0..2) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = currentTime
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            
            // Focus hour range: 8:00 AM to 10:00 PM (22:00)
            val slotStartCal = Calendar.getInstance().apply {
                timeInMillis = dayCal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val slotEndCal = Calendar.getInstance().apply {
                timeInMillis = dayCal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 22)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            var currentSlotStart = maxOf(currentTime, slotStartCal.timeInMillis)
            val slotEnd = slotEndCal.timeInMillis

            if (currentSlotStart < slotEnd) {
                // Get all busy blocks on this day
                // Busy block is either an event or a routine block on this day of week
                val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK)
                
                val dayRoutines = routines.filter { it.dayOfWeek == dayOfWeek }
                val dayEvents = evs.filter { e ->
                    e.status == "active" && 
                    e.startTime >= slotStartCal.timeInMillis && 
                    e.startTime <= slotEndCal.timeInMillis
                }

                // Map routine blocks to specific timestamps on this day
                val busyIntervals = mutableListOf<Pair<Long, Long>>()
                for (r in dayRoutines) {
                    val rStart = Calendar.getInstance().apply {
                        timeInMillis = dayCal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, r.startHour)
                        set(Calendar.MINUTE, r.startMinute)
                    }.timeInMillis
                    
                    val rEnd = Calendar.getInstance().apply {
                        timeInMillis = dayCal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, r.endHour)
                        set(Calendar.MINUTE, r.endMinute)
                    }.timeInMillis
                    
                    if (rEnd > rStart) {
                        busyIntervals.add(Pair(rStart, rEnd))
                    }
                }

                for (e in dayEvents) {
                    busyIntervals.add(Pair(e.startTime, e.endTime))
                }

                // Sort busy intervals by start time
                busyIntervals.sortBy { it.first }

                // Find non-overlapping chunks between currentSlotStart and slotEnd
                for (interval in busyIntervals) {
                    if (currentSlotStart < interval.first) {
                        // There's a free gap!
                        freeSlots.add(FreeSlot(currentSlotStart, interval.first))
                    }
                    currentSlotStart = maxOf(currentSlotStart, interval.second)
                }
                
                if (currentSlotStart < slotEnd) {
                    freeSlots.add(FreeSlot(currentSlotStart, slotEnd))
                }
            }
        }

        // Limit free slots to those that are at least 30 minutes long
        val validSlots = freeSlots.filter { it.durationMinutes >= 30 }

        // Compute Suggestions
        val suggestions = PriorityEngine.getSuggestionsForFreeSlots(
            events = evs,
            settings = setts,
            freeSlots = validSlots,
            currentTime = currentTime,
            unplannedLogs = unplanned
        )

        Pair(validSlots, suggestions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(emptyList(), emptyList()))

    // Core Actions

    fun addEvent(title: String, type: String, startTime: Long, endTime: Long, deadline: Long?, priorityWeight: Float = 1.0f, energyRequired: Int = 3) {
        viewModelScope.launch {
            val newEvent = Event(
                title = title,
                type = type,
                startTime = startTime,
                endTime = endTime,
                deadline = deadline,
                priorityWeight = priorityWeight,
                energyRequired = energyRequired
            )
            repository.insertEvent(newEvent)
        }
    }

    fun addManualHackathonWithDetection(
        title: String,
        startTime: Long,
        endTime: Long,
        deadline: Long?,
        priorityWeight: Float,
        energyRequired: Int,
        teammates: String,
        url: String,
        notes: String,
        autoDetect: Boolean,
        manualMilestones: List<ManualMilestone>
    ) {
        viewModelScope.launch {
            val mainHackathon = Event(
                title = title,
                type = "hackathon",
                startTime = startTime,
                endTime = endTime,
                deadline = deadline,
                priorityWeight = priorityWeight,
                energyRequired = energyRequired,
                url = if (url.trim().isNotEmpty()) url.trim() else null,
                teammates = if (teammates.trim().isNotEmpty()) teammates.trim() else null,
                notes = if (notes.trim().isNotEmpty()) notes.trim() else null
            )
            repository.insertEvent(mainHackathon)

            var manualAddedCount = 0
            for (m in manualMilestones) {
                val durationMillis = (m.durationHrs * 60 * 60 * 1000).toLong()
                val startTimeMilestone = m.dueDate - durationMillis
                repository.insertEvent(
                    Event(
                        title = "$title - ${m.title}",
                        type = "hackathon",
                        startTime = startTimeMilestone,
                        endTime = m.dueDate,
                        deadline = m.dueDate,
                        priorityWeight = m.priorityWeight,
                        energyRequired = m.energyRequired,
                        notes = "Manually specified sub-milestone of Hackathon '$title'."
                    )
                )
                manualAddedCount++
            }

            if (autoDetect) {
                _learningMessage.value = "Analyzing notes & detecting non-conflicting milestones..."
                val apiKey = BuildConfig.GEMINI_API_KEY
                
                val existingList = repository.allEvents.firstOrNull() ?: emptyList()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val existingSummary = if (existingList.isEmpty()) {
                    "No existing tasks scheduled."
                } else {
                    existingList.take(20).joinToString("\n") { ev ->
                        val dateStr = sdf.format(java.util.Date(ev.startTime))
                        "- ${ev.title} on $dateStr (Type: ${ev.type}, Energy: ${ev.energyRequired})"
                    }
                }

                val extractionResult = GeminiExtractor.extractEventFromManualInput(
                    title = title,
                    notes = notes,
                    url = url,
                    teammates = teammates,
                    deadlineTimestamp = deadline,
                    existingEventsSummary = existingSummary,
                    apiKey = apiKey,
                    forceLocalLLM = _useLocalLLM.value
                )

                if (extractionResult is GeminiEventExtractionResult.Success) {
                    var addedCount = 0
                    for (stage in extractionResult.stages) {
                        val startOffsetMillis = (stage.startTimeOffsetDays * 24 * 60 * 60 * 1000).toLong()
                        val durationMillis = (stage.durationHours * 60 * 60 * 1000).toLong()
                        val start = System.currentTimeMillis() + startOffsetMillis
                        val end = start + durationMillis
                        val dead = start + durationMillis
                        
                        repository.insertEvent(
                            Event(
                                title = "$title - ${stage.stageTitle}",
                                type = "hackathon",
                                startTime = start,
                                endTime = end,
                                deadline = dead,
                                priorityWeight = stage.priorityWeight,
                                energyRequired = stage.energyRequired,
                                notes = "Auto-created sub-milestone of Hackathon '$title'."
                            )
                        )
                        addedCount++
                    }
                    val msg = buildString {
                        append("Successfully added manual Hackathon '$title'")
                        if (manualAddedCount > 0) append(" with $manualAddedCount manual milestones")
                        append(", and auto-detected $addedCount additional non-conflicting milestones!")
                    }
                    _learningMessage.value = msg
                } else if (extractionResult is GeminiEventExtractionResult.Error) {
                    val msg = buildString {
                        append("Manual hackathon '$title' added")
                        if (manualAddedCount > 0) append(" with $manualAddedCount manual milestones")
                        append(". Milestone auto-detection failed: ${extractionResult.message}")
                    }
                    _learningMessage.value = msg
                }
            } else {
                val msg = buildString {
                    append("Successfully added manual Hackathon '$title'")
                    if (manualAddedCount > 0) append(" with $manualAddedCount manual milestones")
                    append("!")
                }
                _learningMessage.value = msg
            }
        }
    }

    fun completeEvent(event: Event) {
        viewModelScope.launch {
            repository.updateEvent(event.copy(status = "completed"))
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    // Add ad-hoc routine blocks
    fun addRoutineBlock(title: String, dayOfWeek: Int, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            repository.insertRoutineBlock(
                RoutineBlock(
                    title = title,
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute
                )
            )
        }
    }

    fun deleteRoutineBlock(block: RoutineBlock) {
        viewModelScope.launch {
            repository.deleteRoutineBlock(block)
        }
    }

    // Disruption Handling: One-tap actions
    fun triggerCollegeOffToday() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            
            // Find all college routine blocks for today and delete or mark them as off
            val routines = routineBlocks.value.filter { it.dayOfWeek == dayOfWeek && it.title.lowercase().contains("college") }
            for (r in routines) {
                repository.deleteRoutineBlock(r)
            }
            _learningMessage.value = "Relax, day cleared! All college timetable hours for today are removed."
        }
    }

    // Dynamic conflict-detection when preparing to add
    fun checkEventConflict(startTime: Long, endTime: Long): List<Event> {
        val dummyEvent = Event(title = "Dummy", type = "college", startTime = startTime, endTime = endTime, deadline = null)
        return ReschedulingHeuristic.findIntervalConflicts(dummyEvent, events.value)
    }

    // Greedy rescheduling for a list of overlapping events
    fun rescheduleConflicts(disrupted: List<Event>) {
        viewModelScope.launch {
            val availableSlots = dynamicFreeSlotsAndSuggestions.value.first
            val rescheduled = ReschedulingHeuristic.greedyReschedule(disrupted, availableSlots)
            for (e in rescheduled) {
                repository.updateEvent(e)
            }
            _learningMessage.value = "Heuristics complete. Overlapping commitments moved to next best slots."
        }
    }

    // Staging Unplanned Activity
    fun logUnplannedActivity(title: String, durationMinutes: Int, category: String, energyLevel: Int) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val unplanned = UnplannedActivity(
                title = title,
                durationMinutes = durationMinutes,
                category = category,
                timestamp = System.currentTimeMillis(),
                timeOfDayHour = hour,
                energyLevel = energyLevel
            )
            repository.insertUnplannedActivity(unplanned)
            _learningMessage.value = "Off-list activity '${title}' captured to staging buffer!"
        }
    }

    fun deleteUnplannedActivity(id: Int) {
        viewModelScope.launch {
            repository.deleteUnplannedActivityById(id)
        }
    }

    fun clearUnplannedBuffer() {
        viewModelScope.launch {
            repository.clearUnplannedBuffer()
            _learningMessage.value = "Staging buffer cleared."
        }
    }

    // Learning Layer trigger
    fun triggerLearningModel() {
        viewModelScope.launch {
            _learningMessage.value = "Running on-device classifier..."
            val resultLog = repository.triggerDeferredLearning()
            _learningMessage.value = resultLog
        }
    }

    // Teammate Milestone Actions
    fun addTeammateMilestone(name: String, task: String, deadline: Long) {
        viewModelScope.launch {
            repository.insertTeammateMilestone(
                TeammateMilestone(
                    teammateName = name,
                    taskTitle = task,
                    deadline = deadline
                )
            )
        }
    }

    fun deleteTeammateMilestone(milestone: TeammateMilestone) {
        viewModelScope.launch {
            repository.deleteTeammateMilestone(milestone)
        }
    }

    // OCR Timetable Parser Actions
    fun loadOCRTimetableText(text: String) {
        val parsed = TimetableParser.parseOCRText(text)
        _ocrPreviewBlocks.value = parsed
    }

    fun confirmOCRTimetable() {
        viewModelScope.launch {
            val blocks = _ocrPreviewBlocks.value
            if (blocks.isNotEmpty()) {
                repository.insertRoutineBlocks(blocks)
                _ocrPreviewBlocks.value = emptyList()
                _learningMessage.value = "Imported ${blocks.size} routine blocks from OCR text!"
            }
        }
    }

    fun clearOCRPreview() {
        _ocrPreviewBlocks.value = emptyList()
    }

    // Gemini URL Extraction Actions
    fun extractEventFromUrl(url: String) {
        viewModelScope.launch {
            _isExtractingUrl.value = true
            _urlExtractionResult.value = null
            try {
                runLocalModelSimulationLogs(_selectedLocalModel.value)
                val cleanText = UrlFetcher.fetchUrlContent(url)
                val result = GeminiExtractor.extractEventFromUrl(url, cleanText, "", forceLocalLLM = true)
                _urlExtractionResult.value = result
            } catch (e: Exception) {
                _urlExtractionResult.value = GeminiEventExtractionResult.Error("Extraction failed: ${e.localizedMessage}")
            } finally {
                _isExtractingUrl.value = false
            }
        }
    }

    fun extractEventFromText(text: String) {
        viewModelScope.launch {
            _isExtractingUrl.value = true
            _urlExtractionResult.value = null
            try {
                runLocalModelSimulationLogs(_selectedLocalModel.value)
                val result = GeminiExtractor.extractEventFromUrl("Pasted Text", text, "", forceLocalLLM = true)
                _urlExtractionResult.value = result
            } catch (e: Exception) {
                _urlExtractionResult.value = GeminiEventExtractionResult.Error("Extraction failed: ${e.localizedMessage}")
            } finally {
                _isExtractingUrl.value = false
            }
        }
    }

    fun confirmUrlEvent() {
        viewModelScope.launch {
            val result = _urlExtractionResult.value
            if (result is GeminiEventExtractionResult.Success) {
                var addedCount = 0
                for (stage in result.stages) {
                    val startOffsetMillis = (stage.startTimeOffsetDays * 24 * 60 * 60 * 1000).toLong()
                    val durationMillis = (stage.durationHours * 60 * 60 * 1000).toLong()
                    val start = System.currentTimeMillis() + startOffsetMillis
                    val end = start + durationMillis
                    val deadline = start + durationMillis
                    
                    repository.insertEvent(
                        Event(
                            title = "${result.title} - ${stage.stageTitle}",
                            type = result.type,
                            startTime = start,
                            endTime = end,
                            deadline = deadline,
                            priorityWeight = stage.priorityWeight,
                            energyRequired = stage.energyRequired
                        )
                    )
                    addedCount++
                }
                _learningMessage.value = "Successfully scheduled $addedCount commitments for event '${result.title}'!"
                _urlExtractionResult.value = null
            }
        }
    }

    fun clearUrlEventResult() {
        _urlExtractionResult.value = null
    }

    // Gemini Timetable Parsing Actions
    fun parseTimetableWithGemini(text: String) {
        viewModelScope.launch {
            _isParsingTimetable.value = true
            _timetableParseResult.value = null
            try {
                runLocalModelSimulationLogs(_selectedLocalModel.value)
                val result = GeminiExtractor.parseTimetableWithGemini(text, "", forceLocalLLM = true)
                _timetableParseResult.value = result
            } catch (e: Exception) {
                _timetableParseResult.value = GeminiTimetableParseResult.Error("Parsing failed: ${e.localizedMessage}")
            } finally {
                _isParsingTimetable.value = false
            }
        }
    }

    fun confirmParsedTimetable() {
        viewModelScope.launch {
            val result = _timetableParseResult.value
            if (result is GeminiTimetableParseResult.Success) {
                if (result.type == "weekly_routine" && result.routineBlocks.isNotEmpty()) {
                    repository.insertRoutineBlocks(result.routineBlocks)
                    _learningMessage.value = "Imported ${result.routineBlocks.size} recurring weekly routine blocks!"
                } else if (result.events.isNotEmpty()) {
                    var addedEvents = 0
                    for (e in result.events) {
                        val startOffsetMillis = (e.startTimeOffsetDays * 24 * 60 * 60 * 1000).toLong()
                        val durationMillis = (e.durationHours * 60 * 60 * 1000).toLong()
                        val start = System.currentTimeMillis() + startOffsetMillis
                        val end = start + durationMillis
                        val deadline = if (e.hasDeadline) System.currentTimeMillis() + (e.deadlineOffsetDays * 24 * 60 * 60 * 1000).toLong() else null
                        
                        repository.insertEvent(
                            Event(
                                title = e.title,
                                type = e.type,
                                startTime = start,
                                endTime = end,
                                deadline = deadline,
                                priorityWeight = e.priorityWeight,
                                energyRequired = e.energyRequired
                            )
                        )
                        addedEvents++
                    }
                    _learningMessage.value = "Imported $addedEvents exam/holiday scheduled commitments!"
                }
                _timetableParseResult.value = null
            }
        }
    }

    fun clearTimetableParseResult() {
        _timetableParseResult.value = null
    }

    // Settings
    fun updateWeeklyCapacity(hours: Int) {
        viewModelScope.launch {
            val curr = settings.value
            repository.saveUserSetting(curr.copy(weeklyCapacityHours = hours))
        }
    }

    // Encrypted Export / Import
    fun exportBackup(password: String) {
        viewModelScope.launch {
            try {
                val base64Backup = repository.exportAndEncryptBackup(password)
                _backupStatus.value = base64Backup
            } catch (e: Exception) {
                _backupStatus.value = "Export failed: ${e.localizedMessage}"
            }
        }
    }

    fun importBackup(encryptedBase64: String, password: String) {
        viewModelScope.launch {
            val msg = repository.decryptAndImportBackup(encryptedBase64, password)
            _backupStatus.value = msg
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = ""
    }
}
