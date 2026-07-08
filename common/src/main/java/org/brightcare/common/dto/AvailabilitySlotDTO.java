package org.brightcare.common.dto;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * Represents an available time slot for a doctor on a given day.
 * Slots are 30-minute intervals.
 */
public record AvailabilitySlotDTO(
        LocalTime startTime,
        LocalTime endTime,
        boolean available
) implements Serializable {
    private static final long serialVersionUID = 2L;

    @Override
    public String toString() {
        return startTime + " - " + endTime + (available ? " [Available]" : " [Booked]");
    }
}
