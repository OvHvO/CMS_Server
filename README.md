# BrightCare Medical Centre — RMI Server

Java RMI distributed system server for BrightCare Medical Centre clinic management.

## Prerequisites

| Component | Version | Notes |
|-----------|---------|-------|
| Java JDK   | 21+    | `java --version` |
| PostgreSQL | 14+    | Running and accessible |
| Maven      | 3.9+   | `mvn --version` |

## Project Structure

```
src/main/java/org/brightcare/
├── common/                          # Shared with JavaFX client
│   ├── ClinicService.java           # RMI remote interface
│   ├── dto/                         # Serializable DTOs
│   │   ├── AppointmentDTO.java
│   │   ├── AvailabilitySlotDTO.java
│   │   ├── DoctorDTO.java
│   │   ├── MedicalRecordDTO.java
│   │   ├── PatientDTO.java
│   │   ├── ReportDTO.java
│   │   └── UserDTO.java
│   ├── enums/
│   │   ├── AppointmentStatus.java   # SCHEDULED, COMPLETED, CANCELLED, NO_SHOW
│   │   └── Role.java                # PATIENT, DOCTOR, RECEPTIONIST, ADMIN
│   └── exception/
│       ├── AppointmentConflictException.java
│       ├── AuthenticationException.java
│       ├── InvalidDataException.java
│       ├── NotFoundException.java
│       └── UnauthorizedException.java
├── server/
│   ├── impl/
│   │   └── ClinicServiceImpl.java   # RMI implementation
│   ├── dao/
│   │   ├── AppointmentDAO.java
│   │   ├── DoctorDAO.java
│   │   ├── MedicalRecordDAO.java
│   │   ├── PatientDAO.java
│   │   └── UserDAO.java
│   ├── config/
│   │   └── DatabaseConfig.java      # HikariCP pool
│   └── ServerMain.java              # Entry point
├── util/
│   ├── PasswordUtil.java            # BCrypt hashing
│   └── RmiUtil.java                 # RMI registry helpers
└── resources/
    ├── application.properties       # DB config
    └── logback.xml                  # Logging config
```

## Database Setup

### 1. Create the database

```sql
CREATE DATABASE brightcare;
```

### 2. Run the schema

Execute the SQL in `database_schema.md` against the `brightcare` database:

```bash
psql -U postgres -d brightcare -f database_schema.md
```

This creates the following tables:
- `users` — authentication for all user types
- `patients` — patient demographics
- `doctors` — doctor profiles
- `appointments` — patient-doctor appointments
- `medical_records` — consultation records

### 3. Configure connection

Edit `src/main/resources/application.properties`:

```properties
db.url=jdbc:postgresql://localhost:5432/brightcare
db.username=your_db_user
db.password=your_db_password
```

### 4. (Optional) Seed initial users

Use the SQL below to create test accounts. The password is `password123` for all:

```sql
-- BCrypt hash for "password123"
-- Role: ADMIN
INSERT INTO users (id, username, password_hash, role) VALUES
(gen_random_uuid(), 'admin', '$2a$12$LJ3m4ys3GZfnYMz8kVsKaOm6xJ4vQqVRZ7H0m3j6I5RqGN7voMXhq', 'ADMIN');

-- Role: RECEPTIONIST
INSERT INTO users (id, username, password_hash, role) VALUES
(gen_random_uuid(), 'receptionist', '$2a$12$LJ3m4ys3GZfnYMz8kVsKaOm6xJ4vQqVRZ7H0m3j6I5RqGN7voMXhq', 'RECEPTIONIST');

-- Role: DOCTOR  (also create a doctors row linked to this user)
INSERT INTO users (id, username, password_hash, role) VALUES
(gen_random_uuid(), 'dr.smith', '$2a$12$LJ3m4ys3GZfnYMz8kVsKaOm6xJ4vQqVRZ7H0m3j6I5RqGN7voMXhq', 'DOCTOR');

-- Role: PATIENT (also create a patients row linked to this user)
INSERT INTO users (id, username, password_hash, role) VALUES
(gen_random_uuid(), 'john.doe', '$2a$12$LJ3m4ys3GZfnYMz8kVsKaOm6xJ4vQqVRZ7H0m3j6I5RqGN7voMXhq', 'PATIENT');
```

**Important:** The hash above is a placeholder. Generate your own BCrypt hashes using
the `PasswordUtil` class or an online BCrypt tool.

## Build

```bash
mvn clean package
```

This produces `target/brightcare-server.jar` (a fat JAR with all dependencies).

## Run

```bash
java -jar target/brightcare-server.jar
```

The server will:
1. Initialise the HikariCP connection pool
2. Start the RMI registry on port **1099**
3. Export and bind the `ClinicService` remote object
4. Log `Server is ready` and wait for client connections

Press `Ctrl+C` to stop. The shutdown hook closes the pool and unexports the remote object.

### Firewall Note

Ensure port **1099** is open if clients connect from other machines. For local development
(localhost), no firewall changes are needed.

## For the JavaFX Client Developer

1. Copy the `org.brightcare.common` package (interface + DTOs + enums + exceptions) into your JavaFX project.

2. Connect to the server:
```java
Registry registry = LocateRegistry.getRegistry("localhost", 1099);
ClinicService service = (ClinicService) registry.lookup("ClinicService");
UserDTO user = service.login("username", "password");
```

3. See [`API_DOC.md`](API_DOC.md) for the complete method reference including all exceptions
   the client must handle.

## Concurrency

- The RMI runtime assigns each client call to a **separate thread** automatically.
- HikariCP provides thread-safe connection pooling.
- The `UNIQUE (doctor_id, appointment_time)` constraint prevents double-booking at the
  database level. The server catches the `PSQLException` (SQL state `23505`) and translates
  it to an `AppointmentConflictException` that the client can display to the user.

## Security

- Passwords are hashed with **BCrypt** (workload factor 12) before storage.
- The `users.password_hash` column stores only the hash — never the plain-text password.
- `UserDTO` never includes the password hash.
- Authentication verifies against the stored BCrypt hash using `BCrypt.checkpw()`.

## Logging

Logs are written to:
- **Console** — for development / `docker logs`
- **File** — `logs/brightcare-server.log`, rotated daily, kept for 30 days

Edit `src/main/resources/logback.xml` to adjust log levels.
