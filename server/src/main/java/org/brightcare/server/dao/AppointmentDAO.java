package org.brightcare.server.dao;

import org.brightcare.common.dto.AppointmentDTO;
import org.brightcare.common.enums.AppointmentStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object for the {@code appointments} table.
 * <p>
 * Note: the UNIQUE(doctor_id, appointment_time) constraint ensures
 * a doctor cannot be double-booked. Callers should catch the resulting
 * {@link SQLException} (SQL state 23505) and translate it to an
 * {@link org.brightcare.common.exception.AppointmentConflictException}.
 */
public class AppointmentDAO {

    private final Connection connection;

    public AppointmentDAO(Connection connection) {
        this.connection = connection;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    private static final String INSERT_APPOINTMENT =
            """
            INSERT INTO appointments (patient_id, doctor_id, appointment_time,
                                      status, reason_for_visit)
            VALUES (?, ?, ?, ?::VARCHAR, ?)
            RETURNING id, patient_id, doctor_id, appointment_time, status,
                      reason_for_visit, created_at, updated_at
            """;

    private static final String FIND_BY_ID =
            """
            SELECT a.id, a.patient_id, a.doctor_id, a.appointment_time, a.status,
                   a.reason_for_visit, a.created_at, a.updated_at,
                   p.first_name || ' ' || p.last_name AS patient_name,
                   d.full_name AS doctor_name
            FROM appointments a
            JOIN patients p ON a.patient_id = p.id
            JOIN doctors d ON a.doctor_id = d.id
            WHERE a.id = ?
            """;

    private static final String FIND_BY_PATIENT_ID =
            """
            SELECT a.id, a.patient_id, a.doctor_id, a.appointment_time, a.status,
                   a.reason_for_visit, a.created_at, a.updated_at,
                   p.first_name || ' ' || p.last_name AS patient_name,
                   d.full_name AS doctor_name
            FROM appointments a
            JOIN patients p ON a.patient_id = p.id
            JOIN doctors d ON a.doctor_id = d.id
            WHERE a.patient_id = ?
            ORDER BY a.appointment_time DESC
            """;

    private static final String FIND_BY_DOCTOR_ID =
            """
            SELECT a.id, a.patient_id, a.doctor_id, a.appointment_time, a.status,
                   a.reason_for_visit, a.created_at, a.updated_at,
                   p.first_name || ' ' || p.last_name AS patient_name,
                   d.full_name AS doctor_name
            FROM appointments a
            JOIN patients p ON a.patient_id = p.id
            JOIN doctors d ON a.doctor_id = d.id
            WHERE a.doctor_id = ?
            ORDER BY a.appointment_time DESC
            """;

    private static final String FIND_BY_DOCTOR_AND_DATE_RANGE =
            """
            SELECT a.id, a.patient_id, a.doctor_id, a.appointment_time, a.status,
                   a.reason_for_visit, a.created_at, a.updated_at,
                   p.first_name || ' ' || p.last_name AS patient_name,
                   d.full_name AS doctor_name
            FROM appointments a
            JOIN patients p ON a.patient_id = p.id
            JOIN doctors d ON a.doctor_id = d.id
            WHERE a.doctor_id = ?
              AND a.appointment_time >= ?
              AND a.appointment_time < ?
              AND a.status != 'CANCELLED'
            ORDER BY a.appointment_time
            """;

    private static final String CANCEL_APPOINTMENT =
            """
            UPDATE appointments SET status = 'CANCELLED'
            WHERE id = ? AND status = 'SCHEDULED'
            RETURNING id, patient_id, doctor_id, appointment_time, status,
                      reason_for_visit, created_at, updated_at
            """;

    private static final String UPDATE_STATUS =
            """
            UPDATE appointments SET status = ?::VARCHAR
            WHERE id = ?
            RETURNING id, patient_id, doctor_id, appointment_time, status,
                      reason_for_visit, created_at, updated_at
            """;

    // For monthly report aggregation
    private static final String COUNT_BY_STATUS_IN_MONTH =
            """
            SELECT status, COUNT(*) AS cnt
            FROM appointments
            WHERE appointment_time >= ? AND appointment_time < ?
            GROUP BY status
            """;

    private static final String COUNT_BY_SPECIALIZATION_IN_MONTH =
            """
            SELECT d.specialization, COUNT(*) AS cnt
            FROM appointments a
            JOIN doctors d ON a.doctor_id = d.id
            WHERE a.appointment_time >= ? AND a.appointment_time < ?
            GROUP BY d.specialization
            ORDER BY cnt DESC
            """;

    private static final String FIND_IN_MONTH =
            """
            SELECT a.id, a.patient_id, a.doctor_id, a.appointment_time, a.status,
                   a.reason_for_visit, a.created_at, a.updated_at,
                   p.first_name || ' ' || p.last_name AS patient_name,
                   d.full_name AS doctor_name
            FROM appointments a
            JOIN patients p ON a.patient_id = p.id
            JOIN doctors d ON a.doctor_id = d.id
            WHERE a.appointment_time >= ? AND a.appointment_time < ?
            ORDER BY a.appointment_time
            """;

    private static final String FIND_BY_DOCTOR_WITH_RANGE =
            """
            SELECT a.id, a.patient_id, a.doctor_id, a.appointment_time, a.status,
                   a.reason_for_visit, a.created_at, a.updated_at,
                   p.first_name || ' ' || p.last_name AS patient_name,
                   d.full_name AS doctor_name
            FROM appointments a
            JOIN patients p ON a.patient_id = p.id
            JOIN doctors d ON a.doctor_id = d.id
            WHERE a.doctor_id = ?
            ORDER BY a.appointment_time DESC
            """;

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Create a new appointment.
     *
     * @throws SQLException if a unique-constraint violation occurs
     *                      (SQL state 23505 — doctor double‑booked)
     */
    public AppointmentDTO create(UUID patientId, UUID doctorId,
                                 ZonedDateTime appointmentTime,
                                 String reason) throws SQLException {
        try (var ps = connection.prepareStatement(INSERT_APPOINTMENT)) {
            ps.setObject(1, patientId);
            ps.setObject(2, doctorId);
            ps.setTimestamp(3, Timestamp.from(appointmentTime.toInstant()));
            ps.setString(4, AppointmentStatus.SCHEDULED.name());
            ps.setString(5, reason);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapBasicRow(rs);
                }
                throw new SQLException("Failed to create appointment — no row returned");
            }
        }
    }

    /**
     * Find an appointment by ID (includes patient and doctor names).
     */
    public Optional<AppointmentDTO> findById(UUID appointmentId) throws SQLException {
        try (var ps = connection.prepareStatement(FIND_BY_ID)) {
            ps.setObject(1, appointmentId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapFullRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Find all appointments for a patient, newest first.
     */
    public List<AppointmentDTO> findByPatientId(UUID patientId) throws SQLException {
        List<AppointmentDTO> list = new ArrayList<>();
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
     * Find all appointments for a doctor, newest first.
     */
    public List<AppointmentDTO> findByDoctorId(UUID doctorId) throws SQLException {
        List<AppointmentDTO> list = new ArrayList<>();
        try (var ps = connection.prepareStatement(FIND_BY_DOCTOR_ID)) {
            ps.setObject(1, doctorId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFullRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Find non‑cancelled appointments for a doctor within a date range.
     * Used for checking availability.
     */
    public List<AppointmentDTO> findByDoctorAndDateRange(UUID doctorId,
                                                         ZonedDateTime start,
                                                         ZonedDateTime end)
            throws SQLException {
        List<AppointmentDTO> list = new ArrayList<>();
        try (var ps = connection.prepareStatement(FIND_BY_DOCTOR_AND_DATE_RANGE)) {
            ps.setObject(1, doctorId);
            ps.setTimestamp(2, Timestamp.from(start.toInstant()));
            ps.setTimestamp(3, Timestamp.from(end.toInstant()));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFullRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Cancel a SCHEDULED appointment. Returns the updated row if successful.
     */
    public Optional<AppointmentDTO> cancel(UUID appointmentId) throws SQLException {
        try (var ps = connection.prepareStatement(CANCEL_APPOINTMENT)) {
            ps.setObject(1, appointmentId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapBasicRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Update appointment status (e.g. mark as COMPLETED).
     */
    public Optional<AppointmentDTO> updateStatus(UUID appointmentId,
                                                 AppointmentStatus status)
            throws SQLException {
        try (var ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.name());
            ps.setObject(2, appointmentId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapBasicRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Report helpers (used by service layer)
    // -------------------------------------------------------------------------

    /**
     * Aggregate appointment counts by status for a given month.
     * Returns a list of {status, count} pairs.
     */
    public List<StatusCount> countByStatusInMonth(ZonedDateTime monthStart,
                                                  ZonedDateTime monthEnd)
            throws SQLException {
        List<StatusCount> results = new ArrayList<>();
        try (var ps = connection.prepareStatement(COUNT_BY_STATUS_IN_MONTH)) {
            ps.setTimestamp(1, Timestamp.from(monthStart.toInstant()));
            ps.setTimestamp(2, Timestamp.from(monthEnd.toInstant()));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new StatusCount(
                            rs.getString("status"),
                            rs.getInt("cnt")));
                }
            }
        }
        return results;
    }

    /**
     * Aggregate appointment counts by doctor specialization for a given month.
     */
    public List<SpecializationCount> countBySpecializationInMonth(
            ZonedDateTime monthStart, ZonedDateTime monthEnd) throws SQLException {
        List<SpecializationCount> results = new ArrayList<>();
        try (var ps = connection.prepareStatement(COUNT_BY_SPECIALIZATION_IN_MONTH)) {
            ps.setTimestamp(1, Timestamp.from(monthStart.toInstant()));
            ps.setTimestamp(2, Timestamp.from(monthEnd.toInstant()));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new SpecializationCount(
                            rs.getString("specialization"),
                            rs.getInt("cnt")));
                }
            }
        }
        return results;
    }

    /**
     * Find all appointments in a given month (for report detail).
     */
    public List<AppointmentDTO> findInMonth(ZonedDateTime monthStart,
                                            ZonedDateTime monthEnd)
            throws SQLException {
        List<AppointmentDTO> list = new ArrayList<>();
        try (var ps = connection.prepareStatement(FIND_IN_MONTH)) {
            ps.setTimestamp(1, Timestamp.from(monthStart.toInstant()));
            ps.setTimestamp(2, Timestamp.from(monthEnd.toInstant()));
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFullRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Find all appointments for a doctor (no date filter — for doctor report).
     */
    public List<AppointmentDTO> findByDoctorIdAll(UUID doctorId) throws SQLException {
        List<AppointmentDTO> list = new ArrayList<>();
        try (var ps = connection.prepareStatement(FIND_BY_DOCTOR_WITH_RANGE)) {
            ps.setObject(1, doctorId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFullRow(rs));
                }
            }
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // Row mapping
    // -------------------------------------------------------------------------

    /** Maps a row from the appointments table only (no joins). */
    private static AppointmentDTO mapBasicRow(java.sql.ResultSet rs) throws SQLException {
        return new AppointmentDTO(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("doctor_id"),
                null,  // patientName — not available in basic query
                null,  // doctorName
                rs.getTimestamp("appointment_time").toInstant().atZone(ZoneOffset.UTC),
                AppointmentStatus.fromString(rs.getString("status")),
                rs.getString("reason_for_visit"),
                rs.getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC),
                rs.getTimestamp("updated_at").toInstant().atZone(ZoneOffset.UTC)
        );
    }

    /** Maps a row from a query that JOINs patients and doctors. */
    private static AppointmentDTO mapFullRow(java.sql.ResultSet rs) throws SQLException {
        return new AppointmentDTO(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("patient_id"),
                (UUID) rs.getObject("doctor_id"),
                rs.getString("patient_name"),
                rs.getString("doctor_name"),
                rs.getTimestamp("appointment_time").toInstant().atZone(ZoneOffset.UTC),
                AppointmentStatus.fromString(rs.getString("status")),
                rs.getString("reason_for_visit"),
                rs.getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC),
                rs.getTimestamp("updated_at").toInstant().atZone(ZoneOffset.UTC)
        );
    }

    // -------------------------------------------------------------------------
    // Helper records
    // -------------------------------------------------------------------------

    public record StatusCount(String status, int count) {}

    public record SpecializationCount(String specialization, int count) {}
}
