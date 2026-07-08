package org.brightcare.server.dao;

import org.brightcare.common.dto.PatientDTO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object for the {@code patients} table.
 */
public class PatientDAO {

    private final Connection connection;

    public PatientDAO(Connection connection) {
        this.connection = connection;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    private static final String INSERT_PATIENT =
            """
            INSERT INTO patients (user_id, first_name, last_name, ic_passport_number,
                                  contact_number, medical_record_id)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id, user_id, first_name, last_name, ic_passport_number,
                      contact_number, medical_record_id, created_at, updated_at
            """;

    private static final String FIND_BY_ID =
            """
            SELECT id, user_id, first_name, last_name, ic_passport_number,
                   contact_number, medical_record_id, created_at, updated_at
            FROM patients WHERE id = ?
            """;

    private static final String FIND_BY_USER_ID =
            """
            SELECT id, user_id, first_name, last_name, ic_passport_number,
                   contact_number, medical_record_id, created_at, updated_at
            FROM patients WHERE user_id = ?
            """;

    private static final String UPDATE_PATIENT =
            """
            UPDATE patients
            SET first_name = ?, last_name = ?, ic_passport_number = ?,
                contact_number = ?, medical_record_id = ?
            WHERE id = ?
            RETURNING id, user_id, first_name, last_name, ic_passport_number,
                      contact_number, medical_record_id, created_at, updated_at
            """;

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Create a new patient record.
     */
    public PatientDTO create(UUID userId, String firstName, String lastName,
                             String icPassportNumber, String contactNumber,
                             String medicalRecordId) throws SQLException {
        try (var ps = connection.prepareStatement(INSERT_PATIENT)) {
            ps.setObject(1, userId);
            ps.setString(2, firstName);
            ps.setString(3, lastName);
            ps.setString(4, icPassportNumber);
            ps.setString(5, contactNumber);
            ps.setString(6, medicalRecordId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                throw new SQLException("Failed to create patient — no row returned");
            }
        }
    }

    /**
     * Find a patient by their patient ID.
     */
    public Optional<PatientDTO> findById(UUID patientId) throws SQLException {
        try (var ps = connection.prepareStatement(FIND_BY_ID)) {
            ps.setObject(1, patientId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Find a patient by their linked user ID.
     */
    public Optional<PatientDTO> findByUserId(UUID userId) throws SQLException {
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
     * Update a patient's demographic fields. Returns the updated row.
     */
    public Optional<PatientDTO> update(UUID patientId, String firstName, String lastName,
                                       String icPassportNumber, String contactNumber,
                                       String medicalRecordId) throws SQLException {
        try (var ps = connection.prepareStatement(UPDATE_PATIENT)) {
            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, icPassportNumber);
            ps.setString(4, contactNumber);
            ps.setString(5, medicalRecordId);
            ps.setObject(6, patientId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Row mapping
    // -------------------------------------------------------------------------

    private static PatientDTO mapRow(java.sql.ResultSet rs) throws SQLException {
        return new PatientDTO(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("user_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("ic_passport_number"),
                rs.getString("contact_number"),
                rs.getString("medical_record_id"),
                rs.getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC),
                rs.getTimestamp("updated_at").toInstant().atZone(java.time.ZoneOffset.UTC)
        );
    }
}
