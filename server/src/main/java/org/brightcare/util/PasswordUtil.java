package org.brightcare.util;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for password hashing and verification using BCrypt.
 * <p>
 * BCrypt automatically handles salting — each hash embeds a unique salt.
 * The workload factor (log rounds) is set to 12 for a good balance of
 * security and performance.
 */
public final class PasswordUtil {

    private static final Logger log = LoggerFactory.getLogger(PasswordUtil.class);

    /** BCrypt workload factor. 12 = 2^12 rounds. */
    private static final int BCRYPT_ROUNDS = 12;

    private PasswordUtil() {
        // utility class — prevent instantiation
    }

    /**
     * Hash a plain-text password using BCrypt.
     *
     * @param plainPassword the raw password (must not be null or blank)
     * @return the BCrypt hash string (60 characters, includes embedded salt)
     * @throws IllegalArgumentException if the password is null or blank
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be null or blank");
        }
        String salt = BCrypt.gensalt(BCRYPT_ROUNDS);
        String hash = BCrypt.hashpw(plainPassword, salt);
        log.debug("Password hash generated (rounds={})", BCRYPT_ROUNDS);
        return hash;
    }

    /**
     * Verify a plain-text password against a stored BCrypt hash.
     *
     * @param plainPassword the raw password to check
     * @param storedHash    the BCrypt hash from the database
     * @return true if the password matches the hash
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        try {
            boolean match = BCrypt.checkpw(plainPassword, storedHash);
            log.debug("Password verification: {}", match ? "success" : "failure");
            return match;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid BCrypt hash format encountered during verification", e);
            return false;
        }
    }
}
