package com.vidya.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TimetableSubstitution — PostgreSQL Database Constraint & Concurrency Integration Tests")
class TimetableSubstitutionPostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

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

    private UUID createStaff(UUID userId, String code, String employeeNo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO staff (id, staff_code, tenant_user_id, employee_code, joining_date, employment_status, staff_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, code, userId, employeeNo, LocalDate.now().minusMonths(12), "ACTIVE", "TEACHER"
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
                "INSERT INTO bell_schedule (id, schedule_code, name, is_default, status) " +
                "VALUES (?, ?, ?, ?, ?)",
                id, code, name, isDefault, "ACTIVE"
        );
        return id;
    }

    private UUID createBellSchedulePeriod(UUID scheduleId, String code, String name, int order, LocalTime start, LocalTime end) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bell_schedule_period (id, period_code, bell_schedule_id, name, period_sequence, start_time, end_time, period_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, scheduleId, name, order, start, end, "TEACHING"
        );
        return id;
    }

    private UUID createTimetable(UUID yearId, UUID scheduleId, String code, String name, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable (id, timetable_code, academic_year_id, bell_schedule_id, name, status, effective_from, effective_to) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, yearId, scheduleId, name, status, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(5)
        );
        return id;
    }

    @Test
    @DisplayName("V14 Schema & Partial Unique Index idx_sub_slot_active — Duplicate active substitution for same slot is blocked")
    void substitution_slotActivePartialIndex_blocksDuplicates() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-SB1-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-SB1-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-SB1-" + suffix, "Section " + suffix);
        UUID scheduleId = createBellSchedule("SCH-SB1-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-SB1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-SB1-" + suffix, "Timetable " + suffix, "ACTIVE");

        UUID user1 = createTenantUser("USR-SB1-" + suffix, "sb1_" + suffix + "@test.com", "TEACHER");
        UUID teacher1 = createStaff(user1, "STF-SB1-" + suffix, "EMP-SB1-" + suffix);
        UUID user2 = createTenantUser("USR-SB2-" + suffix, "sb2_" + suffix + "@test.com", "TEACHER");
        UUID teacher2 = createStaff(user2, "STF-SB2-" + suffix, "EMP-SB2-" + suffix);

        LocalDate subDate = LocalDate.now().plusDays(1);

        // First active substitution succeeds
        jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-1-" + suffix, timetableId, subDate, periodId, classId, sectionId, "TEACHER_SUBSTITUTE", teacher1, "ACTIVE"
        );

        // Second active substitution for same slot fails with unique constraint violation
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-2-" + suffix, timetableId, subDate, periodId, classId, sectionId, "TEACHER_SUBSTITUTE", teacher2, "ACTIVE"
        )).isInstanceOf(DataAccessException.class);

        // Cancelling first substitution allows creating a new active substitution for the slot
        jdbcTemplate.update("UPDATE timetable_substitution SET status = 'CANCELLED' WHERE substitution_code = ?", "SUB-1-" + suffix);

        int rows = jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-3-" + suffix, timetableId, subDate, periodId, classId, sectionId, "TEACHER_SUBSTITUTE", teacher2, "ACTIVE"
        );
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("Partial Unique Index idx_sub_teacher_active — Duplicate booking of substitute teacher at date and period is blocked")
    void substitution_teacherActivePartialIndex_blocksDoubleBooking() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-ST1-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-ST1-" + suffix, "Class 1 " + suffix, 1);
        UUID class2 = createAcademicClass("CLS-ST2-" + suffix, "Class 2 " + suffix, 2);
        UUID scheduleId = createBellSchedule("SCH-ST1-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-ST1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-ST1-" + suffix, "Timetable " + suffix, "ACTIVE");

        UUID user1 = createTenantUser("USR-ST1-" + suffix, "st1_" + suffix + "@test.com", "TEACHER");
        UUID teacher1 = createStaff(user1, "STF-ST1-" + suffix, "EMP-ST1-" + suffix);

        LocalDate subDate = LocalDate.now().plusDays(2);

        // First booking for teacher1 in class1
        jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-T1-" + suffix, timetableId, subDate, periodId, class1, null, "TEACHER_SUBSTITUTE", teacher1, "ACTIVE"
        );

        // Second booking for same teacher1 in class2 at same date and period fails
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-T2-" + suffix, timetableId, subDate, periodId, class2, null, "TEACHER_SUBSTITUTE", teacher1, "ACTIVE"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Partial Unique Index idx_sub_room_active — Duplicate booking of substitute room at date and period is blocked")
    void substitution_roomActivePartialIndex_blocksDoubleBooking() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-SR1-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-SR1-" + suffix, "Class 1 " + suffix, 1);
        UUID class2 = createAcademicClass("CLS-SR2-" + suffix, "Class 2 " + suffix, 2);
        UUID scheduleId = createBellSchedule("SCH-SR1-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-SR1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-SR1-" + suffix, "Timetable " + suffix, "ACTIVE");

        UUID room1 = createRoom("ROM-SR1-" + suffix, "Room " + suffix);
        LocalDate subDate = LocalDate.now().plusDays(3);

        // First booking for room1 in class1
        jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_room_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-R1-" + suffix, timetableId, subDate, periodId, class1, null, "ROOM_CHANGE", room1, "ACTIVE"
        );

        // Second booking for same room1 in class2 at same date and period fails
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_room_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "SUB-R2-" + suffix, timetableId, subDate, periodId, class2, null, "ROOM_CHANGE", room1, "ACTIVE"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Two Concurrent Creates — Same substitute teacher and slot: exactly 1 wins, 1 receives constraint violation")
    void concurrentSubstitutionCreates_exactlyOneWins() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-SC1-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-SC1-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-SC1-" + suffix, "Section " + suffix);
        UUID scheduleId = createBellSchedule("SCH-SC1-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-SC1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-SC1-" + suffix, "Timetable " + suffix, "ACTIVE");

        UUID user1 = createTenantUser("USR-SC1-" + suffix, "sc1_" + suffix + "@test.com", "TEACHER");
        UUID teacher1 = createStaff(user1, "STF-SC1-" + suffix, "EMP-SC1-" + suffix);

        LocalDate subDate = LocalDate.now().plusDays(4);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger clashCount = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                latch.await();
                jdbcTemplate.update(
                        "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "SUB-CONC-1-" + suffix, timetableId, subDate, periodId, classId, sectionId, "TEACHER_SUBSTITUTE", teacher1, "ACTIVE"
                );
                successCount.incrementAndGet();
            } catch (DataAccessException e) {
                clashCount.incrementAndGet();
            } catch (InterruptedException ignored) {}
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                latch.await();
                jdbcTemplate.update(
                        "INSERT INTO timetable_substitution (id, substitution_code, timetable_id, substitution_date, period_id, academic_class_id, section_id, substitution_type, substitute_teacher_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "SUB-CONC-2-" + suffix, timetableId, subDate, periodId, classId, sectionId, "TEACHER_SUBSTITUTE", teacher1, "ACTIVE"
                );
                successCount.incrementAndGet();
            } catch (DataAccessException e) {
                clashCount.incrementAndGet();
            } catch (InterruptedException ignored) {}
        });

        latch.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(clashCount.get()).isEqualTo(1);

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_substitution WHERE timetable_id = ? AND substitution_date = ? AND period_id = ? AND academic_class_id = ? AND section_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL",
                Integer.class, timetableId, subDate, periodId, classId, sectionId
        );
        assertThat(activeCount).isEqualTo(1);
    }
}
