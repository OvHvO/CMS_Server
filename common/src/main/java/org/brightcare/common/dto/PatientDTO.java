package org.brightcare.common.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Data transfer object for patient information.
 * Maps to the patients table.
 */
public record PatientDTO(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        String icPassportNumber,
        String contactNumber,
        String medicalRecordId,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) implements Serializable {
    private static final long serialVersionUID = 5L;
}
