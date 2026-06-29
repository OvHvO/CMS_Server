# BrightCare RMI API Documentation

**For:** JavaFX Client Developer  
**Interface:** `org.brightcare.common.ClinicService`  
**Protocol:** Java RMI  
**Registry:** `rmi://<host>:1099/ClinicService`

---

## How to Connect

```java
import org.brightcare.common.ClinicService;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

// 1. Get the registry
Registry registry = LocateRegistry.getRegistry("localhost", 1099);

// 2. Look up the service
ClinicService service = (ClinicService) registry.lookup("ClinicService");

// 3. Call methods — every call can throw RemoteException
UserDTO user = service.login("username", "password");
```

**Important:** Every method in this interface throws `java.rmi.RemoteException`.
Your client code should wrap RMI calls in try-catch blocks that handle both
`RemoteException` (network/communication issues) and the specific business
exceptions documented below.

---

## Exception Hierarchy

All custom exceptions extend `java.lang.Exception` (checked).

| Exception | When It's Thrown | Client Action |
|-----------|-----------------|---------------|
| `AuthenticationException` | Wrong username or password | Show login error, let user retry |
| `AppointmentConflictException` | Doctor already booked at that time | Show conflict message, suggest another time |
| `NotFoundException` | Entity (patient/doctor/appointment) not found | Show "not found" message |
| `InvalidDataException` | Validation failed (bad input) | Show the validation error to the user |
| `UnauthorizedException` | User role doesn't permit the operation | Redirect to login or show access denied |
| `RemoteException` | Network/RMI communication failure | Show "Server unavailable", offer retry |

---

## Method Reference

### 1. Authentication

#### `login`
```
UserDTO login(String username, String password)
    throws AuthenticationException, RemoteException
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `username` | `String` | User's unique login name |
| `password` | `String` | Plain-text password |

| Returns | Type | Description |
|---------|------|-------------|
| `user` | `UserDTO` | Contains `id` (UUID), `username`, `role` (enum), `createdAt` |

**Throws:** `AuthenticationException` — invalid credentials

---

### 2. Receptionist Operations

#### `registerPatient`
```
PatientDTO registerPatient(PatientDTO patient, String username, String password)
    throws InvalidDataException, UnauthorizedException, RemoteException
```

Creates both a `users` row (role=PATIENT) and a `patients` row.

| Parameter | Type | Description |
|-----------|------|-------------|
| `patient` | `PatientDTO` | Demographic fields (`firstName`, `lastName`, `icPassportNumber`, `contactNumber`, `medicalRecordId`). `id`, `userId`, `createdAt`, `updatedAt` are ignored — the server generates these. |
| `username` | `String` | Login username for the new patient |
| `password` | `String` | Plain-text password (min 6 chars) |

| Returns | Type | Description |
|---------|------|-------------|
| `patient` | `PatientDTO` | The created patient with generated IDs |

**Throws:**  
- `InvalidDataException` — missing required fields, username taken, duplicate IC/passport or medical record ID
- `UnauthorizedException` — caller is not RECEPTIONIST (if role-checking is added)

---

### 3. Patient Operations

#### `updatePatientInfo`
```
PatientDTO updatePatientInfo(PatientDTO patient)
    throws NotFoundException, InvalidDataException, RemoteException
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `patient` | `PatientDTO` | Updated fields. Must include a valid `id`. |

| Returns | Type | Description |
|---------|------|-------------|
| `patient` | `PatientDTO` | The updated patient record |

**Throws:**  
- `NotFoundException` — patient ID does not exist
- `InvalidDataException` — required fields blank, or unique constraint conflict

---

#### `bookAppointment`
```
AppointmentDTO bookAppointment(UUID patientId, UUID doctorId,
                                ZonedDateTime appointmentTime, String reason)
    throws NotFoundException, AppointmentConflictException,
           InvalidDataException, RemoteException
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `patientId` | `UUID` | Patient's UUID (from `PatientDTO.id`) |
| `doctorId` | `UUID` | Doctor's UUID (from `DoctorDTO.id`) |
| `appointmentTime` | `ZonedDateTime` | Must be in the future, within 08:00–16:30, on a :00 or :30 boundary |
| `reason` | `String` | Reason for visit |

| Returns | Type | Description |
|---------|------|-------------|
| `appointment` | `AppointmentDTO` | Created appointment with status=SCHEDULED |

**Throws:**  
- `NotFoundException` — patient or doctor does not exist, or doctor is inactive
- `AppointmentConflictException` — doctor already has an appointment at this time. Call `getConflictingTime()` for details.
- `InvalidDataException` — time in the past, outside clinic hours, or not on a 30-minute boundary

---

#### `cancelAppointment`
```
void cancelAppointment(UUID appointmentId)
    throws NotFoundException, InvalidDataException, RemoteException
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `appointmentId` | `UUID` | Appointment to cancel |

**Returns:** nothing (void)

**Throws:**  
- `NotFoundException` — appointment not found
- `InvalidDataException` — appointment is already COMPLETED or CANCELLED

---

#### `getDoctorAvailability`
```
List<AvailabilitySlotDTO> getDoctorAvailability(UUID doctorId, LocalDate date)
    throws NotFoundException, RemoteException
```

Returns 30-minute slots from 08:00 to 17:00, each marked `available` (true/false).

| Parameter | Type | Description |
|-----------|------|-------------|
| `doctorId` | `UUID` | Doctor to query |
| `date` | `LocalDate` | The date to check |

| Returns | Type | Description |
|---------|------|-------------|
| `slots` | `List<AvailabilitySlotDTO>` | Each slot has `startTime`, `endTime`, `available` (boolean) |

**Throws:** `NotFoundException` — doctor not found

**Usage example:**
```java
List<AvailabilitySlotDTO> slots = service.getDoctorAvailability(doctorId, LocalDate.now());
for (AvailabilitySlotDTO slot : slots) {
    if (slot.available()) {
        // Show as clickable/bookable in the UI
    }
}
```

---

#### `getPatientAppointments`
```
List<AppointmentDTO> getPatientAppointments(UUID patientId)
    throws NotFoundException, RemoteException
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `patientId` | `UUID` | Patient to query |

| Returns | Type | Description |
|---------|------|-------------|
| `appointments` | `List<AppointmentDTO>` | All appointments, newest first. Includes `patientName` and `doctorName` for display. |

---

### 4. Doctor Operations

#### `getDoctorAppointments`
```
List<AppointmentDTO> getDoctorAppointments(UUID doctorId)
    throws NotFoundException, RemoteException
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `doctorId` | `UUID` | Doctor to query |

| Returns | Type | Description |
|---------|------|-------------|
| `appointments` | `List<AppointmentDTO>` | All appointments for this doctor, newest first |

---

#### `updateConsultationNotes`
```
MedicalRecordDTO updateConsultationNotes(UUID appointmentId, String notes, String prescription)
    throws NotFoundException, InvalidDataException, RemoteException
```

Creates a `medical_records` row and marks the appointment as COMPLETED.

| Parameter | Type | Description |
|-----------|------|-------------|
| `appointmentId` | `UUID` | The appointment being concluded |
| `notes` | `String` | Consultation notes (required, non-blank) |
| `prescription` | `String` | Prescription details (nullable) |

| Returns | Type | Description |
|---------|------|-------------|
| `record` | `MedicalRecordDTO` | The created medical record |

**Throws:**  
- `NotFoundException` — appointment not found
- `InvalidDataException` — notes are blank, or a medical record already exists for this appointment

---

#### `getPatientMedicalHistory`
```
List<MedicalRecordDTO> getPatientMedicalHistory(UUID patientId)
    throws NotFoundException, RemoteException
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `patientId` | `UUID` | Patient to query |

| Returns | Type | Description |
|---------|------|-------------|
| `records` | `List<MedicalRecordDTO>` | All medical records, newest first. Includes `doctorName` and `appointmentTime`. |

---

### 5. Admin Operations

#### `generateMonthlyReport`
```
ReportDTO generateMonthlyReport(int year, int month)
    throws InvalidDataException, RemoteException
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `year` | `int` | Report year (e.g. 2026) |
| `month` | `int` | Report month (1–12) |

| Returns | Type | Description |
|---------|------|-------------|
| `report` | `ReportDTO` | Contains: `title`, `period`, `totalAppointments`, `completedAppointments`, `cancelledAppointments`, `noShowAppointments`, `appointmentsBySpecialization` (Map), `appointmentDetails` (List) |

---

#### `generateDoctorConsultationReport`
```
ReportDTO generateDoctorConsultationReport(UUID doctorId)
    throws NotFoundException, RemoteException
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `doctorId` | `UUID` | Doctor to report on |

| Returns | Type | Description |
|---------|------|-------------|
| `report` | `ReportDTO` | Aggregated stats for this doctor (all time). `appointmentsBySpecialization` map contains status breakdowns instead of specializations. |

---

### 6. Lookup / Utility

#### `getAllDoctors`
```
List<DoctorDTO> getAllDoctors() throws RemoteException
```

| Returns | Type | Description |
|---------|------|-------------|
| `doctors` | `List<DoctorDTO>` | All active doctors, sorted by name |

Use this to populate doctor selection dropdowns in the UI.

---

#### `getPatientByUserId`
```
PatientDTO getPatientByUserId(UUID userId) throws NotFoundException, RemoteException
```

After a patient logs in, call this with their `UserDTO.id` to get their patient profile.

| Returns | Type | Description |
|---------|------|-------------|
| `patient` | `PatientDTO` | The patient linked to this user ID |

---

#### `getDoctorByUserId`
```
DoctorDTO getDoctorByUserId(UUID userId) throws NotFoundException, RemoteException
```

After a doctor logs in, call this with their `UserDTO.id` to get their doctor profile.

| Returns | Type | Description |
|---------|------|-------------|
| `doctor` | `DoctorDTO` | The doctor linked to this user ID |

---

## DTO Reference

### `UserDTO`
```java
public record UserDTO(
    UUID id,
    String username,
    Role role,          // PATIENT | DOCTOR | RECEPTIONIST | ADMIN
    ZonedDateTime createdAt
) implements Serializable {}
```

### `PatientDTO`
```java
public record PatientDTO(
    UUID id,
    UUID userId,
    String firstName,
    String lastName,
    String icPassportNumber,
    String contactNumber,
    String medicalRecordId,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) implements Serializable {}
```

### `DoctorDTO`
```java
public record DoctorDTO(
    UUID id,
    UUID userId,
    String fullName,
    String specialization,
    boolean isActive,
    ZonedDateTime createdAt
) implements Serializable {}
```

### `AppointmentDTO`
```java
public record AppointmentDTO(
    UUID id,
    UUID patientId,
    UUID doctorId,
    String patientName,           // denormalised, null for basic queries
    String doctorName,            // denormalised, null for basic queries
    ZonedDateTime appointmentTime,
    AppointmentStatus status,     // SCHEDULED | COMPLETED | CANCELLED | NO_SHOW
    String reasonForVisit,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) implements Serializable {}
```

### `MedicalRecordDTO`
```java
public record MedicalRecordDTO(
    UUID id,
    UUID appointmentId,
    UUID patientId,
    UUID doctorId,
    String doctorName,
    ZonedDateTime appointmentTime,
    String consultationNotes,
    String prescription,          // nullable
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) implements Serializable {}
```

### `ReportDTO`
```java
public record ReportDTO(
    String title,
    String period,
    int totalAppointments,
    int completedAppointments,
    int cancelledAppointments,
    int noShowAppointments,
    Map<String, Integer> appointmentsBySpecialization,
    List<AppointmentDTO> appointmentDetails
) implements Serializable {}
```

### `AvailabilitySlotDTO`
```java
public record AvailabilitySlotDTO(
    LocalTime startTime,
    LocalTime endTime,
    boolean available
) implements Serializable {}
```

---

## Typical Client Workflow

### Patient books an appointment:
```
1. login(username, password)                         → UserDTO
2. getPatientByUserId(user.id)                        → PatientDTO
3. getAllDoctors()                                    → List<DoctorDTO>
4. getDoctorAvailability(doctor.id, date)              → List<AvailabilitySlotDTO>
5. bookAppointment(patient.id, doctor.id, time, reason) → AppointmentDTO
```

### Doctor conducts a consultation:
```
1. login(username, password)                         → UserDTO
2. getDoctorByUserId(user.id)                         → DoctorDTO
3. getDoctorAppointments(doctor.id)                    → List<AppointmentDTO>
4. getPatientMedicalHistory(patientId)                 → List<MedicalRecordDTO>
5. updateConsultationNotes(appt.id, notes, rx)         → MedicalRecordDTO
```

### Admin generates reports:
```
1. login(username, password)                         → UserDTO
2. generateMonthlyReport(2026, 6)                     → ReportDTO
3. generateDoctorConsultationReport(doctorId)          → ReportDTO
```
