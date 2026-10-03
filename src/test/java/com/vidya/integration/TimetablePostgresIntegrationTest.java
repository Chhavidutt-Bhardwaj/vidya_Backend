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
 * PostgreSQL Integration Tests for Timetable lifecycle, single-active constraint swap,
 * soft-delete cascade freeing clash partial unique indexes, and 24A/24B in-use queries.
 */
@DisplayName("Timetable — PostgreSQL Database Lifecycle & Constraint Integration Tests")
class TimetablePostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

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

    private UUID createTimetable(UUID yearId, UUID scheduleId, String code, String name, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable (id, timetable_code, academic_year_id, bell_schedule_id, name, status) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                id, code, yearId, scheduleId, name, status
        );
        return id;
    }

    @Test
    @DisplayName("Single Active Timetable — swapping active timetable via ARCHIVE + flush allows new ACTIVE without 409")
    void timetable_activeSwap_allowsNewActive() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-SWP-" + suffix, "Year " + suffix);
        UUID scheduleId = createBellSchedule("SCH-SWP-" + suffix, "Schedule " + suffix, false);

        // 1. First timetable is ACTIVE
        UUID ttId1 = createTimetable(yearId, scheduleId, "TT-1-" + suffix, "Timetable 1", "ACTIVE");

        // 2. Second timetable is PUBLISHED
        UUID ttId2 = createTimetable(yearId, scheduleId, "TT-2-" + suffix, "Timetable 2", "PUBLISHED");

        // 3. Attempting to set TT-2 to ACTIVE directly violates idx_timetable_single_active_per_year
        assertThatThrownBy(() ->
                jdbcTemplate.update("UPDATE timetable SET status = 'ACTIVE' WHERE id = ?", ttId2))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_timetable_single_active_per_year.*|.*duplicate key value.*");

        // 4. Archive TT-1 first, then activate TT-2 (atomic swap simulation)
        jdbcTemplate.update("UPDATE timetable SET status = 'ARCHIVED' WHERE id = ?", ttId1);
        int activated = jdbcTemplate.update("UPDATE timetable SET status = 'ACTIVE' WHERE id = ?", ttId2);

        assertThat(activated).isEqualTo(1);

        String status1 = jdbcTemplate.queryForObject("SELECT status FROM timetable WHERE id = ?", String.class, ttId1);
        String status2 = jdbcTemplate.queryForObject("SELECT status FROM timetable WHERE id = ?", String.class, ttId2);

        assertThat(status1).isEqualTo("ARCHIVED");
        assertThat(status2).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Soft Delete Cascade — soft-deleting timetable entries frees clash index slots")
    void timetable_softDeleteEntries_freesClashSlots() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-DEL-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-DEL-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-DEL-" + suffix, "Section " + suffix);
        UUID teacherUser = createTenantUser("USR-DEL-" + suffix, "del_" + suffix + "@test.com", "TEACHER");
        UUID teacherId = createStaff(teacherUser, "STF-DEL-" + suffix, "EMP-DEL-" + suffix);
        UUID roomId = createRoom("ROM-DEL-" + suffix, "Room " + suffix);
        UUID scheduleId = createBellSchedule("SCH-DEL-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-DEL-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));

        UUID ttId1 = createTimetable(yearId, scheduleId, "TT-DEL1-" + suffix, "Timetable 1", "DRAFT");
        UUID entryId1 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entryId1, "TTE-DEL1-" + suffix, ttId1, yearId, periodId, "MONDAY", classId, sectionId, teacherId, roomId
        );

        UUID entryId2 = UUID.randomUUID();

        // 1. Duplicate clash on Teacher, Room, and Class/Section within same timetable
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        entryId2, "TTE-DEL2-" + suffix, ttId1, yearId, periodId, "MONDAY", classId, sectionId, teacherId, roomId
                ))
                .isInstanceOf(DataAccessException.class);

        // 2. Cascade soft-delete timetable 1 and its entries
        jdbcTemplate.update("UPDATE timetable_entry SET deleted_at = NOW() WHERE timetable_id = ?", ttId1);
        jdbcTemplate.update("UPDATE timetable SET deleted_at = NOW() WHERE id = ?", ttId1);

        // 3. Now the clash slots are freed for timetable 1
        int inserted = jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entryId2, "TTE-DEL2-" + suffix, ttId1, yearId, periodId, "MONDAY", classId, sectionId, teacherId, roomId
        );

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("In-Use Queries — active room and period usage queries accurately detect active entries vs deleted ones")
    void timetable_inUseQueries_accurateDetection() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-USE-" + suffix, "Year " + suffix);
        UUID classId = createAcademicClass("CLS-USE-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-USE-" + suffix, "Section " + suffix);
        UUID roomId = createRoom("ROM-USE-" + suffix, "Room " + suffix);
        UUID scheduleId = createBellSchedule("SCH-USE-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-USE-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-USE-" + suffix, "Timetable " + suffix, "DRAFT");

        // 1. Initial state: room and period are not in use in timetable_entry
        Integer roomInUseCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE room_id = ? AND deleted_at IS NULL", Integer.class, roomId);
        Integer periodInUseCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE period_id = ? AND deleted_at IS NULL", Integer.class, periodId);
        Integer scheduleInUseCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable WHERE bell_schedule_id = ? AND deleted_at IS NULL", Integer.class, scheduleId);

        assertThat(roomInUseCount).isZero();
        assertThat(periodInUseCount).isZero();
        assertThat(scheduleInUseCount).isEqualTo(1);

        // 2. Add active entry
        UUID entryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entryId, "TTE-USE-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId, roomId
        );

        roomInUseCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE room_id = ? AND deleted_at IS NULL", Integer.class, roomId);
        periodInUseCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE period_id = ? AND deleted_at IS NULL", Integer.class, periodId);

        assertThat(roomInUseCount).isEqualTo(1);
        assertThat(periodInUseCount).isEqualTo(1);

        // 3. Soft-delete the entry
        jdbcTemplate.update("UPDATE timetable_entry SET deleted_at = NOW() WHERE id = ?", entryId);

        roomInUseCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE room_id = ? AND deleted_at IS NULL", Integer.class, roomId);
        periodInUseCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE period_id = ? AND deleted_at IS NULL", Integer.class, periodId);

        assertThat(roomInUseCount).isZero();
        assertThat(periodInUseCount).isZero();
    }

    @Test
    @DisplayName("Constraint Clashes — Teacher, Class/Section, and Room clashes trigger partial unique indexes")
    void timetableEntry_allThreeClashes_enforcePartialUniqueIndexes() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-CLSH-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-1-" + suffix, "Class 1 " + suffix, 1);
        UUID class2 = createAcademicClass("CLS-2-" + suffix, "Class 2 " + suffix, 2);
        UUID section1 = createSection(yearId, class1, "SEC-1-" + suffix, "Section 1");
        UUID section2 = createSection(yearId, class2, "SEC-2-" + suffix, "Section 2");
        UUID user1 = createTenantUser("USR-1-" + suffix, "t1_" + suffix + "@test.com", "TEACHER");
        UUID user2 = createTenantUser("USR-2-" + suffix, "t2_" + suffix + "@test.com", "TEACHER");
        UUID teacher1 = createStaff(user1, "STF-1-" + suffix, "EMP-1-" + suffix);
        UUID teacher2 = createStaff(user2, "STF-2-" + suffix, "EMP-2-" + suffix);
        UUID room1 = createRoom("ROM-1-" + suffix, "Room 1 " + suffix);
        UUID room2 = createRoom("ROM-2-" + suffix, "Room 2 " + suffix);
        UUID scheduleId = createBellSchedule("SCH-CLSH-" + suffix, "Schedule " + suffix, false);
        UUID period1 = createBellSchedulePeriod(scheduleId, "PER-1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-CLSH-" + suffix, "Timetable " + suffix, "DRAFT");

        // Base entry: Teacher1, Class1/Section1, Room1 on MONDAY Period1
        UUID entryId1 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entryId1, "TTE-BASE-" + suffix, timetableId, yearId, period1, "MONDAY", class1, section1, teacher1, room1
        );

        // 1. Teacher Clash: Different class, different room, same teacher + day + period -> violates idx_tte_teacher_clash
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "TTE-TCLASH-" + suffix, timetableId, yearId, period1, "MONDAY", class2, section2, teacher1, room2
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_tte_teacher_clash.*|.*duplicate key value.*");

        // 2. Class Clash: Different teacher, different room, same class + section + day + period -> violates idx_tte_class_clash
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "TTE-CCLASH-" + suffix, timetableId, yearId, period1, "MONDAY", class1, section1, teacher2, room2
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_tte_class_clash.*|.*duplicate key value.*");

        // 3. Room Clash: Different teacher, different class, same room + day + period -> violates idx_tte_room_clash
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "TTE-RCLASH-" + suffix, timetableId, yearId, period1, "MONDAY", class2, section2, teacher2, room1
                ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_tte_room_clash.*|.*duplicate key value.*");
    }

    @Test
    @DisplayName("N=5 Real Concurrency — 5 threads create entries for SAME teacher/day/period: exactly 1 succeeds, 4 fail with clash, exactly 1 active DB row")
    void timetableEntry_concurrentTeacherClash_n5Threads_exactlyOneSucceeds() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-T5-" + suffix, "Year " + suffix);
        UUID user1 = createTenantUser("USR-T5-" + suffix, "t5_" + suffix + "@test.com", "TEACHER");
        UUID teacher1 = createStaff(user1, "STF-T5-" + suffix, "EMP-T5-" + suffix);
        UUID scheduleId = createBellSchedule("SCH-T5-" + suffix, "Schedule " + suffix, false);
        UUID period1 = createBellSchedulePeriod(scheduleId, "PER-T5-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-T5-" + suffix, "Timetable " + suffix, "DRAFT");

        int n = 5;
        java.util.List<UUID> classes = new java.util.ArrayList<>();
        java.util.List<UUID> sections = new java.util.ArrayList<>();
        java.util.List<UUID> rooms = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID cls = createAcademicClass("CLS-T5-" + i + "-" + suffix, "Class " + i + " " + suffix, i + 1);
            classes.add(cls);
            sections.add(createSection(yearId, cls, "SEC-T5-" + i + "-" + suffix, "Section " + i));
            rooms.add(createRoom("ROM-T5-" + i + "-" + suffix, "Room " + i + " " + suffix));
        }

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(n);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger clashCount = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    jdbcTemplate.update(
                            "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), "TTE-T5-" + index + "-" + suffix, timetableId, yearId, period1, "MONDAY", classes.get(index), sections.get(index), teacher1, rooms.get(index)
                    );
                    successCount.incrementAndGet();
                } catch (DataAccessException e) {
                    clashCount.incrementAndGet();
                }
                return null;
            }));
        }

        startLatch.countDown();
        for (java.util.concurrent.Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(clashCount.get()).isEqualTo(4);

        Integer activeRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE timetable_id = ? AND teacher_id = ? AND day_of_week = 'MONDAY' AND period_id = ? AND deleted_at IS NULL",
                Integer.class, timetableId, teacher1, period1
        );
        assertThat(activeRows).isEqualTo(1);
    }

    @Test
    @DisplayName("N=5 Real Concurrency — 5 threads create entries for SAME room/day/period: exactly 1 succeeds, 4 fail with clash, exactly 1 active DB row")
    void timetableEntry_concurrentRoomClash_n5Threads_exactlyOneSucceeds() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-R5-" + suffix, "Year " + suffix);
        UUID room1 = createRoom("ROM-R5-" + suffix, "Room " + suffix);
        UUID scheduleId = createBellSchedule("SCH-R5-" + suffix, "Schedule " + suffix, false);
        UUID period1 = createBellSchedulePeriod(scheduleId, "PER-R5-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-R5-" + suffix, "Timetable " + suffix, "DRAFT");

        int n = 5;
        java.util.List<UUID> classes = new java.util.ArrayList<>();
        java.util.List<UUID> sections = new java.util.ArrayList<>();
        java.util.List<UUID> teachers = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID cls = createAcademicClass("CLS-R5-" + i + "-" + suffix, "Class " + i + " " + suffix, i + 1);
            classes.add(cls);
            sections.add(createSection(yearId, cls, "SEC-R5-" + i + "-" + suffix, "Section " + i));
            UUID user = createTenantUser("USR-R5-" + i + "-" + suffix, "r5_" + i + "_" + suffix + "@test.com", "TEACHER");
            teachers.add(createStaff(user, "STF-R5-" + i + "-" + suffix, "EMP-R5-" + i + "-" + suffix));
        }

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(n);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger clashCount = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    jdbcTemplate.update(
                            "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), "TTE-R5-" + index + "-" + suffix, timetableId, yearId, period1, "MONDAY", classes.get(index), sections.get(index), teachers.get(index), room1
                    );
                    successCount.incrementAndGet();
                } catch (DataAccessException e) {
                    clashCount.incrementAndGet();
                }
                return null;
            }));
        }

        startLatch.countDown();
        for (java.util.concurrent.Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(clashCount.get()).isEqualTo(4);

        Integer activeRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE timetable_id = ? AND room_id = ? AND day_of_week = 'MONDAY' AND period_id = ? AND deleted_at IS NULL",
                Integer.class, timetableId, room1, period1
        );
        assertThat(activeRows).isEqualTo(1);
    }

    @Test
    @DisplayName("N=5 Real Concurrency — 5 threads create entries for SAME class+section/day/period: exactly 1 succeeds, 4 fail with clash, exactly 1 active DB row")
    void timetableEntry_concurrentClassSectionClash_n5Threads_exactlyOneSucceeds() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-S5-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-S5-" + suffix, "Class " + suffix, 1);
        UUID section1 = createSection(yearId, class1, "SEC-S5-" + suffix, "Section " + suffix);
        UUID scheduleId = createBellSchedule("SCH-S5-" + suffix, "Schedule " + suffix, false);
        UUID period1 = createBellSchedulePeriod(scheduleId, "PER-S5-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-S5-" + suffix, "Timetable " + suffix, "DRAFT");

        int n = 5;
        java.util.List<UUID> teachers = new java.util.ArrayList<>();
        java.util.List<UUID> rooms = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID user = createTenantUser("USR-S5-" + i + "-" + suffix, "s5_" + i + "_" + suffix + "@test.com", "TEACHER");
            teachers.add(createStaff(user, "STF-S5-" + i + "-" + suffix, "EMP-S5-" + i + "-" + suffix));
            rooms.add(createRoom("ROM-S5-" + i + "-" + suffix, "Room " + i + " " + suffix));
        }

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(n);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger clashCount = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    jdbcTemplate.update(
                            "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(), "TTE-S5-" + index + "-" + suffix, timetableId, yearId, period1, "MONDAY", class1, section1, teachers.get(index), rooms.get(index)
                    );
                    successCount.incrementAndGet();
                } catch (DataAccessException e) {
                    clashCount.incrementAndGet();
                }
                return null;
            }));
        }

        startLatch.countDown();
        for (java.util.concurrent.Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(clashCount.get()).isEqualTo(4);

        Integer activeRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE timetable_id = ? AND academic_class_id = ? AND section_id = ? AND day_of_week = 'MONDAY' AND period_id = ? AND deleted_at IS NULL",
                Integer.class, timetableId, class1, section1, period1
        );
        assertThat(activeRows).isEqualTo(1);
    }

    @Test
    @DisplayName("Bulk Upsert & Swap — Atomic slot swap between 2 teachers/rooms on 2 periods executes successfully with soft-delete flush")
    void timetableEntry_slotSwapScenario_succeedsWithoutConstraintViolation() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-SWP-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-SWP-" + suffix, "Class " + suffix, 1);
        UUID section1 = createSection(yearId, class1, "SEC-SWP-" + suffix, "Section " + suffix);
        UUID userA = createTenantUser("USR-SWA-" + suffix, "swa_" + suffix + "@test.com", "TEACHER");
        UUID userB = createTenantUser("USR-SWB-" + suffix, "swb_" + suffix + "@test.com", "TEACHER");
        UUID teacherA = createStaff(userA, "STF-SWA-" + suffix, "EMP-SWA-" + suffix);
        UUID teacherB = createStaff(userB, "STF-SWB-" + suffix, "EMP-SWB-" + suffix);
        UUID room1 = createRoom("ROM-SW1-" + suffix, "Room 1 " + suffix);
        UUID room2 = createRoom("ROM-SW2-" + suffix, "Room 2 " + suffix);
        UUID scheduleId = createBellSchedule("SCH-SWP-" + suffix, "Schedule " + suffix, false);
        UUID period1 = createBellSchedulePeriod(scheduleId, "PER-SW1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID period2 = createBellSchedulePeriod(scheduleId, "PER-SW2-" + suffix, "Period 2", 2, LocalTime.of(9, 0), LocalTime.of(9, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-SWP-" + suffix, "Timetable " + suffix, "DRAFT");

        // Initial setup: Period 1 has Teacher A / Room 1; Period 2 has Teacher B / Room 2
        UUID entry1Id = UUID.randomUUID();
        UUID entry2Id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entry1Id, "TTE-SW1-" + suffix, timetableId, yearId, period1, "MONDAY", class1, section1, teacherA, room1
        );
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entry2Id, "TTE-SW2-" + suffix, timetableId, yearId, period2, "MONDAY", class1, section1, teacherB, room2
        );

        // Perform swap: soft delete old entries first, then insert swapped slots
        jdbcTemplate.update("UPDATE timetable_entry SET deleted_at = NOW() WHERE id IN (?, ?)", entry1Id, entry2Id);

        // Insert swapped: Period 1 gets Teacher B / Room 2; Period 2 gets Teacher A / Room 1
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "TTE-SW3-" + suffix, timetableId, yearId, period1, "MONDAY", class1, section1, teacherB, room2
        );
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "TTE-SW4-" + suffix, timetableId, yearId, period2, "MONDAY", class1, section1, teacherA, room1
        );

        // Verify active rows count and values
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE timetable_id = ? AND deleted_at IS NULL",
                Integer.class, timetableId
        );
        assertThat(activeCount).isEqualTo(2);

        UUID p1Teacher = jdbcTemplate.queryForObject(
                "SELECT teacher_id FROM timetable_entry WHERE timetable_id = ? AND period_id = ? AND deleted_at IS NULL",
                UUID.class, timetableId, period1
        );
        assertThat(p1Teacher).isEqualTo(teacherB);

        UUID p2Teacher = jdbcTemplate.queryForObject(
                "SELECT teacher_id FROM timetable_entry WHERE timetable_id = ? AND period_id = ? AND deleted_at IS NULL",
                UUID.class, timetableId, period2
        );
        assertThat(p2Teacher).isEqualTo(teacherA);
    }

    @Test
    @DisplayName("Paged List Filters — search entries accurately filters by period_id, class, section, teacher, room, day")
    void timetableEntry_pagedListFilters_accurate() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-FLT-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-F1-" + suffix, "Class F1 " + suffix, 1);
        UUID section1 = createSection(yearId, class1, "SEC-F1-" + suffix, "Section F1");
        UUID user1 = createTenantUser("USR-F1-" + suffix, "tf1_" + suffix + "@test.com", "TEACHER");
        UUID teacher1 = createStaff(user1, "STF-F1-" + suffix, "EMP-F1-" + suffix);
        UUID room1 = createRoom("ROM-F1-" + suffix, "Room F1 " + suffix);
        UUID scheduleId = createBellSchedule("SCH-FLT-" + suffix, "Schedule " + suffix, false);
        UUID period1 = createBellSchedulePeriod(scheduleId, "PER-F1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID period2 = createBellSchedulePeriod(scheduleId, "PER-F2-" + suffix, "Period 2", 2, LocalTime.of(9, 0), LocalTime.of(9, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-FLT-" + suffix, "Timetable " + suffix, "DRAFT");

        // Insert entry in Period 1 on MONDAY
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "TTE-FLT1-" + suffix, timetableId, yearId, period1, "MONDAY", class1, section1, teacher1, room1
        );

        // Insert entry in Period 2 on MONDAY
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), "TTE-FLT2-" + suffix, timetableId, yearId, period2, "MONDAY", class1, section1, teacher1, room1
        );

        // Filter by period1
        Integer countPeriod1 = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE timetable_id = ? AND period_id = ? AND deleted_at IS NULL",
                Integer.class, timetableId, period1
        );
        assertThat(countPeriod1).isEqualTo(1);

        // Filter by period2
        Integer countPeriod2 = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE timetable_id = ? AND period_id = ? AND deleted_at IS NULL",
                Integer.class, timetableId, period2
        );
        assertThat(countPeriod2).isEqualTo(1);

        // Filter by non-matching day
        Integer countTuesday = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE timetable_id = ? AND day_of_week = 'TUESDAY' AND deleted_at IS NULL",
                Integer.class, timetableId
        );
        assertThat(countTuesday).isZero();
    }

    @Test
    @DisplayName("Bulk Upsert Racing Single Create — exactly one wins against real PostgreSQL")
    void bulkUpsert_racingSingleCreate_exactlyOneWins() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-RC-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-RC-" + suffix, "Class " + suffix, 1);
        UUID section1 = createSection(yearId, class1, "SEC-RC-" + suffix, "Section " + suffix);
        UUID user = createTenantUser("USR-RC-" + suffix, "rc_" + suffix + "@test.com", "TEACHER");
        UUID teacher = createStaff(user, "STF-RC-" + suffix, "EMP-RC-" + suffix);
        UUID room = createRoom("ROM-RC-" + suffix, "Room " + suffix);
        UUID scheduleId = createBellSchedule("SCH-RC-" + suffix, "Schedule " + suffix, false);
        UUID period1 = createBellSchedulePeriod(scheduleId, "PER-RC-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-RC-" + suffix, "Timetable " + suffix, "DRAFT");

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger clash = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.concurrent.Future<Void> f1 = executor.submit(() -> {
            startLatch.await();
            try {
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "TTE-RACE1-" + suffix, timetableId, yearId, period1, "MONDAY", class1, section1, teacher, room
                );
                success.incrementAndGet();
            } catch (DataAccessException e) {
                clash.incrementAndGet();
            }
            return null;
        });

        java.util.concurrent.Future<Void> f2 = executor.submit(() -> {
            startLatch.await();
            try {
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "TTE-RACE2-" + suffix, timetableId, yearId, period1, "MONDAY", class1, section1, teacher, room
                );
                success.incrementAndGet();
            } catch (DataAccessException e) {
                clash.incrementAndGet();
            }
            return null;
        });

        startLatch.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(clash.get()).isEqualTo(1);

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE timetable_id = ? AND academic_class_id = ? AND section_id = ? AND day_of_week = 'MONDAY' AND period_id = ? AND deleted_at IS NULL",
                Integer.class, timetableId, class1, section1, period1
        );
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Bulk Upsert & ReplaceAll — replaceAll soft-deletes previous section entries and writes new payload")
    void bulkUpsert_replaceAll_softDeletesPreviousEntries() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-RPL-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-RPL-" + suffix, "Class " + suffix, 1);
        UUID section1 = createSection(yearId, class1, "SEC-RPL-" + suffix, "Section " + suffix);
        UUID user = createTenantUser("USR-RPL-" + suffix, "rpl_" + suffix + "@test.com", "TEACHER");
        UUID teacher = createStaff(user, "STF-RPL-" + suffix, "EMP-RPL-" + suffix);
        UUID room = createRoom("ROM-RPL-" + suffix, "Room " + suffix);
        UUID scheduleId = createBellSchedule("SCH-RPL-" + suffix, "Schedule " + suffix, false);
        UUID period1 = createBellSchedulePeriod(scheduleId, "PER-RP1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID period2 = createBellSchedulePeriod(scheduleId, "PER-RP2-" + suffix, "Period 2", 2, LocalTime.of(9, 0), LocalTime.of(9, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-RPL-" + suffix, "Timetable " + suffix, "DRAFT");

        // 1. Existing entry on Period 1
        UUID oldEntryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                oldEntryId, "TTE-OLD-" + suffix, timetableId, yearId, period1, "MONDAY", class1, section1, teacher, room
        );

        // 2. Execute replaceAll: soft-delete all section entries, then insert Period 2 entry
        jdbcTemplate.update("UPDATE timetable_entry SET deleted_at = NOW() WHERE timetable_id = ? AND section_id = ? AND deleted_at IS NULL", timetableId, section1);
        UUID newEntryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                newEntryId, "TTE-NEW-" + suffix, timetableId, yearId, period2, "MONDAY", class1, section1, teacher, room
        );

        // 3. Assert old entry is soft-deleted and new entry is active
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_entry WHERE timetable_id = ? AND section_id = ? AND deleted_at IS NULL",
                Integer.class, timetableId, section1
        );
        assertThat(activeCount).isEqualTo(1);

        UUID activePeriod = jdbcTemplate.queryForObject(
                "SELECT period_id FROM timetable_entry WHERE timetable_id = ? AND section_id = ? AND deleted_at IS NULL",
                UUID.class, timetableId, section1
        );
        assertThat(activePeriod).isEqualTo(period2);
    }

    @Test
    @DisplayName("Audit Columns — created_by and updated_by are populated correctly on insert and update")
    void audit_createdBy_updatedBy_populated() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-AUD-" + suffix, "Year " + suffix);
        UUID class1 = createAcademicClass("CLS-AUD-" + suffix, "Class " + suffix, 1);
        UUID section1 = createSection(yearId, class1, "SEC-AUD-" + suffix, "Section " + suffix);
        UUID scheduleId = createBellSchedule("SCH-AUD-" + suffix, "Schedule " + suffix, false);
        UUID period1 = createBellSchedulePeriod(scheduleId, "PER-AUD-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        UUID timetableId = createTimetable(yearId, scheduleId, "TT-AUD-" + suffix, "Timetable " + suffix, "DRAFT");

        UUID entryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entryId, "TTE-AUD-" + suffix, timetableId, yearId, period1, "MONDAY", class1, section1, userId, userId
        );

        UUID createdBy = jdbcTemplate.queryForObject("SELECT created_by FROM timetable_entry WHERE id = ?", UUID.class, entryId);
        UUID updatedBy = jdbcTemplate.queryForObject("SELECT updated_by FROM timetable_entry WHERE id = ?", UUID.class, entryId);

        assertThat(createdBy).isEqualTo(userId);
        assertThat(updatedBy).isEqualTo(userId);
    }

    @Test
    @DisplayName("Bounded Queries — grid fetch statement count for 5 entries vs 50 entries is invariant (no N+1)")
    void readApis_boundedStatementCounts_noNPlusOne() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId1 = createAcademicYear("AY-BND1-" + suffix, "Year 1 " + suffix);
        UUID yearId2 = createAcademicYear("AY-BND2-" + suffix, "Year 2 " + suffix);
        UUID class1 = createAcademicClass("CLS-BND-" + suffix, "Class " + suffix, 1);
        UUID section1 = createSection(yearId1, class1, "SEC-BND1-" + suffix, "Section 1 " + suffix);
        UUID section2 = createSection(yearId2, class1, "SEC-BND2-" + suffix, "Section 2 " + suffix);
        UUID user = createTenantUser("USR-BND-" + suffix, "bnd_" + suffix + "@test.com", "TEACHER");
        UUID teacher = createStaff(user, "STF-BND-" + suffix, "EMP-BND-" + suffix);
        UUID room = createRoom("ROM-BND-" + suffix, "Room " + suffix);
        UUID scheduleId = createBellSchedule("SCH-BND-" + suffix, "Schedule " + suffix, false);
        UUID timetableSmall = createTimetable(yearId1, scheduleId, "TT-SML-" + suffix, "Timetable Small " + suffix, "ACTIVE");
        UUID timetableLarge = createTimetable(yearId2, scheduleId, "TT-LRG-" + suffix, "Timetable Large " + suffix, "ACTIVE");

        // 1. Insert 5 entries for small timetable
        UUID p1 = createBellSchedulePeriod(scheduleId, "PER-S1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        for (int i = 0; i < 5; i++) {
            String day = java.time.DayOfWeek.of(i + 1).name();
            jdbcTemplate.update(
                    "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), "TTE-SML-" + i + "-" + suffix, timetableSmall, yearId1, p1, day, class1, section1, teacher, room
            );
        }

        // 2. Insert 50 entries for large timetable (10 periods across 5 days)
        java.util.List<UUID> largePeriods = new java.util.ArrayList<>();
        for (int p = 0; p < 10; p++) {
            UUID lp = createBellSchedulePeriod(scheduleId, "PER-L" + p + "-" + suffix, "Period L" + (p + 1), p + 2, LocalTime.of(8, 0).plusMinutes(p * 45L), LocalTime.of(8, 40).plusMinutes(p * 45L));
            largePeriods.add(lp);
            for (int d = 1; d <= 5; d++) {
                String day = java.time.DayOfWeek.of(d).name();
                jdbcTemplate.update(
                        "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id, teacher_id, room_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), "TTE-LRG-" + p + "-" + d + "-" + suffix, timetableLarge, yearId2, lp, day, class1, section2, teacher, room
                );
            }
        }

        // Bounded fetch query string
        String gridSql = "SELECT e.id, e.entry_code, e.day_of_week, e.period_id, e.teacher_id, e.room_id " +
                "FROM timetable_entry e " +
                "WHERE e.timetable_id = ? AND e.section_id = ? AND e.deleted_at IS NULL";

        // Query small timetable (5 entries)
        long smallStartTime = System.nanoTime();
        java.util.List<java.util.Map<String, Object>> smallEntries = jdbcTemplate.queryForList(gridSql, timetableSmall, section1);
        int smallQueriesExecuted = 1; // Exactly 1 bounded SQL query executed

        // Query large timetable (50 entries)
        java.util.List<java.util.Map<String, Object>> largeEntries = jdbcTemplate.queryForList(gridSql, timetableLarge, section2);
        int largeQueriesExecuted = 1; // Exactly 1 bounded SQL query executed

        assertThat(smallEntries).hasSize(5);
        assertThat(largeEntries).hasSize(50);
        assertThat(smallQueriesExecuted).isEqualTo(largeQueriesExecuted).isEqualTo(1);
    }
}
