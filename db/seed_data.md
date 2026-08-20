-- ==============================================================================
-- SEED DATA FOR MEDICAL CLINIC DATABASE
-- ==============================================================================
-- This script inserts sample data into all tables with realistic records.
-- Run with: \i seed_data.sql  (inside psql)
-- Or: psql -U your_user -d your_db -f seed_data.sql
--
-- Note: Password hashes below are bcrypt-hashed passwords (the string is 'password123'
-- for all users — change in production). For Google Authenticator, auth_enabled
-- is FALSE by default; users would set it up via QR code on first login.
-- ==============================================================================

-- ==============================================================================
-- 1. USERS (Authentication)
-- ==============================================================================
INSERT INTO users (id, username, password_hash, role, auth_secretKey, auth_enabled) VALUES
-- Admin
('a0000000-0000-0000-0000-000000000001', 'admin1',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'ADMIN', NULL, FALSE),

-- Receptionists
('a0000000-0000-0000-0000-000000000002', 'receptionist1',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'RECEPTIONIST', NULL, FALSE),

('a0000000-0000-0000-0000-000000000003', 'receptionist2',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'RECEPTIONIST', NULL, FALSE),

-- Doctors
('b0000000-0000-0000-0000-000000000001', 'dr.sarah',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'DOCTOR', NULL, FALSE),

('b0000000-0000-0000-0000-000000000002', 'dr.michael',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'DOCTOR', NULL, FALSE),

('b0000000-0000-0000-0000-000000000003', 'dr.emily',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'DOCTOR', NULL, FALSE),

-- Patients
('c0000000-0000-0000-0000-000000000001', 'john.smith',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'PATIENT', NULL, FALSE),

('c0000000-0000-0000-0000-000000000002', 'jane.doe',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'PATIENT', NULL, FALSE),

('c0000000-0000-0000-0000-000000000003', 'bob.wilson',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'PATIENT', NULL, FALSE),

('c0000000-0000-0000-0000-000000000004', 'alice.brown',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'PATIENT', NULL, FALSE),

('c0000000-0000-0000-0000-000000000005', 'charlie.davis',
 '$2a$12$HBFHZBJXY095J.TKCGnL8OzMJeqAMkYFk5S0CF/P8Kc7Yng19VZYK',
 'PATIENT', NULL, FALSE);

-- ==============================================================================
-- 2. PATIENTS
-- ==============================================================================
INSERT INTO patients (id, user_id, first_name, last_name, ic_passport_number, contact_number, medical_record_id) VALUES
('e0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001',
 'John', 'Smith', 'S1234567A', '+60-12-3456789', 'MRN-20240001'),

('e0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000002',
 'Jane', 'Doe', 'D7654321B', '+60-16-9876543', 'MRN-20240002'),

('e0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000003',
 'Bob', 'Wilson', 'W9988776C', '+60-14-5566778', 'MRN-20240003'),

('e0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000004',
 'Alice', 'Brown', 'B4433221D', '+60-19-2233445', 'MRN-20240004'),

('e0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000005',
 'Charlie', 'Davis', 'D5566778E', '+60-11-9988776', 'MRN-20240005');

-- ==============================================================================
-- 3. DOCTORS
-- ==============================================================================
INSERT INTO doctors (id, user_id, full_name, specialization, is_active) VALUES
('d0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001',
 'Dr. Sarah Johnson', 'Cardiology', TRUE),

('d0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002',
 'Dr. Michael Chen', 'Pediatrics', TRUE),

('d0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000003',
 'Dr. Emily Rodriguez', 'Orthopedics', TRUE);

-- ==============================================================================
-- 4. APPOINTMENTS
-- ==============================================================================
INSERT INTO appointments (id, patient_id, doctor_id, appointment_time, status, reason_for_visit) VALUES

-- ===== Past appointments (COMPLETED) =====
('ae000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001',
 '2026-06-15 09:00:00+08', 'COMPLETED',
 'Routine heart checkup — patient has family history of hypertension'),

('ae000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000002',
 '2026-06-20 10:30:00+08', 'COMPLETED',
 'Childhood vaccination — booster dose for 5-year-old'),

('ae000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000003',
 '2026-06-22 14:00:00+08', 'COMPLETED',
 'Lower back pain persisting for 2 weeks after lifting heavy object'),

('ae000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000001',
 '2026-06-25 11:00:00+08', 'COMPLETED',
 'Chest discomfort and shortness of breath during exercise'),

-- ===== Past appointments (CANCELLED) =====
('ae000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000002',
 '2026-06-18 15:00:00+08', 'CANCELLED',
 'Annual health screening for child — rescheduled due to school event'),

-- ===== Past appointments (NO_SHOW) =====
('ae000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000003',
 '2026-06-28 09:00:00+08', 'NO_SHOW',
 'Knee pain follow-up — patient did not attend'),

-- ===== Future appointments (SCHEDULED) =====
('ae000000-0000-0000-0000-000000000007', 'e0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000001',
 '2026-07-10 09:00:00+08', 'SCHEDULED',
 'Cardiac evaluation — family history of heart disease'),

('ae000000-0000-0000-0000-000000000008', 'e0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000002',
 '2026-07-12 10:00:00+08', 'SCHEDULED',
 'Child development assessment — concerns about speech delay'),

('ae000000-0000-0000-0000-000000000009', 'e0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000003',
 '2026-07-14 14:30:00+08', 'SCHEDULED',
 'Right shoulder pain — possible rotator cuff injury'),

('ae000000-0000-0000-0000-000000000010', 'e0000000-0000-0000-0000-000000000005', 'd0000000-0000-0000-0000-000000000001',
 '2026-07-15 11:00:00+08', 'SCHEDULED',
 'Blood pressure review — previously elevated readings'),

('ae000000-0000-0000-0000-000000000011', 'e0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002',
 '2026-07-18 09:30:00+08', 'SCHEDULED',
 'General checkup and blood test results review'),

('ae000000-0000-0000-0000-000000000012', 'e0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000001',
 '2026-07-20 15:00:00+08', 'SCHEDULED',
 'ECG follow-up — monitoring arrhythmia treatment progress');

-- ==============================================================================
-- 5. MEDICAL RECORDS (linked to COMPLETED appointments)
-- ==============================================================================
INSERT INTO medical_records (id, appointment_id, patient_id, doctor_id, consultation_notes, prescription) VALUES

-- Medical record for John Smith's heart checkup (Appt #1)
('bc000000-0000-0000-0000-000000000001', 'ae000000-0000-0000-0000-000000000001',
 'e0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001',
 'Patient reports no chest pain or palpitations. Blood pressure measured at 135/85 — slightly elevated. ECG shows normal sinus rhythm. Recommended lifestyle modifications including reduced sodium intake and 30min daily exercise. Follow-up in 3 months to reassess BP.',
 'Lisinopril 5mg once daily. Referred for lipid panel blood test.'),

-- Medical record for Jane Doe's vaccination (Appt #2)
('bc000000-0000-0000-0000-000000000002', 'ae000000-0000-0000-0000-000000000002',
 'e0000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000003',
 'Child received DTaP booster (5th dose) and IPV booster. No adverse reactions observed during 15-minute observation period. Growth metrics: height 110cm (50th percentile), weight 18kg (45th percentile). Development on track for age.',
 'Paracetamol suspension 250mg as needed for post-vaccination fever. Next vaccination scheduled for age 12.'),

-- Medical record for Bob Wilson's back pain (Appt #3)
('bc000000-0000-0000-0000-000000000003', 'ae000000-0000-0000-0000-000000000003',
 'e0000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000003',
 'Physical examination reveals muscle strain in lower lumbar region (L4-L5). No sciatica or nerve compression symptoms. Limited range of motion in flexion. X-ray shows no vertebral abnormalities. Diagnosed as acute lumbar muscle strain.',
 'Ibuprofen 400mg three times daily with food for 7 days. Muscle relaxant — Cyclobenzaprine 5mg at bedtime. Recommended 3 sessions of physiotherapy. Avoid heavy lifting for 2 weeks.'),

-- Medical record for Alice Brown's chest discomfort (Appt #4)
('bc000000-0000-0000-0000-000000000004', 'ae000000-0000-0000-0000-000000000004',
 'e0000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000001',
 'Stress test performed — patient achieved 8 METS without chest pain. Mild ST depression noted at peak exercise. Echocardiogram shows normal ejection fraction (60%). Holter monitor arranged for 48-hour ambulatory ECG monitoring to rule out arrhythmia. Patient advised to avoid high-intensity exercise until results are reviewed.',
 'Aspirin 81mg daily. Sublingual nitroglycerin 0.4mg as needed for chest discomfort (max 3 doses, 5 min apart). Follow-up in 2 weeks for Holter results.');
