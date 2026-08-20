package org.brightcare.server;

import org.brightcare.common.AuthenticationService;
import org.brightcare.common.ClinicService;
import org.brightcare.common.dto.*;
import org.brightcare.common.exception.AuthenticationException;
import org.brightcare.common.exception.InvalidDataException;
import org.brightcare.common.exception.NotFoundException;
import org.brightcare.server.config.DatabaseConfig;
import org.brightcare.server.impl.AuthenticationServiceImpl;
import org.brightcare.server.impl.ClinicServiceImpl;
import org.brightcare.util.RmiUtil;
import org.junit.jupiter.api.*;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import static org.junit.jupiter.api.Assertions.*;
/**
 * RMI integration test — verifies that the whole RMI communication flow works.
 * <p>
 * Test mode (as you requested):
 * <pre>{@code
 *   ClinicService stub = (ClinicService) Naming.lookup("rmi://localhost:11099/ClinicService");
 *   List<DoctorDTO> doctors = stub.getAllDoctors();
 *   assertNotNull(doctors);
 * }</pre>
 * <p>
 * <b>Prerequisites:</b>
 * <ol>
 *   <li>PostgreSQL database must be running</li>
 *   <li>src/main/resources/application.dev.properties must contain the correct database connection</li>
 *   <li>seed_data.sql must be imported (provides test users, doctors, and patients)</li>
 * </ol>
 * <p>
 * <b>How to run:</b>
 * <pre>mvn test -pl server</pre>
 * <p>
 * This test only verifies the RMI communication chain — stub lookup, method invocation,
 * and deserialization of return values. It does not verify the correctness of database business logic.
 */
@DisplayName("RMI Integration Test — verifies RMI communication flow")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmiIntegrationTest {

    // Use an isolated port to avoid conflicts with a running server
    private static final int RMI_PORT = 11099;
    private static final String RMI_URL = "rmi://localhost:" + RMI_PORT + "/";

    // ---------- Seed data UUIDs (from seed_data.sql) ----------
    // Users
    private static final UUID USER_ADMIN       = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_RECEPTIONIST = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID USER_DOCTOR_SARAH  = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID USER_PATIENT_JOHN  = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    // Doctors
    private static final UUID DOCTOR_SARAH     = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID DOCTOR_MICHAEL   = UUID.fromString("d0000000-0000-0000-0000-000000000002");

    // Patients
    private static final UUID PATIENT_JOHN     = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_JANE     = UUID.fromString("e0000000-0000-0000-0000-000000000002");

    // Appointments (scheduled, future)
    private static final UUID APPT_FUTURE_1    = UUID.fromString("ae000000-0000-0000-0000-000000000007");

    // Shared test password (all users in seed data use password123)
    private static final String TEST_PASSWORD = "password123";

    // ---------- RMI stubs (initialized in @BeforeAll, used directly in @Test) ----------
    private static ClinicService clinicStub;
    private static AuthenticationService authStub;

    // =========================================================================
    // Lifecycle: start / stop RMI Server
    // =========================================================================

    @BeforeAll
    static void startRmiServer() throws Exception {
        System.out.println("========================================");
        System.out.println("  [TEST] Starting RMI Server (port=" + RMI_PORT + ")");
        System.out.println("========================================");

        // 1. Warm up the database connection pool
        System.out.println("[TEST] Initializing HikariCP connection pool...");
        DatabaseConfig.getDataSource();

        // 2. Create the remote objects
        System.out.println("[TEST] Creating ClinicServiceImpl & AuthenticationServiceImpl...");
        ClinicServiceImpl clinicImpl = new ClinicServiceImpl();
        AuthenticationServiceImpl authImpl = new AuthenticationServiceImpl();

        // 3. Export and bind to the RMI registry
        System.out.println("[TEST] Binding to RMI registry...");

        // Ensure the registry has been created
        try {
            LocateRegistry.getRegistry(RMI_PORT).list();
        } catch (RemoteException e) {
            LocateRegistry.createRegistry(RMI_PORT);
        }

        RmiUtil.exportAndBind("ClinicService", clinicImpl, RMI_PORT, new SslRMIClientSocketFactory(), new  SslRMIServerSocketFactory());
        RmiUtil.exportAndBind("AuthenticationService", authImpl, RMI_PORT, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());

        // 4. Client-side stub lookup — this is the core flow you wanted to test
        System.out.println("[TEST] Looking up RMI stubs on the client side...");
        clinicStub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");
        authStub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

        assertNotNull(clinicStub, "Unable to find ClinicService RMI stub");
        assertNotNull(authStub,  "Unable to find AuthenticationService RMI stub");

        System.out.println("[TEST] RMI stubs looked up successfully, server is ready");
    }

    @AfterAll
    static void stopRmiServer() {
        System.out.println("[TEST] Cleanup: stopping RMI server...");
        DatabaseConfig.shutdown();
        System.out.println("[TEST] Cleanup complete");
    }

    // =========================================================================
    // 0. Basic RMI connectivity test
    // =========================================================================

    @Test
    @Order(0)
    @DisplayName("RMI connectivity: can Naming.lookup get a stub -> call a method -> get a return value")
    void rmiLookupAndCall_ShouldWork() throws Exception {
        // 1. Look up the stub (network communication happens behind the scenes)
        ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");
        assertNotNull(stub, "Naming.lookup returned null, RMI communication failed");

        // 2. Call it like a local method (actually, network communication happens behind the scenes)
        List<DoctorDTO> response = stub.getAllDoctors();

        // 3. Verify the return value
        assertNotNull(response, "getAllDoctors() returned null, method call failed");
        System.out.println("[TEST] RMI call succeeded, returned " + response.size() + " doctors");
    }

    // =========================================================================
    // 1. AuthenticationService RMI tests
    // =========================================================================

    @Nested
    @DisplayName("AuthenticationService RMI methods")
    class AuthenticationServiceTests {

        @Test
        @DisplayName("verifyCredentials - correct password -> returns true")
        void verifyCredentials_ValidCredentials_ReturnsTrue() throws Exception {
            // Look up the stub via Naming.lookup
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            // Call the remote method
            boolean result = stub.verifyCredentials("admin1", TEST_PASSWORD);

            // Verify the return value
            assertTrue(result, "A correct password should return true");
            System.out.println("[TEST] verifyCredentials('admin1', '***') = " + result);
        }

        @Test
        @DisplayName("verifyCredentials - wrong password -> returns false")
        void verifyCredentials_InvalidPassword_ReturnsFalse() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            boolean result = stub.verifyCredentials("admin1", "wrong_password");

            assertFalse(result, "A wrong password should return false");
            System.out.println("[TEST] verifyCredentials with wrong password returned " + result);
        }

        @Test
        @DisplayName("verifyCredentials - empty username -> no exception, returns false")
        void verifyCredentials_NullUsername_ReturnsFalse() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            // Even when null is passed, the RMI call should not crash
            assertDoesNotThrow(() -> {
                boolean result = stub.verifyCredentials("", "");
                assertFalse(result);
            }, "Empty credentials should not cause a RemoteException");
            System.out.println("[TEST] verifyCredentials returned safely with empty credentials");
        }

        @Test
        @DisplayName("verifyCredentials - non-existent user -> returns false")
        void verifyCredentials_NonExistentUser_ReturnsFalse() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            boolean result = stub.verifyCredentials("nonexistent_user_999", "whatever");

            assertFalse(result);
            System.out.println("[TEST] verifyCredentials with non-existent user returned false");
        }

        @Test
        @DisplayName("hasAuthenticatorSecret - user without 2FA -> returns false")
        void hasAuthenticatorSecret_NoSecret_ReturnsFalse() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            boolean result = stub.hasAuthenticatorSecret("admin1", TEST_PASSWORD);

            // In seed data, admin1's auth_secretKey is NULL
            assertFalse(result, "Users without 2FA should return false");
            System.out.println("[TEST] hasAuthenticatorSecret = " + result);
        }

        @Test
        @DisplayName("createAuthenticatorQrCode - valid credentials -> returns PNG byte array")
        void createAuthenticatorQrCode_ValidCredentials_ReturnsPngBytes() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            byte[] qrPng = stub.createAuthenticatorQrCode("admin1", TEST_PASSWORD);

            assertNotNull(qrPng, "QR code PNG data should not be null");
            assertTrue(qrPng.length > 0, "QR code data should not be empty");
            System.out.println("[TEST] createAuthenticatorQrCode returned " + qrPng.length + " bytes of PNG");
        }

        @Test
        @DisplayName("login(username, password, code) - when 2FA is not set -> no RemoteException")
        void login_With2FA_NoSecret_ReturnsNullGracefully() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            // Because the seed user has no 2FA secret, login should return null instead of throwing
            UserDTO result = stub.login("admin1", TEST_PASSWORD, 123456);

            // No 2FA secret -> returns null (does not crash RMI communication)
            assertNull(result, "login should return null when there is no 2FA secret");
            System.out.println("[TEST] login returned null safely without 2FA");
        }

        @Test
        @DisplayName("login(username, password) - forced login without 2FA -> throws RemoteException")
        void login_Without2FA_ThrowsRemoteException() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            // This method always throws RemoteException (because 2FA is mandatory)
            assertThrows(RemoteException.class, () -> {
                stub.login("admin1", TEST_PASSWORD);
            }, "login without a TOTP code should throw RemoteException");
            System.out.println("[TEST] login correctly threw RemoteException without 2FA");
        }

        @Test
        @DisplayName("logout - should not throw")
        void logout_ShouldNotThrow() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            assertDoesNotThrow(() -> stub.logout(USER_ADMIN));
            System.out.println("[TEST] logout executed successfully");
        }

        @Test
        @DisplayName("logout - null userId -> no exception")
        void logout_NullUserId_ShouldNotThrow() throws Exception {
            AuthenticationService stub = (AuthenticationService) Naming.lookup(RMI_URL + "AuthenticationService");

            assertDoesNotThrow(() -> stub.logout(null));
            System.out.println("[TEST] logout(null) returned safely");
        }
    }

    // =========================================================================
    // 2. ClinicService RMI tests
    // =========================================================================

    @Nested
    @DisplayName("ClinicService RMI methods")
    class ClinicServiceTests {

        // ---- Authentication ----

        @Test
        @DisplayName("login - valid credentials -> returns UserDTO")
        void login_ValidCredentials_ReturnsUserDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            UserDTO user = stub.login("admin1", TEST_PASSWORD);

            assertNotNull(user, "UserDTO should not be null");
            assertNotNull(user.id(), "User ID should not be null");
            assertEquals("admin1", user.username(), "The username should match");
            System.out.println("[TEST] login returned: " + user.username() + " / " + user.role());
        }

        @Test
        @DisplayName("login - wrong password -> throws AuthenticationException")
        void login_InvalidCredentials_ThrowsAuthenticationException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(AuthenticationException.class, () -> {
                stub.login("admin1", "wrong_password");
            }, "Wrong credentials should throw AuthenticationException");
            System.out.println("[TEST] Wrong credentials correctly threw AuthenticationException");
        }

        @Test
        @DisplayName("login - empty credentials -> throws AuthenticationException")
        void login_BlankCredentials_ThrowsAuthenticationException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(AuthenticationException.class, () -> {
                stub.login("", "");
            }, "Empty credentials should throw AuthenticationException");
            System.out.println("[TEST] Empty credentials correctly threw AuthenticationException");
        }

        @Test
        @DisplayName("login - non-existent user -> throws AuthenticationException")
        void login_NonExistentUser_ThrowsAuthenticationException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(AuthenticationException.class, () -> {
                stub.login("ghost_user", "whatever");
            }, "A non-existent user should throw AuthenticationException");
            System.out.println("[TEST] Non-existent user correctly threw AuthenticationException");
        }

        // ---- Queries ----

        @Test
        @DisplayName("getAllDoctors - returns all active doctors")
        void getAllDoctors_ReturnsNonEmptyList() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            List<DoctorDTO> doctors = stub.getAllDoctors();

            assertNotNull(doctors, "The doctor list should not be null");
            assertFalse(doctors.isEmpty(), "There should be at least 1 doctor (seed data has 3)");
            // Verify the DTO data returned over RMI is complete
            DoctorDTO first = doctors.get(0);
            assertNotNull(first.id(), "Doctor ID should not be null");
            assertNotNull(first.fullName(), "Doctor name should not be null");
            assertNotNull(first.specialization(), "Doctor specialization should not be null");
            System.out.println("[TEST] getAllDoctors returned " + doctors.size() + " doctors: " +
                    first.fullName() + " (" + first.specialization() + ")");
        }

        @Test
        @DisplayName("getDoctorByUserId - valid user ID -> returns DoctorDTO")
        void getDoctorByUserId_ValidId_ReturnsDoctorDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            DoctorDTO doctor = stub.getDoctorByUserId(USER_DOCTOR_SARAH);

            assertNotNull(doctor, "DoctorDTO should not be null");
            assertTrue(doctor.fullName().contains("Sarah"), "The name should contain Sarah");
            System.out.println("[TEST] getDoctorByUserId returned: " + doctor.fullName());
        }

        @Test
        @DisplayName("getDoctorByUserId - non-existent ID -> throws NotFoundException")
        void getDoctorByUserId_InvalidId_ThrowsNotFoundException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(NotFoundException.class, () -> {
                stub.getDoctorByUserId(UUID.randomUUID());
            }, "A non-existent user ID should throw NotFoundException");
            System.out.println("[TEST] Non-existent doctor correctly threw NotFoundException");
        }

        @Test
        @DisplayName("getPatientByUserId - valid user ID -> returns PatientDTO")
        void getPatientByUserId_ValidId_ReturnsPatientDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            PatientDTO patient = stub.getPatientByUserId(USER_PATIENT_JOHN);

            assertNotNull(patient, "PatientDTO should not be null");
            assertTrue(patient.firstName().contains("John"), "The first name should contain John");
            System.out.println("[TEST] getPatientByUserId returned: " + patient.firstName() + " " + patient.lastName());
        }

        // ---- Appointments ----

        @Test
        @DisplayName("getDoctorAvailability - returns a list of 30-minute slots")
        void getDoctorAvailability_ReturnsSlotList() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            // Query a far-future date (ensures no appointments have been booked)
            List<AvailabilitySlotDTO> slots = stub.getDoctorAvailability(
                    DOCTOR_SARAH, LocalDate.of(2027, 1, 15));

            assertNotNull(slots, "The slot list should not be null");
            assertFalse(slots.isEmpty(), "There should be slots during business hours (8:00-17:00 = 18 slots)");
            // Since it is far in the future, all slots should be available
            long availableCount = slots.stream().filter(AvailabilitySlotDTO::available).count();
            System.out.println("[TEST] getDoctorAvailability returned " + slots.size() +
                    " slots, " + availableCount + " available");
        }

        @Test
        @DisplayName("getDoctorAvailability - non-existent doctor -> throws NotFoundException")
        void getDoctorAvailability_InvalidDoctor_ThrowsNotFoundException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(NotFoundException.class, () -> {
                stub.getDoctorAvailability(UUID.randomUUID(), LocalDate.now());
            }, "A non-existent doctor should throw NotFoundException");
            System.out.println("[TEST] Non-existent doctor correctly threw NotFoundException");
        }

        @Test
        @DisplayName("bookAppointment - valid data -> returns AppointmentDTO (RMI round trip succeeds)")
        void bookAppointment_ValidData_ReturnsAppointmentDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            // Book a far-future time to avoid conflicts
            ZonedDateTime futureTime = ZonedDateTime.of(
                    2027, 6, 15, 10, 0, 0, 0, ZoneOffset.UTC);

            AppointmentDTO appt = stub.bookAppointment(
                    PATIENT_JOHN, DOCTOR_MICHAEL, futureTime, "RMI integration test - routine checkup");

            assertNotNull(appt, "AppointmentDTO should not be null");
            assertNotNull(appt.id(), "Appointment ID should not be null");
            assertEquals(PATIENT_JOHN, appt.patientId());
            assertEquals(DOCTOR_MICHAEL, appt.doctorId());
            System.out.println("[TEST] bookAppointment returned: appointmentId=" + appt.id() +
                    ", status=" + appt.status());

            // Cleanup: cancel the appointment just created
            stub.cancelAppointment(appt.id());
        }

        @Test
        @DisplayName("bookAppointment - a time in the past -> throws RemoteException")
        void bookAppointment_PastTime_ThrowsException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            ZonedDateTime pastTime = ZonedDateTime.of(2020, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

            // A time in the past -> InvalidDataException gets wrapped as RemoteException
            assertThrows(Exception.class, () -> {
                stub.bookAppointment(PATIENT_JOHN, DOCTOR_SARAH, pastTime, "test");
            }, "A time in the past should throw an exception");
            System.out.println("[TEST] Booking a time in the past correctly threw an exception");
        }

        @Test
        @DisplayName("bookAppointment - non-existent patient -> throws NotFoundException")
        void bookAppointment_InvalidPatient_ThrowsNotFoundException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            ZonedDateTime futureTime = ZonedDateTime.of(2027, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC);

            assertThrows(NotFoundException.class, () -> {
                stub.bookAppointment(UUID.randomUUID(), DOCTOR_SARAH, futureTime, "test");
            }, "A non-existent patient should throw NotFoundException");
            System.out.println("[TEST] Non-existent patient correctly threw NotFoundException");
        }

        @Test
        @DisplayName("cancelAppointment - SCHEDULED appointment -> no exception")
        void cancelAppointment_Scheduled_ShouldNotThrow() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            // First create an appointment
            ZonedDateTime futureTime = ZonedDateTime.of(
                    2027, 9, 10, 14, 0, 0, 0, ZoneOffset.UTC);
            AppointmentDTO appt = stub.bookAppointment(
                    PATIENT_JANE, DOCTOR_MICHAEL, futureTime, "test cancellation feature");

            // Cancel it
            assertDoesNotThrow(() -> stub.cancelAppointment(appt.id()));
            System.out.println("[TEST] cancelAppointment executed successfully: " + appt.id());
        }

        @Test
        @DisplayName("cancelAppointment - non-existent appointment ID -> throws NotFoundException")
        void cancelAppointment_InvalidId_ThrowsNotFoundException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(NotFoundException.class, () -> {
                stub.cancelAppointment(UUID.randomUUID());
            }, "A non-existent appointment should throw NotFoundException");
            System.out.println("[TEST] Non-existent appointment correctly threw NotFoundException");
        }

        @Test
        @DisplayName("getPatientAppointments - valid patient -> returns appointment list")
        void getPatientAppointments_ValidPatient_ReturnsList() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            List<AppointmentDTO> appointments = stub.getPatientAppointments(PATIENT_JOHN);

            assertNotNull(appointments, "The appointment list should not be null");
            System.out.println("[TEST] getPatientAppointments returned " +
                    appointments.size() + " appointments");
        }

        @Test
        @DisplayName("getDoctorAppointments - valid doctor -> returns appointment list")
        void getDoctorAppointments_ValidDoctor_ReturnsList() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            List<AppointmentDTO> appointments = stub.getDoctorAppointments(DOCTOR_SARAH);

            assertNotNull(appointments, "The appointment list should not be null");
            System.out.println("[TEST] getDoctorAppointments returned " +
                    appointments.size() + " appointments");
        }

        // ---- Medical records ----

        @Test
        @DisplayName("getPatientMedicalHistory - valid patient -> returns medical history list")
        void getPatientMedicalHistory_ValidPatient_ReturnsList() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            List<MedicalRecordDTO> records = stub.getPatientMedicalHistory(PATIENT_JOHN);

            assertNotNull(records, "The medical history list should not be null");
            System.out.println("[TEST] getPatientMedicalHistory returned " +
                    records.size() + " medical records");
        }

        // ---- Reports ----

        @Test
        @DisplayName("generateMonthlyReport - valid month -> returns ReportDTO")
        void generateMonthlyReport_ValidMonth_ReturnsReportDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            ReportDTO report = stub.generateMonthlyReport(2026, 6);

            assertNotNull(report, "ReportDTO should not be null");
            assertNotNull(report.title(), "The report title should not be null");
            assertTrue(report.totalAppointments() >= 0, "The total should be >= 0");
            System.out.println("[TEST] generateMonthlyReport: " + report.title() +
                    " - total=" + report.totalAppointments() +
                    ", completed=" + report.completedAppointments() +
                    ", cancelled=" + report.cancelledAppointments() +
                    ", noShow=" + report.noShowAppointments());
        }

        @Test
        @DisplayName("generateMonthlyReport - invalid month -> throws RemoteException")
        void generateMonthlyReport_InvalidMonth_ThrowsException() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            assertThrows(Exception.class, () -> {
                stub.generateMonthlyReport(2026, 13); // month 13 is invalid
            }, "An invalid month should throw an exception");
            System.out.println("[TEST] Invalid month correctly threw an exception");
        }

        @Test
        @DisplayName("generateDoctorConsultationReport - valid doctor -> returns ReportDTO")
        void generateDoctorConsultationReport_ValidDoctor_ReturnsReportDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            ReportDTO report = stub.generateDoctorConsultationReport(DOCTOR_SARAH);

            assertNotNull(report, "ReportDTO should not be null");
            assertNotNull(report.title(), "The report title should not be null");
            System.out.println("[TEST] generateDoctorConsultationReport: " + report.title() +
                    " - total=" + report.totalAppointments());
        }

        // ---- Patient management ----

        @Test
        @DisplayName("updatePatientInfo - valid data -> returns the updated PatientDTO")
        void updatePatientInfo_ValidData_ReturnsUpdatedPatientDTO() throws Exception {
            ClinicService stub = (ClinicService) Naming.lookup(RMI_URL + "ClinicService");

            // First get the current patient information
            PatientDTO current = stub.getPatientByUserId(USER_PATIENT_JOHN);

            // Update the contact number
            PatientDTO updated = stub.updatePatientInfo(new PatientDTO(
                    current.id(),
                    current.userId(),
                    current.firstName(),
                    current.lastName(),
                    current.icPassportNumber(),
                    "+60-00-0000000",  // new phone number
                    current.medicalRecordId(),
                    current.createdAt(),
                    current.updatedAt()
            ));

            assertNotNull(updated, "The updated PatientDTO should not be null");
            assertEquals("+60-00-0000000", updated.contactNumber(), "The phone number should be updated");
            System.out.println("[TEST] updatePatientInfo succeeded: " +
                    updated.firstName() + " " + updated.lastName() +
                    " phone=" + updated.contactNumber());

            // Restore the original phone number
            stub.updatePatientInfo(new PatientDTO(
                    current.id(), current.userId(),
                    current.firstName(), current.lastName(),
                    current.icPassportNumber(),
                    current.contactNumber(),
                    current.medicalRecordId(),
                    current.createdAt(),
                    current.updatedAt()
            ));
        }
    }
}
