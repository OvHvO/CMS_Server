package org.brightcare.server.dao;

import org.brightcare.common.enums.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object for the {@code users} table.
 */
public class UserDAO {

    private final Connection connection;

    public UserDAO(Connection connection) {
        this.connection = connection;
    }

    // -------------------------------------------------------------------------
    // Queries (matching the schema exactly)
    // -------------------------------------------------------------------------

    private static final String INSERT_USER =
            "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?) RETURNING id";

    private static final String FIND_BY_USERNAME =
            "SELECT id, username, password_hash, role, auth_secretKey, auth_enabled, created_at FROM users WHERE username = ?";

    private static final String FIND_BY_ID =
            "SELECT id, username, password_hash, role, auth_secretKey, auth_enabled, created_at FROM users WHERE id = ?";

    private static final String EXISTS_BY_USERNAME =
            "SELECT 1 FROM users WHERE username = ?";

    private static final String UPDATE_AUTH_ENABLED = 
              "UPDATE users SET auth_enabled = ? WHERE id = ?";

    private static final String UPDATE_AUTH_SECRET =
                "UPDATE users SET auth_secretKey = ?, auth_enabled = FALSE WHERE id = ?";
    


    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Create a new user and return the generated UUID.
     */
    public UUID create(String username, String passwordHash, Role role) throws SQLException {
        try (var ps = connection.prepareStatement(INSERT_USER)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, role.name());
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
                throw new SQLException("Failed to create user — no ID returned");
            }
        }
    }

    /**
     * Find a user by username. Returns the full row including password_hash.
     */
    public Optional<UserRow> findByUsername(String username) throws SQLException {
        try (var ps = connection.prepareStatement(FIND_BY_USERNAME)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Find a user by ID. Returns the full row including password_hash.
     */
    public Optional<UserRow> findById(UUID id) throws SQLException {
        try (var ps = connection.prepareStatement(FIND_BY_ID)) {
            ps.setObject(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Check whether a username already exists.
     */
    public boolean existsByUsername(String username) throws SQLException {
        try (var ps = connection.prepareStatement(EXISTS_BY_USERNAME)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }


    /**
     * Update the authenticator enabled state for a user. This is called after the user successfully logs in with the authenticator code.
     * @param userId
     * @param state
     * @throws SQLException
     */
    public void updateAuthEnabled(UUID userId, boolean state) throws SQLException {
        try (var ps = connection.prepareStatement(UPDATE_AUTH_ENABLED)) {
            ps.setBoolean(1, state);
            ps.setObject(2, userId);
            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated == 0) {
                throw new SQLException("No user found with ID: " + userId);
            }
        }
    }


    /**
     * Save the Google Authenticator secret for a user. This is only called after the user's password has been verified.
     * The secret is stored in the database, and the authenticator is disabled until the user successfully logs in with the authenticator code.
     * @param userId
     * @param secret
     * @throws SQLException
     */
    public void saveAuthenticatorSecret(UUID userId, String secret) throws SQLException {
        try (var ps = connection.prepareStatement(UPDATE_AUTH_SECRET)) {
            ps.setString(1, secret);
            ps.setObject(2, userId);
            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated == 0) {
                throw new SQLException("No user found with ID: " + userId);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Row mapping
    // -------------------------------------------------------------------------

    private static UserRow mapRow(java.sql.ResultSet rs) throws SQLException {
        return new UserRow(
                (UUID) rs.getObject("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                Role.fromString(rs.getString("role")),
                rs.getString("auth_secretKey"),
                rs.getBoolean("auth_enabled"),
                rs.getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC)
        );
    }

    // -------------------------------------------------------------------------
    // Row type
    // -------------------------------------------------------------------------

    /**
     * Internal row object representing a full {@code users} row.
     * Not exposed outside the DAO layer.
     */
    public record UserRow(
            UUID id,
            String username,
            String passwordHash,
            Role role,
            String authSecret,
            boolean authenticatorEnabled,
            java.time.ZonedDateTime createdAt
    ) {}
}
