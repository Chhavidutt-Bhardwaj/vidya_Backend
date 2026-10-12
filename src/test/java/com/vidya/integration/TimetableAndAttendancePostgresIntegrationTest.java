package com.vidya.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL Integration Test verifying database constraints, partial unique indexes,
 * and soft-delete coexistence against Flyway migrations V1 through V13.
 */
@DisplayName("Timetable & Attendance — PostgreSQL Database Constraints Integration Tests")
class TimetableAndAttendancePostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

    // ── Helper ID generators & Fixture Creators ───────────────────────────────

    private UUID createAcademicYear(String code, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, code, name, LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6), false, "ACTIVE"
        );
        return id;
    }

    private UUID createAcademicClass(String code, String name, int order) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, ?)",
                id, code, name, order
        );
        return id;
    }

    private UUID createSection(UUID yearId, UUID classId, String code, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO section (id, academic_year_id, academic_class_id, code, name, capacity) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                id, yearId, classId, code, name, 40
        );
        return id;
    }

    private UUID createTenantUser(String code, String email, String type) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenant_user (id, user_code, email, phone, user_type) VALUES (?, ?, ?, ?, ?)",
                id, code, email, "9876543210", type
        );
        return id;
    }

    private UUID createStudent(UUID userId, String code, String admissionNo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO student (id, student_code, tenant_user_id, admission_number, admission_date, status) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                id, code, userId, admissionNo, LocalDate.now().minusMonths(6), "ACTIVE"
        );
        return id;
    }

    private UUID createStaff(UUID userId, String code, String employeeNo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO staff (id, staff_code, tenant_user_id, employee_code, joining_date, employment_status, staff_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, code, userId, employeeNo, LocalDate.now().minusMonths(12), "ACTIVE", "TEACHER"
        );
        return id;
    }

    private UUID createSubject(String code, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO subject (id, subject_code, name, subject_type, status) VALUES (?, ?, ?, ?, ?)",
                id, code, name, "CORE", "ACTIVE"
        );
        return id;
    }

    private UUID createRoom(String code, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO room (id, room_code, name, room_type, capacity, status) VALUES (?, ?, ?, ?, ?, ?)",
                id, code, name, "CLASSROOM", 40, "ACTIVE"
        );
        return id;
    }

    private UUID createBellSchedule(String code, String name, boolean isDefault) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule (id, schedule_code, name, is_default, status) VALUES (?, ?, ?, ?, ?)",
                id, code, name, isDefault, "ACTIVE"
        );
        return id;
    }

    private UUID createBellSchedulePeriod(UUID scheduleId, String code, String name, int seq, LocalTime start, LocalTime end) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule_period (id, period_code, bell_schedule_id, name, period_sequence, start_time, end_time, period_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, scheduleId, name, seq, start, end, "TEACHING"
        );
        return id;
    }

    private UUID createTimetable(UUID yearId, UUID scheduleId, String code, String name, String status, LocalDate from, LocalDate to) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable (id, timetable_code, academic_year_id, bell_schedule_id, name, status, effective_from, effective_to) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, yearId, scheduleId, name, status, from, to
        );
        return id;
    }

    // ── 1. Attendance Partial Unique Index Tests ───────────────────────────────

    @Test
    @DisplayName("Attendance — Partial unique index rejects duplicate active record, allows new record after soft-delete")
    void attendancePartialUniqueIndex_rejectsActiveDuplicate_allowsSoftDeleted() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-ATT-" + suffix, "Academic Year " + suffix);
        UUID classId = createAcademicClass("CLS-ATT-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-ATT-" + suffix, "Section " + suffix);
        UUID userId = createTenantUser("USR-ATT-" + suffix, "stu_" + suffix + "@test.com", "STUDENT");
        UUID studentId = createStudent(userId, "STU-ATT-" + suffix, "ADM-" + suffix);
        LocalDate attendanceDate = LocalDate.now();

        // 1. Insert first active attendance record
        UUID attId1 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO student_attendance (id, attendance_code, student_id, academic_year_id, academic_class_id, section_id, attendance_date, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                attId1, "ATT-1-" + suffix, studentId, yearId, classId, sectionId, attendanceDate, "PRESENT"
        );

        // 2. Attempt duplicate active insert for same student + year + date -> must fail
        UUID attId2 = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO student_attendance (id, attendance_code, student_id, academic_year_id, academic_class_id, section_id, attendance_date, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        attId2, "ATT-2-" + suffix, studentId, yearId, classId, sectionId, attendanceDate, "ABSENT"
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_sa_unique_active_student_year_date.*|.*duplicate key value.*");

        // 3. Soft-delete the first record
        jdbcTemplate.update("UPDATE student_attendance SET deleted_at = NOW() WHERE id = ?", attId1);

        // 4. Insert new active attendance record for same student + year + date -> must succeed
        UUID attId3 = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
                "INSERT INTO student_attendance (id, attendance_code, student_id, academic_year_id, academic_class_id, section_id, attendance_date, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                attId3, "ATT-3-" + suffix, studentId, yearId, classId, sectionId, attendanceDate, "LATE"
        );
        assertThat(inserted).isEqualTo(1);
    }

    // ── 2. Timetable Clash Partial Unique Indexes Tests ─────────────────────────

    @Test
    @DisplayName("Timetable Clash — Teacher clash index (idx_tte_teacher_clash) rejects duplicate, allows after soft-delete")
    void timetableClash_teacherClash_rejectsDuplicate_allowsAfterSoftDelete() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-TC-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-TC-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-TC-" + suffix, "Section " + suffix);
        UUID teacherUser = createTenantUser("USR-T-" + suffix, "t_" + suffix + "@test.com", "TEACHER");
        UUID teacherId = createStaff(teacherUser, "STF-T-" + suffix, "EMP-T-" + suffix);
        UUID scheduleId = createBellSchedule("SCH-TC-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-TC-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-TC-" + suffix, "Timetable " + suffix, "ACTIVE", null, null);

        // 1. Insert entry 1 with teacher at Monday Period 1
        UUID entryId1 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entryId1, "TTE-1-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId, teacherId
        );

        // 2. Attempt duplicate entry for same teacher at same Monday Period 1 -> must fail
        UUID entryId2 = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        entryId2, "TTE-2-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId, teacherId
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_tte_teacher_clash.*|.*duplicate key value.*");

        // 3. Soft-delete entry 1
        jdbcTemplate.update("UPDATE timetable_entry SET deleted_at = NOW() WHERE id = ?", entryId1);

        // 4. Insert entry for same teacher at Monday Period 1 -> must succeed
        UUID entryId3 = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entryId3, "TTE-3-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId, teacherId
        );
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("Timetable Clash — Class+Section clash index (idx_tte_class_clash) rejects duplicate, allows after soft-delete")
    void timetableClash_classSectionClash_rejectsDuplicate_allowsAfterSoftDelete() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-CC-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-CC-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-CC-" + suffix, "Section " + suffix);
        UUID scheduleId = createBellSchedule("SCH-CC-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-CC-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-CC-" + suffix, "Timetable " + suffix, "ACTIVE", null, null);

        // 1. Insert entry 1 for Class+Section at Monday Period 1
        UUID entryId1 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                entryId1, "TTE-CC1-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId
        );

        // 2. Duplicate entry for same Class+Section at Monday Period 1 -> must fail
        UUID entryId2 = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        entryId2, "TTE-CC2-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_tte_class_clash.*|.*duplicate key value.*");

        // 3. Soft-delete entry 1
        jdbcTemplate.update("UPDATE timetable_entry SET deleted_at = NOW() WHERE id = ?", entryId1);

        // 4. Insert entry for same Class+Section -> must succeed
        UUID entryId3 = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                entryId3, "TTE-CC3-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId
        );
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("Timetable Clash — Room clash index (idx_tte_room_clash) rejects duplicate, allows after soft-delete")
    void timetableClash_roomClash_rejectsDuplicate_allowsAfterSoftDelete() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-RC-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-RC-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-RC-" + suffix, "Section " + suffix);
        UUID roomId = createRoom("ROOM-RC-" + suffix, "Room " + suffix);
        UUID scheduleId = createBellSchedule("SCH-RC-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-RC-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-RC-" + suffix, "Timetable " + suffix, "ACTIVE", null, null);

        // 1. Insert entry 1 with Room at Monday Period 1
        UUID entryId1 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entryId1, "TTE-RC1-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId, roomId
        );

        // 2. Duplicate entry for same Room at Monday Period 1 -> must fail
        UUID entryId2 = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, room_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        entryId2, "TTE-RC2-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId, roomId
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_tte_room_clash.*|.*duplicate key value.*");

        // 3. Soft-delete entry 1
        jdbcTemplate.update("UPDATE timetable_entry SET deleted_at = NOW() WHERE id = ?", entryId1);

        // 4. Insert entry for same Room -> must succeed
        UUID entryId3 = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entryId3, "TTE-RC3-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId, roomId
        );
        assertThat(inserted).isEqualTo(1);
    }

    // ── 3. V13 Hardening Constraints & Partial Indexes Tests ───────────────────

    @Test
    @DisplayName("V13 — idx_bell_schedule_single_default allows only one active default schedule")
    void v13_bellSchedule_singleDefaultIndex() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Insert first default bell schedule
        UUID id1 = createBellSchedule("SCH-DEF1-" + suffix, "Default Schedule 1", true);

        // 2. Attempt second active default bell schedule -> must fail
        assertThatThrownBy(() ->
                createBellSchedule("SCH-DEF2-" + suffix, "Default Schedule 2", true))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_bell_schedule_single_default.*|.*duplicate key value.*");

        // 3. Non-default active schedules can exist without conflict
        UUID nonDefId = createBellSchedule("SCH-NONDEF-" + suffix, "Non-default Schedule", false);
        assertThat(nonDefId).isNotNull();

        // 4. Soft-delete first default schedule
        jdbcTemplate.update("UPDATE bell_schedule SET deleted_at = NOW() WHERE id = ?", id1);

        // 5. Insert new default bell schedule -> must succeed
        UUID id2 = createBellSchedule("SCH-DEF2-" + suffix, "Default Schedule 2", true);
        assertThat(id2).isNotNull();
    }

    @Test
    @DisplayName("V13 — chk_timetable_effective_dates enforces effective_from <= effective_to")
    void v13_timetable_effectiveDatesCheckConstraint() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-ED-" + suffix, "Year " + suffix);
        UUID scheduleId = createBellSchedule("SCH-ED-" + suffix, "Schedule " + suffix, false);

        // 1. Invalid date range: from > to -> must fail
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 5, 1);
        assertThatThrownBy(() ->
                createTimetable(yearId, scheduleId, "TT-INV-" + suffix, "Invalid TT", "DRAFT", from, to))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*chk_timetable_effective_dates.*|.*check constraint.*");

        // 2. Valid date range: from <= to -> must succeed
        UUID validId = createTimetable(yearId, scheduleId, "TT-VAL-" + suffix, "Valid TT", "DRAFT", to, from);
        assertThat(validId).isNotNull();

        // 3. Null dates -> must succeed
        UUID nullDatesId = createTimetable(yearId, scheduleId, "TT-NULL-" + suffix, "Null Dates TT", "DRAFT", null, null);
        assertThat(nullDatesId).isNotNull();
    }

    @Test
    @DisplayName("V13 — chk_tte_teacher_assignment_requires_teacher enforces teacher_id when teacher_assignment_id is present")
    void v13_timetableEntry_teacherAssignmentRequiresTeacher() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-TA-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-TA-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-TA-" + suffix, "Section " + suffix);
        UUID subjectId = createSubject("SUB-TA-" + suffix, "Subject " + suffix);
        UUID scheduleId = createBellSchedule("SCH-TA-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-TA-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-TA-" + suffix, "Timetable " + suffix, "ACTIVE", null, null);

        UUID teacherUser = createTenantUser("USR-STA-" + suffix, "sta_" + suffix + "@test.com", "TEACHER");
        UUID teacherId = createStaff(teacherUser, "STF-STA-" + suffix, "EMP-STA-" + suffix);

        // Create teacher_subject_assignment
        UUID tsaId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO teacher_subject_assignment (id, assignment_code, academic_year_id, academic_class_id, teacher_id, subject_id, section_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                tsaId, "TSA-" + suffix, yearId, classId, teacherId, subjectId, sectionId, "ACTIVE"
        );

        // 1. teacher_assignment_id present, but teacher_id is NULL -> must fail
        UUID invalidEntryId = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, subject_id, teacher_assignment_id, teacher_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        invalidEntryId, "TTE-INV-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId, subjectId, tsaId, null
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*chk_tte_teacher_assignment_requires_teacher.*|.*check constraint.*");

        // 2. Both teacher_assignment_id and teacher_id present -> must succeed
        UUID validEntryId = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, subject_id, teacher_assignment_id, teacher_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                validEntryId, "TTE-VAL-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId, subjectId, tsaId, teacherId
        );
        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("V13 — idx_timetable_single_active_per_year allows only one ACTIVE timetable per academic year")
    void v13_timetable_singleActivePerYearIndex() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-SAY-" + suffix, "Year " + suffix);
        UUID scheduleId = createBellSchedule("SCH-SAY-" + suffix, "Schedule " + suffix, false);

        // 1. Insert first ACTIVE timetable for academic year
        UUID ttId1 = createTimetable(yearId, scheduleId, "TT-ACT1-" + suffix, "Active TT 1", "ACTIVE", null, null);
        assertThat(ttId1).isNotNull();

        // 2. Attempt second ACTIVE timetable for same academic year -> must fail
        assertThatThrownBy(() ->
                createTimetable(yearId, scheduleId, "TT-ACT2-" + suffix, "Active TT 2", "ACTIVE", null, null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_timetable_single_active_per_year.*|.*duplicate key value.*");

        // 3. DRAFT timetable for same year -> must succeed
        UUID draftId = createTimetable(yearId, scheduleId, "TT-DFT-" + suffix, "Draft TT", "DRAFT", null, null);
        assertThat(draftId).isNotNull();

        // 4. Soft-delete first active timetable
        jdbcTemplate.update("UPDATE timetable SET deleted_at = NOW() WHERE id = ?", ttId1);

        // 5. Insert new ACTIVE timetable for same academic year -> must succeed
        UUID ttId2 = createTimetable(yearId, scheduleId, "TT-ACT2-" + suffix, "Active TT 2", "ACTIVE", null, null);
        assertThat(ttId2).isNotNull();
    }

    @Test
    @DisplayName("V13 — bell_schedule_period created_by and updated_by columns persist and query correctly")
    void v13_bellSchedulePeriod_auditColumns() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID scheduleId = createBellSchedule("SCH-AUD-" + suffix, "Schedule " + suffix, false);
        UUID createdBy = UUID.randomUUID();
        UUID updatedBy = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO bell_schedule_period (id, period_code, bell_schedule_id, name, period_sequence, start_time, end_time, period_type, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                periodId, "PER-AUD-" + suffix, scheduleId, "Period 1", 1, LocalTime.of(9, 0), LocalTime.of(9, 45), "TEACHING", createdBy, updatedBy
        );

        UUID fetchedCreatedBy = jdbcTemplate.queryForObject(
                "SELECT created_by FROM bell_schedule_period WHERE id = ?", UUID.class, periodId);
        UUID fetchedUpdatedBy = jdbcTemplate.queryForObject(
                "SELECT updated_by FROM bell_schedule_period WHERE id = ?", UUID.class, periodId);

        assertThat(fetchedCreatedBy).isEqualTo(createdBy);
        assertThat(fetchedUpdatedBy).isEqualTo(updatedBy);
    }

    @Test
    @DisplayName("V16 — student_attendance non_working_day_override CHECK constraint requires override_reason when true")
    void v16_studentAttendance_overrideCheckConstraint() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-ATT-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-ATT-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-ATT-" + suffix, "Section " + suffix);
        UUID userId = createTenantUser("USR-ATT-" + suffix, "att" + suffix + "@test.com", "STUDENT");
        UUID studentId = createStudent(userId, "STU-ATT-" + suffix, "ADM-" + suffix);

        LocalDate attDate = LocalDate.now();

        // 1. Override is TRUE but override_reason is NULL -> must fail due to chk_student_attendance_override_reason
        UUID invalidAttId = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO student_attendance (id, attendance_code, student_id, academic_year_id, academic_class_id, section_id, attendance_date, status, non_working_day_override, override_reason) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        invalidAttId, "ATT-INV-" + suffix, studentId, yearId, classId, sectionId, attDate, "PRESENT", true, null
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*chk_student_attendance_override_reason.*|.*check constraint.*");

        // 2. Override is TRUE and override_reason is provided -> must succeed
        UUID validAttId1 = UUID.randomUUID();
        int inserted1 = jdbcTemplate.update(
                "INSERT INTO student_attendance (id, attendance_code, student_id, academic_year_id, academic_class_id, section_id, attendance_date, status, non_working_day_override, override_reason) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                validAttId1, "ATT-V1-" + suffix, studentId, yearId, classId, sectionId, attDate, "PRESENT", true, "Special Saturday class"
        );
        assertThat(inserted1).isEqualTo(1);

        // 3. Override is FALSE and override_reason is NULL -> must succeed
        UUID student2UserId = createTenantUser("USR-ATT2-" + suffix, "att2" + suffix + "@test.com", "STUDENT");
        UUID student2Id = createStudent(student2UserId, "STU-ATT2-" + suffix, "ADM2-" + suffix);
        UUID validAttId2 = UUID.randomUUID();
        int inserted2 = jdbcTemplate.update(
                "INSERT INTO student_attendance (id, attendance_code, student_id, academic_year_id, academic_class_id, section_id, attendance_date, status, non_working_day_override, override_reason) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                validAttId2, "ATT-V2-" + suffix, student2Id, yearId, classId, sectionId, attDate, "PRESENT", false, null
        );
        assertThat(inserted2).isEqualTo(1);
    }
}
