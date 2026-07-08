package org.brightcare.common.exception;

import org.brightcare.common.enums.Role;

/**
 * Thrown when a user attempts an operation their role does not permit.
 * For example, a patient trying to access admin-only reports.
 */
public class UnauthorizedException extends Exception {
    private final Role requiredRole;
    private final Role actualRole;

    public UnauthorizedException(String message) {
        super(message);
        this.requiredRole = null;
        this.actualRole = null;
    }

    public UnauthorizedException(Role requiredRole, Role actualRole) {
        super("Access denied: requires " + requiredRole + ", but user has role " + actualRole);
        this.requiredRole = requiredRole;
        this.actualRole = actualRole;
    }

    public Role getRequiredRole() {
        return requiredRole;
    }

    public Role getActualRole() {
        return actualRole;
    }
}
