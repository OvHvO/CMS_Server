package org.brightcare.server.dao;

import org.brightcare.common.dto.DoctorDTO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object for the {@code doctors} table.
 */
public class DoctorDAO {

    private final Connection connection;

    public DoctorDAO(Connection connection) {
        this.connection = connection;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    private static final String INSERT_DOCTOR =
            """
            INSERT INTO doctors (user_id, full_name, specialization, is_active)
            VALUES (?, ?, ?, ?)
            RETURNING id, user_id, full_name, specialization, is_active, created_at
            """;

    private static final String FIND_BY_ID =
            """
            SELECT id, user_id, full_name, specialization, is_active, created_at
            FROM doctors WHERE id = ?
            """;

    private static final String FIND_BY_USER_ID =
            """
            SELECT id, user_id, full_name, specialization, is_active, created_at
            FROM doctors WHERE user_id = ?
            """;

    private static final String FIND_ALL_ACTIVE =
            """
            SELECT id, user_id, full_name, specialization, is_active, created_at
            FROM doctors WHERE is_active = TRUE
            ORDER BY full_name
            """;

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Create a new doctor record (admin bootstrap use).
     */
    public DoctorDTO create(UUID userId, String fullName, String specialization,
                            boolean isActive) throws SQLException {
        try (var ps = connection.prepareStatement(INSERT_DOCTOR)) {
            ps.setObject(1, userId);
            ps.setString(2, fullName);
            ps.setString(3, specialization);
            ps.setBoolean(4, isActive);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                throw new SQLException("Failed to create doctor — no row returned");
            }
        }
    }

    /**
     * Find a doctor by their doctor ID.
     */
    public Optional<DoctorDTO> findById(UUID doctorId) throws SQLException {
        try (var ps = connection.prepareStatement(FIND_BY_ID)) {
            ps.setObject(1, doctorId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Find a doctor by their linked user ID.
     */
    public Optional<DoctorDTO> findByUserId(UUID userId) throws SQLException {
        try (var ps = connection.prepareStatement(FIND_BY_USER_ID)) {
            ps.setObject(1, userId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Get all active doctors.
     */
    public List<DoctorDTO> findAllActive() throws SQLException {
        List<DoctorDTO> doctors = new ArrayList<>();
        try (var ps = connection.prepareStatement(FIND_ALL_ACTIVE);
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                doctors.add(mapRow(rs));
            }
        }
        return doctors;
    }

    // -------------------------------------------------------------------------
    // Row mapping
    // -------------------------------------------------------------------------

    private static DoctorDTO mapRow(java.sql.ResultSet rs) throws SQLException {
        return new DoctorDTO(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("user_id"),
                rs.getString("full_name"),
                rs.getString("specialization"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC)
        );
    }
}
