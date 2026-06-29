-- ==============================================================================
-- 1. Create Core Data Tables
-- ==============================================================================

-- Unified User Authentication Table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL, 
    role VARCHAR(20) NOT NULL, -- Controlled at business logic level: 'RECEPTIONIST', 'DOCTOR', 'PATIENT', 'ADMIN'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Patient Details Table (1:1 relationship with users table)
CREATE TABLE patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    ic_passport_number VARCHAR(50) UNIQUE NOT NULL, 
    contact_number VARCHAR(20) NOT NULL,
    medical_record_id VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Doctor Details Table (1:1 relationship with users table)
CREATE TABLE doctors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    full_name VARCHAR(100) NOT NULL,
    specialization VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Appointment Records Table
CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id UUID NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    appointment_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) DEFAULT 'SCHEDULED' NOT NULL, -- Controlled at business logic level: 'SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'
    reason_for_visit TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- Constraint: Prevent the same doctor from having two appointments at the same time
    CONSTRAINT unique_doctor_time UNIQUE (doctor_id, appointment_time)
);

-- Medical Records Table (1:1 relationship with appointments table)
CREATE TABLE medical_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID UNIQUE NOT NULL REFERENCES appointments(id) ON DELETE RESTRICT,
    patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id UUID NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    consultation_notes TEXT NOT NULL,
    prescription TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ==============================================================================
-- 2. Create Indexes for High-Frequency Queries
-- ==============================================================================
-- Speed up querying appointments by time range
CREATE INDEX idx_appointments_time ON appointments(appointment_time);

-- Speed up querying appointment lists for a specific doctor
CREATE INDEX idx_appointments_doctor ON appointments(doctor_id, status);

-- Speed up querying historical appointments and medical records for a specific patient
CREATE INDEX idx_appointments_patient ON appointments(patient_id);
CREATE INDEX idx_medical_records_patient ON medical_records(patient_id);

-- ==============================================================================
-- 3. Create Trigger Functions to Automatically Update 'updated_at'
-- ==============================================================================
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_modtime BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_modified_column();
CREATE TRIGGER update_patients_modtime BEFORE UPDATE ON patients FOR EACH ROW EXECUTE FUNCTION update_modified_column();
CREATE TRIGGER update_appointments_modtime BEFORE UPDATE ON appointments FOR EACH ROW EXECUTE FUNCTION update_modified_column();
CREATE TRIGGER update_medical_records_modtime BEFORE UPDATE ON medical_records FOR EACH ROW EXECUTE FUNCTION update_modified_column();
