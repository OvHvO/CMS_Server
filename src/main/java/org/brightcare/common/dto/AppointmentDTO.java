package org.brightcare.common.dto;

import org.brightcare.common.enums.AppointmentStatus;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Data transfer object for appointment information.
 * Maps to the appointments table.
 */
public record AppointmentDTO(
        UUID id,
        UUID patientId,
        UUID doctorId,
        String patientName,       // denormalised for display convenience
        String doctorName,        // denormalised for display convenience
        ZonedDateTime appointmentTime,
        AppointmentStatus status,
        String reasonForVisit,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
