-- ============================================================
-- DATABASE TEST QUERIES — OpenMRS MySQL
-- Project  : OpenMRS Healthcare Testing
-- Author   : Chandra Siddartha
-- DB       : MySQL 8 | Schema: openmrs
-- ============================================================
-- How to connect:
--   mysql -u root -p openmrs
-- Or via MySQL Workbench → localhost:3306 → openmrs schema
-- ============================================================


-- ════════════════════════════════════════════════════════════
-- MODULE 1: USER / LOGIN VERIFICATION
-- ════════════════════════════════════════════════════════════

-- DBT_001: Verify admin user exists in the system
SELECT user_id, username, system_id, retired
FROM users
WHERE username = 'admin';
-- Expected: 1 row returned, retired = 0

-- DBT_002: Verify no duplicate usernames exist
SELECT username, COUNT(*) AS count
FROM users
GROUP BY username
HAVING COUNT(*) > 1;
-- Expected: 0 rows (no duplicates)

-- DBT_003: Verify user is not retired/disabled
SELECT username, retired, retire_reason
FROM users
WHERE username = 'admin';
-- Expected: retired = 0

-- DBT_004: Verify user has at least one role assigned
SELECT u.username, r.role
FROM users u
JOIN user_role ur ON u.user_id = ur.user_id
JOIN role r ON ur.role = r.role
WHERE u.username = 'admin';
-- Expected: At least 1 row (e.g., System Developer, Admin)


-- ════════════════════════════════════════════════════════════
-- MODULE 2: PATIENT REGISTRATION VERIFICATION
-- ════════════════════════════════════════════════════════════

-- DBT_005: Verify patient was saved after registration
-- Replace 'John' and 'Doe' with actual registered patient name
SELECT p.person_id, pn.given_name, pn.family_name, p.gender, p.birthdate, p.voided
FROM person p
JOIN person_name pn ON p.person_id = pn.person_id
WHERE pn.given_name = 'John'
  AND pn.family_name = 'Doe'
  AND p.voided = 0;
-- Expected: 1 row returned

-- DBT_006: Verify patient ID (identifier) was auto-generated
SELECT pi.identifier, pi.identifier_type, pi.preferred
FROM patient_identifier pi
JOIN person_name pn ON pi.patient_id = pn.person_id
WHERE pn.given_name = 'John'
  AND pn.family_name = 'Doe';
-- Expected: 1 or more rows with non-empty identifier

-- DBT_007: Verify patient gender saved correctly
SELECT p.person_id, pn.given_name, p.gender
FROM person p
JOIN person_name pn ON p.person_id = pn.person_id
WHERE pn.given_name = 'John'
  AND pn.family_name = 'Doe';
-- Expected: gender = 'M' for Male, 'F' for Female

-- DBT_008: Verify patient date of birth saved correctly
SELECT pn.given_name, pn.family_name, p.birthdate
FROM person p
JOIN person_name pn ON p.person_id = pn.person_id
WHERE pn.given_name = 'John'
  AND pn.family_name = 'Doe';
-- Expected: birthdate = '1990-01-15'

-- DBT_009: Verify no duplicate patient records
SELECT pn.given_name, pn.family_name, p.birthdate, COUNT(*) AS count
FROM person p
JOIN person_name pn ON p.person_id = pn.person_id
WHERE pn.given_name = 'John'
  AND pn.family_name = 'Doe'
GROUP BY pn.given_name, pn.family_name, p.birthdate
HAVING COUNT(*) > 1;
-- Expected: 0 rows (no duplicate patients with same name + DOB)

-- DBT_010: Count total active (non-voided) patients
SELECT COUNT(*) AS total_active_patients
FROM person
WHERE voided = 0;
-- Expected: Count should increase by 1 after each registration


-- ════════════════════════════════════════════════════════════
-- MODULE 3: PATIENT ADDRESS VERIFICATION
-- ════════════════════════════════════════════════════════════

-- DBT_011: Verify patient address was saved
SELECT pa.address1, pa.city_village, pa.state_province, pa.country
FROM person_address pa
JOIN person_name pn ON pa.person_id = pn.person_id
WHERE pn.given_name = 'Jane'
  AND pn.family_name = 'Smith';
-- Expected: Address row returned with entered values

-- DBT_012: Verify preferred address flag is set
SELECT pa.preferred, pa.address1
FROM person_address pa
JOIN person_name pn ON pa.person_id = pn.person_id
WHERE pn.given_name = 'Jane';
-- Expected: preferred = 1


-- ════════════════════════════════════════════════════════════
-- MODULE 4: APPOINTMENT / VISIT VERIFICATION
-- ════════════════════════════════════════════════════════════

-- DBT_013: Verify appointment (visit) was created for a patient
-- Replace patient_id with actual ID
SELECT v.visit_id, v.patient_id, v.visit_type_id, v.date_started, v.voided
FROM visit v
WHERE v.patient_id = 1
  AND v.voided = 0
ORDER BY v.date_started DESC
LIMIT 5;
-- Expected: At least 1 row for the patient's appointment

-- DBT_014: Verify encounter created during visit
SELECT e.encounter_id, e.patient_id, e.encounter_type, e.encounter_datetime
FROM encounter e
WHERE e.patient_id = 1
  AND e.voided = 0
ORDER BY e.encounter_datetime DESC
LIMIT 5;
-- Expected: Encounter records linked to the patient

-- DBT_015: Verify appointment was not scheduled in the past
SELECT v.visit_id, v.date_started
FROM visit v
WHERE v.patient_id = 1
  AND DATE(v.date_started) < CURDATE();
-- Expected: Should be 0 rows if past-date restriction works


-- ════════════════════════════════════════════════════════════
-- MODULE 5: ROLE-BASED ACCESS CONTROL
-- ════════════════════════════════════════════════════════════

-- DBT_016: List all roles and their privileges
SELECT r.role, rp.privilege
FROM role r
JOIN role_privilege rp ON r.role = rp.role
ORDER BY r.role, rp.privilege;
-- Expected: Roles like 'System Developer', 'Provider', etc.

-- DBT_017: Verify specific user has expected role
SELECT u.username, ur.role
FROM users u
JOIN user_role ur ON u.user_id = ur.user_id
WHERE u.username = 'admin';
-- Expected: admin should have 'System Developer' role


-- ════════════════════════════════════════════════════════════
-- MODULE 6: DATA INTEGRITY CHECKS
-- ════════════════════════════════════════════════════════════

-- DBT_018: Verify no orphan patient names (names without person record)
SELECT pn.person_id, pn.given_name
FROM person_name pn
LEFT JOIN person p ON pn.person_id = p.person_id
WHERE p.person_id IS NULL;
-- Expected: 0 rows

-- DBT_019: Verify all patients have at least one identifier
SELECT pat.patient_id
FROM patient pat
LEFT JOIN patient_identifier pi ON pat.patient_id = pi.patient_id
WHERE pi.identifier IS NULL;
-- Expected: 0 rows (every patient must have an ID)

-- DBT_020: Check for voided patients that still appear in identifiers
SELECT pi.patient_id, pi.identifier, p.voided
FROM patient_identifier pi
JOIN person p ON pi.patient_id = p.person_id
WHERE p.voided = 1;
-- Expected: These should be handled/cleaned up

-- DBT_021: Get summary of patient count by gender
SELECT gender, COUNT(*) AS count
FROM person
WHERE voided = 0
GROUP BY gender;
-- Expected: M and F counts visible — useful for sanity check

-- DBT_022: Verify recently added patient appears in last 24 hours
SELECT pn.given_name, pn.family_name, p.date_created
FROM person p
JOIN person_name pn ON p.person_id = pn.person_id
WHERE p.date_created >= NOW() - INTERVAL 1 DAY
  AND p.voided = 0
ORDER BY p.date_created DESC;
-- Expected: Recently registered test patients appear here
