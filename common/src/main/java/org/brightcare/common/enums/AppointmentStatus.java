package org.brightcare.common.enums;

/**
 * Appointment status values.
 * Maps to the appointments.status column in the database.
 */
public enum AppointmentStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public static AppointmentStatus fromString(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            throw new IllegalArgumentException("Appointment status cannot be null or blank");
        }
        for (AppointmentStatus s : values()) {
            if (s.name().equalsIgnoreCase(statusStr.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown appointment status: " + statusStr);
    }
}
