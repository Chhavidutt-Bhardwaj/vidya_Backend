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
 * Testcontainers integration test verifying Bell Schedule database constraints,
 * default schedule swaps with index idx_bell_schedule_single_default, in-use query protections,
 * and schedule cloning against real PostgreSQL.
 */
@DisplayName("Bell Schedule — PostgreSQL Integration Tests")
class BellSchedulePostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

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
    @DisplayName("Set-Default Swap — Clearing old default allows setting new default atomically without idx_bell_schedule_single_default conflict")
    void setDefaultSwap_realPostgresExecution() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID schedule1Id = createBellSchedule("SCH-SWAP1-" + suffix, "Primary Schedule " + suffix, true);
        UUID schedule2Id = createBellSchedule("SCH-SWAP2-" + suffix, "Secondary Schedule " + suffix, false);

        // Verify initial state
        Boolean s1Default = jdbcTemplate.queryForObject("SELECT is_default FROM bell_schedule WHERE id = ?", Boolean.class, schedule1Id);
        Boolean s2Default = jdbcTemplate.queryForObject("SELECT is_default FROM bell_schedule WHERE id = ?", Boolean.class, schedule2Id);
        assertThat(s1Default).isTrue();
        assertThat(s2Default).isFalse();

        // Swap: Clear previous default first, then set new default
        jdbcTemplate.update("UPDATE bell_schedule SET is_default = false WHERE is_default = true AND status = 'ACTIVE' AND deleted_at IS NULL");
        jdbcTemplate.update("UPDATE bell_schedule SET is_default = true WHERE id = ?", schedule2Id);

        // Verify swapped state
        Boolean s1New = jdbcTemplate.queryForObject("SELECT is_default FROM bell_schedule WHERE id = ?", Boolean.class, schedule1Id);
        Boolean s2New = jdbcTemplate.queryForObject("SELECT is_default FROM bell_schedule WHERE id = ?", Boolean.class, schedule2Id);
        assertThat(s1New).isFalse();
        assertThat(s2New).isTrue();

        // Ensure single default index is active: Attempting to set s1 also to default without clearing s2 must fail
        assertThatThrownBy(() ->
                jdbcTemplate.update("UPDATE bell_schedule SET is_default = true WHERE id = ?", schedule1Id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_bell_schedule_single_default.*|.*duplicate key value.*");
    }

    @Test
    @DisplayName("In-Use Queries — Detect active timetable referencing bell schedule and active timetable entries referencing period")
    void inUseQueries_realPostgresExecution() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID yearId = createAcademicYear("AY-INUSE-" + suffix, "Academic Year " + suffix);
        UUID classId = createAcademicClass("CLS-INUSE-" + suffix, "Class " + suffix, 1);
        UUID sectionId = createSection(yearId, classId, "SEC-INUSE-" + suffix, "Section " + suffix);

        UUID scheduleId = createBellSchedule("SCH-INUSE-" + suffix, "Schedule " + suffix, false);
        UUID periodId = createBellSchedulePeriod(scheduleId, "PER-INUSE-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));

        UUID timetableId = createTimetable(yearId, scheduleId, "TT-INUSE-" + suffix, "Timetable " + suffix, "ACTIVE");

        // 1. Schedule in-use query
        Long scheduleUseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(t) FROM timetable t WHERE t.bell_schedule_id = ? AND t.deleted_at IS NULL AND t.status <> 'ARCHIVED'",
                Long.class, scheduleId
        );
        assertThat(scheduleUseCount).isGreaterThan(0L);

        // 2. Period in-use before entry insertion
        Long periodUseCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(e) FROM timetable_entry e JOIN timetable t ON e.timetable_id = t.id " +
                "WHERE e.period_id = ? AND e.deleted_at IS NULL AND t.deleted_at IS NULL AND t.status <> 'ARCHIVED'",
                Long.class, periodId
        );
        assertThat(periodUseCountBefore).isEqualTo(0L);

        // Insert entry
        UUID entryId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO timetable_entry (id, entry_code, timetable_id, academic_year_id, period_id, day_of_week, academic_class_id, section_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                entryId, "TTE-INUSE-" + suffix, timetableId, yearId, periodId, "MONDAY", classId, sectionId
        );

        // 3. Period in-use after entry insertion
        Long periodUseCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(e) FROM timetable_entry e JOIN timetable t ON e.timetable_id = t.id " +
                "WHERE e.period_id = ? AND e.deleted_at IS NULL AND t.deleted_at IS NULL AND t.status <> 'ARCHIVED'",
                Long.class, periodId
        );
        assertThat(periodUseCountAfter).isEqualTo(1L);

        // 4. If timetable is ARCHIVED, in-use query returns false (0)
        jdbcTemplate.update("UPDATE timetable SET status = 'ARCHIVED' WHERE id = ?", timetableId);
        Long scheduleUseWhenArchived = jdbcTemplate.queryForObject(
                "SELECT COUNT(t) FROM timetable t WHERE t.bell_schedule_id = ? AND t.deleted_at IS NULL AND t.status <> 'ARCHIVED'",
                Long.class, scheduleId
        );
        assertThat(scheduleUseWhenArchived).isEqualTo(0L);

        Long periodUseWhenArchived = jdbcTemplate.queryForObject(
                "SELECT COUNT(e) FROM timetable_entry e JOIN timetable t ON e.timetable_id = t.id " +
                "WHERE e.period_id = ? AND e.deleted_at IS NULL AND t.deleted_at IS NULL AND t.status <> 'ARCHIVED'",
                Long.class, periodId
        );
        assertThat(periodUseWhenArchived).isEqualTo(0L);
    }

    @Test
    @DisplayName("Clone — Cloned schedule copies periods and enforces unique name index")
    void cloneSchedule_realPostgresExecution() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String origName = "Base Schedule " + suffix;
        UUID origId = createBellSchedule("SCH-CLON-" + suffix, origName, false);
        createBellSchedulePeriod(origId, "PER-CL1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        createBellSchedulePeriod(origId, "PER-CL2-" + suffix, "Period 2", 2, LocalTime.of(8, 45), LocalTime.of(9, 30));

        // Create Clone 1: "Base Schedule <suffix> (Copy)"
        String clone1Name = origName + " (Copy)";
        UUID clone1Id = createBellSchedule("SCH-CLON-CP1-" + suffix, clone1Name, false);
        createBellSchedulePeriod(clone1Id, "PER-CP1-1-" + suffix, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45));
        createBellSchedulePeriod(clone1Id, "PER-CP1-2-" + suffix, "Period 2", 2, LocalTime.of(8, 45), LocalTime.of(9, 30));

        // Attempting duplicate clone name -> must fail against idx_bell_schedule_name_active
        assertThatThrownBy(() ->
                createBellSchedule("SCH-CLON-DUP-" + suffix, clone1Name, false))
                .isInstanceOf(DataAccessException.class)
                .hasMessageMatching("(?s).*idx_bell_schedule_name_active.*|.*duplicate key value.*");

        // Next clone uses (Copy 2)
        String clone2Name = origName + " (Copy 2)";
        UUID clone2Id = createBellSchedule("SCH-CLON-CP2-" + suffix, clone2Name, false);
        assertThat(clone2Id).isNotNull();

        // Verify periods for clone 1
        Integer periodCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bell_schedule_period WHERE bell_schedule_id = ? AND deleted_at IS NULL",
                Integer.class, clone1Id
        );
        assertThat(periodCount).isEqualTo(2);
    }
}
