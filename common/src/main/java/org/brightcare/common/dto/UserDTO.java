package org.brightcare.common.dto;

import org.brightcare.common.enums.Role;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Data transfer object for user authentication results.
 * Never contains the password hash — only identity and role information.
 */
public record UserDTO(
        UUID id,
        String username,
        Role role,
        ZonedDateTime createdAt
) implements Serializable {
    private static final long serialVersionUID = 7L;
}
