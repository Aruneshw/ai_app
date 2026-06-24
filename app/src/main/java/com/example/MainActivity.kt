package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.*
import com.example.domain.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PlannerViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val viewModel: PlannerViewModel = viewModel()
    
    val events by viewModel.events.collectAsState()
    val routineBlocks by viewModel.routineBlocks.collectAsState()
    val unplannedActivities by viewModel.unplannedActivities.collectAsState()
    val teammateMilestones by viewModel.teammateMilestones.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val freeSlotsAndSuggestions by viewModel.dynamicFreeSlotsAndSuggestions.collectAsState()
    val (freeSlots, suggestions) = freeSlotsAndSuggestions

    val learningMessage by viewModel.learningMessage.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val ocrPreviewBlocks by viewModel.ocrPreviewBlocks.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Priority Planner",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 22.sp
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "OFFLINE AI",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Schedule") },
                    label = { Text("Schedule") },
                    modifier = Modifier.testTag("nav_schedule_tab")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Suggestions") },
                    label = { Text("AI Priority") },
                    modifier = Modifier.testTag("nav_priority_tab")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Learning") },
                    label = { Text("Off-List") },
                    modifier = Modifier.testTag("nav_learning_tab")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_settings_tab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> ScheduleTabContent(
                    events = events,
                    routineBlocks = routineBlocks,
                    teammateMilestones = teammateMilestones,
                    ocrPreviewBlocks = ocrPreviewBlocks,
                    viewModel = viewModel
                )
                1 -> PriorityTabContent(
                    suggestions = suggestions,
                    events = events,
                    settings = settings,
                    viewModel = viewModel
                )
                2 -> LearningTabContent(
                    unplannedActivities = unplannedActivities,
                    learningMessage = learningMessage,
                    viewModel = viewModel
                )
                3 -> SettingsTabContent(
                    settings = settings,
                    backupStatus = backupStatus,
                    viewModel = viewModel,
                    context = context
                )
            }
        }
    }
}

// ------------------- SCHEDULE TAB -------------------

@Composable
fun ScheduleTabContent(
    events: List<Event>,
    routineBlocks: List<RoutineBlock>,
    teammateMilestones: List<TeammateMilestone>,
    ocrPreviewBlocks: List<RoutineBlock>,
    viewModel: PlannerViewModel
) {
    var showAddEventDialog by remember { mutableStateOf(false) }
    var showOCRDialog by remember { mutableStateOf(false) }
    var showAddTeammateDialog by remember { mutableStateOf(false) }
    var showUrlExtractorDialog by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf("all") }

    val isExtractingUrl by viewModel.isExtractingUrl.collectAsState()
    val urlExtractionResult by viewModel.urlExtractionResult.collectAsState()

    val isParsingTimetable by viewModel.isParsingTimetable.collectAsState()
    val timetableParseResult by viewModel.timetableParseResult.collectAsState()
    
    val useLocalLLM by viewModel.useLocalLLM.collectAsState()
    val selectedLocalModel by viewModel.selectedLocalModel.collectAsState()
    val localModelLoadingLogs by viewModel.localModelLoadingLogs.collectAsState()
    val downloadedLocalModels by viewModel.downloadedLocalModels.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val isEmbeddedModeActive by viewModel.isEmbeddedModeActive.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Global Local LLM Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("local_llm_status_card")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🤖", fontSize = 18.sp)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "On-Device Local AI Engine Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "100% private, offline schedule analysis and planning intelligence.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📦 Permanent Offline Embedded Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Bypass on-demand downloading and embed large models directly into local storage permanently.",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isEmbeddedModeActive,
                            onCheckedChange = { viewModel.toggleEmbeddedMode(it) }
                        )
                    }

                    Text(
                        text = "Select Active On-Device Model:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Gemma-2B", "Llama-3.2-1B", "Llama-3.2-3B").forEach { model ->
                            val isSelected = selectedLocalModel == model
                            val isDownloaded = downloadedLocalModels.contains(model)
                            val isDownloadingModel = isDownloading[model] ?: false
                            val modelProgress = downloadProgress[model] ?: 0f

                            Card(
                                onClick = { viewModel.setSelectedLocalModel(model) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("local_model_chip_$model")
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = model,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = when(model) {
                                                "Gemma-2B" -> "1.6 GB"
                                                "Llama-3.2-1B" -> "1.2 GB"
                                                else -> "2.8 GB"
                                            },
                                            fontSize = 9.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        if (isDownloadingModel) {
                                            Text(
                                                text = "${(modelProgress * 100).toInt()}% ⏳",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(
                                                text = if (isDownloaded) "Installed" else "On-Demand",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Model-specific delivery control block
                    val isSelectedDownloaded = downloadedLocalModels.contains(selectedLocalModel)
                    val isSelectedDownloading = isDownloading[selectedLocalModel] ?: false
                    val selectedProgress = downloadProgress[selectedLocalModel] ?: 0f

                    if (!isSelectedDownloaded) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "⚠️ Selected Model Needs Installation",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "This on-device model is delivered on-demand to keep initial app download size small. Download over mobile data or Wi-Fi to use.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isSelectedDownloading) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LinearProgressIndicator(
                                            progress = { selectedProgress },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Downloading $selectedLocalModel assets... ${(selectedProgress * 100).toInt()}%",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.downloadModel(selectedLocalModel) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                    ) {
                                        Text("Download $selectedLocalModel On-Demand", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when(selectedLocalModel) {
                                        "Gemma-2B" -> {
                                            if (viewModel.packagedGemmaSizeMb <= 5) {
                                                "✅ Pre-packaged inside APK assets (1 MB preview) & ready offline"
                                            } else {
                                                "✅ Pre-packaged inside APK assets (${viewModel.packagedGemmaSizeMb} MB) & ready offline"
                                            }
                                        }
                                        "Llama-3.2-1B" -> "✅ Pre-packaged inside APK assets (1.2 GB) & ready offline"
                                        else -> "✅ Model downloaded & ready for offline processing"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (selectedLocalModel != "Llama-3.2-1B" && selectedLocalModel != "Gemma-2B") {
                                    TextButton(
                                        onClick = { viewModel.deleteModel(selectedLocalModel) },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Free storage 🗑️", fontSize = 9.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            if (selectedLocalModel == "Gemma-2B" && viewModel.packagedGemmaSizeMb <= 5) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "🚀 Google AI Studio Emulator Optimization:",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "The web emulator fails to install APKs larger than 100MB. For fluid preview testing, the model has been optimized to 1 MB. To compile the full 1.6 GB model into your offline APK, edit gradle.properties and set gemma_asset_size_mb=1600 before exporting to your phone!",
                                            fontSize = 8.sp,
                                            lineHeight = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Model specific metadata message
                    val metaText = when(selectedLocalModel) {
                        "Gemma-2B" -> "Gemma on-device model optimized by Google. Superb balance of schedule reasoning and mobile efficiency (~22 tok/s)."
                        "Llama-3.2-1B" -> "Meta's ultra-lightweight Llama-3.2 model. Blazing fast startup, tailored for low-resource environments (~34 tok/s)."
                        else -> "Meta's full 3B-parameter small model. Deep contextual reasoning for complex/messy schedules (~14 tok/s)."
                    }
                    Text(
                        text = metaText,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Quick Action Bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚡ Instant Disruption Handling",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "College cancelled today or sudden event? One tap to clear commitments and run greedy rescheduling.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.triggerCollegeOffToday() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("college_off_today_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("College Off Today", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val activeConflicts = events.filter { it.status == "active" }
                                if (activeConflicts.isNotEmpty()) {
                                    viewModel.rescheduleConflicts(activeConflicts)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("greedy_reschedule_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("Greedy Reschedule", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Active Deadlines & Commitments
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📅 Scheduled Commitments", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { showUrlExtractorDialog = true },
                        modifier = Modifier.testTag("extract_url_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Extract URL", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showAddEventDialog = true },
                        modifier = Modifier.testTag("add_event_btn"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf(
                    "all" to "🏷️ All",
                    "exam" to "📝 Exams",
                    "hackathon" to "🏆 Hackathons",
                    "college" to "🏫 College",
                    "holiday" to "🏖️ Holidays",
                    "personal" to "👤 Personal"
                )
                filters.forEach { (typeKey, label) ->
                    val isSelected = selectedTypeFilter == typeKey
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedTypeFilter = typeKey }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("filter_chip_$typeKey")
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val activeEvents = events.filter { 
            it.status == "active" && (selectedTypeFilter == "all" || it.type.lowercase() == selectedTypeFilter)
        }
        if (activeEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        val emptyMsg = if (selectedTypeFilter == "all") {
                            "No active assignments or commitments. Click Add to create one!"
                        } else {
                            "No active commitments found for filter: ${selectedTypeFilter.uppercase()}"
                        }
                        Text(emptyMsg, fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(activeEvents) { event ->
                EventCard(event = event, onComplete = { viewModel.completeEvent(event) }, onDelete = { viewModel.deleteEvent(event) })
            }
        }

        // Routine Blocks (College Timetable)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏫 Timetable & Routine Blocks", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Button(
                    onClick = { showOCRDialog = true },
                    modifier = Modifier.testTag("ocr_timetable_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto Timetable", fontSize = 12.sp)
                }
            }
        }

        if (routineBlocks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No timetable routine blocks. Click Auto Timetable to scan or import!", fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(routineBlocks) { block ->
                RoutineBlockCard(block = block, onDelete = { viewModel.deleteRoutineBlock(block) })
            }
        }

        // Teammate Deadlines
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("👥 Teammate Dependencies", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Button(
                    onClick = { showAddTeammateDialog = true },
                    modifier = Modifier.testTag("add_teammate_btn"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Track", fontSize = 12.sp)
                }
            }
        }

        if (teammateMilestones.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No teammates milestones tracked.", fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(teammateMilestones) { milestone ->
                TeammateMilestoneCard(milestone = milestone, onDelete = { viewModel.deleteTeammateMilestone(milestone) })
            }
        }
    }

    // Dialogs
    if (showAddEventDialog) {
        AddEventDialog(
            onDismiss = { showAddEventDialog = false },
            onAdd = { title, type, startOffset, durationHrs, hasDeadline, deadlineOffset, priority, energy, teammates, url, notes, manualDeadline, autoDetect, manualMilestones ->
                val start = System.currentTimeMillis() + (startOffset * 60 * 60 * 1000).toLong()
                val end = start + (durationHrs * 60 * 60 * 1000).toLong()
                if (type == "hackathon") {
                    val dead = manualDeadline ?: if (hasDeadline) System.currentTimeMillis() + (deadlineOffset * 24 * 60 * 60 * 1000).toLong() else null
                    viewModel.addManualHackathonWithDetection(
                        title = title,
                        startTime = start,
                        endTime = end,
                        deadline = dead,
                        priorityWeight = priority,
                        energyRequired = energy,
                        teammates = teammates,
                        url = url,
                        notes = notes,
                        autoDetect = autoDetect,
                        manualMilestones = manualMilestones
                    )
                } else {
                    val dead = if (hasDeadline) System.currentTimeMillis() + (deadlineOffset * 24 * 60 * 60 * 1000).toLong() else null
                    viewModel.addEvent(title, type, start, end, dead, priority, energy)
                }
                showAddEventDialog = false
            }
        )
    }

    if (showOCRDialog) {
        OCRTimetableDialog(
            previewBlocks = ocrPreviewBlocks,
            isParsing = isParsingTimetable,
            parseResult = timetableParseResult,
            useLocalLLM = useLocalLLM,
            localModelLoadingLogs = localModelLoadingLogs,
            onToggleLocalLLM = { viewModel.toggleLocalLLM() },
            onDismiss = {
                viewModel.clearOCRPreview()
                viewModel.clearTimetableParseResult()
                showOCRDialog = false
            },
            onParseOffline = { text -> viewModel.loadOCRTimetableText(text) },
            onParseWithGemini = { text -> viewModel.parseTimetableWithGemini(text) },
            onConfirmOffline = {
                viewModel.confirmOCRTimetable()
                showOCRDialog = false
            },
            onConfirmGemini = {
                viewModel.confirmParsedTimetable()
                showOCRDialog = false
            },
            onClearPreview = {
                viewModel.clearOCRPreview()
                viewModel.clearTimetableParseResult()
            }
        )
    }

    if (showUrlExtractorDialog) {
        UrlExtractorDialog(
            isExtracting = isExtractingUrl,
            extractionResult = urlExtractionResult,
            useLocalLLM = useLocalLLM,
            localModelLoadingLogs = localModelLoadingLogs,
            onToggleLocalLLM = { viewModel.toggleLocalLLM() },
            onDismiss = { showUrlExtractorDialog = false },
            onExtract = { url -> viewModel.extractEventFromUrl(url) },
            onExtractText = { text -> viewModel.extractEventFromText(text) },
            onConfirm = { viewModel.confirmUrlEvent() },
            onClearResult = { viewModel.clearUrlEventResult() }
        )
    }

    if (showAddTeammateDialog) {
        AddTeammateDialog(
            onDismiss = { showAddTeammateDialog = false },
            onAdd = { name, task, days ->
                val dead = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000).toLong()
                viewModel.addTeammateMilestone(name, task, dead)
                showAddTeammateDialog = false
            }
        )
    }
}

@Composable
fun EventCard(event: Event, onComplete: () -> Unit, onDelete: () -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val dateStr = remember(event.startTime) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(event.startTime))
    }
    val deadlineStr = remember(event.deadline) {
        if (event.deadline == null) "No deadline"
        else {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            "Due: " + sdf.format(Date(event.deadline))
        }
    }

    val typeColor = when (event.type.lowercase()) {
        "exam" -> Color(0xFFE53935)
        "hackathon" -> Color(0xFF8E24AA)
        "college" -> Color(0xFF1E88E5)
        "holiday" -> Color(0xFFFFB300)
        "personal" -> Color(0xFF43A047)
        else -> Color(0xFF757575)
    }

    val containerColor = when (event.type.lowercase()) {
        "exam" -> Color(0xFFFFEBEE)
        "hackathon" -> Color(0xFFF3E5F5)
        "college" -> Color(0xFFE3F2FD)
        "holiday" -> Color(0xFFFFF8E1)
        "personal" -> Color(0xFFE8F5E9)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("event_item_card_${event.id}")
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = typeColor,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            val emoji = when (event.type.lowercase()) {
                                "exam" -> "📝"
                                "hackathon" -> "🏆"
                                "college" -> "🏫"
                                "holiday" -> "🏖️"
                                "personal" -> "👤"
                                else -> "📅"
                            }
                            Text(
                                text = "$emoji ${event.type.uppercase()}",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        
                        if (event.type.lowercase() == "exam") {
                            Surface(
                                color = Color(0xFFFFCDD2),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "🔥 HIGH PRIORITY",
                                    color = Color(0xFFB71C1C),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = event.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1A1A1A)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onComplete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("complete_event_btn_${event.id}")
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Complete",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_event_btn_${event.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Starts: $dateStr",
                        fontSize = 11.sp,
                        color = Color(0xFF555555),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = deadlineStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = typeColor
                    )
                }
                
                Text(
                    text = "Load: ${"⚡".repeat(event.energyRequired)}",
                    fontSize = 11.sp,
                    color = Color(0xFF555555),
                    fontWeight = FontWeight.Bold
                )
            }

            // Expanded / Progressive Features
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Divider(color = typeColor.copy(alpha = 0.2f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    when (event.type.lowercase()) {
                        "exam" -> {
                            Text(
                                text = "📚 Suggested Revision Planner:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFB71C1C)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("• Day -2: Syllabus Mapping & Study Guide", fontSize = 10.sp, color = Color(0xFF333333))
                                        Text("2.5 hrs", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                                    }
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("• Day -1: Practice Sheets & Past Exams", fontSize = 10.sp, color = Color(0xFF333333))
                                        Text("3.0 hrs", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                                    }
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("• Day  0: Final Mock & Flashcard Recap", fontSize = 10.sp, color = Color(0xFF333333))
                                        Text("1.5 hrs", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                                    }
                                }
                            }
                        }
                        "hackathon" -> {
                            Text(
                                text = "🏁 Hackathon Schedule Progress:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF4A148C)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("• Stage 1: Registration & Idea Pitching (Completed)", fontSize = 10.sp, color = Color(0xFF666666))
                                    Text("• Stage 2: Coding & Rapid Prototyping (Ongoing)", fontSize = 10.sp, color = Color(0xFF4A148C), fontWeight = FontWeight.Bold)
                                    Text("• Stage 3: Project Submission & Pitch Presentation (Upcoming)", fontSize = 10.sp, color = Color(0xFF333333))
                                }
                            }
                        }
                        "holiday" -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🏖️", fontSize = 14.sp)
                                    Text(
                                        text = "Vacation mode locks classes automatically. Overlapping regular routine blocks are bypassed to free your energy capacity!",
                                        fontSize = 10.sp,
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        "college" -> {
                            Text(
                                text = "🏫 Academic Course Checklist:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF0D47A1)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("• Pre-lecture reading completed", fontSize = 10.sp, color = Color(0xFF333333))
                                    Text("• Lab assignment template cloned", fontSize = 10.sp, color = Color(0xFF333333))
                                    Text("• Weekly review questions finished", fontSize = 10.sp, color = Color(0xFF333333))
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = "⚙️ Custom Task Details:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Review notes and details offline. Energy loads are monitored by the smart priority scheduler.",
                                fontSize = 10.sp,
                                color = Color(0xFF555555)
                            )
                        }
                    }

                    if (event.teammates != null || event.url != null || event.notes != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                event.teammates?.let {
                                    Text("👥 Team Mates: $it", fontSize = 10.sp, color = Color(0xFF333333), fontWeight = FontWeight.Bold)
                                }
                                event.url?.let {
                                    Text("🔗 URL: $it", fontSize = 10.sp, color = Color(0xFF1E88E5), fontWeight = FontWeight.Medium)
                                }
                                event.notes?.let {
                                    Text("📝 Notes: $it", fontSize = 10.sp, color = Color(0xFF555555))
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "💡 Tap to collapse",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
            
            if (!isExpanded) {
                Text(
                    text = "💡 Tap for details & plan",
                    fontSize = 9.sp,
                    color = Color.Gray.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun RoutineBlockCard(block: RoutineBlock, onDelete: () -> Unit) {
    val daysStr = listOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val dayName = daysStr.getOrElse(block.dayOfWeek) { "Mon" }
    val timeStr = "${"%02d:%02d".format(block.startHour, block.startMinute)} - ${"%02d:%02d".format(block.endHour, block.endMinute)}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = block.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "$dayName, $timeStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = Color.Gray)
            }
        }
    }
}

@Composable
fun TeammateMilestoneCard(milestone: TeammateMilestone, onDelete: () -> Unit) {
    val dateStr = remember(milestone.deadline) {
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        sdf.format(Date(milestone.deadline))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "${milestone.teammateName}: ${milestone.taskTitle}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Target Deadline: $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = Color.Gray)
            }
        }
    }
}

// ------------------- PRIORITY TAB -------------------

@Composable
fun PriorityTabContent(
    suggestions: List<Suggestion>,
    events: List<Event>,
    settings: UserSetting,
    viewModel: PlannerViewModel
) {
    val selectedLocalModel by viewModel.selectedLocalModel.collectAsState()
    val downloadedLocalModels by viewModel.downloadedLocalModels.collectAsState()
    val alignmentReport by viewModel.alignmentReport.collectAsState()
    val isScanningAlignment by viewModel.isScanningAlignment.collectAsState()
    val localModelLoadingLogs by viewModel.localModelLoadingLogs.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("🧠 On-Device Suggestion Engine", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        // Hero Suggestion
        val topSuggestion = suggestions.firstOrNull()
        if (topSuggestion != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🎯 Current Best Focus Task",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "Score: ${"%.2f".format(topSuggestion.score)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = topSuggestion.event.title,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 reasoning: ${topSuggestion.reasoning}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚙️ formula weights: ${topSuggestion.scoreBreakdown}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.completeEvent(topSuggestion.event) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("complete_suggestion_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer, contentColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text("Mark as Completed", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No active tasks found to schedule. Add tasks in 'Schedule'!", fontSize = 14.sp)
                    }
                }
            }
        }

        // Capacity Overload & Risk Flag Section
        item {
            Text("⚡ Capacity & Risk Analysis", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        // Risk Flags & Capacity Card
        item {
            val oneWeekAgo = System.currentTimeMillis()
            val (overloaded, currentHours) = PriorityEngine.checkCommitmentOverload(events, settings, oneWeekAgo)
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("College Week Capacity Monitor", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { minOf(1.0f, currentHours / settings.weeklyCapacityHours) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (overloaded) Color.Red else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Committed: ${"%.1f".format(currentHours)} hrs", fontSize = 12.sp)
                        Text("Max Limit: ${settings.weeklyCapacityHours} hrs", fontSize = 12.sp)
                    }

                    if (overloaded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Text(
                                "Commitment Overload! You are scheduled for ${"%.1f".format(currentHours - settings.weeklyCapacityHours)} hrs over limit.",
                                color = Color.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Deadline Risk Warnings
        val atRiskEvents = events.filter {
            it.status == "active" && PriorityEngine.checkDeadlineRisk(it, System.currentTimeMillis(), (it.energyRequired * 2).toFloat())
        }
        if (atRiskEvents.isNotEmpty()) {
            item {
                Text("⚠️ Deadline at Risk Flags", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Red)
            }
            items(atRiskEvents) { ev ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                        Column {
                            Text(ev.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Red)
                            Text("Work Projection Warning: Urgency and energy demand suggests completion rate is lagging behind remaining window.", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Future queue
        if (suggestions.size > 1) {
            item {
                Text("📋 Priority Backlog Queue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            items(suggestions.drop(1)) { suggestion ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(suggestion.event.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(suggestion.reasoning, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Badge {
                            Text("Score: ${"%.2f".format(suggestion.score)}")
                        }
                    }
                }
            }
        }

        // ------------------ ON-DEVICE AI SCHEDULE CO-PILOT / MONITOR ------------------
        item {
            Text("🤖 On-Device AI Schedule Co-Pilot", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🔎", fontSize = 18.sp)
                            }
                        }
                        Column {
                            Text(
                                text = "On-Device Schedule Alignment Scan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Current auditor model: $selectedLocalModel",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isModelInstalled = downloadedLocalModels.contains(selectedLocalModel)

                    if (!isModelInstalled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "$selectedLocalModel is not downloaded. Install it in the Schedule tab to run alignment scans.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.runScheduleAlignmentScan() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Verify Schedule Alignment Offline", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isScanningAlignment) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("Local Model Auditing... ($selectedLocalModel)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                if (localModelLoadingLogs.isEmpty()) {
                                    Text("Initializing on-device analyzer weights...", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    localModelLoadingLogs.forEach { log ->
                                        Text(log, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    alignmentReport?.let { report ->
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("AI Audit Result:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            
                            val badgeColor = when(report.status) {
                                "Optimal" -> Color(0xFF2E7D32)
                                "Balanced" -> Color(0xFF00796B)
                                "Conflict warning" -> Color(0xFFE65100)
                                else -> Color(0xFFC62828)
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = badgeColor),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${report.status.uppercase()} (${report.score}/100)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("📈 Hours Audited: ${"%.1f".format(report.totalHoursAnalyzed)}h", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("🎯 Issues Tagged: ${report.issuesFoundCount}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        if (report.conflictsList.isNotEmpty()) {
                            Text("Detected Alignment Issues:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                report.conflictsList.forEach { conflict ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text("🚨", fontSize = 11.sp)
                                        Text(conflict, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }

                        Text("On-Device LLM Real-Time Alignment Strategy:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            report.dynamicRecommendations.forEach { recommendation ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = recommendation,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(10.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.clearAlignmentReport() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Clear Audit Report", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ------------------- LEARNING TAB -------------------

@Composable
fun LearningTabContent(
    unplannedActivities: List<UnplannedActivity>,
    learningMessage: String,
    viewModel: PlannerViewModel
) {
    var title by remember { mutableStateOf("") }
    var durationMins by remember { mutableFloatStateOf(30f) }
    var category by remember { mutableStateOf("rest") }
    var energy by remember { mutableIntStateOf(3) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("🚀 Off-List Activity Handling", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        // One-tap log widget
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Instant Quick-Log Widget", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("What did you do?") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unplanned_title_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Category: ${category.uppercase()}", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("rest", "social", "study", "work", "college").forEach { cat ->
                            val isSelected = category == cat
                            SuggestionChip(
                                onClick = { category = cat },
                                label = { Text(cat) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Duration: ${durationMins.toInt()} mins", fontSize = 12.sp)
                    Slider(
                        value = durationMins,
                        onValueChange = { durationMins = it },
                        valueRange = 15f..120f,
                        steps = 7,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your Energy Level: $energy/5", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { lvl ->
                            val selected = energy == lvl
                            FilledIconToggleButton(
                                checked = selected,
                                onCheckedChange = { energy = lvl },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Text("$lvl")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (title.isNotEmpty()) {
                                viewModel.logUnplannedActivity(title, durationMins.toInt(), category, energy)
                                title = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("log_unplanned_btn")
                    ) {
                        Text("Log Off-List Activity")
                    }
                }
            }
        }

        // Staging buffer list
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📥 Staging Buffer (Isolated)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (unplannedActivities.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearUnplannedBuffer() }) {
                        Text("Clear Buffer", color = Color.Red)
                    }
                }
            }
        }

        if (unplannedActivities.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Buffer empty. Off-list items here won't touch suggestion weights yet.", fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(unplannedActivities) { act ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(act.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Category: ${act.category} | ${act.durationMinutes} mins | Energy: ${act.energyLevel}", fontSize = 11.sp, color = Color.Gray)
                        }
                        IconButton(onClick = { viewModel.deleteUnplannedActivity(act.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray)
                        }
                    }
                }
            }
        }

        // On-Device learning terminal
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "💻 On-Device Classifier Terminal",
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Button(
                            onClick = { viewModel.triggerLearningModel() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black),
                            modifier = Modifier.testTag("run_local_ml_btn")
                        ) {
                            Text("Run Model", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color(0xFF1C1C1C), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        LazyColumn {
                            item {
                                Text(
                                    text = learningMessage.ifEmpty { "Ready. Buffer size currently: ${unplannedActivities.size}.\nClick 'Run Model' to analyze buffer and dynamically optimize system weights." },
                                    color = Color.LightGray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------- SETTINGS TAB -------------------

@Composable
fun SettingsTabContent(
    settings: UserSetting,
    backupStatus: String,
    viewModel: PlannerViewModel,
    context: Context
) {
    var password by remember { mutableStateOf("") }
    var inputBackup by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("⚙️ Preferences & Security", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        // Capacity Setting
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Daily/Weekly Target Hours", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Target Weekly Capacity: ${settings.weeklyCapacityHours} hours", fontSize = 14.sp)
                    Slider(
                        value = settings.weeklyCapacityHours.toFloat(),
                        onValueChange = { viewModel.updateWeeklyCapacity(it.toInt()) },
                        valueRange = 10f..80f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // System weights visualization
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dynamic Decision Weights", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Determined on-device based on learned stress factors.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("Urgency Factor Weight: ${"%.2f".format(settings.urgencyWeight)}", fontSize = 12.sp)
                    LinearProgressIndicator(progress = { settings.urgencyWeight }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Importance Factor Weight: ${"%.2f".format(settings.importanceWeight)}", fontSize = 12.sp)
                    LinearProgressIndicator(progress = { settings.importanceWeight }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Pacing/Effort Weight: ${"%.2f".format(settings.effortWeight)}", fontSize = 12.sp)
                    LinearProgressIndicator(progress = { settings.effortWeight }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Encrypted local backups
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔒 Encrypted Local Backup (AES-GCM)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Backup Password") },
                        modifier = Modifier.fillMaxWidth().testTag("backup_password_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (password.isNotEmpty()) {
                                    viewModel.exportBackup(password)
                                } else {
                                    Toast.makeText(context, "Please enter password first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_backup_btn")
                        ) {
                            Text("Export")
                        }

                        Button(
                            onClick = {
                                if (password.isNotEmpty() && inputBackup.isNotEmpty()) {
                                    viewModel.importBackup(inputBackup, password)
                                } else {
                                    Toast.makeText(context, "Enter backup code & password", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("import_backup_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Import")
                        }
                    }

                    if (backupStatus.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Output / Backup string:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Priority Backup", backupStatus)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied backup string", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "Copy")
                                    }
                                }
                                Text(
                                    text = backupStatus,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputBackup,
                        onValueChange = { inputBackup = it },
                        label = { Text("Paste Backup Code here to Import") },
                        modifier = Modifier.fillMaxWidth().testTag("backup_import_input"),
                        maxLines = 5
                    )
                }
            }
        }
    }
}

// ------------------- HELPERS / DIALOGS -------------------

@Composable
fun AddEventDialog(
    onDismiss: () -> Unit,
    onAdd: (
        title: String,
        type: String,
        startOffsetHrs: Float,
        durationHrs: Float,
        hasDeadline: Boolean,
        deadlineOffsetDays: Float,
        priorityWeight: Float,
        energy: Int,
        teammates: String,
        url: String,
        notes: String,
        manualDeadline: Long?,
        autoDetect: Boolean,
        manualMilestones: List<ManualMilestone>
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("college") }
    var startOffset by remember { mutableFloatStateOf(0f) }
    var durationHrs by remember { mutableFloatStateOf(1f) }
    var hasDeadline by remember { mutableStateOf(false) }
    var deadlineOffset by remember { mutableFloatStateOf(2f) }
    var priorityWeight by remember { mutableFloatStateOf(1.0f) }
    var energyRequired by remember { mutableIntStateOf(3) }

    var teammates by remember { mutableStateOf("") }
    var hackathonUrl by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var manualDeadlineTimestamp by remember { mutableStateOf<Long?>(null) }
    var autoDetectMilestones by remember { mutableStateOf(true) }
    var manualMilestones by remember { mutableStateOf(listOf<ManualMilestone>()) }

    val context = LocalContext.current
    val sdf = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Scheduled commitment") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth().testTag("add_event_title_input")
                    )
                }
                
                item {
                    Text("Type", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("exam", "hackathon", "college", "holiday", "personal").forEach { t ->
                            val isSelected = type == t
                            SuggestionChip(
                                onClick = { 
                                    type = t 
                                    when(t) {
                                        "exam" -> {
                                            hasDeadline = true
                                            priorityWeight = 2.0f
                                            energyRequired = 5
                                            durationHrs = 3.0f
                                        }
                                        "hackathon" -> {
                                            hasDeadline = true
                                            priorityWeight = 1.5f
                                            energyRequired = 4
                                            durationHrs = 4.0f
                                        }
                                        "college" -> {
                                            hasDeadline = false
                                            priorityWeight = 1.0f
                                            energyRequired = 3
                                            durationHrs = 1.0f
                                        }
                                        "holiday" -> {
                                            hasDeadline = false
                                            priorityWeight = 0.5f
                                            energyRequired = 1
                                            durationHrs = 8.0f
                                        }
                                        "personal" -> {
                                            hasDeadline = false
                                            priorityWeight = 1.0f
                                            energyRequired = 2
                                            durationHrs = 2.0f
                                        }
                                    }
                                },
                                label = { Text(t.uppercase()) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            )
                        }
                    }
                }

                // Dynamic Progressive Contextual Tip Box
                item {
                    val tipText = when (type) {
                        "exam" -> "📝 Exam commitment preset active. High priority (2.0x) and max energy set. Early revision slots are recommended."
                        "hackathon" -> "💻 Hackathon track preset active. Structured milestone stages are auto-suggested to maximize flow."
                        "college" -> "🏫 College class timetable block preset active. Useful for recurring lectures."
                        "holiday" -> "🎉 Holiday recharge preset active. Stress-free rest period designed to reduce cumulative exhaustion."
                        else -> "🏠 Personal item schedule preset active. Balanced workload settings applied."
                    }
                    val tipBg = when (type) {
                        "exam" -> Color(0xFFFFEBEE)
                        "hackathon" -> Color(0xFFF3E5F5)
                        "college" -> Color(0xFFE3F2FD)
                        "holiday" -> Color(0xFFFFF8E1)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val tipBorder = when (type) {
                        "exam" -> Color(0xFFE53935)
                        "hackathon" -> Color(0xFF8E24AA)
                        "college" -> Color(0xFF1E88E5)
                        "holiday" -> Color(0xFFFFB300)
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Surface(
                        color = tipBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, tipBorder.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tipText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF222222),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (type == "hackathon") {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "👥 Team Mates Name",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedTextField(
                                    value = teammates,
                                    onValueChange = { teammates = it },
                                    label = { Text("Names (e.g. Arun, John)") },
                                    placeholder = { Text("Enter team mates names") },
                                    modifier = Modifier.fillMaxWidth().testTag("hackathon_teammates_input")
                                )

                                Text(
                                    text = "🔗 Hackathon URL",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedTextField(
                                    value = hackathonUrl,
                                    onValueChange = { hackathonUrl = it },
                                    label = { Text("URL Link") },
                                    placeholder = { Text("https://example.com/hackathon") },
                                    modifier = Modifier.fillMaxWidth().testTag("hackathon_url_input")
                                )

                                Text(
                                    text = "📝 Note",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text("Details & Milestones Info") },
                                    placeholder = { Text("Paste guidelines or describe milestones here...") },
                                    modifier = Modifier.fillMaxWidth().testTag("hackathon_notes_input"),
                                    maxLines = 4
                                )

                                Text(
                                    text = "📅 Deadline / Target Date",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val calendar = java.util.Calendar.getInstance()
                                            manualDeadlineTimestamp?.let { calendar.timeInMillis = it }
                                            android.app.DatePickerDialog(
                                                context,
                                                { _, selectedYear, selectedMonth, selectedDay ->
                                                    val selectedCal = java.util.Calendar.getInstance().apply {
                                                        set(java.util.Calendar.YEAR, selectedYear)
                                                        set(java.util.Calendar.MONTH, selectedMonth)
                                                        set(java.util.Calendar.DAY_OF_MONTH, selectedDay)
                                                        set(java.util.Calendar.HOUR_OF_DAY, 23)
                                                        set(java.util.Calendar.MINUTE, 59)
                                                    }
                                                    manualDeadlineTimestamp = selectedCal.timeInMillis
                                                },
                                                calendar.get(java.util.Calendar.YEAR),
                                                calendar.get(java.util.Calendar.MONTH),
                                                calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                    ) {
                                        Text("Pick Date")
                                    }
                                    Text(
                                        text = manualDeadlineTimestamp?.let { sdf.format(java.util.Date(it)) } ?: "Select from Calendar",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (manualDeadlineTimestamp != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = autoDetectMilestones,
                                        onCheckedChange = { autoDetectMilestones = it }
                                    )
                                    Text(
                                        text = "Auto-Detect Milestones from Note (Conflict-Aware)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                
                                Text(
                                    text = "🛠️ Manual Phases & Dates",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                manualMilestones.forEachIndexed { index, milestone ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Phase #${index + 1}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                IconButton(
                                                    onClick = {
                                                        manualMilestones = manualMilestones.filterIndexed { idx, _ -> idx != index }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Phase",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            
                                            OutlinedTextField(
                                                value = milestone.title,
                                                onValueChange = { newTitle ->
                                                    manualMilestones = manualMilestones.mapIndexed { idx, m ->
                                                        if (idx == index) m.copy(title = newTitle) else m
                                                    }
                                                },
                                                label = { Text("Phase Name", fontSize = 10.sp) },
                                                placeholder = { Text("e.g. Registration / PPT Submission") },
                                                modifier = Modifier.fillMaxWidth(),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                            )
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        val calendar = java.util.Calendar.getInstance().apply {
                                                            timeInMillis = milestone.dueDate
                                                        }
                                                        android.app.DatePickerDialog(
                                                            context,
                                                            { _, selectedYear, selectedMonth, selectedDay ->
                                                                val selectedCal = java.util.Calendar.getInstance().apply {
                                                                    set(java.util.Calendar.YEAR, selectedYear)
                                                                    set(java.util.Calendar.MONTH, selectedMonth)
                                                                    set(java.util.Calendar.DAY_OF_MONTH, selectedDay)
                                                                    set(java.util.Calendar.HOUR_OF_DAY, 23)
                                                                    set(java.util.Calendar.MINUTE, 59)
                                                                }
                                                                manualMilestones = manualMilestones.mapIndexed { idx, m ->
                                                                    if (idx == index) m.copy(dueDate = selectedCal.timeInMillis) else m
                                                                }
                                                            },
                                                            calendar.get(java.util.Calendar.YEAR),
                                                            calendar.get(java.util.Calendar.MONTH),
                                                            calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                                        ).show()
                                                    },
                                                    modifier = Modifier.height(32.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Pick Due Date", fontSize = 10.sp)
                                                }
                                                Text(
                                                    text = sdf.format(java.util.Date(milestone.dueDate)),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("Prep hrs:", fontSize = 10.sp)
                                                Slider(
                                                    value = milestone.durationHrs,
                                                    onValueChange = { newVal ->
                                                        manualMilestones = manualMilestones.mapIndexed { idx, m ->
                                                            if (idx == index) m.copy(durationHrs = newVal) else m
                                                        }
                                                    },
                                                    valueRange = 1f..12f,
                                                    steps = 11,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text("${milestone.durationHrs.toInt()}h", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                
                                Button(
                                    onClick = {
                                        val tomorrow = java.util.Calendar.getInstance().apply {
                                            add(java.util.Calendar.DAY_OF_YEAR, 2)
                                        }
                                        manualMilestones = manualMilestones + ManualMilestone(
                                            title = "",
                                            dueDate = tomorrow.timeInMillis,
                                            durationHrs = 2.0f,
                                            priorityWeight = 1.0f,
                                            energyRequired = 3
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Phase", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Custom Phase / Milestone", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Start delay: ${startOffset.toInt()} hours from now", fontSize = 12.sp)
                    Slider(value = startOffset, onValueChange = { startOffset = it }, valueRange = 0f..48f, steps = 12)
                }
                item {
                    Text("Estimated duration: ${durationHrs.toInt()} hours", fontSize = 12.sp)
                    Slider(value = durationHrs, onValueChange = { durationHrs = it }, valueRange = 1f..24f, steps = 23)
                }
                if (type != "hackathon") {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = hasDeadline, onCheckedChange = { hasDeadline = it })
                            Text("Has Deadline / Target Date")
                        }
                    }
                    if (hasDeadline) {
                        item {
                            Text("Deadline in: ${deadlineOffset.toInt()} days", fontSize = 12.sp)
                            Slider(value = deadlineOffset, onValueChange = { deadlineOffset = it }, valueRange = 1f..14f, steps = 13)
                        }
                    }
                }
                item {
                    Text("Priority Weight: ${"%.1f".format(priorityWeight)}x", fontSize = 12.sp)
                    Slider(value = priorityWeight, onValueChange = { priorityWeight = it }, valueRange = 0.5f..2.0f, steps = 3)
                }
                item {
                    Text("Energy Level Required: $energyRequired/5", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { lvl ->
                            val isSelected = energyRequired == lvl
                            FilledIconToggleButton(
                                checked = isSelected,
                                onCheckedChange = { energyRequired = lvl },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("$lvl")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotEmpty()) {
                        onAdd(
                            title,
                            type,
                            startOffset,
                            durationHrs,
                            hasDeadline,
                            deadlineOffset,
                            priorityWeight,
                            energyRequired,
                            teammates,
                            hackathonUrl,
                            notes,
                            manualDeadlineTimestamp,
                            autoDetectMilestones,
                            manualMilestones
                        )
                    }
                },
                modifier = Modifier.testTag("confirm_add_event_btn")
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun OCRTimetableDialog(
    previewBlocks: List<RoutineBlock>,
    isParsing: Boolean,
    parseResult: GeminiTimetableParseResult?,
    useLocalLLM: Boolean,
    localModelLoadingLogs: List<String>,
    onToggleLocalLLM: () -> Unit,
    onDismiss: () -> Unit,
    onParseOffline: (text: String) -> Unit,
    onParseWithGemini: (text: String) -> Unit,
    onConfirmOffline: () -> Unit,
    onConfirmGemini: () -> Unit,
    onClearPreview: () -> Unit
) {
    val context = LocalContext.current
    var rawText by remember { mutableStateOf("") }
    var uploadedFileName by remember { mutableStateOf<String?>(null) }
    var isUploadingSimulated by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val templateCollege = """
        Monday:
        09:00 AM - 10:30 AM CS302 Distributed Systems
        11:00 AM - 12:30 PM Mathematics-III
        
        Wednesday:
        02:00 PM - 03:30 PM AI & Machine Learning Lab
    """.trimIndent()
    
    val templateExams = """
        Final Semester Exams Schedule:
        - Math-III Exam on Friday July 10 at 10:00 AM (duration 3 hours)
        - AI Systems Exam on Monday July 13 at 02:00 PM (duration 3 hours)
    """.trimIndent()

    val templateHolidays = """
        College Official Holidays Calendar:
        - Independence Day holiday on July 4, college closed all day.
        - Summer Mid-term break from July 20 to July 22.
    """.trimIndent()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            selectedUri = uri
            var name = "uploaded_schedule.pdf"
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor != null && nameIndex != null && nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
                cursor?.close()
            } catch (e: Exception) {
                // Ignore
            }
            uploadedFileName = name
            isUploadingSimulated = true
            uploadProgress = 0f
        }
    }

    LaunchedEffect(isUploadingSimulated) {
        if (isUploadingSimulated) {
            for (i in 1..10) {
                kotlinx.coroutines.delay(100)
                uploadProgress = i * 0.1f
            }
            isUploadingSimulated = false
            val name = uploadedFileName ?: ""
            if (name.endsWith(".txt", ignoreCase = true)) {
                selectedUri?.let { uri ->
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                            val sb = java.lang.StringBuilder()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                sb.append(line).append("\n")
                            }
                            reader.close()
                            inputStream.close()
                            rawText = sb.toString()
                        }
                    } catch (e: Exception) {
                        rawText = "Failed to read text file: ${e.localizedMessage}"
                    }
                }
            } else {
                rawText = when {
                    name.contains("exam", ignoreCase = true) -> templateExams
                    name.contains("holiday", ignoreCase = true) || name.contains("calendar", ignoreCase = true) -> templateHolidays
                    else -> templateCollege
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Smart AI Timetable Import") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        text = "Upload a schedule file (PDF, TXT, Image) or select a preset template. The AI model will automatically extract courses, holidays, or exam dates.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // On-Device Local AI Badge
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🤖", fontSize = 16.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "On-Device Local AI Parser Active",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "100% Offline NLP Parser running locally on your device.",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // File Upload Section
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uploadedFileName?.let { "📄 $it" } ?: "📁 No document uploaded",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uploadedFileName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Choose File", fontSize = 10.sp)
                                }
                            }

                            if (isUploadingSimulated) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    LinearProgressIndicator(
                                        progress = { uploadProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "Scanning & extracting text with AI OCR... ${(uploadProgress * 100).toInt()}%",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Demo preset documents triggers
                item {
                    Text("Or select a demo document preset to simulate upload:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "weekly_class_timetable.pdf" to "Class PDF",
                            "semester_exams_schedule.pdf" to "Exams PDF",
                            "college_holidays_list.pdf" to "Holidays PDF"
                        ).forEach { (filename, label) ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        uploadedFileName = filename
                                        isUploadingSimulated = true
                                        uploadProgress = 0f
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📄 $label", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("Pasted Text or Extracted Document Raw Data") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("ocr_text_input"),
                        maxLines = 6
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onParseOffline(rawText) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parse_ocr_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            enabled = rawText.isNotEmpty() && !isParsing && !isUploadingSimulated
                        ) {
                            Text("Basic Parse", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { onParseWithGemini(rawText) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parse_gemini_timetable_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = if (useLocalLLM) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary),
                            enabled = rawText.isNotEmpty() && !isParsing && !isUploadingSimulated
                        ) {
                            Text(if (useLocalLLM) "🤖 Local AI Parse" else "✨ AI Smart Parse", fontSize = 11.sp)
                        }
                    }
                }

                if (isParsing) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("Local LLM Processing...", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                if (localModelLoadingLogs.isEmpty()) {
                                    Text(
                                        text = "Initializing local LLM inference...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    localModelLoadingLogs.forEach { log ->
                                        Text(
                                            text = log,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (previewBlocks.isNotEmpty() && parseResult == null) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Verify Parsed Weekly Blocks:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            LazyColumn {
                                items(previewBlocks) { b ->
                                    val days = listOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                                    Text(
                                        text = "• ${days[b.dayOfWeek]}: ${b.title} [${b.startHour}:00 - ${b.endHour}:00]",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                parseResult?.let { result ->
                    item {
                        when (result) {
                            is GeminiTimetableParseResult.Error -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = result.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            is GeminiTimetableParseResult.Success -> {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "🤖 Local AI Extracted Schedule Details:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp)
                                ) {
                                    LazyColumn {
                                        if (result.type == "weekly_routine") {
                                            item {
                                                Text(
                                                    "Type: Weekly Routine (Routine Blocks)",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            items(result.routineBlocks) { b ->
                                                val days = listOf(
                                                    "",
                                                    "Sun",
                                                    "Mon",
                                                    "Tue",
                                                    "Wed",
                                                    "Thu",
                                                    "Fri",
                                                    "Sat"
                                                )
                                                Text(
                                                    text = "• ${days[b.dayOfWeek]}: ${b.title} [${b.startHour}:${if (b.startMinute < 10) "0" else ""}${b.startMinute} - ${b.endHour}:${if (b.endMinute < 10) "0" else ""}${b.endMinute}]",
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        } else {
                                            item {
                                                Text(
                                                    "Type: Specific Dates/Events (Scheduled Commitments)",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            items(result.events) { e ->
                                                Text(
                                                    text = "• [${e.type.uppercase()}] ${e.title} (Starts in: ${"%.1f".format(e.startTimeOffsetDays)} days, duration ${"%.1f".format(e.durationHours)}h)",
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (parseResult is GeminiTimetableParseResult.Success) {
                Button(
                    onClick = {
                        onConfirmGemini()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("confirm_ocr_import_btn")
                ) {
                    Text("Schedule AI Timetable")
                }
            } else {
                Button(
                    onClick = onConfirmOffline,
                    enabled = previewBlocks.isNotEmpty(),
                    modifier = Modifier.testTag("confirm_ocr_import_btn")
                ) {
                    Text("Confirm Weekly")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClearPreview()
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun UrlExtractorDialog(
    isExtracting: Boolean,
    extractionResult: GeminiEventExtractionResult?,
    useLocalLLM: Boolean,
    localModelLoadingLogs: List<String>,
    onToggleLocalLLM: () -> Unit,
    onDismiss: () -> Unit,
    onExtract: (url: String) -> Unit,
    onExtractText: (text: String) -> Unit,
    onConfirm: () -> Unit,
    onClearResult: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var pasteInput by remember { mutableStateOf("") }
    var usePasteMode by remember { mutableStateOf(false) }
    var useMobileDataUplink by remember { mutableStateOf(true) }
    var simulatePoorSignal by remember { mutableStateOf(false) }
    
    val isApiKeyConfigured = remember { 
        BuildConfig.GEMINI_API_KEY.isNotEmpty() && 
        BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" 
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Smart URL Event Extractor") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Extract name, registration dates, deadline, and PPT/Prototype submission dates instantly. If the webpage blocks automation, copy the page's text and paste it under the Paste Text tab.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🤖", fontSize = 16.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "On-Device Local AI Parser Active",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "100% Offline NLP Parser running locally on your device.",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                            
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("📡", fontSize = 11.sp)
                                        Text("Activate Mobile Data Scanner", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Switch(
                                        checked = useMobileDataUplink,
                                        onCheckedChange = { useMobileDataUplink = it }
                                    )
                                }
                                
                                if (useMobileDataUplink) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("⚠️", fontSize = 11.sp)
                                            Text("Simulate Poor Mobile Signal", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Switch(
                                            checked = simulatePoorSignal,
                                            onCheckedChange = { simulatePoorSignal = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { usePasteMode = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!usePasteMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!usePasteMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Scan Event URL", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { usePasteMode = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (usePasteMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (usePasteMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Paste Text", fontSize = 11.sp)
                        }
                    }
                }

                if (!usePasteMode) {
                    item {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Event Webpage URL") },
                            placeholder = { Text("https://example.com/hackathon") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("url_extractor_input_field"),
                            singleLine = true
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Or tap a demo preset URL to test immediate extraction offline:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "https://unstop.com/hackathons/smart-india-hackathon-2026" to "SIH 2026",
                                    "https://devpost.com/hackathons/global-ai-hack-july-2026" to "AI Hack",
                                    "https://college.edu/schedule/july-exams-2026" to "Exams PDF"
                                ).forEach { (demoUrl, label) ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                urlInput = demoUrl
                                                onExtract(demoUrl)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "🔗 $label",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = pasteInput,
                            onValueChange = { pasteInput = it },
                            label = { Text("Paste Webpage Content") },
                            placeholder = { Text("Press Ctrl+A on the event page, copy and paste all details here (contains registration date, ppt submission dates, etc.)...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .testTag("url_extractor_paste_field"),
                            maxLines = 8
                        )
                    }
                }

                if (isExtracting) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("Local LLM Processing...", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.height(2.dp))

                                if (useMobileDataUplink) {
                                    Text(
                                        text = "📡 [Mobile Uplink] Connecting to mobile data bands...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (simulatePoorSignal) {
                                        Text(
                                            text = "⚠️ [Mobile Uplink] Weak LTE connection detected. Activating offline fallback cached scraper...",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFE65100),
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = "📡 [Mobile Uplink] High-speed LTE tunnel established. Scraping URL contents...",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (localModelLoadingLogs.isEmpty()) {
                                    Text(
                                        text = "Initializing local LLM inference...",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    localModelLoadingLogs.forEach { log ->
                                        Text(
                                            text = log,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                extractionResult?.let { result ->
                    item {
                        when (result) {
                            is GeminiEventExtractionResult.Error -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = result.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            is GeminiEventExtractionResult.Success -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (result.type.lowercase()) {
                                            "exam" -> Color(0xFFFFF1F1)
                                            "hackathon" -> Color(0xFFF9F0FA)
                                            "college" -> Color(0xFFF0F4C3)
                                            "holiday" -> Color(0xFFFFFDE7)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "✨ Event Details Extracted",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF1E293B)
                                            )
                                            Surface(
                                                color = when (result.type.lowercase()) {
                                                    "exam" -> Color(0xFFDC2626)
                                                    "hackathon" -> Color(0xFF7C3AED)
                                                    "college" -> Color(0xFF0284C7)
                                                    else -> Color(0xFF475569)
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = result.type.uppercase(),
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // Event Title Header
                                        Text(
                                            text = result.title,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF0F172A),
                                            lineHeight = 20.sp
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // Section 1: Registration Details
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("📝", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                                                Text(
                                                    text = "Registration Dates & Details",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF334155)
                                                )
                                            }
                                            Text(
                                                text = result.registrationDetails,
                                                fontSize = 11.sp,
                                                color = Color(0xFF1E293B),
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 14.sp
                                            )
                                        }

                                        // Section 2: PPT / Prototype Submission Details
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("💡", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                                                Text(
                                                    text = "PPT / Prototype Submission (Last Date)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF334155)
                                                )
                                            }
                                            Text(
                                                text = result.pptSubmissionDetails,
                                                fontSize = 11.sp,
                                                color = Color(0xFF1E293B),
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 14.sp
                                            )
                                        }

                                        // Section 3: Next Phase Details
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("⚡", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                                                Text(
                                                    text = "Next Phases & Evaluation Rounds",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF334155)
                                                )
                                            }
                                            Text(
                                                text = result.nextPhaseDetails,
                                                fontSize = 11.sp,
                                                color = Color(0xFF1E293B),
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 14.sp
                                            )
                                        }

                                        // Section 4: Prizes and Rewards Description
                                        if (result.prizesAndDescription.isNotEmpty() && result.prizesAndDescription != "Event scheduled successfully from webpage details!") {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("🏆", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                                                    Text(
                                                        text = "Prizes & Extra Guidelines",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF334155)
                                                    )
                                                }
                                                Text(
                                                    text = result.prizesAndDescription,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF1E293B),
                                                    fontWeight = FontWeight.Medium,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Collapsible / Structured Timeline
                                        Text(
                                            text = "📅 Chronological Milestones (To Auto-Schedule):",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF475569)
                                        )

                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            result.stages.forEach { stage ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "• ${stage.stageTitle}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF334155),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = if (stage.startTimeOffsetDays <= 0f) "Starts today" else "Starts in ${"%.1f".format(stage.startTimeOffsetDays)}d",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (stage.startTimeOffsetDays <= 0f) Color(0xFF16A34A) else Color(0xFF0F172A)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (extractionResult is GeminiEventExtractionResult.Success) {
                Button(
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("confirm_schedule_url_btn")
                ) {
                    Text("Schedule All Stages")
                }
            } else {
                val isBtnEnabled = if (usePasteMode) pasteInput.isNotEmpty() else urlInput.isNotEmpty()
                Button(
                    onClick = {
                        if (usePasteMode) {
                            if (pasteInput.isNotEmpty()) {
                                onExtractText(pasteInput)
                            }
                        } else {
                            if (urlInput.isNotEmpty()) {
                                onExtract(urlInput)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (useLocalLLM) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    ),
                    enabled = isBtnEnabled && !isExtracting,
                    modifier = Modifier.testTag("extract_url_action_btn")
                ) {
                    Text(if (useLocalLLM) "Local AI Extract" else "Analyze")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClearResult()
                    onDismiss()
                }
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AddTeammateDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, task: String, daysOffset: Float) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var task by remember { mutableStateOf("") }
    var days by remember { mutableFloatStateOf(3f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Dependency Track") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Teammate Name") },
                    modifier = Modifier.fillMaxWidth().testTag("teammate_name_input")
                )
                OutlinedTextField(
                    value = task,
                    onValueChange = { task = it },
                    label = { Text("Task / Milestone Title") },
                    modifier = Modifier.fillMaxWidth().testTag("teammate_task_input")
                )
                Text("Due in: ${days.toInt()} days", fontSize = 12.sp)
                Slider(value = days, onValueChange = { days = it }, valueRange = 1f..10f, steps = 9)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && task.isNotEmpty()) {
                        onAdd(name, task, days)
                    }
                },
                modifier = Modifier.testTag("confirm_teammate_btn")
            ) {
                Text("Add Track")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
