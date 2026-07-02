package org.brightcare.server.impl;

import org.brightcare.common.ClinicService;
import org.brightcare.common.dto.*;
import org.brightcare.common.enums.AppointmentStatus;
import org.brightcare.common.enums.Role;
import org.brightcare.common.exception.*;
import org.brightcare.server.config.DatabaseConfig;
import org.brightcare.server.dao.*;
import org.brightcare.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side implementation of the {@link ClinicService} RMI interface.
 * <p>
 * Each method obtains a fresh {@link Connection} from HikariCP, performs its
 * work, and closes the connection in a finally block — preventing connection
 * leaks even when exceptions occur.
 * <p>
 * Thread safety: the RMI runtime dispatches each client call to a separate
 * thread. The DAOs are local to each method call, so no shared mutable state
 * exists between threads. HikariCP handles concurrent connection access.
 */
public class ClinicServiceImpl implements ClinicService {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ClinicServiceImpl.class);

    // Clinic operating hours
    private static final LocalTime CLINIC_OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLINIC_CLOSE = LocalTime.of(17, 0);
    private static final int SLOT_MINUTES = 30;

    // PostgreSQL unique-constraint violation
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    public ClinicServiceImpl() {
        // Default constructor
    }

    // =========================================================================
    // Authentication
    // =========================================================================

    @Override
    public UserDTO login(String username, String password)
            throws AuthenticationException, RemoteException {
        log.info("Login attempt for user: {}", username);

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new AuthenticationException("Username and password are required");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            UserDAO userDAO = new UserDAO(conn);

            UserDAO.UserRow user = userDAO.findByUsername(username)
                    .orElseThrow(() -> new AuthenticationException(
                            "Invalid username or password"));

            if (!PasswordUtil.verify(password, user.passwordHash())) {
                log.warn("Failed login attempt for user: {}", username);
                throw new AuthenticationException("Invalid username or password");
            }

            log.info("User '{}' authenticated successfully with role {}", username, user.role());
            return new UserDTO(user.id(), user.username(), user.role(), user.createdAt());

        } catch (SQLException e) {
            log.error("Database error during login for user: {}", username, e);
            throw new RemoteException("Internal server error during authentication", e);
        } finally {
            closeQuietly(conn);
        }
    }

    // =========================================================================
    // Receptionist Operations
    // =========================================================================

    @Override
    public PatientDTO registerPatient(PatientDTO patient, String username, String password)
            throws InvalidDataException, UnauthorizedException, RemoteException {
        log.info("Registering new patient: username={}, name={} {}",
                username, patient.firstName(), patient.lastName());

        validatePatientData(patient, username, password);

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);  // transaction: create user + patient atomically

            UserDAO userDAO = new UserDAO(conn);
            PatientDAO patientDAO = new PatientDAO(conn);

            // Check username uniqueness
            if (userDAO.existsByUsername(username)) {
                throw new InvalidDataException(
                        "Username '" + username + "' is already taken");
            }

            // Create user with PATIENT role
            String passwordHash = PasswordUtil.hash(password);
            UUID userId = userDAO.create(username, passwordHash, Role.PATIENT);

            // Create patient record
            PatientDTO created = patientDAO.create(
                    userId,
                    patient.firstName(),
                    patient.lastName(),
                    patient.icPassportNumber(),
                    patient.contactNumber(),
                    patient.medicalRecordId()
            );

            conn.commit();
            log.info("Patient registered successfully: patientId={}, userId={}",
                    created.id(), userId);
            return created;

        } catch (SQLException e) {
            rollbackQuietly(conn);
            log.error("Database error registering patient", e);

            // Check for duplicate key on ic_passport_number or medical_record_id
            if (SQLSTATE_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new InvalidDataException(
                        "A patient with this IC/Passport number or Medical Record ID already exists");
            }
            throw new RemoteException("Internal server error registering patient", e);
        } finally {
            closeQuietly(conn);
        }
    }

    // =========================================================================
    // Patient Operations
    // =========================================================================

    @Override
    public PatientDTO updatePatientInfo(PatientDTO patient)
            throws NotFoundException, InvalidDataException, RemoteException {
        log.info("Updating patient info: patientId={}", patient.id());

        if (patient.id() == null) {
            throw new InvalidDataException("Patient ID is required for update");
        }
        validatePatientFields(patient);

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PatientDAO patientDAO = new PatientDAO(conn);

            // Verify patient exists
            patientDAO.findById(patient.id())
                    .orElseThrow(() -> new NotFoundException("Patient", patient.id()));

            PatientDTO updated = patientDAO.update(
                    patient.id(),
                    patient.firstName(),
                    patient.lastName(),
                    patient.icPassportNumber(),
                    patient.contactNumber(),
                    patient.medicalRecordId()
            ).orElseThrow(() -> new NotFoundException("Patient", patient.id()));

            log.info("Patient updated successfully: patientId={}", patient.id());
            return updated;

        } catch (SQLException e) {
            log.error("Database error updating patient: {}", patient.id(), e);
            if (SQLSTATE_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new InvalidDataException(
                        "IC/Passport number or Medical Record ID conflicts with another patient");
            }
            throw new RemoteException("Internal server error updating patient", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public AppointmentDTO bookAppointment(UUID patientId, UUID doctorId,
                                          ZonedDateTime appointmentTime, String reason)
            throws NotFoundException, AppointmentConflictException,
            InvalidDataException, RemoteException {
        log.info("Booking appointment: patientId={}, doctorId={}, time={}",
                patientId, doctorId, appointmentTime);

        if (patientId == null || doctorId == null || appointmentTime == null) {
            throw new InvalidDataException("Patient ID, doctor ID, and appointment time are required");
        }

        // Validate appointment time is in the future
        if (appointmentTime.isBefore(ZonedDateTime.now())) {
            throw new InvalidDataException("Appointment time must be in the future");
        }

        // Validate within clinic hours
        LocalTime time = appointmentTime.toLocalTime();
        if (time.isBefore(CLINIC_OPEN) || time.isAfter(CLINIC_CLOSE.minusMinutes(SLOT_MINUTES))) {
            throw new InvalidDataException(
                    "Appointment time must be between " + CLINIC_OPEN + " and " +
                    CLINIC_CLOSE.minusMinutes(SLOT_MINUTES));
        }

        // Validate on the half-hour boundary
        if (time.getMinute() % SLOT_MINUTES != 0) {
            throw new InvalidDataException(
                    "Appointment time must be on a " + SLOT_MINUTES + "-minute boundary");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PatientDAO patientDAO = new PatientDAO(conn);
            DoctorDAO doctorDAO = new DoctorDAO(conn);
            AppointmentDAO appointmentDAO = new AppointmentDAO(conn);

            // Verify patient exists
            patientDAO.findById(patientId)
                    .orElseThrow(() -> new NotFoundException("Patient", patientId));

            // Verify doctor exists and is active
            DoctorDTO doctor = doctorDAO.findById(doctorId)
                    .orElseThrow(() -> new NotFoundException("Doctor", doctorId));
            if (!doctor.isActive()) {
                throw new InvalidDataException("Doctor '" + doctor.fullName() + "' is not currently active");
            }

            // Attempt to create the appointment
            // UNIQUE(doctor_id, appointment_time) will reject double-booking
            AppointmentDTO appointment = appointmentDAO.create(
                    patientId, doctorId, appointmentTime, reason);

            log.info("Appointment booked successfully: appointmentId={}", appointment.id());
            return appointment;

        } catch (SQLException e) {
            log.error("Database error booking appointment", e);

            if (SQLSTATE_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new AppointmentConflictException(
                        "Doctor already has an appointment at " + appointmentTime,
                        appointmentTime);
            }
            throw new RemoteException("Internal server error booking appointment", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public void cancelAppointment(UUID appointmentId)
            throws NotFoundException, InvalidDataException, RemoteException {
        log.info("Cancelling appointment: appointmentId={}", appointmentId);

        if (appointmentId == null) {
            throw new InvalidDataException("Appointment ID is required");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            AppointmentDAO appointmentDAO = new AppointmentDAO(conn);

            // Verify appointment exists
            AppointmentDTO appointment = appointmentDAO.findById(appointmentId)
                    .orElseThrow(() -> new NotFoundException("Appointment", appointmentId));

            // Only SCHEDULED appointments can be cancelled
            if (appointment.status() != AppointmentStatus.SCHEDULED) {
                throw new InvalidDataException(
                        "Cannot cancel appointment — current status is " + appointment.status());
            }

            appointmentDAO.cancel(appointmentId)
                    .orElseThrow(() -> new NotFoundException("Appointment", appointmentId));

            log.info("Appointment cancelled successfully: appointmentId={}", appointmentId);

        } catch (SQLException e) {
            log.error("Database error cancelling appointment: {}", appointmentId, e);
            throw new RemoteException("Internal server error cancelling appointment", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public List<AvailabilitySlotDTO> getDoctorAvailability(UUID doctorId, LocalDate date)
            throws NotFoundException, RemoteException {
        log.debug("Checking availability: doctorId={}, date={}", doctorId, date);

        if (doctorId == null || date == null) {
            throw new NotFoundException("Doctor ID and date are required");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            DoctorDAO doctorDAO = new DoctorDAO(conn);
            AppointmentDAO appointmentDAO = new AppointmentDAO(conn);

            // Verify doctor exists
            doctorDAO.findById(doctorId)
                    .orElseThrow(() -> new NotFoundException("Doctor", doctorId));

            // Get all booked slots for this doctor on this day
            ZonedDateTime dayStart = date.atStartOfDay(ZoneOffset.UTC);
            ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC);

            List<AppointmentDTO> bookedAppointments =
                    appointmentDAO.findByDoctorAndDateRange(doctorId, dayStart, dayEnd);

            Set<LocalTime> bookedTimes = bookedAppointments.stream()
                    .map(a -> a.appointmentTime().toLocalTime())
                    .collect(Collectors.toSet());

            // Build 30-minute slots from CLINIC_OPEN to CLINIC_CLOSE
            List<AvailabilitySlotDTO> slots = new ArrayList<>();
            LocalTime current = CLINIC_OPEN;
            while (current.isBefore(CLINIC_CLOSE)) {
                LocalTime slotEnd = current.plusMinutes(SLOT_MINUTES);
                boolean available = !bookedTimes.contains(current);
                slots.add(new AvailabilitySlotDTO(current, slotEnd, available));
                current = slotEnd;
            }

            return slots;

        } catch (SQLException e) {
            log.error("Database error checking availability", e);
            throw new RemoteException("Internal server error checking availability", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public List<AppointmentDTO> getPatientAppointments(UUID patientId)
            throws NotFoundException, RemoteException {
        log.debug("Fetching appointments for patient: {}", patientId);

        if (patientId == null) {
            throw new NotFoundException("Patient ID is required");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PatientDAO patientDAO = new PatientDAO(conn);
            AppointmentDAO appointmentDAO = new AppointmentDAO(conn);

            // Verify patient exists
            patientDAO.findById(patientId)
                    .orElseThrow(() -> new NotFoundException("Patient", patientId));

            return appointmentDAO.findByPatientId(patientId);

        } catch (SQLException e) {
            log.error("Database error fetching patient appointments: {}", patientId, e);
            throw new RemoteException("Internal server error fetching appointments", e);
        } finally {
            closeQuietly(conn);
        }
    }

    // =========================================================================
    // Doctor Operations
    // =========================================================================

    @Override
    public List<AppointmentDTO> getDoctorAppointments(UUID doctorId)
            throws NotFoundException, RemoteException {
        log.debug("Fetching appointments for doctor: {}", doctorId);

        if (doctorId == null) {
            throw new NotFoundException("Doctor ID is required");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            DoctorDAO doctorDAO = new DoctorDAO(conn);
            AppointmentDAO appointmentDAO = new AppointmentDAO(conn);

            // Verify doctor exists
            doctorDAO.findById(doctorId)
                    .orElseThrow(() -> new NotFoundException("Doctor", doctorId));

            return appointmentDAO.findByDoctorId(doctorId);

        } catch (SQLException e) {
            log.error("Database error fetching doctor appointments: {}", doctorId, e);
            throw new RemoteException("Internal server error fetching appointments", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public MedicalRecordDTO updateConsultationNotes(UUID appointmentId,
                                                    String notes,
                                                    String prescription)
            throws NotFoundException, InvalidDataException, RemoteException {
        log.info("Updating consultation notes for appointment: {}", appointmentId);

        if (appointmentId == null) {
            throw new InvalidDataException("Appointment ID is required");
        }
        if (notes == null || notes.isBlank()) {
            throw new InvalidDataException("Consultation notes must not be empty");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            AppointmentDAO appointmentDAO = new AppointmentDAO(conn);
            MedicalRecordDAO medicalRecordDAO = new MedicalRecordDAO(conn);

            // Verify appointment exists
            AppointmentDTO appointment = appointmentDAO.findById(appointmentId)
                    .orElseThrow(() -> new NotFoundException("Appointment", appointmentId));

            // Check if a medical record already exists for this appointment (1:1)
            if (medicalRecordDAO.findByAppointmentId(appointmentId).isPresent()) {
                throw new InvalidDataException(
                        "A medical record already exists for this appointment");
            }

            // Create the medical record
            MedicalRecordDTO record = medicalRecordDAO.create(
                    appointmentId,
                    appointment.patientId(),
                    appointment.doctorId(),
                    notes,
                    prescription
            );

            // Mark appointment as COMPLETED
            appointmentDAO.updateStatus(appointmentId, AppointmentStatus.COMPLETED);

            conn.commit();
            log.info("Consultation notes saved: recordId={}, appointmentId={}",
                    record.id(), appointmentId);
            return record;

        } catch (SQLException e) {
            rollbackQuietly(conn);
            log.error("Database error saving consultation notes: {}", appointmentId, e);
            if (SQLSTATE_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new InvalidDataException(
                        "A medical record already exists for this appointment");
            }
            throw new RemoteException("Internal server error saving consultation notes", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public List<MedicalRecordDTO> getPatientMedicalHistory(UUID patientId)
            throws NotFoundException, RemoteException {
        log.debug("Fetching medical history for patient: {}", patientId);

        if (patientId == null) {
            throw new NotFoundException("Patient ID is required");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PatientDAO patientDAO = new PatientDAO(conn);
            MedicalRecordDAO medicalRecordDAO = new MedicalRecordDAO(conn);

            // Verify patient exists
            patientDAO.findById(patientId)
                    .orElseThrow(() -> new NotFoundException("Patient", patientId));

            return medicalRecordDAO.findByPatientId(patientId);

        } catch (SQLException e) {
            log.error("Database error fetching medical history: {}", patientId, e);
            throw new RemoteException("Internal server error fetching medical history", e);
        } finally {
            closeQuietly(conn);
        }
    }

    // =========================================================================
    // Admin Operations
    // =========================================================================

    @Override
    public ReportDTO generateMonthlyReport(int year, int month)
            throws InvalidDataException, RemoteException {
        log.info("Generating monthly report: {}/{}", year, month);

        if (month < 1 || month > 12) {
            throw new InvalidDataException("Month must be between 1 and 12, got: " + month);
        }
        if (year < 2000 || year > 2100) {
            throw new InvalidDataException("Year out of valid range: " + year);
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            AppointmentDAO appointmentDAO = new AppointmentDAO(conn);

            ZonedDateTime monthStart = ZonedDateTime.of(
                    year, month, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            ZonedDateTime monthEnd = monthStart.plusMonths(1);

            // Aggregate counts
            List<AppointmentDAO.StatusCount> statusCounts =
                    appointmentDAO.countByStatusInMonth(monthStart, monthEnd);

            int total = 0, completed = 0, cancelled = 0, noShow = 0;
            for (AppointmentDAO.StatusCount sc : statusCounts) {
                total += sc.count();
                try {
                    switch (AppointmentStatus.fromString(sc.status())) {
                        case COMPLETED -> completed = sc.count();
                        case CANCELLED -> cancelled = sc.count();
                        case NO_SHOW -> noShow = sc.count();
                    }
                } catch (IllegalArgumentException ignored) {
                    // unknown status — still counts toward total
                }
            }

            // By specialization
            List<AppointmentDAO.SpecializationCount> specCounts =
                    appointmentDAO.countBySpecializationInMonth(monthStart, monthEnd);
            Map<String, Integer> bySpec = new LinkedHashMap<>();
            for (AppointmentDAO.SpecializationCount sc : specCounts) {
                bySpec.put(sc.specialization(), sc.count());
            }

            // Detail rows
            List<AppointmentDTO> details =
                    appointmentDAO.findInMonth(monthStart, monthEnd);

            String title = String.format("Monthly Report — %d/%02d", year, month);
            String period = String.format("%s — %s",
                    monthStart.toLocalDate(), monthEnd.minusDays(1).toLocalDate());

            log.info("Monthly report generated: {} appointments ({} completed, {} cancelled, {} no-show)",
                    total, completed, cancelled, noShow);

            return new ReportDTO(title, period, total, completed, cancelled,
                    noShow, bySpec, details);

        } catch (SQLException e) {
            log.error("Database error generating monthly report", e);
            throw new RemoteException("Internal server error generating report", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public ReportDTO generateDoctorConsultationReport(UUID doctorId)
            throws NotFoundException, RemoteException {
        log.info("Generating doctor consultation report: doctorId={}", doctorId);

        if (doctorId == null) {
            throw new NotFoundException("Doctor ID is required");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            DoctorDAO doctorDAO = new DoctorDAO(conn);
            AppointmentDAO appointmentDAO = new AppointmentDAO(conn);

            // Verify doctor exists
            DoctorDTO doctor = doctorDAO.findById(doctorId)
                    .orElseThrow(() -> new NotFoundException("Doctor", doctorId));

            List<AppointmentDTO> appointments =
                    appointmentDAO.findByDoctorIdAll(doctorId);

            int total = appointments.size();
            int completed = 0, cancelled = 0, noShow = 0, scheduled = 0;
            for (AppointmentDTO a : appointments) {
                switch (a.status()) {
                    case COMPLETED -> completed++;
                    case CANCELLED -> cancelled++;
                    case NO_SHOW -> noShow++;
                    case SCHEDULED -> scheduled++;
                }
            }

            // Group by status for the report
            Map<String, Integer> byStatus = new LinkedHashMap<>();
            byStatus.put("Completed", completed);
            byStatus.put("Cancelled", cancelled);
            byStatus.put("No Show", noShow);
            byStatus.put("Scheduled", scheduled);

            String title = "Doctor Consultation Report — Dr. " + doctor.fullName();
            String period = "All time";

            log.info("Doctor report generated: {} ({} total appointments)", doctor.fullName(), total);

            return new ReportDTO(title, period, total, completed, cancelled,
                    noShow, byStatus, appointments);

        } catch (SQLException e) {
            log.error("Database error generating doctor report: {}", doctorId, e);
            throw new RemoteException("Internal server error generating doctor report", e);
        } finally {
            closeQuietly(conn);
        }
    }

    // =========================================================================
    // Lookup / Utility
    // =========================================================================

    @Override
    public List<DoctorDTO> getAllDoctors() throws RemoteException {
        log.debug("Fetching all active doctors");

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            DoctorDAO doctorDAO = new DoctorDAO(conn);
            return doctorDAO.findAllActive();
        } catch (SQLException e) {
            log.error("Database error fetching doctors", e);
            throw new RemoteException("Internal server error fetching doctors", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public PatientDTO getPatientByUserId(UUID userId)
            throws NotFoundException, RemoteException {
        log.debug("Looking up patient by userId: {}", userId);

        if (userId == null) {
            throw new NotFoundException("User ID is required");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PatientDAO patientDAO = new PatientDAO(conn);
            return patientDAO.findByUserId(userId)
                    .orElseThrow(() -> new NotFoundException(
                            "Patient not found for user ID: " + userId));
        } catch (SQLException e) {
            log.error("Database error looking up patient by userId: {}", userId, e);
            throw new RemoteException("Internal server error", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public DoctorDTO getDoctorByUserId(UUID userId)
            throws NotFoundException, RemoteException {
        log.debug("Looking up doctor by userId: {}", userId);

        if (userId == null) {
            throw new NotFoundException("User ID is required");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            DoctorDAO doctorDAO = new DoctorDAO(conn);
            return doctorDAO.findByUserId(userId)
                    .orElseThrow(() -> new NotFoundException(
                            "Doctor not found for user ID: " + userId));
        } catch (SQLException e) {
            log.error("Database error looking up doctor by userId: {}", userId, e);
            throw new RemoteException("Internal server error", e);
        } finally {
            closeQuietly(conn);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void validatePatientData(PatientDTO patient, String username, String password)
            throws InvalidDataException {
        if (username == null || username.isBlank()) {
            throw new InvalidDataException("Username is required");
        }
        if (password == null || password.isBlank()) {
            throw new InvalidDataException("Password is required");
        }
        if (password.length() < 6) {
            throw new InvalidDataException("Password must be at least 6 characters");
        }
        validatePatientFields(patient);
    }

    private void validatePatientFields(PatientDTO patient) throws InvalidDataException {
        if (patient.firstName() == null || patient.firstName().isBlank()) {
            throw new InvalidDataException("First name is required");
        }
        if (patient.lastName() == null || patient.lastName().isBlank()) {
            throw new InvalidDataException("Last name is required");
        }
        if (patient.icPassportNumber() == null || patient.icPassportNumber().isBlank()) {
            throw new InvalidDataException("IC/Passport number is required");
        }
        if (patient.contactNumber() == null || patient.contactNumber().isBlank()) {
            throw new InvalidDataException("Contact number is required");
        }
        if (patient.medicalRecordId() == null || patient.medicalRecordId().isBlank()) {
            throw new InvalidDataException("Medical record ID is required");
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.warn("Error closing database connection", e);
            }
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                log.warn("Error rolling back transaction", e);
            }
        }
    }
}
