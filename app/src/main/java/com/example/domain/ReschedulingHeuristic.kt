package com.example.domain

import com.example.data.model.Event

object ReschedulingHeuristic {

    // Conflict Detector: returns all existing active events that overlap with the new event interval
    fun findIntervalConflicts(newEvent: Event, existingEvents: List<Event>): List<Event> {
        return existingEvents.filter { existing ->
            existing.id != newEvent.id &&
                    existing.status == "active" &&
                    maxOf(newEvent.startTime, existing.startTime) < minOf(newEvent.endTime, existing.endTime)
        }
    }

    // Greedy Re-scheduling Heuristic:
    // Re-inserts only the affected disrupted events into the next-best available free slots,
    // preserving their durations, and leaves other parts of the plan intact.
    fun greedyReschedule(
        disruptedEvents: List<Event>,
        availableFreeSlots: List<FreeSlot>
    ): List<Event> {
        if (disruptedEvents.isEmpty() || availableFreeSlots.isEmpty()) return disruptedEvents

        // Sort disrupted events by priority or deadline to greedily place the most critical ones first
        val sortedDisrupted = disruptedEvents.sortedWith(
            compareByDescending<Event> { it.priorityWeight }
                .thenBy { it.deadline ?: Long.MAX_VALUE }
        )

        val rescheduledEvents = mutableListOf<Event>()
        val dynamicSlots = availableFreeSlots.toMutableList()

        for (event in sortedDisrupted) {
            val eventDuration = event.endTime - event.startTime
            val durationMinutes = eventDuration / (1000 * 60)

            // Find the first free slot that can accommodate this event's duration
            val targetSlotIndex = dynamicSlots.indexOfFirst { slot ->
                slot.durationMinutes >= durationMinutes
            }

            if (targetSlotIndex != -1) {
                val selectedSlot = dynamicSlots[targetSlotIndex]
                val newStartTime = selectedSlot.startTime
                val newEndTime = newStartTime + eventDuration

                // Update the event with new scheduling times
                val updatedEvent = event.copy(
                    startTime = newStartTime,
                    endTime = newEndTime
                )
                rescheduledEvents.add(updatedEvent)

                // Update the remaining free slot segment
                if (selectedSlot.endTime > newEndTime) {
                    val remainingSlot = FreeSlot(newEndTime, selectedSlot.endTime)
                    dynamicSlots[targetSlotIndex] = remainingSlot
                } else {
                    dynamicSlots.removeAt(targetSlotIndex)
                }
            } else {
                // If no available free slot fits, keep the original event (or flag as unscheduled / status active)
                rescheduledEvents.add(event)
            }
        }

        return rescheduledEvents
    }
}
