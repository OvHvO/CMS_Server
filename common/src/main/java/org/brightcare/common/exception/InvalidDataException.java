package org.brightcare.common.exception;

/**
 * Thrown when input data fails validation (e.g. empty required fields,
 * invalid date range, malformed data).
 */
public class InvalidDataException extends Exception {
    public InvalidDataException(String message) {
        super(message);
    }

    public InvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
