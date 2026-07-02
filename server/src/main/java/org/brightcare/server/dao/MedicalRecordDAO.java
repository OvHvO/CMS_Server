package org.brightcare.server.dao;

import org.brightcare.common.dto.MedicalRecordDTO;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object for the {@code medical_records} table.
 */
public class MedicalRecordDAO {

    private final Connection connection;

    public MedicalRecordDAO(Connection connection) {
        this.connection = connection;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    private static final String INSERT_RECORD =
            """
            INSERT INTO medical_records (appointment_id, patient_id, doctor_id,
                                         consultation_notes, prescription)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id, appointment_id, patient_id, doctor_id,
                      consultation_notes, prescription, created_at, updated_at
            """;

    private static final String FIND_BY_ID =
            """
            SELECT mr.id, mr.appointment_id, mr.patient_id, mr.doctor_id,
                   mr.consultation_notes, mr.prescription, mr.created_at, mr.updated_at,
                   d.full_name AS doctor_name,
                   a.appointment_time
            FROM medical_records mr
            JOIN doctors d ON mr.doctor_id = d.id
            JOIN appointments a ON mr.appointment_id = a.id
            WHERE mr.id = ?
            """;

    private static final String FIND_BY_PATIENT_ID =
            """
            SELECT mr.id, mr.appointment_id, mr.patient_id, mr.doctor_id,
                   mr.consultation_notes, mr.prescription, mr.created_at, mr.updated_at,
                   d.full_name AS doctor_name,
                   a.appointment_time
            FROM medical_records mr
            JOIN doctors d ON mr.doctor_id = d.id
            JOIN appointments a ON mr.appointment_id = a.id
            WHERE mr.patient_id = ?
            ORDER BY mr.created_at DESC
            """;

    private static final String FIND_BY_APPOINTMENT_ID =
            """
            SELECT mr.id, mr.appointment_id, mr.patient_id, mr.doctor_id,
                   mr.consultation_notes, mr.prescription, mr.created_at, mr.updated_at,
                   d.full_name AS doctor_name,
                   a.appointment_time
            FROM medical_records mr
            JOIN doctors d ON mr.doctor_id = d.id
            JOIN appointments a ON mr.appointment_id = a.id
            WHERE mr.appointment_id = ?
            """;

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Create a new medical record linked to an appointment.
     */
    public MedicalRecordDTO create(UUID appointmentId, UUID patientId,
                                   UUID doctorId, String consultationNotes,
                                   String prescription) throws SQLException {
        try (var ps = connection.prepareStatement(INSERT_RECORD)) {
            ps.setObject(1, appointmentId);
            ps.setObject(2, patientId);
            ps.setObject(3, doctorId);
            ps.setString(4, consultationNotes);
            ps.setString(5, prescription);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapBasicRow(rs);
                }
                throw new SQLException("Failed to create medical record — no row returned");
            }
        }
    }

    /**
     * Find a medical record by its ID.
     */
    public Optional<MedicalRecordDTO> findById(UUID recordId) throws SQLException {
        try (var ps = connection.prepareStatement(FIND_BY_ID)) {
            ps.setObject(1, recordId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapFullRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Get all medical records for a patient (medical history).
     */
    public List<MedicalRecordDTO> findByPatientId(UUID patientId) throws SQLException {
        List<MedicalRecordDTO> list = new ArrayList<>();
        try (var ps = connection.prepareStatement(FIND_BY_PATIENT_ID)) {
            ps.setObject(1, patientId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFullRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Find the medical record associated with a specific appointment.
     */
    public Optional<MedicalRecordDTO> findByAppointmentId(UUID appointmentId)
            throws SQLException {
        try (var ps = connection.prepareStatement(FIND_BY_APPOINTMENT_ID)) {
            ps.setObject(1, appointmentId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapFullRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Row mapping
    // -------------------------------------------------------------------------

    private static MedicalRecordDTO mapBasicRow(java.sql.ResultSet rs) throws SQLException {
        return new MedicalRecordDTO(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("appointment_id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("doctor_id"),
                null,  // doctorName — from basic insert return
                null,  // appointmentTime
                rs.getString("consultation_notes"),
                rs.getString("prescription"),
                rs.getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC),
                rs.getTimestamp("updated_at").toInstant().atZone(ZoneOffset.UTC)
        );
    }

    private static MedicalRecordDTO mapFullRow(java.sql.ResultSet rs) throws SQLException {
        return new MedicalRecordDTO(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("appointment_id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("doctor_id"),
                rs.getString("doctor_name"),
                rs.getTimestamp("appointment_time").toInstant().atZone(ZoneOffset.UTC),
                rs.getString("consultation_notes"),
                rs.getString("prescription"),
                rs.getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC),
                rs.getTimestamp("updated_at").toInstant().atZone(ZoneOffset.UTC)
        );
    }
}
