package org.brightcare.common.exception;

import java.time.ZonedDateTime;

/**
 * Thrown when a patient tries to book an appointment with a doctor
 * who already has another appointment at the same time.
 * The UNIQUE(doctor_id, appointment_time) constraint enforces this at the DB level.
 */
public class AppointmentConflictException extends Exception {
    private final ZonedDateTime conflictingTime;

    public AppointmentConflictException(String message, ZonedDateTime conflictingTime) {
        super(message);
        this.conflictingTime = conflictingTime;
    }

    public ZonedDateTime getConflictingTime() {
        return conflictingTime;
    }
}
