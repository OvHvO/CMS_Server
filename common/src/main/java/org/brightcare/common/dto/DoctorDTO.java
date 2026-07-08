package org.brightcare.common.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Data transfer object for doctor information.
 * Maps to the doctors table.
 */
public record DoctorDTO(
        UUID id,
        UUID userId,
        String fullName,
        String specialization,
        boolean isActive,
        ZonedDateTime createdAt
) implements Serializable {
    private static final long serialVersionUID = 3L;
}
