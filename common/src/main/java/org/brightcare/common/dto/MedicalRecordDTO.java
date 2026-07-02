package org.brightcare.common.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Data transfer object for medical record information.
 * Maps to the medical_records table.
 */
public record MedicalRecordDTO(
        UUID id,
        UUID appointmentId,
        UUID patientId,
        UUID doctorId,
        String doctorName,          // denormalised for display
        ZonedDateTime appointmentTime, // denormalised for display
        String consultationNotes,
        String prescription,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) implements Serializable {
    private static final long serialVersionUID = 4L;
}
