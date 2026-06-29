package org.brightcare.common;

import org.brightcare.common.dto.*;
import org.brightcare.common.exception.*;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * RMI Remote Interface for BrightCare Medical Centre.
 * <p>
 * All methods throw {@link RemoteException} as required by Java RMI.
 * Business-level errors are conveyed via the custom exception types
 * declared on each method.
 * <p>
 * <b>Thread safety:</b> The RMI runtime dispatches each client call to
 * a separate thread. Implementations must ensure DAO-level thread safety
 * through connection-per-operation (HikariCP).
 */
public interface ClinicService extends Remote {

    // =========================================================================
    // Authentication
    // =========================================================================

    /**
     * Authenticate a user with username and password.
     *
     * @param username the user's unique username
     * @param password the user's plain-text password (verified against BCrypt hash)
     * @return UserDTO containing the user's ID and role (never the password)
     * @throws AuthenticationException if credentials are invalid
     * @throws RemoteException         if an RMI communication error occurs
     */
    UserDTO login(String username, String password)
            throws AuthenticationException, RemoteException;

    // =========================================================================
    // Receptionist Operations
    // =========================================================================

    /**
     * Register a new patient. Creates both a users row and a patients row.
     * Restricted to RECEPTIONIST role.
     *
     * @param patient  the patient's demographic details
     * @param username the login username for the patient
     * @param password the plain-text password (will be BCrypt-hashed before storage)
     * @return the created PatientDTO with generated IDs
     * @throws InvalidDataException if required fields are missing or data is malformed
     * @throws UnauthorizedException if the calling user is not a RECEPTIONIST
     * @throws RemoteException      if an RMI communication error occurs
     */
    PatientDTO registerPatient(PatientDTO patient, String username, String password)
            throws InvalidDataException, UnauthorizedException, RemoteException;

    // =========================================================================
    // Patient Operations
    // =========================================================================

    /**
     * Update a patient's own demographic information.
     *
     * @param patient the updated patient data (id must match an existing patient)
     * @return the updated PatientDTO
     * @throws NotFoundException   if the patient ID does not exist
     * @throws InvalidDataException if the update data fails validation
     * @throws RemoteException     if an RMI communication error occurs
     */
    PatientDTO updatePatientInfo(PatientDTO patient)
            throws NotFoundException, InvalidDataException, RemoteException;

    /**
     * Book an appointment for a patient with a specific doctor.
     *
     * @param patientId       the UUID of the patient
     * @param doctorId        the UUID of the doctor
     * @param appointmentTime the desired date and time of the appointment
     * @param reason          the reason for the visit
     * @return the created AppointmentDTO
     * @throws NotFoundException            if the patient or doctor does not exist
     * @throws AppointmentConflictException if the doctor already has an appointment at that time
     * @throws InvalidDataException         if the appointment time is in the past
     * @throws RemoteException              if an RMI communication error occurs
     */
    AppointmentDTO bookAppointment(UUID patientId, UUID doctorId,
                                   ZonedDateTime appointmentTime, String reason)
            throws NotFoundException, AppointmentConflictException,
            InvalidDataException, RemoteException;

    /**
     * Cancel an existing appointment. Only SCHEDULED appointments can be cancelled.
     *
     * @param appointmentId the UUID of the appointment to cancel
     * @throws NotFoundException   if the appointment does not exist
     * @throws InvalidDataException if the appointment is already COMPLETED or CANCELLED
     * @throws RemoteException     if an RMI communication error occurs
     */
    void cancelAppointment(UUID appointmentId)
            throws NotFoundException, InvalidDataException, RemoteException;

    /**
     * Get available time slots for a doctor on a specific date.
     * Returns 30-minute slots from 08:00 to 17:00, marking which are free.
     *
     * @param doctorId the UUID of the doctor
     * @param date     the date to check
     * @return list of 30-minute slots with availability status
     * @throws NotFoundException if the doctor does not exist
     * @throws RemoteException   if an RMI communication error occurs
     */
    List<AvailabilitySlotDTO> getDoctorAvailability(UUID doctorId, LocalDate date)
            throws NotFoundException, RemoteException;

    /**
     * Get all appointments for a specific patient.
     *
     * @param patientId the UUID of the patient
     * @return list of the patient's appointments, ordered by appointment_time descending
     * @throws NotFoundException if the patient does not exist
     * @throws RemoteException   if an RMI communication error occurs
     */
    List<AppointmentDTO> getPatientAppointments(UUID patientId)
            throws NotFoundException, RemoteException;

    // =========================================================================
    // Doctor Operations
    // =========================================================================

    /**
     * Get all appointments for a specific doctor.
     *
     * @param doctorId the UUID of the doctor
     * @return list of the doctor's appointments, ordered by appointment_time
     * @throws NotFoundException if the doctor does not exist
     * @throws RemoteException   if an RMI communication error occurs
     */
    List<AppointmentDTO> getDoctorAppointments(UUID doctorId)
            throws NotFoundException, RemoteException;

    /**
     * Update consultation notes for a completed appointment.
     * Creates a medical_records row linked to the appointment.
     *
     * @param appointmentId  the UUID of the appointment
     * @param notes          the doctor's consultation notes
     * @param prescription   the prescription details (may be null)
     * @return the created MedicalRecordDTO
     * @throws NotFoundException   if the appointment does not exist
     * @throws InvalidDataException if notes are empty or the appointment is not COMPLETED
     * @throws RemoteException     if an RMI communication error occurs
     */
    MedicalRecordDTO updateConsultationNotes(UUID appointmentId,
                                             String notes,
                                             String prescription)
            throws NotFoundException, InvalidDataException, RemoteException;

    /**
     * Get the full medical history for a patient.
     *
     * @param patientId the UUID of the patient
     * @return list of medical records, ordered by created_at descending
     * @throws NotFoundException if the patient does not exist
     * @throws RemoteException   if an RMI communication error occurs
     */
    List<MedicalRecordDTO> getPatientMedicalHistory(UUID patientId)
            throws NotFoundException, RemoteException;

    // =========================================================================
    // Admin Operations
    // =========================================================================

    /**
     * Generate a monthly report for the specified year and month.
     *
     * @param year  the report year (e.g. 2026)
     * @param month the report month (1-12)
     * @return ReportDTO containing aggregated statistics
     * @throws InvalidDataException if year/month is invalid
     * @throws RemoteException      if an RMI communication error occurs
     */
    ReportDTO generateMonthlyReport(int year, int month)
            throws InvalidDataException, RemoteException;

    /**
     * Generate a consultation report for a specific doctor.
     *
     * @param doctorId the UUID of the doctor
     * @return ReportDTO containing the doctor's consultation statistics
     * @throws NotFoundException if the doctor does not exist
     * @throws RemoteException   if an RMI communication error occurs
     */
    ReportDTO generateDoctorConsultationReport(UUID doctorId)
            throws NotFoundException, RemoteException;

    // =========================================================================
    // Lookup / Utility
    // =========================================================================

    /**
     * Get all active doctors in the system.
     *
     * @return list of all active DoctorDTOs
     * @throws RemoteException if an RMI communication error occurs
     */
    List<DoctorDTO> getAllDoctors() throws RemoteException;

    /**
     * Get a patient by their user ID (for looking up own profile after login).
     *
     * @param userId the users.id UUID
     * @return the PatientDTO
     * @throws NotFoundException if no patient is linked to this user ID
     * @throws RemoteException   if an RMI communication error occurs
     */
    PatientDTO getPatientByUserId(UUID userId)
            throws NotFoundException, RemoteException;

    /**
     * Get a doctor by their user ID (for looking up own profile after login).
     *
     * @param userId the users.id UUID
     * @return the DoctorDTO
     * @throws NotFoundException if no doctor is linked to this user ID
     * @throws RemoteException   if an RMI communication error occurs
     */
    DoctorDTO getDoctorByUserId(UUID userId)
            throws NotFoundException, RemoteException;
}
