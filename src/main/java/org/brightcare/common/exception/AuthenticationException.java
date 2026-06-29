package org.brightcare.common.exception;

/**
 * Thrown when login credentials are invalid (wrong username or password).
 * The JavaFX client should catch this and prompt the user to re-enter credentials.
 */
public class AuthenticationException extends Exception {
    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
