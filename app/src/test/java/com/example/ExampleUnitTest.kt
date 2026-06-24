package com.example

import com.example.data.model.Event
import com.example.data.model.RoutineBlock
import com.example.data.model.UnplannedActivity
import com.example.data.model.UserSetting
import com.example.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun testPriorityEngineFormula() {
        val currentTime = System.currentTimeMillis()
        val settings = UserSetting(
            urgencyWeight = 0.5f,
            importanceWeight = 0.3f,
            effortWeight = 0.2f
        )
        // Set deadline in 2 days (urgency = 1/2 = 0.5)
        val twoDaysMillis = 2 * 24 * 60 * 60 * 1000L
        val event = Event(
            id = 1,
            title = "Prepare Hackathon Pitch",
            type = "hackathon", // importance = 0.8
            startTime = currentTime,
            endTime = currentTime + 3600000,
            deadline = currentTime + twoDaysMillis,
            priorityWeight = 1.0f,
            energyRequired = 4 // effort = 4/5 = 0.8
        )

        val (score, reasoning) = PriorityEngine.calculatePriorityScore(event, settings, currentTime)
        
        // Expected score:
        // Urgency = 1.0 / 2.0 = 0.5
        // Importance = 0.8
        // Effort = 0.8
        // score = 0.5 * 0.5 + 0.3 * 0.8 + 0.2 * 0.8 = 0.25 + 0.24 + 0.16 = 0.65
        assertEquals(0.65f, score, 0.01f)
        assertTrue(reasoning.contains("Hackathon milestone"))
    }

    @Test
    fun testEarliestDeadlineFirstScheduling() {
        val currentTime = System.currentTimeMillis()
        val settings = UserSetting()
        
        val freeSlots = listOf(FreeSlot(currentTime, currentTime + 3600000))
        
        val event1 = Event(
            id = 1,
            title = "Exam Prep A",
            type = "exam",
            startTime = currentTime,
            endTime = currentTime + 3600000,
            deadline = currentTime + 5 * 24 * 60 * 60 * 1000L // 5 days
        )
        val event2 = Event(
            id = 2,
            title = "Exam Prep B",
            type = "exam",
            startTime = currentTime,
            endTime = currentTime + 3600000,
            deadline = currentTime + 1 * 24 * 60 * 60 * 1000L // 1 day (should suggest first due to urgency/EDF!)
        )

        val suggestions = PriorityEngine.getSuggestionsForFreeSlots(
            events = listOf(event1, event2),
            settings = settings,
            freeSlots = freeSlots,
            currentTime = currentTime
        )

        assertFalse(suggestions.isEmpty())
        assertEquals("Exam Prep B", suggestions[0].event.title)
    }

    @Test
    fun testGreedyRescheduling() {
        val currentTime = System.currentTimeMillis()
        
        val event1 = Event(id = 1, title = "Overlap Event", type = "college", startTime = currentTime, endTime = currentTime + 3600000, deadline = null)
        val event2 = Event(id = 2, title = "New Conflicting Event", type = "exam", startTime = currentTime + 1800000, endTime = currentTime + 5400000, deadline = null)

        // Conflict check
        val conflicts = ReschedulingHeuristic.findIntervalConflicts(event2, listOf(event1))
        assertEquals(1, conflicts.size)
        assertEquals("Overlap Event", conflicts[0].title)

        // Greedy reschedule event1 into an upcoming free slot
        val freeSlots = listOf(
            FreeSlot(currentTime + 7200000, currentTime + 10800000) // 2 hours later
        )
        val rescheduled = ReschedulingHeuristic.greedyReschedule(listOf(event1), freeSlots)
        assertEquals(1, rescheduled.size)
        assertEquals(currentTime + 7200000, rescheduled[0].startTime)
        assertEquals(currentTime + 10800000, rescheduled[0].endTime)
    }

    @Test
    fun testDeferredLearningClassifier() {
        val settings = UserSetting(
            urgencyWeight = 0.5f,
            importanceWeight = 0.3f,
            effortWeight = 0.2f
        )
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val mondayTimestamp = calendar.timeInMillis

        // Add 3 unplanned logs of the same activity ("Gym") at the same hour of day to trigger recurring pattern detection
        val activities = listOf(
            UnplannedActivity(id = 1, title = "Gym", durationMinutes = 60, category = "personal", timestamp = mondayTimestamp, timeOfDayHour = 18, energyLevel = 4),
            UnplannedActivity(id = 2, title = "Gym", durationMinutes = 60, category = "personal", timestamp = mondayTimestamp, timeOfDayHour = 18, energyLevel = 5),
            UnplannedActivity(id = 3, title = "Gym", durationMinutes = 60, category = "personal", timestamp = mondayTimestamp, timeOfDayHour = 18, energyLevel = 4)
        )

        val result = DeferredLearning.analyzeBuffer(activities, settings)
        
        // 1. Should promote "Gym" to a recurring block
        assertEquals(1, result.promotedRoutineBlocks.size)
        assertEquals("Gym (Auto-learned)", result.promotedRoutineBlocks[0].title)
        assertEquals(Calendar.MONDAY, result.promotedRoutineBlocks[0].dayOfWeek)
        assertEquals(18, result.promotedRoutineBlocks[0].startHour)

        // 2. High average energy (4.3) and enough samples should adjust settings weight
        // In DeferredLearning, if avgEnergy > 4.0, urgency and importance get boosted!
        assertTrue(result.updatedSettings.importanceWeight > settings.importanceWeight)
    }

    @Test
    fun testBackupEncryptionGCM() {
        val originalData = "{\"events\":[{\"id\":1,\"title\":\"Startup Pitch\"}]}"
        val password = "secret_password_123"

        // Encrypt
        val encryptedBase64 = BackupEncryptor.encrypt(originalData, password)
        assertNotNull(encryptedBase64)
        assertNotEquals(originalData, encryptedBase64)

        // Decrypt
        val decryptedData = BackupEncryptor.decrypt(encryptedBase64, password)
        assertEquals(originalData, decryptedData)
    }

    @Test
    fun testTimetableOCRParser() {
        val ocrText = """
            Monday:
            9:00 AM - 10:30 AM CS101 Lecture
            
            Wednesday:
            2:00 PM - 3:30 PM Math202 Seminar
        """.trimIndent()

        val blocks = TimetableParser.parseOCRText(ocrText)

        assertEquals(2, blocks.size)
        
        val firstBlock = blocks[0]
        assertEquals("CS101 Lecture", firstBlock.title)
        assertEquals(Calendar.MONDAY, firstBlock.dayOfWeek)
        assertEquals(9, firstBlock.startHour)
        assertEquals(0, firstBlock.startMinute)
        assertEquals(10, firstBlock.endHour)
        assertEquals(30, firstBlock.endMinute)

        val secondBlock = blocks[1]
        assertEquals("Math202 Seminar", secondBlock.title)
        assertEquals(Calendar.WEDNESDAY, secondBlock.dayOfWeek)
        assertEquals(14, secondBlock.startHour)
        assertEquals(0, secondBlock.startMinute)
        assertEquals(15, secondBlock.endHour)
        assertEquals(30, secondBlock.endMinute)
    }
}
