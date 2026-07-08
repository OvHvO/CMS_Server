package org.brightcare.common.exception;

import java.util.UUID;

/**
 * Thrown when a requested entity (patient, doctor, appointment, etc.)
 * cannot be found in the database.
 */
public class NotFoundException extends Exception {
    private final String entityType;
    private final UUID entityId;

    public NotFoundException(String entityType, UUID entityId) {
        super(entityType + " not found with ID: " + entityId);
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public NotFoundException(String message) {
        super(message);
        this.entityType = null;
        this.entityId = null;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }
}
