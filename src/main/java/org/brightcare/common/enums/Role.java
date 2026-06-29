package org.brightcare.common.enums;

/**
 * User roles in the BrightCare Medical Centre system.
 * Maps to the users.role column in the database.
 */
public enum Role {
    PATIENT,
    DOCTOR,
    RECEPTIONIST,
    ADMIN;

    /**
     * Parse a role string (case-insensitive) into a Role enum.
     *
     * @param roleStr the role string from the database or user input
     * @return the corresponding Role enum constant
     * @throws IllegalArgumentException if the string does not match any role
     */
    public static Role fromString(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            throw new IllegalArgumentException("Role string cannot be null or blank");
        }
        for (Role r : values()) {
            if (r.name().equalsIgnoreCase(roleStr.trim())) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + roleStr);
    }
}
